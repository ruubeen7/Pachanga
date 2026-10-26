package com.pachanga.api.dto;

import com.pachanga.api.modelo.Usuario;

/**
 * Lo que se puede enseñar de OTRO usuario: su id y su nombre. Nada de email ni de otros datos.
 * La app usa el id para saber si eres tú (por ejemplo, "Organizas tú").
 */
public record UsuarioPublico(Long id, String nombre) {

    public static UsuarioPublico de(Usuario usuario) {
        return new UsuarioPublico(usuario.getId(), usuario.getNombre());
    }
}
