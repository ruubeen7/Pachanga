package com.pachanga.api.controller;

import com.pachanga.api.dto.CrearEventoPeticion;
import com.pachanga.api.dto.EnviarMensajePeticion;
import com.pachanga.api.dto.EventoDetalle;
import com.pachanga.api.dto.MensajeRespuesta;
import com.pachanga.api.dto.MisEventos;
import com.pachanga.api.servicio.EventoService;
import com.pachanga.api.servicio.MensajeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Eventos. Todas estas rutas necesitan token.
 * Las acciones (apuntarse, desapuntarse, cancelar) devuelven la ficha ya actualizada,
 * así la app la pinta directamente sin tener que volver a pedirla.
 */
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoService servicio;
    private final MensajeService mensajes;

    public EventoController(EventoService servicio, MensajeService mensajes) {
        this.servicio = servicio;
        this.mensajes = mensajes;
    }

    /** POST /api/eventos → organiza una pachanga o un entreno. 201 con la ficha. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventoDetalle crear(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CrearEventoPeticion datos) {
        return servicio.crear(usuario(token), datos);
    }

    /** GET /api/eventos/mios → mis eventos (próximos y pasados) para el perfil. */
    @GetMapping("/mios")
    public MisEventos mios(@AuthenticationPrincipal Jwt token) {
        return servicio.misEventos(usuario(token));
    }

    /** GET /api/eventos/7 → ficha del evento 7. */
    @GetMapping("/{id}")
    public EventoDetalle detalle(@AuthenticationPrincipal Jwt token, @PathVariable Long id) {
        return servicio.detalle(id, usuario(token));
    }

    /** POST /api/eventos/7/inscripcion → me apunto. */
    @PostMapping("/{id}/inscripcion")
    public EventoDetalle apuntarse(@AuthenticationPrincipal Jwt token, @PathVariable Long id) {
        return servicio.apuntarse(id, usuario(token));
    }

    /** DELETE /api/eventos/7/inscripcion → me desapunto (borro mi inscripción). */
    @DeleteMapping("/{id}/inscripcion")
    public EventoDetalle desapuntarse(@AuthenticationPrincipal Jwt token, @PathVariable Long id) {
        return servicio.desapuntarse(id, usuario(token));
    }

    /** POST /api/eventos/7/cancelar → el organizador cancela el evento. */
    @PostMapping("/{id}/cancelar")
    public EventoDetalle cancelar(@AuthenticationPrincipal Jwt token, @PathVariable Long id) {
        return servicio.cancelar(id, usuario(token));
    }

    /**
     * GET /api/eventos/7/mensajes → últimos mensajes del chat.
     * GET /api/eventos/7/mensajes?despuesDe=120 → solo los que llegaron después del mensaje 120.
     */
    @GetMapping("/{id}/mensajes")
    public List<MensajeRespuesta> mensajes(@AuthenticationPrincipal Jwt token, @PathVariable Long id,
                                           @RequestParam(required = false) Long despuesDe) {
        return mensajes.leer(id, usuario(token), despuesDe);
    }

    /** POST /api/eventos/7/mensajes → escribe en el chat. 201 con el mensaje guardado. */
    @PostMapping("/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    public MensajeRespuesta enviarMensaje(@AuthenticationPrincipal Jwt token, @PathVariable Long id,
                                          @Valid @RequestBody EnviarMensajePeticion datos) {
        return mensajes.enviar(id, usuario(token), datos.texto());
    }

    /** El "subject" del token es el id del usuario (ver TokenService). */
    private static Long usuario(Jwt token) {
        return Long.valueOf(token.getSubject());
    }
}
