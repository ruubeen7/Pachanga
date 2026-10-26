package com.pachanga.api.dto;

import java.util.List;

/**
 * Los eventos de un usuario para su perfil (GET /api/eventos/mios): los que organiza
 * y a los que se ha apuntado. "proximos" son los que aún no han terminado (el más cercano primero)
 * y "pasados" los últimos que ya terminaron (el más reciente primero).
 */
public record MisEventos(List<EventoResumen> proximos, List<EventoResumen> pasados) {
}
