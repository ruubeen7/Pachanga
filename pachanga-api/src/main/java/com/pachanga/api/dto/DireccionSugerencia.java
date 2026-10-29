package com.pachanga.api.dto;

/**
 * Una sugerencia del buscador de direcciones, ya lista para enseñarla en la app.
 *
 * titulo:    lo que se lee en grande ("Calle Feria, 25" o "Parque de María Luisa").
 * detalle:   la línea pequeña de debajo ("Sevilla, Andalucía").
 * direccion: lo que se guarda en la cancha como dirección (calle y número, si los hay).
 * ciudad:    lo que se guarda en la cancha como ciudad (puede ser null si el buscador no la sabe).
 * nombreSugerido: si el sitio es un parque o una instalación deportiva, su nombre, para proponerlo
 *                 como nombre de la cancha. Si es una calle, una tienda, un edificio... va a null.
 */
public record DireccionSugerencia(String titulo, String detalle, String direccion, String ciudad,
                                  String nombreSugerido, double latitud, double longitud) {
}
