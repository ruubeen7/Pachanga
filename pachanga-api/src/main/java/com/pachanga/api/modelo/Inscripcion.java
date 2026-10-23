package com.pachanga.api.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Un usuario apuntado a un evento. Es la tabla intermedia de la relación N:M
 * (un usuario va a muchos eventos y un evento tiene muchos usuarios). Tabla "inscripcion".
 */
@Entity
@Table(name = "inscripcion",
        uniqueConstraints = @UniqueConstraint(name = "uk_inscripcion", columnNames = {"evento_id", "usuario_id"}))
public class Inscripcion extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    protected Inscripcion() {
    }

    public Inscripcion(Evento evento, Usuario usuario) {
        this.evento = evento;
        this.usuario = usuario;
    }

    public Evento getEvento() {
        return evento;
    }

    public Usuario getUsuario() {
        return usuario;
    }
}
