package com.pachanga.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Lo que manda la app para iniciar sesión (POST /api/auth/login). */
public record LoginPeticion(
        @NotBlank(message = "Escribe tu email")
        String email,

        @NotBlank(message = "Escribe tu contraseña")
        String password
) {
    /** Quitamos los espacios de los lados del email antes de validar. */
    public LoginPeticion {
        email = email == null ? null : email.trim();
    }
}
