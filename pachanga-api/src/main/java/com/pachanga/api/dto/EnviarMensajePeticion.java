package com.pachanga.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo que manda la app al escribir en el chat (POST /api/eventos/{id}/mensajes). */
public record EnviarMensajePeticion(
        @NotBlank(message = "Escribe algo")
        @Size(max = 500, message = "El mensaje es demasiado largo (máximo 500 caracteres)")
        String texto
) {
    public EnviarMensajePeticion {
        texto = texto == null ? null : texto.trim();
    }
}
