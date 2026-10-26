package com.pachanga.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El token de notificaciones del móvil (PUT /api/usuarios/yo/push-token).
 * Los tokens de Expo tienen la forma "ExponentPushToken[xxxxxxxx]"; si no la tienen, no los guardamos.
 */
public record PushTokenPeticion(
        @NotBlank(message = "Falta el token")
        @Size(max = 255, message = "El token no es válido")
        @Pattern(regexp = "^Expo(nent)?PushToken\\[.+]$", message = "El token no es válido")
        String token
) {
}
