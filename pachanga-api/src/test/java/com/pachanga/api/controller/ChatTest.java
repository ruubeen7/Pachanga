package com.pachanga.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.repositorio.CanchaRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba el chat de los eventos. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ChatTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CanchaRepository canchas;
    @Autowired
    private UsuarioRepository usuarios;

    private String organizador;
    private String jugador;
    private Integer evento;

    @BeforeEach
    void preparar() throws Exception {
        organizador = registrar("org.chat@ejemplo.com", "Org");
        jugador = registrar("jug.chat@ejemplo.com", "Jug");
        Long canchaId = canchas.save(new Cancha("Pista del chat", "Villaprueba", 30.0, -30.0, null)).getId();

        Instant inicio = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        String json = mvc.perform(post("/api/eventos").header("Authorization", organizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canchaId": %d, "tipo": "PACHANGA", "titulo": "Pachanga con chat", "inicio": "%s",
                                 "fin": "%s", "plazas": 10}
                                """.formatted(canchaId, inicio, inicio.plus(Duration.ofHours(2)))))
                .andReturn().getResponse().getContentAsString();
        evento = JsonPath.read(json, "$.id");
    }

    @Test
    void soloLosApuntadosUsanElChat() throws Exception {
        leer(jugador, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Apúntate al evento para ver el chat"));
        escribir(jugador, "Hola").andExpect(status().isForbidden());

        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", jugador));
        escribir(jugador, "Hola").andExpect(status().isCreated());
    }

    @Test
    void escribirYLeerSoloLosNuevos() throws Exception {
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", jugador));

        String json = escribir(organizador, "  ¿Quién trae balón?  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("¿Quién trae balón?"))
                .andExpect(jsonPath("$.mio").value(true))
                .andReturn().getResponse().getContentAsString();
        Integer primero = JsonPath.read(json, "$.id");
        escribir(jugador, "Yo llevo uno").andExpect(status().isCreated());

        // Al abrir el chat: todos, en orden, y "mio" según quién pregunta.
        leer(jugador, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].autor.nombre").value("Org"))
                .andExpect(jsonPath("$[0].mio").value(false))
                .andExpect(jsonPath("$[1].texto").value("Yo llevo uno"))
                .andExpect(jsonPath("$[1].mio").value(true));

        // Las consultas periódicas: solo lo posterior al último que ya tengo.
        leer(organizador, primero)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].texto").value("Yo llevo uno"));
    }

    @Test
    void mensajesNoValidos() throws Exception {
        escribir(organizador, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.texto").value("Escribe algo"));
        escribir(organizador, "a".repeat(501))
                .andExpect(status().isBadRequest());
    }

    @Test
    void losMensajesDeUnBloqueadoNoSeVenYSePuedenDenunciar() throws Exception {
        String tercero = registrar("ter.chat@ejemplo.com", "Ter");
        Long idTercero = usuarios.findByEmail("ter.chat@ejemplo.com").orElseThrow().getId();
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", jugador));
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", tercero));

        String json = escribir(tercero, "Mensaje molesto").andReturn().getResponse().getContentAsString();
        Integer mensajeId = JsonPath.read(json, "$.id");

        // El jugador lo denuncia...
        mvc.perform(post("/api/denuncias").header("Authorization", jugador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "MENSAJE", "contenidoId": %d, "motivo": "INAPROPIADO"}
                                """.formatted(mensajeId)))
                .andExpect(status().isCreated());

        // ...y lo bloquea: deja de ver sus mensajes. El organizador los sigue viendo.
        mvc.perform(post("/api/usuarios/" + idTercero + "/bloqueo").header("Authorization", jugador));
        leer(jugador, null).andExpect(jsonPath("$.length()").value(0));
        leer(organizador, null).andExpect(jsonPath("$.length()").value(1));
    }

    // ------------------------------------------------------------------ ayudantes

    private ResultActions escribir(String token, String texto) throws Exception {
        return mvc.perform(post("/api/eventos/" + evento + "/mensajes").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\": \"" + texto + "\"}"));
    }

    private ResultActions leer(String token, Integer despuesDe) throws Exception {
        var peticion = get("/api/eventos/" + evento + "/mensajes").header("Authorization", token);
        if (despuesDe != null) peticion.param("despuesDe", String.valueOf(despuesDe));
        return mvc.perform(peticion);
    }

    private String registrar(String email, String nombre) throws Exception {
        String json = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "nombre": "%s", "password": "secreta123"}
                                """.formatted(email, nombre)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
    }
}
