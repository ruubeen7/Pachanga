package com.pachanga.api.dto;

import com.pachanga.api.modelo.MotivoDenuncia;
import com.pachanga.api.modelo.TipoContenido;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lo que manda la app para denunciar algo (POST /api/denuncias).
 * tipo + contenidoId dicen QUÉ se denuncia: por ejemplo FOTO 12 = la foto con id 12.
 */
public record CrearDenunciaPeticion(
        @NotNull(message = "Falta qué se denuncia")
        TipoContenido tipo,

        @NotNull(message = "Falta qué se denuncia")
        Long contenidoId,

        @NotNull(message = "Elige un motivo")
        MotivoDenuncia motivo,

        @Size(max = 500, message = "El comentario es demasiado largo (máximo 500 caracteres)")
        String comentario
) {
    public CrearDenunciaPeticion {
        comentario = comentario == null || comentario.isBlank() ? null : comentario.trim();
    }
}
