package com.pachanga.api.dto;

import com.pachanga.api.modelo.Mensaje;

import java.time.Instant;

/**
 * Un mensaje del chat de un evento.
 * mio = true si lo ha escrito quien pregunta: la app lo pinta a la derecha y en naranja.
 */
public record MensajeRespuesta(Long id, String texto, Instant creadoEn, UsuarioPublico autor, boolean mio) {

    public static MensajeRespuesta de(Mensaje mensaje, Long usuarioId) {
        return new MensajeRespuesta(mensaje.getId(), mensaje.getTexto(), mensaje.getCreadoEn(),
                UsuarioPublico.de(mensaje.getAutor()), mensaje.getAutor().getId().equals(usuarioId));
    }
}
