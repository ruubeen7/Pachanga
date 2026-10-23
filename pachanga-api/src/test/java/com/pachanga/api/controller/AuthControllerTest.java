package com.pachanga.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba el registro, el login y las rutas protegidas haciendo peticiones HTTP "de mentira"
 * (MockMvc) contra la API completa, con la base de datos de Docker.
 * Gracias a @Transactional, los usuarios creados se borran al acabar cada test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    private static final String REGISTRO_VALIDO = """
            {"email": "  Nuevo@Ejemplo.com ", "nombre": "Rubén", "password": "secreta123"}
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;

    @Test
    void registrarDevuelveTokenYGuardaLaContrasenaCifrada() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.email").value("nuevo@ejemplo.com"))
                .andExpect(jsonPath("$.usuario.nombre").value("Rubén"))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist());

        String hash = usuarios.findByEmail("nuevo@ejemplo.com").orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("secreta123").startsWith("$2"); // los hashes BCrypt empiezan por $2
    }

    @Test
    void noPermiteRegistrarDosVecesElMismoEmail() throws Exception {
        registrar();

        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya hay una cuenta con ese email"));
    }

    @Test
    void registroConDatosMalosDevuelveElErrorDeCadaCampo() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "no-es-un-email", "nombre": "R", "password": "corta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.email").value("El email no es válido"))
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.password").exists());
    }

    @Test
    void loginCorrectoYConContrasenaMala() throws Exception {
        registrar();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "NUEVO@ejemplo.com", "password": "secreta123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nuevo@ejemplo.com", "password": "otra-cosa"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Email o contraseña incorrectos"));
    }

    @Test
    void lasRutasProtegidasNecesitanUnTokenValido() throws Exception {
        String token = registrar();

        // Sin token → 401
        mvc.perform(get("/api/usuarios/yo")).andExpect(status().isUnauthorized());

        // Con un token inventado → 401
        mvc.perform(get("/api/usuarios/yo").header("Authorization", "Bearer token.falso.123"))
                .andExpect(status().isUnauthorized());

        // Con el token del registro → 200 y sus datos
        mvc.perform(get("/api/usuarios/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nuevo@ejemplo.com"));
    }

    @Test
    void laRutaDeSaludSigueSiendoPublica() throws Exception {
        mvc.perform(get("/api/salud")).andExpect(status().isOk());
    }

    /** Registra el usuario de prueba y devuelve su token. */
    private String registrar() throws Exception {
        String json = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRO_VALIDO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
