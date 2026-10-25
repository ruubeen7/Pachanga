package com.pachanga.api.dto;

import com.pachanga.api.modelo.FotoCancha;

/**
 * Una foto de una cancha.
 * url puede ser relativa ("/fotos/abc.jpg", servida por esta API en local) o completa
 * ("https://...", cuando las fotos estén en Supabase). La app sabe manejar las dos.
 */
public record FotoRespuesta(Long id, String url) {

    public static FotoRespuesta de(FotoCancha foto) {
        return new FotoRespuesta(foto.getId(), foto.getUrl());
    }
}
