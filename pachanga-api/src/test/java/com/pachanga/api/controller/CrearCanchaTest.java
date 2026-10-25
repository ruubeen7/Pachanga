package com.pachanga.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.modelo.Evento;
import com.pachanga.api.modelo.TipoEvento;
import com.pachanga.api.repositorio.CanchaRepository;
import com.pachanga.api.repositorio.EventoRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba dar de alta canchas, ver su ficha, el aviso de duplicados y la subida de fotos.
 * Las fotos de los tests se guardan en target/fotos-test (se borra con "mvnw clean").
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "pachanga.fotos.carpeta=target/fotos-test")
class CrearCanchaTest {

    private static final double LAT = 30.0;
    private static final double LNG = -30.0;

    /** Primeros bytes de un JPEG de verdad: con eso basta para que la API lo reconozca. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CanchaRepository canchas;
    @Autowired
    private EventoRepository eventos;
    @Autowired
    private UsuarioRepository usuarios;

    private String token;

    @BeforeEach
    void iniciarSesion() throws Exception {
        String json = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "creador@ejemplo.com", "nombre": "Creador", "password": "secreta123"}
                                """))
                .andReturn().getResponse().getContentAsString();
        token = "Bearer " + JsonPath.read(json, "$.token");
    }

    @Test
    void crearDevuelveLaFichaConValoresPorDefecto() throws Exception {
        String json = mvc.perform(post("/api/canchas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "  Pista nueva ", "ciudad": "Villaprueba", "latitud": 30.0, "longitud": -30.0,
                                 "superficie": "CEMENTO", "numCanastas": 2, "iluminacion": true, "descripcion": "   "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Pista nueva"))
                .andExpect(jsonPath("$.creadaPor").value("Creador"))
                .andExpect(jsonPath("$.iluminacion").value(true))
                .andExpect(jsonPath("$.cubierta").value(false))
                .andExpect(jsonPath("$.gratuita").value(true))
                .andExpect(jsonPath("$.descripcion").doesNotExist())
                .andExpect(jsonPath("$.fotos.length()").value(0))
                .andReturn().getResponse().getContentAsString();

        Integer id = JsonPath.read(json, "$.id");
        mvc.perform(get("/api/canchas/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitud").value(30.0))
                .andExpect(jsonPath("$.superficie").value("CEMENTO"));
    }

    @Test
    void crearConDatosMalosDevuelveElErrorDeCadaCampo() throws Exception {
        mvc.perform(post("/api/canchas").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "", "ciudad": "Villaprueba", "latitud": 100, "numCanastas": 50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.latitud").value("La latitud no es válida"))
                .andExpect(jsonPath("$.campos.longitud").value("Falta la ubicación"))
                .andExpect(jsonPath("$.campos.numCanastas").value("Pon entre 1 y 20 canastas"));

        mvc.perform(post("/api/canchas").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Pista", "ciudad": "X", "latitud": 30, "longitud": -30, "superficie": "MADERA"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Los datos enviados no tienen el formato correcto"));
    }

    @Test
    void fichaDeUnaCanchaQueNoExiste() throws Exception {
        mvc.perform(get("/api/canchas/999999999").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Esa cancha no existe"));
    }

    @Test
    void posiblesDuplicadosSoloDevuelveLasDeMenosDe50Metros() throws Exception {
        // 0.0003 grados de latitud son unos 33 m; 0.001, unos 111 m.
        canchas.save(new Cancha("A 33 m", "Villaprueba", LAT + 0.0003, LNG, null));
        canchas.save(new Cancha("A 111 m", "Villaprueba", LAT + 0.001, LNG, null));
        canchas.flush();

        mvc.perform(get("/api/canchas/posibles-duplicados").header("Authorization", token)
                        .param("lat", "30.0").param("lng", "-30.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("A 33 m"))
                .andExpect(jsonPath("$[0].distanciaMetros").value(33));
    }

    @Test
    void laFichaIncluyeLosProximosEventos() throws Exception {
        Cancha cancha = canchas.save(new Cancha("Con eventos", "Villaprueba", LAT, LNG, null));
        var organizador = usuarios.findByEmail("creador@ejemplo.com").orElseThrow();
        Instant manana = Instant.now().plus(Duration.ofDays(1));
        Instant ayer = Instant.now().minus(Duration.ofDays(1));
        eventos.save(new Evento(cancha, organizador, TipoEvento.PACHANGA, "Pachanga de mañana",
                manana, manana.plus(Duration.ofHours(2)), 10));
        eventos.save(new Evento(cancha, organizador, TipoEvento.ENTRENO, "Entreno de ayer",
                ayer, ayer.plus(Duration.ofHours(2)), 10));

        mvc.perform(get("/api/canchas/" + cancha.getId()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximosEventos.length()").value(1))
                .andExpect(jsonPath("$.proximosEventos[0].titulo").value("Pachanga de mañana"))
                .andExpect(jsonPath("$.proximosEventos[0].inscritos").value(0));
    }

    @Test
    void subirUnaFotoYVerla() throws Exception {
        Cancha cancha = canchas.save(new Cancha("Con fotos", "Villaprueba", LAT, LNG, null));

        String json = mvc.perform(multipart("/api/canchas/" + cancha.getId() + "/fotos")
                        .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", JPEG))
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(startsWith("/fotos/")))
                .andReturn().getResponse().getContentAsString();

        // La foto se puede ver sin token, como cualquier imagen.
        String url = JsonPath.read(json, "$.url");
        mvc.perform(get(url)).andExpect(status().isOk());

        mvc.perform(get("/api/canchas/" + cancha.getId()).header("Authorization", token))
                .andExpect(jsonPath("$.fotos.length()").value(1))
                .andExpect(jsonPath("$.fotos[0].url").value(url));
    }

    @Test
    void noSePuedeSubirAlgoQueNoEsUnaFoto() throws Exception {
        Cancha cancha = canchas.save(new Cancha("Pista", "Villaprueba", LAT, LNG, null));

        // Aunque diga que es image/jpeg, el contenido es texto: la API mira los bytes de verdad.
        mvc.perform(multipart("/api/canchas/" + cancha.getId() + "/fotos")
                        .file(new MockMultipartFile("foto", "virus.jpg", "image/jpeg", "hola".getBytes()))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Solo se pueden subir fotos JPG, PNG o WebP"));

        mvc.perform(multipart("/api/canchas/" + cancha.getId() + "/fotos").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No ha llegado ninguna foto"));
    }

    @Test
    void sinTokenNoSePuedeCrear() throws Exception {
        mvc.perform(post("/api/canchas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
