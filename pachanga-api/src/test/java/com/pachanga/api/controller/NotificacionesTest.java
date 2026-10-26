package com.pachanga.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.modelo.Evento;
import com.pachanga.api.notificacion.Aviso;
import com.pachanga.api.notificacion.EnvioAvisos;
import com.pachanga.api.repositorio.CanchaRepository;
import com.pachanga.api.repositorio.EventoRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import com.pachanga.api.servicio.AvisoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba QUIÉN recibe cada notificación.
 *
 * @MockitoBean cambia el EnvioAvisos de verdad por un "doble" (mock) que no envía nada,
 * pero apunta con qué datos se le ha llamado. Con ArgumentCaptor recogemos esos datos y los comprobamos.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class NotificacionesTest {

    private static final String TOKEN_ORG = "ExponentPushToken[organizador]";
    private static final String TOKEN_JUG = "ExponentPushToken[jugador]";

    @MockitoBean
    private EnvioAvisos envio;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CanchaRepository canchas;
    @Autowired
    private EventoRepository eventos;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private AvisoService avisos;

    private String organizador;
    private String jugador;
    private Long canchaId;

    @BeforeEach
    void preparar() throws Exception {
        organizador = registrar("org.push@ejemplo.com", "Org");
        jugador = registrar("jug.push@ejemplo.com", "Jug");
        guardarToken(organizador, TOKEN_ORG);
        guardarToken(jugador, TOKEN_JUG);
        canchaId = canchas.save(new Cancha("Pista de avisos", "Villaprueba", 30.0, -30.0, null)).getId();
    }

    @Test
    void tokenNoValido() throws Exception {
        mvc.perform(put("/api/usuarios/yo/push-token").header("Authorization", jugador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"hola\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alApuntarseSeAvisaAlOrganizador() throws Exception {
        Integer evento = crearEvento();
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", jugador));

        List<Aviso> enviados = capturar();
        assertThat(enviados).hasSize(1);
        assertThat(enviados.get(0).token()).isEqualTo(TOKEN_ORG);
        assertThat(enviados.get(0).cuerpo()).isEqualTo("Jug se ha apuntado a «Pachanga de prueba» (2/10)");
        assertThat(enviados.get(0).ruta()).isEqualTo("/evento/" + evento);
    }

    @Test
    void alCancelarSeAvisaALosApuntadosMenosAlOrganizador() throws Exception {
        Integer evento = crearEvento();
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", jugador));
        clearInvocations(envio); // olvidamos el aviso de "se ha apuntado"

        mvc.perform(post("/api/eventos/" + evento + "/cancelar").header("Authorization", organizador));

        List<Aviso> enviados = capturar();
        assertThat(enviados).extracting(Aviso::token).containsExactly(TOKEN_JUG);
        assertThat(enviados.get(0).titulo()).isEqualTo("❌ Evento cancelado");
    }

    @Test
    void unMensajeAvisaATodosMenosAlAutor() throws Exception {
        Integer evento = crearEvento();
        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", jugador));
        clearInvocations(envio);

        mvc.perform(post("/api/eventos/" + evento + "/mensajes").header("Authorization", jugador)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\": \"Llevo balón\"}"));

        List<Aviso> enviados = capturar();
        assertThat(enviados).extracting(Aviso::token).containsExactly(TOKEN_ORG);
        assertThat(enviados.get(0).cuerpo()).isEqualTo("Jug: Llevo balón");
        assertThat(enviados.get(0).ruta()).isEqualTo("/chat/" + evento);
    }

    @Test
    void alCerrarSesionNoLleganMasAvisos() throws Exception {
        Integer evento = crearEvento();
        mvc.perform(delete("/api/usuarios/yo/push-token").header("Authorization", organizador))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/eventos/" + evento + "/inscripcion").header("Authorization", jugador));
        verify(envio, never()).enviar(any());
    }

    @Test
    void recordatorioUnaVezParaLosEventosQueEmpiezanPronto() throws Exception {
        var org = usuarios.findByEmail("org.push@ejemplo.com").orElseThrow();
        Cancha cancha = canchas.getReferenceById(canchaId);
        Instant dentroDe30 = Instant.now().plus(Duration.ofMinutes(30));
        Instant dentroDe3h = Instant.now().plus(Duration.ofHours(3));
        Evento pronto = new Evento(cancha, org, com.pachanga.api.modelo.TipoEvento.PACHANGA, "Pronto",
                dentroDe30, dentroDe30.plus(Duration.ofHours(1)), 10);
        Evento tarde = new Evento(cancha, org, com.pachanga.api.modelo.TipoEvento.PACHANGA, "Tarde",
                dentroDe3h, dentroDe3h.plus(Duration.ofHours(1)), 10);
        eventos.saveAll(List.of(pronto, tarde));
        // Solo apuntamos al jugador (estos eventos se han creado sin la API, así que nadie más está inscrito).
        mvc.perform(post("/api/eventos/" + pronto.getId() + "/inscripcion").header("Authorization", jugador));
        mvc.perform(post("/api/eventos/" + tarde.getId() + "/inscripcion").header("Authorization", jugador));
        clearInvocations(envio);

        avisos.enviarRecordatorios();

        // Miramos solo los avisos de nuestros eventos: en tu base de datos local podría haber otros.
        List<Aviso> dePronto = avisosHacia("/evento/" + pronto.getId());
        assertThat(dePronto).extracting(Aviso::token).containsExactly(TOKEN_JUG);
        assertThat(dePronto.get(0).cuerpo()).startsWith("«Pronto» empieza en 30 min");
        assertThat(avisosHacia("/evento/" + tarde.getId())).isEmpty(); // empieza en 3 h: aún no toca

        // La segunda vuelta ya no lo repite.
        clearInvocations(envio);
        avisos.enviarRecordatorios();
        assertThat(avisosHacia("/evento/" + pronto.getId())).isEmpty();
    }

    // ------------------------------------------------------------------ ayudantes

    /** Recoge lo que se le pasó a envio.enviar(...) la última vez. */
    @SuppressWarnings("unchecked")
    private List<Aviso> capturar() {
        ArgumentCaptor<List<Aviso>> captor = ArgumentCaptor.forClass(List.class);
        verify(envio).enviar(captor.capture());
        return captor.getValue();
    }

    /** Todos los avisos que se han pasado a envio.enviar(...), en todas las llamadas, que abren esa ruta. */
    @SuppressWarnings("unchecked")
    private List<Aviso> avisosHacia(String ruta) {
        return mockingDetails(envio).getInvocations().stream()
                .filter(llamada -> llamada.getMethod().getName().equals("enviar"))
                .flatMap(llamada -> ((List<Aviso>) llamada.getArgument(0)).stream())
                .filter(aviso -> aviso.ruta().equals(ruta))
                .toList();
    }

    private Integer crearEvento() throws Exception {
        Instant inicio = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        String json = mvc.perform(post("/api/eventos").header("Authorization", organizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canchaId": %d, "tipo": "PACHANGA", "titulo": "Pachanga de prueba", "inicio": "%s",
                                 "fin": "%s", "plazas": 10}
                                """.formatted(canchaId, inicio, inicio.plus(Duration.ofHours(2)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private void guardarToken(String sesion, String pushToken) throws Exception {
        mvc.perform(put("/api/usuarios/yo/push-token").header("Authorization", sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + pushToken + "\"}"))
                .andExpect(status().isNoContent());
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
