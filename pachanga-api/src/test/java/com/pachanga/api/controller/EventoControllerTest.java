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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba organizar eventos, apuntarse, desapuntarse, cancelar y "mis eventos". */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class EventoControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CanchaRepository canchas;

    private String organizador;
    private String jugador;
    private Long canchaId;

    @BeforeEach
    void preparar() throws Exception {
        organizador = registrar("organiza@ejemplo.com", "Organiza");
        jugador = registrar("juega@ejemplo.com", "Juega");
        canchaId = canchas.save(new Cancha("Pista de eventos", "Villaprueba", 30.0, -30.0, null)).getId();
    }

    @Test
    void crearUnEventoApuntaAlOrganizador() throws Exception {
        mvc.perform(post("/api/eventos").header("Authorization", organizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evento(dentroDe(Duration.ofDays(1)), 2, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Pachanga del sábado"))
                .andExpect(jsonPath("$.nivel").value("TODOS"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.cancha.nombre").value("Pista de eventos"))
                .andExpect(jsonPath("$.organizador.nombre").value("Organiza"))
                .andExpect(jsonPath("$.apuntados.length()").value(1))
                .andExpect(jsonPath("$.apuntado").value(true))
                .andExpect(jsonPath("$.organizo").value(true))
                // Nunca se manda el email de otros usuarios.
                .andExpect(jsonPath("$.organizador.email").doesNotExist());

        // Y sale en la ficha de la cancha, con 1 apuntado.
        mvc.perform(get("/api/canchas/" + canchaId).header("Authorization", jugador))
                .andExpect(jsonPath("$.proximosEventos.length()").value(1))
                .andExpect(jsonPath("$.proximosEventos[0].inscritos").value(1));
    }

    @Test
    void reglasDeFechas() throws Exception {
        Instant manana = dentroDe(Duration.ofDays(1));

        // Fin antes que inicio.
        crearConFechas(manana, manana.minus(Duration.ofHours(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La hora de fin tiene que ser después de la de inicio"));
        // En el pasado.
        Instant ayer = dentroDe(Duration.ofDays(-1));
        crearConFechas(ayer, ayer.plus(Duration.ofHours(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El evento no puede empezar en el pasado"));
        // Más de 8 horas.
        crearConFechas(manana, manana.plus(Duration.ofHours(9)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Un evento puede durar como mucho 8 horas"));
        // Demasiado lejos.
        Instant lejos = dentroDe(Duration.ofDays(100));
        crearConFechas(lejos, lejos.plus(Duration.ofHours(2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void camposObligatoriosYCanchaQueNoExiste() throws Exception {
        mvc.perform(post("/api/eventos").header("Authorization", organizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canchaId": %d, "titulo": "x", "plazas": 1}
                                """.formatted(canchaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tipo").value("Elige si es pachanga o entreno"))
                .andExpect(jsonPath("$.campos.titulo").exists())
                .andExpect(jsonPath("$.campos.inicio").exists())
                .andExpect(jsonPath("$.campos.plazas").value("Tiene que haber entre 2 y 50 plazas"));

        Instant manana = dentroDe(Duration.ofDays(1));
        mvc.perform(post("/api/eventos").header("Authorization", organizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evento(manana, 2, 10).replace("\"canchaId\": " + canchaId, "\"canchaId\": 999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Esa cancha no existe"));
    }

    @Test
    void apuntarseYDesapuntarse() throws Exception {
        Integer id = crear(2);

        mvc.perform(get("/api/eventos/" + id).header("Authorization", jugador))
                .andExpect(jsonPath("$.apuntado").value(false))
                .andExpect(jsonPath("$.organizo").value(false));

        mvc.perform(post("/api/eventos/" + id + "/inscripcion").header("Authorization", jugador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apuntado").value(true))
                .andExpect(jsonPath("$.apuntados.length()").value(2))
                .andExpect(jsonPath("$.apuntados[1].nombre").value("Juega"));

        // Dos veces no.
        mvc.perform(post("/api/eventos/" + id + "/inscripcion").header("Authorization", jugador))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya estás apuntado a este evento"));

        mvc.perform(delete("/api/eventos/" + id + "/inscripcion").header("Authorization", jugador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apuntado").value(false))
                .andExpect(jsonPath("$.apuntados.length()").value(1));

        mvc.perform(delete("/api/eventos/" + id + "/inscripcion").header("Authorization", jugador))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("No estás apuntado a este evento"));
    }

    @Test
    void noSePuedeApuntarNadieSiNoQuedanPlazas() throws Exception {
        Integer id = crear(2); // el organizador ya ocupa una
        mvc.perform(post("/api/eventos/" + id + "/inscripcion").header("Authorization", jugador))
                .andExpect(status().isOk());

        String tercero = registrar("tercero@ejemplo.com", "Tercero");
        mvc.perform(post("/api/eventos/" + id + "/inscripcion").header("Authorization", tercero))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya no quedan plazas"));
    }

    @Test
    void elOrganizadorNoSeDesapuntaSinoQueCancela() throws Exception {
        Integer id = crear(10);

        mvc.perform(delete("/api/eventos/" + id + "/inscripcion").header("Authorization", organizador))
                .andExpect(status().isConflict());

        // Otro usuario no puede cancelarlo.
        mvc.perform(post("/api/eventos/" + id + "/cancelar").header("Authorization", jugador))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/eventos/" + id + "/cancelar").header("Authorization", organizador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        // Cancelado: nadie se apunta y ya no sale en la ficha de la cancha.
        mvc.perform(post("/api/eventos/" + id + "/inscripcion").header("Authorization", jugador))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Este evento está cancelado"));
        mvc.perform(get("/api/canchas/" + canchaId).header("Authorization", jugador))
                .andExpect(jsonPath("$.proximosEventos.length()").value(0));
    }

    @Test
    void misEventosIncluyeLosOrganizadosYLosApuntados() throws Exception {
        Integer mio = crear(10);
        mvc.perform(post("/api/eventos/" + mio + "/inscripcion").header("Authorization", jugador));

        mvc.perform(get("/api/eventos/mios").header("Authorization", jugador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximos.length()").value(1))
                .andExpect(jsonPath("$.proximos[0].canchaNombre").value("Pista de eventos"))
                .andExpect(jsonPath("$.proximos[0].inscritos").value(2))
                .andExpect(jsonPath("$.pasados.length()").value(0));

        mvc.perform(get("/api/eventos/mios").header("Authorization", organizador))
                .andExpect(jsonPath("$.proximos.length()").value(1));
    }

    @Test
    void eventoQueNoExiste() throws Exception {
        mvc.perform(get("/api/eventos/999999999").header("Authorization", jugador))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Ese evento no existe"));
    }

    // ------------------------------------------------------------------ ayudantes

    private String registrar(String email, String nombre) throws Exception {
        String json = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "nombre": "%s", "password": "secreta123"}
                                """.formatted(email, nombre)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
    }

    /** Crea un evento para mañana con las plazas indicadas y devuelve su id. */
    private Integer crear(int plazas) throws Exception {
        String json = mvc.perform(post("/api/eventos").header("Authorization", organizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evento(dentroDe(Duration.ofDays(1)), 2, plazas)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions crearConFechas(Instant inicio, Instant fin)
            throws Exception {
        return mvc.perform(post("/api/eventos").header("Authorization", organizador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"canchaId": %d, "tipo": "PACHANGA", "titulo": "Pachanga", "inicio": "%s", "fin": "%s",
                         "plazas": 10}
                        """.formatted(canchaId, inicio, fin)));
    }

    private String evento(Instant inicio, int horas, int plazas) {
        return """
                {"canchaId": %d, "tipo": "PACHANGA", "titulo": " Pachanga del sábado ", "inicio": "%s",
                 "fin": "%s", "plazas": %d}
                """.formatted(canchaId, inicio, inicio.plus(Duration.ofHours(horas)), plazas);
    }

    /** Una fecha relativa a ahora, sin segundos (como las que manda la app). */
    private static Instant dentroDe(Duration tiempo) {
        return Instant.now().plus(tiempo).truncatedTo(ChronoUnit.MINUTES);
    }
}
