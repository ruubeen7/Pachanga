package com.pachanga.api.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Aviso de un usuario sobre contenido inapropiado. Tabla "denuncia".
 * tipoContenido + contenidoId indican qué se denuncia (por ejemplo, CANCHA 12 = la cancha con id 12).
 */
@Entity
@Table(name = "denuncia")
public class Denuncia extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "denunciante_id", nullable = false)
    private Usuario denunciante;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_contenido", nullable = false, length = 20)
    private TipoContenido tipoContenido;

    @Column(name = "contenido_id", nullable = false)
    private Long contenidoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MotivoDenuncia motivo;

    @Column(length = 500)
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDenuncia estado = EstadoDenuncia.PENDIENTE;

    protected Denuncia() {
    }

    public Denuncia(Usuario denunciante, TipoContenido tipoContenido, Long contenidoId,
                    MotivoDenuncia motivo, String comentario) {
        this.denunciante = denunciante;
        this.tipoContenido = tipoContenido;
        this.contenidoId = contenidoId;
        this.motivo = motivo;
        this.comentario = comentario;
    }

    public Usuario getDenunciante() {
        return denunciante;
    }

    public TipoContenido getTipoContenido() {
        return tipoContenido;
    }

    public Long getContenidoId() {
        return contenidoId;
    }

    public MotivoDenuncia getMotivo() {
        return motivo;
    }

    public String getComentario() {
        return comentario;
    }

    public EstadoDenuncia getEstado() {
        return estado;
    }

    public void setEstado(EstadoDenuncia estado) {
        this.estado = estado;
    }
}
