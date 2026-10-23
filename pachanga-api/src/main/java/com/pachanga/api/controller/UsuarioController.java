package com.pachanga.api.controller;

import com.pachanga.api.dto.UsuarioRespuesta;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Datos de usuarios. Todas estas rutas necesitan token. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarios;

    public UsuarioController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /**
     * GET /api/usuarios/yo → los datos del usuario que hace la petición.
     * La app lo llama al abrirse para comprobar que el token guardado sigue siendo válido.
     *
     * @AuthenticationPrincipal nos da el token ya comprobado por Spring Security;
     * su "subject" es el id del usuario (ver TokenService).
     */
    @GetMapping("/yo")
    public UsuarioRespuesta yo(@AuthenticationPrincipal Jwt token) {
        Long id = Long.valueOf(token.getSubject());
        return usuarios.findById(id)
                .map(UsuarioRespuesta::de)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));
    }
}
