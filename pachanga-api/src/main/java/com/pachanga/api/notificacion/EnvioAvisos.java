package com.pachanga.api.notificacion;

import java.util.List;

/**
 * Envía notificaciones push. Es una interfaz (como AlmacenFotos) para poder cambiar
 * cómo se envían sin tocar el resto del código:
 * - EnvioAvisosExpo: las envía de verdad con el servicio gratuito de Expo.
 * - EnvioAvisosDesactivado: no envía nada (tests, o si se apaga con pachanga.push.activo=false).
 *
 * Nunca lanza excepciones: si un aviso no se puede enviar, la acción del usuario
 * (apuntarse, escribir...) tiene que funcionar igual.
 */
public interface EnvioAvisos {

    void enviar(List<Aviso> avisos);
}
