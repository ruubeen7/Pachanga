package com.pachanga.api.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * No envía nada, solo lo apunta en el log. Se usa cuando pachanga.push.activo=false
 * (por ejemplo en los tests, para no mandar notificaciones de verdad a Expo).
 */
@Component
@ConditionalOnProperty(name = "pachanga.push.activo", havingValue = "false")
public class EnvioAvisosDesactivado implements EnvioAvisos {

    private static final Logger log = LoggerFactory.getLogger(EnvioAvisosDesactivado.class);

    @Override
    public void enviar(List<Aviso> avisos) {
        avisos.forEach(a -> log.debug("Aviso (no enviado): {} - {}", a.titulo(), a.cuerpo()));
    }
}
