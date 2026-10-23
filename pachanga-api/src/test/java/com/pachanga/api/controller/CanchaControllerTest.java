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

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba las búsquedas de canchas del mapa a través de la API (con token, como hace la app).
 * Las canchas de prueba están en un punto inventado del Atlántico para no mezclarse con otras.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CanchaControllerTest {

    private static final double LAT = 30.0;
    private static final double LNG = -30.0;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CanchaRepository canchas;

    private String token;

    @BeforeEach
    void prepararDatos() throws Exception {
        String json = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "mapa@ejemplo.com", "nombre": "Mapa", "password": "secreta123"}
                                """))
                .andReturn().getResponse().getContentAsString();
        token = "Bearer " + JsonPath.read(json, "$.token");

        // 0.009 grados de latitud son unos 1.000 m; 0.05 son unos 5,5 km.
        canchas.save(new Cancha("Cerca", "Villaprueba", LAT + 0.009, LNG, null));
        canchas.save(new Cancha("Lejos", "Villaprueba", LAT + 0.05, LNG, null));
        canchas.save(new Cancha("Otra ciudad", "Pueblo Lejano", 31.0, -31.0, null));
        canchas.flush();
    }

    @Test
    void cercanasDevuelveLasDelRadioConSuDistancia() throws Exception {
        mvc.perform(get("/api/canchas/cercanas").header("Authorization", token)
                        .param("lat", "30.0").param("lng", "-30.0").param("radio", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Cerca"))
                .andExpect(jsonPath("$[0].latitud").value(closeTo(30.009, 0.000001)))
                .andExpect(jsonPath("$[0].distanciaMetros").value(1001));
    }

    @Test
    void sinRadioBuscaA10Km() throws Exception {
        mvc.perform(get("/api/canchas/cercanas").header("Authorization", token)
                        .param("lat", "30.0").param("lng", "-30.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre").value(contains("Cerca", "Lejos")));
    }

    @Test
    void porCiudadNoDistingueMayusculasNiEspacios() throws Exception {
        mvc.perform(get("/api/canchas").header("Authorization", token).param("ciudad", "  VILLAPRUEBA "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].distanciaMetros").doesNotExist());
    }

    @Test
    void porCiudadNoDistingueTildes() throws Exception {
        canchas.save(new Cancha("Con tilde", "Villaprúeba Alta", LAT, LNG + 0.1, null));

        mvc.perform(get("/api/canchas").header("Authorization", token).param("ciudad", "villaprueba alta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Con tilde"));
    }

    @Test
    void datosMalosDevuelven400ConMensaje() throws Exception {
        mvc.perform(get("/api/canchas/cercanas").header("Authorization", token).param("lat", "30.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el dato 'lng' o no es válido"));

        mvc.perform(get("/api/canchas/cercanas").header("Authorization", token)
                        .param("lat", "hola").param("lng", "-30.0"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/canchas/cercanas").header("Authorization", token)
                        .param("lat", "30.0").param("lng", "-30.0").param("radio", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El radio debe estar entre 1 y 50000 metros"));

        mvc.perform(get("/api/canchas").header("Authorization", token).param("ciudad", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Escribe el nombre de una ciudad"));
    }

    @Test
    void sinTokenNoSePuedenVerLasCanchas() throws Exception {
        mvc.perform(get("/api/canchas").param("ciudad", "Villaprueba")).andExpect(status().isUnauthorized());
    }
}
