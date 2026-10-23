package com.pachanga.api.servicio;

import com.pachanga.api.dto.CanchaResumen;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.repositorio.CanchaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Búsquedas de canchas para el mapa. */
@Service
@Transactional(readOnly = true) // solo lee: así la base de datos puede optimizar y no se cambia nada por error
public class CanchaService {

    /** Si la app no dice el radio, buscamos a 10 km. */
    public static final int RADIO_POR_DEFECTO = 10_000;
    /** Más de 50 km no tiene sentido para "canchas cerca" y haría la consulta pesada. */
    public static final int RADIO_MAXIMO = 50_000;
    /** Tope de canchas por respuesta, para que el mapa no se llene de miles de chinchetas. */
    public static final int MAX_RESULTADOS = 200;

    /** Radio medio de la Tierra en metros, para calcular distancias. */
    private static final double RADIO_TIERRA = 6_371_000;

    private final CanchaRepository canchas;

    public CanchaService(CanchaRepository canchas) {
        this.canchas = canchas;
    }

    /** Canchas a menos de {@code radio} metros del punto, la más cercana primero. */
    public List<CanchaResumen> cercanas(double latitud, double longitud, Integer radio) {
        if (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "Las coordenadas no son válidas");
        }
        int metros = radio == null ? RADIO_POR_DEFECTO : radio;
        if (metros < 1 || metros > RADIO_MAXIMO) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "El radio debe estar entre 1 y " + RADIO_MAXIMO + " metros");
        }

        return canchas.buscarCercanas(latitud, longitud, metros, MAX_RESULTADOS).stream()
                .map(c -> CanchaResumen.de(c, distanciaMetros(latitud, longitud, c)))
                .toList();
    }

    /** Canchas de una ciudad, ordenadas por nombre. No distingue mayúsculas. */
    public List<CanchaResumen> porCiudad(String ciudad) {
        String limpia = ciudad == null ? "" : ciudad.trim();
        if (limpia.length() < 2) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "Escribe el nombre de una ciudad");
        }
        return canchas.buscarPorCiudad(limpia).stream()
                .limit(MAX_RESULTADOS)
                .map(c -> CanchaResumen.de(c, null))
                .toList();
    }

    /**
     * Distancia en línea recta entre el usuario y la cancha, con la fórmula del haversine
     * (tiene en cuenta que la Tierra es una esfera). Para mostrar "a 350 m" es más que suficiente.
     */
    private static int distanciaMetros(double latitud, double longitud, Cancha cancha) {
        double dLat = Math.toRadians(cancha.getLatitud() - latitud);
        double dLng = Math.toRadians(cancha.getLongitud() - longitud);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latitud)) * Math.cos(Math.toRadians(cancha.getLatitud()))
                * Math.pow(Math.sin(dLng / 2), 2);
        return (int) Math.round(2 * RADIO_TIERRA * Math.asin(Math.sqrt(a)));
    }
}
