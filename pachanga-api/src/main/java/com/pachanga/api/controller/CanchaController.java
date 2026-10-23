package com.pachanga.api.controller;

import com.pachanga.api.dto.CanchaResumen;
import com.pachanga.api.servicio.CanchaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Canchas. Todas estas rutas necesitan token (ver SeguridadConfig). */
@RestController
@RequestMapping("/api/canchas")
public class CanchaController {

    private final CanchaService servicio;

    public CanchaController(CanchaService servicio) {
        this.servicio = servicio;
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

    /** GET /api/canchas?ciudad=Sevilla → canchas de esa ciudad. */
    @GetMapping
    public List<CanchaResumen> porCiudad(@RequestParam String ciudad) {
        return servicio.porCiudad(ciudad);
    }
}
