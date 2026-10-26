package com.pachanga.api.servicio;

import com.pachanga.api.dto.CanchaResumen;
import com.pachanga.api.dto.CrearEventoPeticion;
import com.pachanga.api.dto.EventoDetalle;
import com.pachanga.api.dto.EventoResumen;
import com.pachanga.api.dto.MisEventos;
import com.pachanga.api.dto.UsuarioPublico;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.modelo.EstadoEvento;
import com.pachanga.api.modelo.Evento;
import com.pachanga.api.modelo.Inscripcion;
import com.pachanga.api.modelo.NivelEvento;
import com.pachanga.api.modelo.Usuario;
import com.pachanga.api.repositorio.EventoRepository;
import com.pachanga.api.repositorio.InscripcionRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Eventos (pachangas y entrenos): crearlos, verlos, apuntarse, desapuntarse y cancelarlos.
 * Aquí están TODAS las reglas; la app solo enseña lo que le decimos y manda las acciones.
 */
@Service
@Transactional(readOnly = true)
public class EventoService {

    /** Un evento no puede durar más de esto (evita errores como poner fin al día siguiente). */
    public static final Duration DURACION_MAXIMA = Duration.ofHours(8);
    /** Hasta cuántos días antes se puede organizar algo. */
    public static final int DIAS_ANTELACION_MAXIMA = 90;
    /** Margen por si el reloj del móvil va un poco adelantado o atrasado. */
    private static final Duration MARGEN_RELOJ = Duration.ofMinutes(5);
    /** Cuántos eventos pasados se enseñan en el perfil. */
    private static final int MAX_PASADOS = 20;

    private final EventoRepository eventos;
    private final InscripcionRepository inscripciones;
    private final UsuarioRepository usuarios;
    private final CanchaService canchas;
    private final BloqueoService bloqueos;
    private final AvisoService avisos;

    public EventoService(EventoRepository eventos, InscripcionRepository inscripciones, UsuarioRepository usuarios,
                         CanchaService canchas, BloqueoService bloqueos, AvisoService avisos) {
        this.eventos = eventos;
        this.inscripciones = inscripciones;
        this.usuarios = usuarios;
        this.canchas = canchas;
        this.bloqueos = bloqueos;
        this.avisos = avisos;
    }

    /** Organiza un evento. Quien lo crea queda apuntado automáticamente (ocupa una plaza). */
    @Transactional
    public EventoDetalle crear(Long usuarioId, CrearEventoPeticion datos) {
        Usuario organizador = buscarUsuario(usuarioId);
        Cancha cancha = canchas.buscarCancha(datos.canchaId());
        comprobarFechas(datos.inicio(), datos.fin());

        Evento evento = new Evento(cancha, organizador, datos.tipo(), datos.titulo(),
                datos.inicio(), datos.fin(), datos.plazas());
        evento.setDescripcion(datos.descripcion());
        evento.setNivel(datos.nivel() == null ? NivelEvento.TODOS : datos.nivel());
        eventos.save(evento);

        inscripciones.save(new Inscripcion(evento, organizador));
        return detalle(evento, usuarioId);
    }

    /** La ficha de un evento, vista por el usuario que pregunta. */
    public EventoDetalle detalle(Long eventoId, Long usuarioId) {
        return detalle(buscarEventoVisible(eventoId, usuarioId), usuarioId);
    }

    /** Apunta al usuario al evento si quedan plazas. */
    @Transactional
    public EventoDetalle apuntarse(Long eventoId, Long usuarioId) {
        // Bloqueamos el evento para que nadie más se apunte mientras contamos las plazas.
        Evento evento = eventos.buscarBloqueando(eventoId)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.NOT_FOUND, "Ese evento no existe"));
        if (bloqueos.hayBloqueoEntre(usuarioId, evento.getOrganizador().getId())) {
            throw new ExcepcionApi(HttpStatus.NOT_FOUND, "Ese evento no existe");
        }
        comprobarQueSigueEnPie(evento);

        if (inscripciones.existsByEventoIdAndUsuarioId(eventoId, usuarioId)) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "Ya estás apuntado a este evento");
        }
        if (inscripciones.countByEventoId(eventoId) >= evento.getPlazas()) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "Ya no quedan plazas");
        }

        Usuario nuevo = buscarUsuario(usuarioId);
        inscripciones.save(new Inscripcion(evento, nuevo));
        avisos.alApuntarse(evento, nuevo, inscripciones.countByEventoId(eventoId));
        return detalle(evento, usuarioId);
    }

    /** Quita al usuario del evento. El organizador no puede: si no va, tiene que cancelarlo. */
    @Transactional
    public EventoDetalle desapuntarse(Long eventoId, Long usuarioId) {
        Evento evento = buscarEvento(eventoId);
        if (esOrganizador(evento, usuarioId)) {
            throw new ExcepcionApi(HttpStatus.CONFLICT,
                    "Organizas este evento: si no puedes ir, cancélalo");
        }
        comprobarQueSigueEnPie(evento);

        Inscripcion inscripcion = inscripciones.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.CONFLICT, "No estás apuntado a este evento"));
        inscripciones.delete(inscripcion);
        return detalle(evento, usuarioId);
    }

    /**
     * Cancela el evento. Solo puede hacerlo quien lo organiza.
     * No se borra: se marca como CANCELADO para que los apuntados vean qué ha pasado.
     */
    @Transactional
    public EventoDetalle cancelar(Long eventoId, Long usuarioId) {
        Evento evento = buscarEvento(eventoId);
        if (!esOrganizador(evento, usuarioId)) {
            throw new ExcepcionApi(HttpStatus.FORBIDDEN, "Solo quien organiza el evento puede cancelarlo");
        }
        comprobarQueSigueEnPie(evento);

        evento.setEstado(EstadoEvento.CANCELADO);
        avisos.alCancelar(evento);
        // No hace falta llamar a save(): la entidad está "gestionada" por JPA dentro de la transacción,
        // y al terminar Hibernate guarda solo los cambios (UPDATE evento SET estado = ...).
        return detalle(evento, usuarioId);
    }

    /** Eventos del usuario (organizados o apuntado) para su perfil. */
    public MisEventos misEventos(Long usuarioId) {
        Instant ahora = Instant.now();
        List<EventoResumen> proximos = inscripciones.proximosDe(usuarioId, ahora).stream()
                .map(this::resumen)
                .toList();
        List<EventoResumen> pasados = inscripciones.pasadosDe(usuarioId, ahora, PageRequest.of(0, MAX_PASADOS))
                .stream()
                .map(this::resumen)
                .toList();
        return new MisEventos(proximos, pasados);
    }

    // ------------------------------------------------------------------ ayudantes

    private EventoDetalle detalle(Evento evento, Long usuarioId) {
        List<UsuarioPublico> todos = inscripciones.findByEventoIdOrderByCreadoEnAsc(evento.getId()).stream()
                .map(i -> UsuarioPublico.de(i.getUsuario()))
                .toList();
        boolean apuntado = todos.stream().anyMatch(u -> u.id().equals(usuarioId));

        // En la lista no enseñamos a quien tenga un bloqueo contigo, pero sí cuenta para las plazas.
        Set<Long> ocultos = bloqueos.ocultosPara(usuarioId);
        List<UsuarioPublico> visibles = todos.stream().filter(u -> !ocultos.contains(u.id())).toList();

        return new EventoDetalle(evento.getId(), evento.getTipo(), evento.getTitulo(), evento.getDescripcion(),
                evento.getInicio(), evento.getFin(), evento.getPlazas(), todos.size(), evento.getNivel(),
                evento.getEstado(), CanchaResumen.de(evento.getCancha(), null),
                UsuarioPublico.de(evento.getOrganizador()), visibles, apuntado, esOrganizador(evento, usuarioId));
    }

    /**
     * Como buscarEvento, pero si hay un bloqueo con el organizador responde igual que si no existiera.
     * Así quien ha sido bloqueado no puede ni ver ni apuntarse a los eventos de quien le bloqueó.
     */
    Evento buscarEventoVisible(Long id, Long usuarioId) {
        Evento evento = buscarEvento(id);
        if (bloqueos.hayBloqueoEntre(usuarioId, evento.getOrganizador().getId())) {
            throw new ExcepcionApi(HttpStatus.NOT_FOUND, "Ese evento no existe");
        }
        return evento;
    }

    private EventoResumen resumen(Evento evento) {
        return EventoResumen.de(evento, inscripciones.countByEventoId(evento.getId()));
    }

    /** Reglas de fechas al crear. Las fechas llegan en UTC, así que se comparan sin líos de zonas horarias. */
    private static void comprobarFechas(Instant inicio, Instant fin) {
        Instant ahora = Instant.now();
        if (!fin.isAfter(inicio)) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "La hora de fin tiene que ser después de la de inicio");
        }
        if (inicio.isBefore(ahora.minus(MARGEN_RELOJ))) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "El evento no puede empezar en el pasado");
        }
        if (Duration.between(inicio, fin).compareTo(DURACION_MAXIMA) > 0) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST,
                    "Un evento puede durar como mucho " + DURACION_MAXIMA.toHours() + " horas");
        }
        if (inicio.isAfter(ahora.plus(Duration.ofDays(DIAS_ANTELACION_MAXIMA)))) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST,
                    "Solo se pueden organizar eventos para los próximos " + DIAS_ANTELACION_MAXIMA + " días");
        }
    }

    /** Un evento cancelado o que ya terminó no admite cambios. */
    private static void comprobarQueSigueEnPie(Evento evento) {
        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "Este evento está cancelado");
        }
        if (!evento.getFin().isAfter(Instant.now())) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "Este evento ya ha terminado");
        }
    }

    /** getOrganizador() es LAZY, pero pedir solo su id no hace ninguna consulta. */
    private static boolean esOrganizador(Evento evento, Long usuarioId) {
        return evento.getOrganizador().getId().equals(usuarioId);
    }

    private Evento buscarEvento(Long id) {
        return eventos.findById(id)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.NOT_FOUND, "Ese evento no existe"));
    }

    private Usuario buscarUsuario(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));
    }
}
