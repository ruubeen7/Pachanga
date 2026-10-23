package com.pachanga.api.dto;

import com.pachanga.api.modelo.Nivel;
import com.pachanga.api.modelo.Usuario;

/**
 * Datos de un usuario que se envían a la app.
 * Nunca devolvemos la entidad Usuario directamente: así el hash de la contraseña
 * (y cualquier campo interno) no sale nunca de la API.
 */
public record UsuarioRespuesta(Long id, String email, String nombre, Nivel nivel, String fotoUrl) {

    public static UsuarioRespuesta de(Usuario usuario) {
        return new UsuarioRespuesta(usuario.getId(), usuario.getEmail(), usuario.getNombre(),
                usuario.getNivel(), usuario.getFotoUrl());
    }
}
