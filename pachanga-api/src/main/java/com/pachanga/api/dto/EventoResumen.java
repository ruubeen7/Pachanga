package com.pachanga.api.dto;

import com.pachanga.api.modelo.EstadoEvento;
import com.pachanga.api.modelo.Evento;
import com.pachanga.api.modelo.NivelEvento;
import com.pachanga.api.modelo.TipoEvento;

import java.time.Instant;

/**
 * Un evento en una lista (los próximos de una cancha, o "mis eventos" en el perfil).
 * Las fechas van en UTC (Instant); la app las muestra en la hora del móvil.
 */
public record EventoResumen(Long id, TipoEvento tipo, String titulo, Instant inicio, Instant fin,
                            int plazas, long inscritos, NivelEvento nivel, EstadoEvento estado,
                            Long canchaId, String canchaNombre, Long organizadorId) {

    /** Hay que llamarlo dentro de una transacción: cancha y organizador se cargan al pedirlos (LAZY). */
    public static EventoResumen de(Evento evento, long inscritos) {
        return new EventoResumen(evento.getId(), evento.getTipo(), evento.getTitulo(), evento.getInicio(),
                evento.getFin(), evento.getPlazas(), inscritos, evento.getNivel(), evento.getEstado(),
                evento.getCancha().getId(), evento.getCancha().getNombre(), evento.getOrganizador().getId());
    }
}
