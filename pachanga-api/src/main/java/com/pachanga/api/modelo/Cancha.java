package com.pachanga.api.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** Una pista de baloncesto con su ubicación. Tabla "cancha". */
@Entity
@Table(name = "cancha")
public class Cancha extends EntidadBase {

    /**
     * Fábrica de puntos geográficos. 4326 es el código del sistema de coordenadas GPS
     * (el mismo que usan Google Maps, OpenStreetMap y el GPS del móvil).
     */
    private static final GeometryFactory GEOMETRIAS = new GeometryFactory(new PrecisionModel(), 4326);

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    /** Punto en el mapa. Point viene de la librería JTS, que es la que entiende Hibernate Spatial. */
    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point ubicacion;

    private String direccion;

    @Column(nullable = false, length = 100)
    private String ciudad;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Superficie superficie;

    @Column(name = "num_canastas")
    private Integer numCanastas;

    @Column(nullable = false)
    private boolean cubierta;

    @Column(nullable = false)
    private boolean iluminacion;

    @Column(nullable = false)
    private boolean gratuita = true;

    /**
     * Quién la dio de alta. Muchas canchas pueden ser de un mismo usuario (ManyToOne).
     * LAZY: el usuario no se carga de la base de datos hasta que se pide con getCreadaPor().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creada_por")
    private Usuario creadaPor;

    protected Cancha() {
    }

    public Cancha(String nombre, String ciudad, double latitud, double longitud, Usuario creadaPor) {
        this.nombre = nombre;
        this.ciudad = ciudad;
        this.creadaPor = creadaPor;
        setUbicacion(latitud, longitud);
    }

    /** Cuidado: en geografía el punto se crea como (longitud, latitud), al revés de como se suele decir. */
    public void setUbicacion(double latitud, double longitud) {
        this.ubicacion = GEOMETRIAS.createPoint(new Coordinate(longitud, latitud));
    }

    public double getLatitud() {
        return ubicacion.getY();
    }

    public double getLongitud() {
        return ubicacion.getX();
    }

    public Point getUbicacion() {
        return ubicacion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public Superficie getSuperficie() {
        return superficie;
    }

    public void setSuperficie(Superficie superficie) {
        this.superficie = superficie;
    }

    public Integer getNumCanastas() {
        return numCanastas;
    }

    public void setNumCanastas(Integer numCanastas) {
        this.numCanastas = numCanastas;
    }

    public boolean isCubierta() {
        return cubierta;
    }

    public void setCubierta(boolean cubierta) {
        this.cubierta = cubierta;
    }

    public boolean isIluminacion() {
        return iluminacion;
    }

    public void setIluminacion(boolean iluminacion) {
        this.iluminacion = iluminacion;
    }

    public boolean isGratuita() {
        return gratuita;
    }

    public void setGratuita(boolean gratuita) {
        this.gratuita = gratuita;
    }

    public Usuario getCreadaPor() {
        return creadaPor;
    }
}
