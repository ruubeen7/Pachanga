package com.pachanga.api.dto;

import com.pachanga.api.modelo.EstadoEvento;
import com.pachanga.api.modelo.NivelEvento;
import com.pachanga.api.modelo.TipoEvento;

import java.time.Instant;
import java.util.List;

/**
 * La ficha de un evento (GET /api/eventos/{id}).
 *
 * apuntados incluye al organizador (es el primero, porque se apunta al crearlo).
 * apuntado y organizo dependen de QUIÉN pregunta: así la app sabe qué botón enseñar
 * ("Apuntarme", "Desapuntarme" o "Cancelar evento") sin tener que calcularlo ella.
 */
public record EventoDetalle(
        Long id,
        TipoEvento tipo,
        String titulo,
        String descripcion,
        Instant inicio,
        Instant fin,
        int plazas,
        NivelEvento nivel,
        EstadoEvento estado,
        CanchaResumen cancha,
        UsuarioPublico organizador,
        List<UsuarioPublico> apuntados,
        boolean apuntado,
        boolean organizo
) {
}
