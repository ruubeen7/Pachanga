package com.pachanga.api.servicio;

import com.pachanga.api.dto.MensajeRespuesta;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Evento;
import com.pachanga.api.modelo.Mensaje;
import com.pachanga.api.modelo.Usuario;
import com.pachanga.api.repositorio.InscripcionRepository;
import com.pachanga.api.repositorio.MensajeRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Chat de los eventos. Solo pueden leer y escribir los apuntados (el organizador también lo está).
 *
 * No es "en tiempo real" con conexiones abiertas: la app pregunta cada pocos segundos
 * "¿hay mensajes después del último que tengo?" (a esto se le llama polling). Es más sencillo,
 * funciona en cualquier hosting gratuito y para un chat de una pachanga es más que suficiente.
 */
@Service
@Transactional(readOnly = true)
public class MensajeService {

    /** Como mucho, cuántos mensajes se devuelven de una vez. */
    public static final int MAX_MENSAJES = 100;

    private final MensajeRepository mensajes;
    private final InscripcionRepository inscripciones;
    private final UsuarioRepository usuarios;
    private final EventoService eventos;
    private final BloqueoService bloqueos;

    public MensajeService(MensajeRepository mensajes, InscripcionRepository inscripciones,
                          UsuarioRepository usuarios, EventoService eventos, BloqueoService bloqueos) {
        this.mensajes = mensajes;
        this.inscripciones = inscripciones;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.bloqueos = bloqueos;
    }

    /**
     * Mensajes del chat, del más viejo al más nuevo.
     * Sin {@code despuesDe}: los últimos 100 (al abrir el chat).
     * Con {@code despuesDe}: solo los que llegaron después de ese id (las consultas periódicas).
     * No salen los mensajes de usuarios con los que hay un bloqueo.
     */
    public List<MensajeRespuesta> leer(Long eventoId, Long usuarioId, Long despuesDe) {
        Evento evento = eventoDelChat(eventoId, usuarioId);
        PageRequest limite = PageRequest.of(0, MAX_MENSAJES);

        List<Mensaje> lista;
        if (despuesDe == null) {
            // Vienen del más nuevo al más viejo; les damos la vuelta para pintarlos en orden.
            lista = new ArrayList<>(mensajes.ultimos(evento.getId(), limite));
            Collections.reverse(lista);
        } else {
            lista = mensajes.posterioresA(evento.getId(), despuesDe, limite);
        }

        Set<Long> ocultos = bloqueos.ocultosPara(usuarioId);
        return lista.stream()
                .filter(m -> !ocultos.contains(m.getAutor().getId()))
                .map(m -> MensajeRespuesta.de(m, usuarioId))
                .toList();
    }

    /** Escribe un mensaje en el chat. */
    @Transactional
    public MensajeRespuesta enviar(Long eventoId, Long usuarioId, String texto) {
        Evento evento = eventoDelChat(eventoId, usuarioId);
        Usuario autor = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));
        Mensaje mensaje = mensajes.save(new Mensaje(evento, autor, texto));
        return MensajeRespuesta.de(mensaje, usuarioId);
    }

    /** El evento, si existe, lo puedes ver (sin bloqueos con el organizador) y estás apuntado. */
    private Evento eventoDelChat(Long eventoId, Long usuarioId) {
        Evento evento = eventos.buscarEventoVisible(eventoId, usuarioId);
        if (!inscripciones.existsByEventoIdAndUsuarioId(eventoId, usuarioId)) {
            throw new ExcepcionApi(HttpStatus.FORBIDDEN, "Apúntate al evento para ver el chat");
        }
        return evento;
    }
}
