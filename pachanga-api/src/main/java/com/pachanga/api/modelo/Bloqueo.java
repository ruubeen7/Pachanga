package com.pachanga.api.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un usuario (bloqueador) ha bloqueado a otro (bloqueado). Tabla "bloqueo". */
@Entity
@Table(name = "bloqueo")
public class Bloqueo extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bloqueador_id", nullable = false)
    private Usuario bloqueador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bloqueado_id", nullable = false)
    private Usuario bloqueado;

    protected Bloqueo() {
    }

    public Bloqueo(Usuario bloqueador, Usuario bloqueado) {
        this.bloqueador = bloqueador;
        this.bloqueado = bloqueado;
    }

    public Usuario getBloqueador() {
        return bloqueador;
    }

    public Usuario getBloqueado() {
        return bloqueado;
    }
}
