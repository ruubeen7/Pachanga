package com.pachanga.api.dto;

import java.util.Map;

/**
 * Formato de todos los errores de la API.
 *
 * @param mensaje texto para mostrar al usuario
 * @param campos  error de cada campo del formulario (vacío si no es un error de validación)
 */
public record ErrorRespuesta(String mensaje, Map<String, String> campos) {
}
