package com.pachanga.api.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Una pachanga o un entreno en una cancha, con día y hora. Tabla "evento". */
@Entity
@Table(name = "evento")
public class Evento extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancha_id", nullable = false)
    private Cancha cancha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizador_id", nullable = false)
    private Usuario organizador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEvento tipo;

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(length = 1000)
    private String descripcion;

    /**
     * Inicio y fin en UTC. La app los convierte a la hora local del móvil al mostrarlos
     * (así funciona igual en la península y en Canarias, que va una hora menos).
     */
    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    /** Plazas totales, contando al organizador. Entre 2 y 50. */
    @Column(nullable = false)
    private int plazas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelEvento nivel = NivelEvento.TODOS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEvento estado = EstadoEvento.ACTIVO;

    /** Si ya se ha avisado a los apuntados de que empieza en menos de una hora (columna de V4). */
    @Column(name = "recordatorio_enviado", nullable = false)
    private boolean recordatorioEnviado = false;

    protected Evento() {
    }

    public Evento(Cancha cancha, Usuario organizador, TipoEvento tipo, String titulo,
                  Instant inicio, Instant fin, int plazas) {
        this.cancha = cancha;
        this.organizador = organizador;
        this.tipo = tipo;
        this.titulo = titulo;
        this.inicio = inicio;
        this.fin = fin;
        this.plazas = plazas;
    }

    public Cancha getCancha() {
        return cancha;
    }

    public Usuario getOrganizador() {
        return organizador;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public void setTipo(TipoEvento tipo) {
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Instant getInicio() {
        return inicio;
    }

    public void setInicio(Instant inicio) {
        this.inicio = inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public void setFin(Instant fin) {
        this.fin = fin;
    }

    public int getPlazas() {
        return plazas;
    }

    public void setPlazas(int plazas) {
        this.plazas = plazas;
    }

    public NivelEvento getNivel() {
        return nivel;
    }

    public void setNivel(NivelEvento nivel) {
        this.nivel = nivel;
    }

    public EstadoEvento getEstado() {
        return estado;
    }

    public void setEstado(EstadoEvento estado) {
        this.estado = estado;
    }

    public boolean isRecordatorioEnviado() {
        return recordatorioEnviado;
    }

    public void setRecordatorioEnviado(boolean recordatorioEnviado) {
        this.recordatorioEnviado = recordatorioEnviado;
    }
}
