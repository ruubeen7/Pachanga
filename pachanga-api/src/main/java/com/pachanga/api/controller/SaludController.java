package com.pachanga.api.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de comprobación: sirve para saber si la API está encendida
 * y si puede hablar con la base de datos.
 *
 * GET http://localhost:8080/api/salud
 */
@RestController
@RequestMapping("/api/salud")
public class SaludController {

    private final JdbcTemplate jdbc;

    // Spring nos "inyecta" el JdbcTemplate ya configurado con los datos de application.properties.
    public SaludController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public SaludResponse comprobar() {
        String postgres = jdbc.queryForObject("SHOW server_version", String.class);
        String postgis = jdbc.queryForObject("SELECT postgis_lib_version()", String.class);
        return new SaludResponse("ok", postgres, postgis);
    }

    /** Lo que devuelve el endpoint. Spring lo convierte a JSON automáticamente. */
    public record SaludResponse(String estado, String postgresql, String postgis) {
    }
}
