package com.pachanga.api.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Una foto de una cancha. La imagen en sí está guardada en Supabase Storage;
 * aquí solo guardamos su URL. Tabla "foto_cancha".
 */
@Entity
@Table(name = "foto_cancha")
public class FotoCancha extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancha_id", nullable = false)
    private Cancha cancha;

    @Column(nullable = false, length = 500)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subida_por")
    private Usuario subidaPor;

    protected FotoCancha() {
    }

    public FotoCancha(Cancha cancha, String url, Usuario subidaPor) {
        this.cancha = cancha;
        this.url = url;
        this.subidaPor = subidaPor;
    }

    public Cancha getCancha() {
        return cancha;
    }

    public String getUrl() {
        return url;
    }

    public Usuario getSubidaPor() {
        return subidaPor;
    }
}
