package com.pachanga.api.notificacion;

/**
 * Una notificación push para un móvil.
 *
 * @param token  el token de Expo del móvil ("ExponentPushToken[...]")
 * @param titulo la línea en negrita
 * @param cuerpo el texto de debajo
 * @param ruta   la pantalla de la app que se abre al tocarla, por ejemplo "/evento/7"
 */
public record Aviso(String token, String titulo, String cuerpo, String ruta) {
}
