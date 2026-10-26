package com.pachanga.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.modelo.FotoCancha;
import com.pachanga.api.repositorio.CanchaRepository;
import com.pachanga.api.repositorio.FotoCanchaRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
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

/** Prueba denunciar contenido y bloquear usuarios. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ModeracionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CanchaRepository canchas;
    @Autowired
    private FotoCanchaRepository fotos;
    @Autowired
    private UsuarioRepository usuarios;

    private String ana;
    private String beto;
    private Long idAna;
    private Long idBeto;
    private Long canchaId;

    @BeforeEach
    void preparar() throws Exception {
        ana = registrar("ana@ejemplo.com", "Ana");
        beto = registrar("beto@ejemplo.com", "Beto");
        idAna = usuarios.findByEmail("ana@ejemplo.com").orElseThrow().getId();
        idBeto = usuarios.findByEmail("beto@ejemplo.com").orElseThrow().getId();
        canchaId = canchas.save(new Cancha("Pista de moderación", "Villaprueba", 30.0, -30.0, null)).getId();
    }

    // ------------------------------------------------------------------ denuncias

    @Test
    void denunciarUnaCanchaYNoRepetir() throws Exception {
        String denuncia = """
                {"tipo": "CANCHA", "contenidoId": %d, "motivo": "FALSO", "comentario": "Aquí no hay canasta"}
                """.formatted(canchaId);

        mvc.perform(post("/api/denuncias").header("Authorization", ana)
                        .contentType(MediaType.APPLICATION_JSON).content(denuncia))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/denuncias").header("Authorization", ana)
                        .contentType(MediaType.APPLICATION_JSON).content(denuncia))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya lo habías denunciado. Lo revisaremos pronto."));
    }

    @Test
    void denunciasNoValidas() throws Exception {
        // Algo que no existe.
        mvc.perform(post("/api/denuncias").header("Authorization", ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "EVENTO", "contenidoId": 999999999, "motivo": "SPAM"}
                                """))
                .andExpect(status().isNotFound());

        // A uno mismo.
        mvc.perform(post("/api/denuncias").header("Authorization", ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "USUARIO", "contenidoId": %d, "motivo": "OTRO"}
                                """.formatted(idAna)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No puedes denunciarte a ti mismo"));

        // Sin motivo.
        mvc.perform(post("/api/denuncias").header("Authorization", ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo": "USUARIO", "contenidoId": %d}
                                """.formatted(idBeto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.motivo").value("Elige un motivo"));
    }

    // ------------------------------------------------------------------ bloqueos

    @Test
    void bloquearYDesbloquear() throws Exception {
        mvc.perform(post("/api/usuarios/" + idBeto + "/bloqueo").header("Authorization", ana))
                .andExpect(status().isNoContent());
        // Repetirlo no da error.
        mvc.perform(post("/api/usuarios/" + idBeto + "/bloqueo").header("Authorization", ana))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/usuarios/bloqueados").header("Authorization", ana))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Beto"));

        mvc.perform(delete("/api/usuarios/" + idBeto + "/bloqueo").header("Authorization", ana))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/bloqueados").header("Authorization", ana))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/usuarios/" + idAna + "/bloqueo").header("Authorization", ana))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elBloqueadoNoVeLosEventosNiLasFotosDeQuienLeBloquea() throws Exception {
        Integer evento = crearEvento(ana);
        fotos.save(new FotoCancha(canchas.getReferenceById(canchaId), "/fotos/de-ana.jpg",
                usuarios.getReferenceById(idAna)));
        fotos.flush();

        mvc.perform(post("/api/usuarios/" + idBeto + "/bloqueo").header("Authorization", ana));

        // Beto (el bloqueado) ya no ve nada de Ana...
        mvc.perform(get("/api/canchas/" + canchaId).header("Authorization", beto))
                .andExpect(jsonPath("$.proximosEventos.length()").value(0))
                .andExpect(jsonPath("$.fotos.length()").value(0));
        mvc.perform(get("/api/eventos/" + evento).header("Authorization", beto))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", beto))
                .andExpect(status().isNotFound());

        // ...pero los demás sí.
        String otro = registrar("otro@ejemplo.com", "Otro");
        mvc.perform(get("/api/canchas/" + canchaId).header("Authorization", otro))
                .andExpect(jsonPath("$.proximosEventos.length()").value(1))
                .andExpect(jsonPath("$.fotos.length()").value(1));
    }

    @Test
    void alBloquearSeSaleDeLosEventosDelOtroYNoSaleEnLaLista() throws Exception {
        Integer evento = crearEvento(ana);
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", beto))
                .andExpect(jsonPath("$.inscritos").value(2));

        // Ana bloquea a Beto: Beto sale de su evento.
        mvc.perform(post("/api/usuarios/" + idBeto + "/bloqueo").header("Authorization", ana));
        mvc.perform(get("/api/eventos/" + evento).header("Authorization", ana))
                .andExpect(jsonPath("$.inscritos").value(1));
        mvc.perform(get("/api/eventos/mios").header("Authorization", beto))
                .andExpect(jsonPath("$.proximos.length()").value(0));

        // Carla y Dani se apuntan al evento de Ana.
        String carla = registrar("carla@ejemplo.com", "Carla");
        Long idCarla = usuarios.findByEmail("carla@ejemplo.com").orElseThrow().getId();
        String dani = registrar("dani@ejemplo.com", "Dani");
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", carla));
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", dani));

        // Dani bloquea a Carla: los dos siguen en el evento de Ana (no es de ninguno de ellos),
        // pero Dani no ve a Carla en la lista, aunque su plaza sí cuenta.
        Long idDani = usuarios.findByEmail("dani@ejemplo.com").orElseThrow().getId();
        mvc.perform(post("/api/usuarios/" + idCarla + "/bloqueo").header("Authorization", dani));
        mvc.perform(get("/api/eventos/" + evento).header("Authorization", dani))
                .andExpect(jsonPath("$.inscritos").value(3))
                .andExpect(jsonPath("$.apuntados.length()").value(2));
        mvc.perform(get("/api/eventos/" + evento).header("Authorization", carla))
                .andExpect(jsonPath("$.apuntados.length()").value(2))
                .andExpect(jsonPath("$.apuntados[?(@.id == " + idDani + ")]").isEmpty());
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

    private Integer crearEvento(String token) throws Exception {
        Instant inicio = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        String json = mvc.perform(post("/api/eventos").header("Authorization", token)
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
