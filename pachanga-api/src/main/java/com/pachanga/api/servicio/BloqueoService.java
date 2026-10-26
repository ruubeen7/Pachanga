package com.pachanga.api.servicio;

import com.pachanga.api.dto.UsuarioPublico;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Bloqueo;
import com.pachanga.api.modelo.Usuario;
import com.pachanga.api.repositorio.BloqueoRepository;
import com.pachanga.api.repositorio.InscripcionRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bloqueos entre usuarios. Un bloqueo funciona en LAS DOS direcciones: si A bloquea a B,
 * ninguno ve los eventos ni las fotos del otro, y no salen en las listas de apuntados del otro.
 * Así B no puede seguir molestando a A, y tampoco sabe quién le ha bloqueado.
 */
@Service
@Transactional(readOnly = true)
public class BloqueoService {

    private final BloqueoRepository bloqueos;
    private final UsuarioRepository usuarios;
    private final InscripcionRepository inscripciones;

    public BloqueoService(BloqueoRepository bloqueos, UsuarioRepository usuarios,
                          InscripcionRepository inscripciones) {
        this.bloqueos = bloqueos;
        this.usuarios = usuarios;
        this.inscripciones = inscripciones;
    }

    /**
     * Bloquea a otro usuario. Si ya estaba bloqueado no pasa nada (se puede repetir sin error).
     * Además, cada uno sale de los próximos eventos que organiza el otro.
     */
    @Transactional
    public void bloquear(Long yo, Long otroId) {
        if (yo.equals(otroId)) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "No puedes bloquearte a ti mismo");
        }
        Usuario otro = usuarios.findById(otroId)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.NOT_FOUND, "Ese usuario no existe"));
        if (bloqueos.existsByBloqueadorIdAndBloqueadoId(yo, otroId)) return;

        Usuario bloqueador = usuarios.findById(yo)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));
        bloqueos.save(new Bloqueo(bloqueador, otro));

        Instant ahora = Instant.now();
        inscripciones.quitarDeEventosDe(otroId, yo, ahora); // el bloqueado sale de mis eventos
        inscripciones.quitarDeEventosDe(yo, otroId, ahora); // y yo de los suyos
    }

    /** Quita el bloqueo. Si no existía, tampoco pasa nada. */
    @Transactional
    public void desbloquear(Long yo, Long otroId) {
        bloqueos.findByBloqueadorIdAndBloqueadoId(yo, otroId).ifPresent(bloqueos::delete);
    }

    /** A quién he bloqueado yo (para poder desbloquearlos desde el perfil). */
    public List<UsuarioPublico> bloqueados(Long yo) {
        return bloqueos.findByBloqueadorIdOrderByCreadoEnDesc(yo).stream()
                .map(b -> UsuarioPublico.de(b.getBloqueado()))
                .toList();
    }

    /**
     * Ids de los usuarios cuyo contenido NO debe ver {@code yo}:
     * los que he bloqueado y los que me han bloqueado.
     * Un Set es como una lista sin repetidos, y comprobar si contiene algo es muy rápido.
     */
    public Set<Long> ocultosPara(Long yo) {
        Set<Long> ids = new HashSet<>(bloqueos.idsBloqueadosPor(yo));
        ids.addAll(bloqueos.idsQueHanBloqueadoA(yo));
        return ids;
    }

    public boolean hayBloqueoEntre(Long a, Long b) {
        return bloqueos.hayBloqueoEntre(a, b);
    }
}
