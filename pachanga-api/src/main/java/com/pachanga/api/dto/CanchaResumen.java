package com.pachanga.api.dto;

import com.pachanga.api.modelo.Cancha;

/**
 * Lo mínimo de una cancha para pintarla en el mapa o en una lista.
 * La ficha completa (fotos, eventos...) tendrá su propio DTO más adelante.
 *
 * distanciaMetros solo viene relleno en la búsqueda "cerca de mí"; en la búsqueda por ciudad es null.
 */
public record CanchaResumen(Long id, String nombre, String ciudad, String direccion,
                            double latitud, double longitud, Integer distanciaMetros) {

    public static CanchaResumen de(Cancha cancha, Integer distanciaMetros) {
        return new CanchaResumen(cancha.getId(), cancha.getNombre(), cancha.getCiudad(), cancha.getDireccion(),
                cancha.getLatitud(), cancha.getLongitud(), distanciaMetros);
    }
}
