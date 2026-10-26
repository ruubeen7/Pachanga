package com.pachanga.api.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un mensaje en el chat de un evento. Tabla "mensaje" (creada en V3). */
@Entity
@Table(name = "mensaje")
public class Mensaje extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(nullable = false, length = 500)
    private String texto;

    protected Mensaje() {
    }

    public Mensaje(Evento evento, Usuario autor, String texto) {
        this.evento = evento;
        this.autor = autor;
        this.texto = texto;
    }

    public Evento getEvento() {
        return evento;
    }

    public Usuario getAutor() {
        return autor;
    }

    public String getTexto() {
        return texto;
    }
}
