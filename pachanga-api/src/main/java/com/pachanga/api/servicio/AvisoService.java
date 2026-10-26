package com.pachanga.api.servicio;

import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Evento;
import com.pachanga.api.modelo.Mensaje;
import com.pachanga.api.modelo.Usuario;
import com.pachanga.api.notificacion.Aviso;
import com.pachanga.api.notificacion.EnvioAvisos;
import com.pachanga.api.repositorio.EventoRepository;
import com.pachanga.api.repositorio.InscripcionRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decide QUIÉN recibe una notificación y con QUÉ texto. El envío en sí lo hace EnvioAvisos.
 *
 * Avisos que hay:
 * - Alguien se apunta a tu evento → al organizador.
 * - Se cancela un evento → a los apuntados (menos el organizador, que es quien lo cancela).
 * - Mensaje nuevo en el chat → a los demás apuntados (menos quien tenga un bloqueo con el autor).
 * - Falta menos de una hora para un evento → a todos los apuntados.
 */
@Service
public class AvisoService {

    private static final Logger log = LoggerFactory.getLogger(AvisoService.class);

    /** Con cuánta antelación se manda el recordatorio. */
    public static final Duration ANTELACION_RECORDATORIO = Duration.ofHours(1);
    /** Los mensajes largos del chat se recortan en la notificación. */
    private static final int LARGO_MAXIMO = 120;

    private final InscripcionRepository inscripciones;
    private final UsuarioRepository usuarios;
    private final EventoRepository eventos;
    private final BloqueoService bloqueos;
    private final EnvioAvisos envio;
    private final boolean recordatoriosActivos;

    public AvisoService(InscripcionRepository inscripciones, UsuarioRepository usuarios, EventoRepository eventos,
                        BloqueoService bloqueos, EnvioAvisos envio,
                        @Value("${pachanga.recordatorios.activo:true}") boolean recordatoriosActivos) {
        this.inscripciones = inscripciones;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.bloqueos = bloqueos;
        this.envio = envio;
        this.recordatoriosActivos = recordatoriosActivos;
    }

    // ------------------------------------------------------------------ token del móvil

    /**
     * Guarda el token de notificaciones del móvil del usuario.
     * Si ese móvil estaba asociado a otra cuenta (alguien cerró sesión y entró otro),
     * se lo quitamos a la otra: si no, le llegarían los avisos de la cuenta nueva.
     */
    @Transactional
    public void guardarToken(Long usuarioId, String token) {
        usuarios.quitarPushToken(token);
        buscarUsuario(usuarioId).setPushToken(token);
    }

    /** Al cerrar sesión: ese móvil deja de recibir avisos de esta cuenta. */
    @Transactional
    public void borrarToken(Long usuarioId) {
        buscarUsuario(usuarioId).setPushToken(null);
    }

    // ------------------------------------------------------------------ avisos

    /** Se llama dentro de la transacción de EventoService.apuntarse. */
    public void alApuntarse(Evento evento, Usuario nuevo, long inscritos) {
        String token = evento.getOrganizador().getPushToken();
        if (token == null) return;
        enviar(List.of(new Aviso(token, "🏀 Nuevo apuntado",
                nuevo.getNombre() + " se ha apuntado a «" + evento.getTitulo() + "» ("
                        + inscritos + "/" + evento.getPlazas() + ")",
                "/evento/" + evento.getId())));
    }

    /** Se llama dentro de la transacción de EventoService.cancelar. */
    public void alCancelar(Evento evento) {
        Long organizador = evento.getOrganizador().getId();
        List<Aviso> avisos = inscripciones.apuntadosConAvisos(evento.getId()).stream()
                .filter(u -> !u.getId().equals(organizador))
                .map(u -> new Aviso(u.getPushToken(), "❌ Evento cancelado",
                        "Se ha cancelado «" + evento.getTitulo() + "» en " + evento.getCancha().getNombre() + ".",
                        "/evento/" + evento.getId()))
                .toList();
        enviar(avisos);
    }

    /** Se llama dentro de la transacción de MensajeService.enviar. */
    public void alEscribir(Evento evento, Mensaje mensaje) {
        Usuario autor = mensaje.getAutor();
        Set<Long> ocultos = bloqueos.ocultosPara(autor.getId());
        List<Aviso> avisos = inscripciones.apuntadosConAvisos(evento.getId()).stream()
                .filter(u -> !u.getId().equals(autor.getId()) && !ocultos.contains(u.getId()))
                .map(u -> new Aviso(u.getPushToken(), "💬 " + evento.getTitulo(),
                        autor.getNombre() + ": " + recortar(mensaje.getTexto()),
                        "/chat/" + evento.getId()))
                .toList();
        enviar(avisos);
    }

    /**
     * @Scheduled: Spring llama a este método solo, cada 5 minutos (y la primera vez, 1 minuto
     * después de arrancar). Se puede apagar con pachanga.recordatorios.activo=false (los tests lo hacen,
     * para que no toquen los eventos de tu base de datos).
     *
     * Ojo: en Render gratis el servidor se "duerme" si nadie lo usa en 15 minutos, y dormido no
     * ejecuta tareas. En ese caso el recordatorio puede llegar tarde o no llegar.
     */
    @Scheduled(fixedDelayString = "${pachanga.recordatorios.cada:PT5M}", initialDelayString = "PT1M")
    public void tareaRecordatorios() {
        if (!recordatoriosActivos) return;
        try {
            enviarRecordatorios();
        } catch (Exception e) {
            // Si falla (por ejemplo, la base de datos no responde), se intenta en la siguiente vuelta.
            log.warn("No se pudieron enviar los recordatorios: {}", e.getMessage());
        }
    }

    /** Avisa de los eventos que empiezan en menos de una hora y los marca para no repetir. */
    @Transactional
    public void enviarRecordatorios() {
        Instant ahora = Instant.now();
        List<Aviso> avisos = new ArrayList<>();
        for (Evento evento : eventos.pendientesDeRecordatorio(ahora, ahora.plus(ANTELACION_RECORDATORIO))) {
            // Redondeamos hacia arriba: si faltan 54 min y 10 s, decimos "55 min".
            long minutos = (Duration.between(ahora, evento.getInicio()).getSeconds() + 59) / 60;
            for (Usuario u : inscripciones.apuntadosConAvisos(evento.getId())) {
                avisos.add(new Aviso(u.getPushToken(), "⏰ ¡Empieza pronto!",
                        "«" + evento.getTitulo() + "» empieza en " + minutos + " min en "
                                + evento.getCancha().getNombre() + ".",
                        "/evento/" + evento.getId()));
            }
            // Como el evento está "gestionado" por JPA, al terminar la transacción se guarda solo.
            evento.setRecordatorioEnviado(true);
        }
        enviar(avisos);
    }

    // ------------------------------------------------------------------ ayudantes

    private void enviar(List<Aviso> avisos) {
        if (!avisos.isEmpty()) envio.enviar(avisos);
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_MAXIMO ? texto : texto.substring(0, LARGO_MAXIMO - 1) + "…";
    }

    private Usuario buscarUsuario(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));
    }
}
