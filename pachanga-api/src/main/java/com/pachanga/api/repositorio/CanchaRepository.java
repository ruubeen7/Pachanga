package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Cancha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CanchaRepository extends JpaRepository<Cancha, Long> {

    /**
     * Canchas a menos de {@code metros} de un punto, de la más cercana a la más lejana.
     * Sirve para dos cosas: el mapa ("canchas cerca de mí") y evitar duplicados
     * ("¿hay alguna a menos de 50 m de la que vas a crear?").
     *
     * Es SQL nativo de PostgreSQL porque usa funciones de PostGIS:
     *  - ST_MakePoint(longitud, latitud) crea el punto.
     *  - ST_DWithin(a, b, metros) es verdadero si a y b están a menos de esos metros (usa el índice GIST).
     *  - ST_Distance(a, b) da la distancia en metros, para ordenar.
     */
    @Query(value = """
            SELECT *
            FROM cancha c
            WHERE ST_DWithin(c.ubicacion, ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)::geography, :metros)
            ORDER BY ST_Distance(c.ubicacion, ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)::geography)
            LIMIT :limite
            """, nativeQuery = true)
    List<Cancha> buscarCercanas(@Param("latitud") double latitud,
                                @Param("longitud") double longitud,
                                @Param("metros") double metros,
                                @Param("limite") int limite);

    /**
     * Búsqueda por ciudad sin distinguir mayúsculas ni tildes:
     * "sevilla" encuentra "Sevilla" y "malaga" encuentra "Málaga".
     * unaccent() es una extensión de PostgreSQL (se activa en V2__busqueda_sin_tildes.sql).
     */
    @Query(value = """
            SELECT *
            FROM cancha c
            WHERE unaccent(lower(c.ciudad)) = unaccent(lower(:ciudad))
            ORDER BY c.nombre
            """, nativeQuery = true)
    List<Cancha> buscarPorCiudad(@Param("ciudad") String ciudad);
}
