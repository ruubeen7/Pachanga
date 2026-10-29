package com.pachanga.api.dto;

import com.pachanga.api.modelo.Cancha;

import java.time.Instant;

/**
 * Lo mínimo de una cancha para pintarla en el mapa o en una lista.
 *
 * distanciaMetros solo viene relleno en las búsquedas "cerca de mí"; en la búsqueda por ciudad es null.
 * eventosProximos: cuántos eventos activos que aún no han terminado tiene (el mapa resalta las que tienen alguno).
 * proximoEvento: cuándo empieza el primero de ellos (null si no hay ninguno).
 */
public record CanchaResumen(Long id, String nombre, String ciudad, String direccion,
                            double latitud, double longitud, Integer distanciaMetros,
                            int eventosProximos, Instant proximoEvento) {

    public static CanchaResumen de(Cancha cancha, Integer distanciaMetros, int eventosProximos, Instant proximoEvento) {
        return new CanchaResumen(cancha.getId(), cancha.getNombre(), cancha.getCiudad(), cancha.getDireccion(),
                cancha.getLatitud(), cancha.getLongitud(), distanciaMetros, eventosProximos, proximoEvento);
    }

    /** Para cuando no hace falta contar eventos (por ejemplo, la cancha dentro de la ficha de un evento). */
    public static CanchaResumen de(Cancha cancha, Integer distanciaMetros) {
        return de(cancha, distanciaMetros, 0, null);
    }
}
