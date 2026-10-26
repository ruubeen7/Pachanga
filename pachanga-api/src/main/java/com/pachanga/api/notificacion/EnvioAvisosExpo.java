package com.pachanga.api.notificacion;

import com.pachanga.api.repositorio.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Envía las notificaciones con el servicio de Expo (gratis y sin cuenta de pago).
 * Funciona así: hacemos un POST a exp.host con la lista de avisos, y Expo se encarga
 * de mandarlos a Apple (iPhone) o a Google (Android).
 * Documentación: https://docs.expo.dev/push-notifications/sending-notifications/
 *
 * Es la versión que se usa por defecto (matchIfMissing = true).
 */
@Component
@ConditionalOnProperty(name = "pachanga.push.activo", havingValue = "true", matchIfMissing = true)
public class EnvioAvisosExpo implements EnvioAvisos {

    private static final Logger log = LoggerFactory.getLogger(EnvioAvisosExpo.class);
    private static final String URL_EXPO = "https://exp.host/--/api/v2/push/send";
    /** Expo acepta como mucho 100 avisos por petición. */
    private static final int MAXIMO_POR_PETICION = 100;

    private final RestClient cliente;
    private final UsuarioRepository usuarios;

    public EnvioAvisosExpo(UsuarioRepository usuarios) {
        this.cliente = RestClient.create(); // cliente HTTP de Spring para llamar a otras APIs
        this.usuarios = usuarios;
    }

    /**
     * @Async: este método se ejecuta en otro hilo, en segundo plano. Así quien se apunta
     * a un evento no tiene que esperar a que Expo conteste para ver su respuesta.
     * @Transactional: por si hay que borrar tokens que ya no sirven (ver abajo).
     */
    @Override
    @Async
    @Transactional
    public void enviar(List<Aviso> avisos) {
        for (int i = 0; i < avisos.size(); i += MAXIMO_POR_PETICION) {
            enviarTanda(avisos.subList(i, Math.min(i + MAXIMO_POR_PETICION, avisos.size())));
        }
    }

    private void enviarTanda(List<Aviso> tanda) {
        List<MensajeExpo> mensajes = tanda.stream()
                .map(a -> new MensajeExpo(a.token(), a.titulo(), a.cuerpo(), Map.of("ruta", a.ruta()), "default"))
                .toList();
        try {
            RespuestaExpo respuesta = cliente.post()
                    .uri(URL_EXPO)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(mensajes)
                    .retrieve()
                    .body(RespuestaExpo.class);
            revisarRespuesta(tanda, respuesta);
        } catch (Exception e) {
            // Sin internet, Expo caído... No pasa nada: se pierde el aviso, pero la app sigue funcionando.
            log.warn("No se pudieron enviar {} avisos: {}", tanda.size(), e.getMessage());
        }
    }

    /**
     * Expo contesta con un "ticket" por aviso, en el mismo orden. Si dice DeviceNotRegistered,
     * ese móvil ha desinstalado la app o quitado el permiso: borramos el token para no insistir.
     */
    private void revisarRespuesta(List<Aviso> tanda, RespuestaExpo respuesta) {
        if (respuesta == null || respuesta.data() == null) return;
        for (int i = 0; i < Math.min(tanda.size(), respuesta.data().size()); i++) {
            TicketExpo ticket = respuesta.data().get(i);
            if (!"error".equals(ticket.status())) continue;
            Object tipoError = ticket.details() == null ? null : ticket.details().get("error");
            if ("DeviceNotRegistered".equals(tipoError)) {
                usuarios.quitarPushToken(tanda.get(i).token());
            } else {
                log.warn("Expo no aceptó un aviso: {}", ticket.message());
            }
        }
    }

    /** Lo que se manda a Expo por cada aviso. Jackson lo convierte en JSON con estos mismos nombres. */
    record MensajeExpo(String to, String title, String body, Map<String, String> data, String sound) {
    }

    /** Lo que contesta Expo: { "data": [ { "status": "ok" }, { "status": "error", ... } ] } */
    record RespuestaExpo(List<TicketExpo> data) {
    }

    record TicketExpo(String status, String message, Map<String, Object> details) {
    }
}
