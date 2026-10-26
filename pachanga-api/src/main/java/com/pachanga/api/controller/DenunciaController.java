package com.pachanga.api.controller;

import com.pachanga.api.dto.CrearDenunciaPeticion;
import com.pachanga.api.servicio.DenunciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Denuncias de contenido. Necesita token. */
@RestController
@RequestMapping("/api/denuncias")
public class DenunciaController {

    private final DenunciaService servicio;

    public DenunciaController(DenunciaService servicio) {
        this.servicio = servicio;
    }

    /** POST /api/denuncias → 201 sin cuerpo. La app solo necesita saber que ha ido bien. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void denunciar(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CrearDenunciaPeticion datos) {
        servicio.denunciar(Long.valueOf(token.getSubject()), datos);
    }
}
