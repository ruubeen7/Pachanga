package com.pachanga.api.controller;

import com.pachanga.api.dto.LoginPeticion;
import com.pachanga.api.dto.RegistroPeticion;
import com.pachanga.api.dto.SesionRespuesta;
import com.pachanga.api.servicio.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro e inicio de sesión. Son rutas públicas (no necesitan token): ver SeguridadConfig.
 *
 * POST /api/auth/registro  { "email", "nombre", "password" }  → 201 { token, usuario }
 * POST /api/auth/login     { "email", "password" }            → 200 { token, usuario }
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // @RequestBody: convierte el JSON recibido en el record. @Valid: aplica sus validaciones.
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public SesionRespuesta registrar(@Valid @RequestBody RegistroPeticion peticion) {
        return authService.registrar(peticion);
    }

    @PostMapping("/login")
    public SesionRespuesta iniciarSesion(@Valid @RequestBody LoginPeticion peticion) {
        return authService.iniciarSesion(peticion);
    }
}
