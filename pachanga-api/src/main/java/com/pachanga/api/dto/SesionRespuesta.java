package com.pachanga.api.dto;

/**
 * Respuesta al registrarse o iniciar sesión.
 *
 * @param token   el JWT que la app debe mandar en cada petición ("Authorization: Bearer ...")
 * @param usuario los datos del usuario, para mostrarlos sin hacer otra petición
 */
public record SesionRespuesta(String token, UsuarioRespuesta usuario) {
}
