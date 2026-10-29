package com.pachanga.api.controller;

import com.pachanga.api.dto.DireccionSugerencia;
import com.pachanga.api.servicio.DireccionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Buscador de direcciones para crear canchas. Necesita token, como el resto (ver SeguridadConfig). */
@RestController
@RequestMapping("/api/direcciones")
public class DireccionController {

    private final DireccionService servicio;

    public DireccionController(DireccionService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/direcciones?texto=calle feria 25&lat=37.39&lng=-5.98
     * lat y lng son opcionales: si vienen, salen antes los sitios cercanos.
     * Con menos de 3 letras devuelve una lista vacía.
     */
    @GetMapping
    public List<DireccionSugerencia> buscar(@RequestParam String texto,
                                            @RequestParam(required = false) Double lat,
                                            @RequestParam(required = false) Double lng) {
        return servicio.buscar(texto, lat, lng);
    }
}
