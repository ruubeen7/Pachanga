package com.pachanga.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.repositorio.CanchaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Canchas con eventos: el número de eventos próximos en el mapa y la lista
 * "Canchas con partidos cerca de ti" (GET /api/canchas/con-eventos).
 * Como en CanchaControllerTest, todo pasa en un punto inventado del Atlántico.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CanchasConEventosTest {

    private static final double LAT = 30.0;
    private static final double LNG = -30.0;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CanchaRepository canchas;

    private String organizador;
    private Integer organizadorId;
    private String jugador;
    private Long cerca;
    private Long lejos;
    private Long sinEventos;

    @BeforeEach
    void preparar() throws Exception {
        String json = registrar("organiza@ejemplo.com", "Organiza");
        organizador = "Bearer " + JsonPath.read(json, "$.token");
        organizadorId = JsonPath.read(json, "$.usuario.id");
        jugador = "Bearer " + JsonPath.read(registrar("juega@ejemplo.com", "Juega"), "$.token");

        // 0.009 grados de latitud son unos 1.000 m.
        lejos = canchas.save(new Cancha("Lejos", "Villaprueba", LAT + 0.027, LNG, null)).getId();
        cerca = canchas.save(new Cancha("Cerca", "Villaprueba", LAT + 0.009, LNG, null)).getId();
        sinEventos = canchas.save(new Cancha("Sin eventos", "Villaprueba", LAT + 0.018, LNG, null)).getId();
        canchas.flush();

        crearEvento(cerca, Duration.ofDays(2));
        crearEvento(cerca, Duration.ofDays(1)); // el más próximo de "Cerca"
        crearEvento(lejos, Duration.ofDays(3));

        // Un evento cancelado no cuenta
        Integer cancelado = crearEvento(sinEventos, Duration.ofDays(1));
        mvc.perform(post("/api/eventos/" + cancelado + "/cancelar").header("Authorization", organizador))
                .andExpect(status().isOk());
    }

    @Test
    void elMapaDiceCuantosEventosTieneCadaCancha() throws Exception {
        mvc.perform(get("/api/canchas/cercanas").header("Authorization", jugador)
                        .param("lat", "30.0").param("lng", "-30.0").param("radio", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre").value(contains("Cerca", "Sin eventos", "Lejos")))
                .andExpect(jsonPath("$[0].eventosProximos").value(2))
                .andExpect(jsonPath("$[0].proximoEvento").value(notNullValue()))
                .andExpect(jsonPath("$[1].eventosProximos").value(0))
                .andExpect(jsonPath("$[1].proximoEvento").value(nullValue()))
                .andExpect(jsonPath("$[2].eventosProximos").value(1));
    }

    @Test
    void conEventosSoloDevuelveLasQueTienenYOrdenadasPorDistancia() throws Exception {
        mvc.perform(get("/api/canchas/con-eventos").header("Authorization", jugador)
                        .param("lat", "30.0").param("lng", "-30.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre").value(contains("Cerca", "Lejos")))
                .andExpect(jsonPath("$[0].distanciaMetros").value(1001))
                .andExpect(jsonPath("$[0].eventosProximos").value(2));
    }

    @Test
    void conEventosRespetaElRadio() throws Exception {
        mvc.perform(get("/api/canchas/con-eventos").header("Authorization", jugador)
                        .param("lat", "30.0").param("lng", "-30.0").param("radio", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre").value(contains("Cerca")));
    }

    @Test
    void losEventosDeAlguienBloqueadoNoCuentan() throws Exception {
        mvc.perform(post("/api/usuarios/" + organizadorId + "/bloqueo").header("Authorization", jugador))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/canchas/con-eventos").header("Authorization", jugador)
                        .param("lat", "30.0").param("lng", "-30.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/canchas/cercanas").header("Authorization", jugador)
                        .param("lat", "30.0").param("lng", "-30.0").param("radio", "5000"))
                .andExpect(jsonPath("$[0].eventosProximos").value(0));
    }

    // ------------------------------------------------------------------ ayudantes

    private String registrar(String email, String nombre) throws Exception {
        return mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "nombre": "%s", "password": "secreta123"}
                                """.formatted(email, nombre)))
                .andReturn().getResponse().getContentAsString();
    }

    /** Crea un evento de 2 horas en la cancha, dentro de {@code cuando}, y devuelve su id. */
    private Integer crearEvento(Long canchaId, Duration cuando) throws Exception {
        Instant inicio = Instant.now().plus(cuando).truncatedTo(ChronoUnit.MINUTES);
        String json = mvc.perform(post("/api/eventos").header("Authorization", organizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canchaId": %d, "tipo": "PACHANGA", "titulo": "Pachanga", "inicio": "%s",
                                 "fin": "%s", "plazas": 10}
                                """.formatted(canchaId, inicio, inicio.plus(Duration.ofHours(2)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
}
