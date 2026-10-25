package com.pachanga.api.dto;

import com.pachanga.api.modelo.Superficie;

import java.util.List;

/**
 * La ficha completa de una cancha (GET /api/canchas/{id}): sus datos, sus fotos
 * y los próximos eventos que hay en ella.
 *
 * creadaPor es solo el nombre de quien la dio de alta (o null si fue el sistema o ya no existe):
 * nunca mandamos el email ni otros datos de otros usuarios.
 */
public record CanchaDetalle(
        Long id,
        String nombre,
        String descripcion,
        String direccion,
        String ciudad,
        double latitud,
        double longitud,
        Superficie superficie,
        Integer numCanastas,
        boolean cubierta,
        boolean iluminacion,
        boolean gratuita,
        String creadaPor,
        List<FotoRespuesta> fotos,
        List<EventoResumen> proximosEventos
) {
}
