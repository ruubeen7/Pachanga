package com.pachanga.api.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.time.Instant;

/**
 * Lo que tienen en común todas las tablas: un id autonumérico y la fecha de creación.
 * Las entidades heredan de esta clase para no repetir el mismo código en cada una.
 */
@MappedSuperclass
public abstract class EntidadBase {

    /** Clave primaria. La genera la base de datos (como AUTO_INCREMENT en MySQL). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Momento exacto de creación. Instant = fecha y hora en UTC, sin líos de zonas horarias. */
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    /** JPA llama a este método justo antes de hacer el INSERT. */
    @PrePersist
    protected void alCrear() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
