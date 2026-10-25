package com.pachanga.api.dto;

import com.pachanga.api.modelo.Superficie;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lo que manda la app para dar de alta una cancha (POST /api/canchas).
 * Las fotos no van aquí: se suben después, una a una, a POST /api/canchas/{id}/fotos.
 *
 * Los campos opcionales pueden venir a null. Usamos Boolean (con mayúscula) en vez de boolean
 * para distinguir "no lo ha dicho" (null) de "ha dicho que no" (false).
 */
public record CrearCanchaPeticion(
        @NotBlank(message = "Ponle un nombre a la cancha")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
        String nombre,

        @Size(max = 1000, message = "La descripción es demasiado larga (máximo 1000 caracteres)")
        String descripcion,

        @NotNull(message = "Falta la ubicación")
        @DecimalMin(value = "-90", message = "La latitud no es válida")
        @DecimalMax(value = "90", message = "La latitud no es válida")
        Double latitud,

        @NotNull(message = "Falta la ubicación")
        @DecimalMin(value = "-180", message = "La longitud no es válida")
        @DecimalMax(value = "180", message = "La longitud no es válida")
        Double longitud,

        @Size(max = 255, message = "La dirección es demasiado larga")
        String direccion,

        @NotBlank(message = "Escribe la ciudad")
        @Size(max = 100, message = "La ciudad es demasiado larga")
        String ciudad,

        Superficie superficie,

        @Min(value = 1, message = "Pon entre 1 y 20 canastas")
        @Max(value = 20, message = "Pon entre 1 y 20 canastas")
        Integer numCanastas,

        Boolean cubierta,
        Boolean iluminacion,
        Boolean gratuita
) {
    /** Quitamos espacios sobrantes; un texto opcional vacío se guarda como null. */
    public CrearCanchaPeticion {
        nombre = nombre == null ? null : nombre.trim();
        ciudad = ciudad == null ? null : ciudad.trim();
        descripcion = vacioANull(descripcion);
        direccion = vacioANull(direccion);
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
