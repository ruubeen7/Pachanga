package com.pachanga.api.controller;

import com.pachanga.api.dto.CanchaDetalle;
import com.pachanga.api.dto.CanchaResumen;
import com.pachanga.api.dto.CrearCanchaPeticion;
import com.pachanga.api.dto.FotoRespuesta;
import com.pachanga.api.servicio.CanchaService;
import com.pachanga.api.servicio.FotoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Canchas. Todas estas rutas necesitan token (ver SeguridadConfig). */
@RestController
@RequestMapping("/api/canchas")
public class CanchaController {

    private final CanchaService servicio;
    private final FotoService fotos;

    public CanchaController(CanchaService servicio, FotoService fotos) {
        this.servicio = servicio;
        this.fotos = fotos;
    }

    /**
     * GET /api/canchas/cercanas?lat=37.38&lng=-5.97&radio=5000
     * Canchas cerca de un punto (la ubicación del usuario o el centro del mapa).
     * radio es opcional, en metros.
     */
    @GetMapping("/cercanas")
    public List<CanchaResumen> cercanas(@RequestParam double lat,
                                        @RequestParam double lng,
                                        @RequestParam(required = false) Integer radio) {
        return servicio.cercanas(lat, lng, radio);
    }

    /**
     * GET /api/canchas/posibles-duplicados?lat=37.38&lng=-5.97
     * Canchas a menos de 50 m: la app las enseña antes de crear una ("¿es esta?").
     */
    @GetMapping("/posibles-duplicados")
    public List<CanchaResumen> posiblesDuplicados(@RequestParam double lat, @RequestParam double lng) {
        return servicio.posiblesDuplicados(lat, lng);
    }

    /** GET /api/canchas?ciudad=Sevilla → canchas de esa ciudad. */
    @GetMapping
    public List<CanchaResumen> porCiudad(@RequestParam String ciudad) {
        return servicio.porCiudad(ciudad);
    }

    /** GET /api/canchas/5 → ficha de la cancha 5 (con fotos y próximos eventos). */
    @GetMapping("/{id}")
    public CanchaDetalle detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    /** POST /api/canchas → da de alta una cancha. Responde 201 (Created) con su ficha. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CanchaDetalle crear(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CrearCanchaPeticion datos) {
        return servicio.crear(Long.valueOf(token.getSubject()), datos);
    }

    /**
     * POST /api/canchas/5/fotos → sube una foto a la cancha 5.
     * Va como "multipart/form-data" (como un formulario web con un archivo), en el campo "foto".
     */
    @PostMapping(value = "/{id}/fotos", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public FotoRespuesta subirFoto(@AuthenticationPrincipal Jwt token, @PathVariable Long id,
                                   @RequestPart("foto") MultipartFile foto) {
        return fotos.subir(id, Long.valueOf(token.getSubject()), foto);
    }
}
