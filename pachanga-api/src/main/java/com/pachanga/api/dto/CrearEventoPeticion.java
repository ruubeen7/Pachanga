package com.pachanga.api.dto;

import com.pachanga.api.modelo.NivelEvento;
import com.pachanga.api.modelo.TipoEvento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Lo que manda la app para organizar una pachanga o un entreno (POST /api/eventos).
 *
 * inicio y fin llegan como texto ISO en UTC, por ejemplo "2026-11-01T17:00:00Z",
 * y Spring los convierte solo a Instant. Las reglas que mezclan varios campos
 * (que fin sea después de inicio, que no sea en el pasado...) se comprueban en EventoService.
 *
 * nivel es opcional: si no viene, TODOS (cualquiera puede apuntarse).
 */
public record CrearEventoPeticion(
        @NotNull(message = "Falta la cancha")
        Long canchaId,

        @NotNull(message = "Elige si es pachanga o entreno")
        TipoEvento tipo,

        @NotBlank(message = "Ponle un título")
        @Size(min = 3, max = 100, message = "El título debe tener entre 3 y 100 caracteres")
        String titulo,

        @Size(max = 1000, message = "La descripción es demasiado larga (máximo 1000 caracteres)")
        String descripcion,

        @NotNull(message = "Elige el día y la hora de inicio")
        Instant inicio,

        @NotNull(message = "Elige la hora de fin")
        Instant fin,

        @NotNull(message = "Di cuántas plazas hay")
        @Min(value = 2, message = "Tiene que haber entre 2 y 50 plazas")
        @Max(value = 50, message = "Tiene que haber entre 2 y 50 plazas")
        Integer plazas,

        NivelEvento nivel
) {
    /** Quitamos espacios sobrantes; una descripción vacía se guarda como null. */
    public CrearEventoPeticion {
        titulo = titulo == null ? null : titulo.trim();
        descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
    }
}
