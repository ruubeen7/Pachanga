package com.pachanga.api.servicio;

import com.pachanga.api.dto.CrearDenunciaPeticion;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Denuncia;
import com.pachanga.api.modelo.TipoContenido;
import com.pachanga.api.modelo.Usuario;
import com.pachanga.api.repositorio.CanchaRepository;
import com.pachanga.api.repositorio.DenunciaRepository;
import com.pachanga.api.repositorio.EventoRepository;
import com.pachanga.api.repositorio.FotoCanchaRepository;
import com.pachanga.api.repositorio.MensajeRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Denuncias de contenido. Por ahora solo se guardan como PENDIENTE para que las revise
 * un administrador (rol ADMIN). Más adelante habrá una forma de revisarlas.
 */
@Service
public class DenunciaService {

    private final DenunciaRepository denuncias;
    private final UsuarioRepository usuarios;
    private final CanchaRepository canchas;
    private final FotoCanchaRepository fotos;
    private final EventoRepository eventos;
    private final MensajeRepository mensajes;

    public DenunciaService(DenunciaRepository denuncias, UsuarioRepository usuarios, CanchaRepository canchas,
                           FotoCanchaRepository fotos, EventoRepository eventos, MensajeRepository mensajes) {
        this.denuncias = denuncias;
        this.usuarios = usuarios;
        this.canchas = canchas;
        this.fotos = fotos;
        this.eventos = eventos;
        this.mensajes = mensajes;
    }

    @Transactional
    public void denunciar(Long usuarioId, CrearDenunciaPeticion datos) {
        Usuario denunciante = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));

        if (datos.tipo() == TipoContenido.USUARIO && datos.contenidoId().equals(usuarioId)) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "No puedes denunciarte a ti mismo");
        }
        if (!existe(datos.tipo(), datos.contenidoId())) {
            throw new ExcepcionApi(HttpStatus.NOT_FOUND, "Lo que intentas denunciar ya no existe");
        }
        if (denuncias.existsByDenuncianteIdAndTipoContenidoAndContenidoId(
                usuarioId, datos.tipo(), datos.contenidoId())) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "Ya lo habías denunciado. Lo revisaremos pronto.");
        }

        denuncias.save(new Denuncia(denunciante, datos.tipo(), datos.contenidoId(), datos.motivo(),
                datos.comentario()));
    }

    /**
     * La tabla denuncia no tiene clave foránea (contenido_id puede apuntar a tablas distintas),
     * así que comprobamos aquí que lo denunciado existe, mirando en la tabla que toca.
     * "switch" con flechas: como un switch de siempre, pero devuelve un valor y no necesita break.
     */
    private boolean existe(TipoContenido tipo, Long id) {
        return switch (tipo) {
            case CANCHA -> canchas.existsById(id);
            case FOTO -> fotos.existsById(id);
            case EVENTO -> eventos.existsById(id);
            case USUARIO -> usuarios.existsById(id);
            case MENSAJE -> mensajes.existsById(id);
        };
    }
}
