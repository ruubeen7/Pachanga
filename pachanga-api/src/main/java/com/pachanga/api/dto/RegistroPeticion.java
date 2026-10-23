package com.pachanga.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que manda la app para crear una cuenta (POST /api/auth/registro).
 * Las anotaciones validan los datos antes de que lleguen al servicio: si algo no cumple,
 * se devuelve un 400 con el mensaje de cada campo.
 */
public record RegistroPeticion(
        @NotBlank(message = "Escribe tu email")
        @Email(message = "El email no es válido")
        @Size(max = 255, message = "El email es demasiado largo")
        String email,

        @NotBlank(message = "Escribe tu nombre")
        @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
        String nombre,

        // BCrypt solo tiene en cuenta los primeros 72 bytes, por eso el máximo.
        @NotBlank(message = "Escribe una contraseña")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password
) {
    /**
     * Constructor compacto: se ejecuta al crear el record, antes de validar.
     * Quitamos los espacios de los lados (en el móvil es fácil que se cuele uno al final).
     */
    public RegistroPeticion {
        email = email == null ? null : email.trim();
        nombre = nombre == null ? null : nombre.trim();
    }
}
