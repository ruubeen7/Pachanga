package com.pachanga.api.controller;

import com.pachanga.api.dto.PushTokenPeticion;
import com.pachanga.api.dto.UsuarioPublico;
import com.pachanga.api.dto.UsuarioRespuesta;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.repositorio.UsuarioRepository;
import com.pachanga.api.servicio.AvisoService;
import com.pachanga.api.servicio.BloqueoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Datos de usuarios y bloqueos. Todas estas rutas necesitan token. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarios;
    private final BloqueoService bloqueos;
    private final AvisoService avisos;

    public UsuarioController(UsuarioRepository usuarios, BloqueoService bloqueos, AvisoService avisos) {
        this.usuarios = usuarios;
        this.bloqueos = bloqueos;
        this.avisos = avisos;
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
        return usuarios.findById(usuario(token))
                .map(UsuarioRespuesta::de)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));
    }

    /**
     * PUT /api/usuarios/yo/push-token → guarda el token de notificaciones de este móvil.
     * PUT (y no POST) porque sustituye el que hubiera: repetirlo deja todo igual.
     */
    @PutMapping("/yo/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarPushToken(@AuthenticationPrincipal Jwt token, @Valid @RequestBody PushTokenPeticion datos) {
        avisos.guardarToken(usuario(token), datos.token());
    }

    /** DELETE /api/usuarios/yo/push-token → deja de recibir notificaciones (al cerrar sesión). */
    @DeleteMapping("/yo/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarPushToken(@AuthenticationPrincipal Jwt token) {
        avisos.borrarToken(usuario(token));
    }

    /** GET /api/usuarios/bloqueados → a quién he bloqueado. */
    @GetMapping("/bloqueados")
    public List<UsuarioPublico> bloqueados(@AuthenticationPrincipal Jwt token) {
        return bloqueos.bloqueados(usuario(token));
    }

    /** POST /api/usuarios/7/bloqueo → bloqueo al usuario 7. 204 = hecho, sin nada que devolver. */
    @PostMapping("/{id}/bloqueo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bloquear(@AuthenticationPrincipal Jwt token, @PathVariable Long id) {
        bloqueos.bloquear(usuario(token), id);
    }

    /** DELETE /api/usuarios/7/bloqueo → lo desbloqueo. */
    @DeleteMapping("/{id}/bloqueo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@AuthenticationPrincipal Jwt token, @PathVariable Long id) {
        bloqueos.desbloquear(usuario(token), id);
    }

    private static Long usuario(Jwt token) {
        return Long.valueOf(token.getSubject());
    }
}
