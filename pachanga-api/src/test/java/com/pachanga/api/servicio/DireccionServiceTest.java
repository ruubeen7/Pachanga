package com.pachanga.api.servicio;

import com.pachanga.api.dto.DireccionSugerencia;
import com.pachanga.api.error.ExcepcionApi;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba DireccionService sin salir a internet: un mini servidor HTTP hace de "Photon falso"
 * y contesta siempre lo que pongamos en respuestaJson.
 */
class DireccionServiceTest {

    private HttpServer servidor;
    private DireccionService servicio;
    private int estado = 200;
    private String respuestaJson = "{\"features\":[]}";
    private final AtomicReference<String> consulta = new AtomicReference<>();

    @BeforeEach
    void arrancarPhotonFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            String query = intercambio.getRequestURI().getRawQuery();
            consulta.set(intercambio.getRequestURI().getPath() + "?" + URLDecoder.decode(query, StandardCharsets.UTF_8));
            byte[] cuerpo = respuestaJson.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
        servicio = new DireccionService("http://localhost:" + servidor.getAddress().getPort());
    }

    @AfterEach
    void pararPhotonFalso() {
        servidor.stop(0);
    }

    @Test
    void convierteLosResultadosYQuitaLosRepetidos() {
        respuestaJson = """
                {"features":[
                  {"properties":{"name":"Calle Feria","type":"street","city":"Sevilla","state":"Andalucía","osm_id":1},
                   "geometry":{"type":"Point","coordinates":[-5.9912,37.3963]}},
                  {"properties":{"name":"Calle Feria","type":"street","city":"Sevilla","state":"Andalucía","osm_id":2},
                   "geometry":{"type":"Point","coordinates":[-5.9911,37.3961]}},
                  {"properties":{"name":"Archivo de Protocolos","osm_value":"yes","type":"house","street":"Calle Feria","housenumber":"25","city":"Sevilla","state":"Andalucía"},
                   "geometry":{"type":"Point","coordinates":[-5.99,37.40]}},
                  {"properties":{"name":"Pista de baloncesto","type":"house","osm_value":"pitch","street":"Avenida de los Deportes","county":"Gandía"},
                   "geometry":{"type":"Point","coordinates":[-0.18,38.97]}}
                ]}""";

        List<DireccionSugerencia> sugerencias = servicio.buscar("  calle feria  ", 37.39, -5.98);

        assertThat(consulta.get())
                .startsWith("/api/?")
                .contains("q=calle feria", "lat=37.39", "lon=-5.98", "bbox=-18.5,27.5,4.5,44");

        assertThat(sugerencias).hasSize(3); // la calle repetida sale una vez

        DireccionSugerencia calle = sugerencias.get(0);
        assertThat(calle.titulo()).isEqualTo("Calle Feria");
        assertThat(calle.detalle()).isEqualTo("Sevilla, Andalucía");
        assertThat(calle.direccion()).isEqualTo("Calle Feria");
        assertThat(calle.ciudad()).isEqualTo("Sevilla");
        assertThat(calle.latitud()).isEqualTo(37.3963); // en GeoJSON va primero la longitud
        assertThat(calle.longitud()).isEqualTo(-5.9912);
        assertThat(calle.nombreSugerido()).isNull();

        DireccionSugerencia edificio = sugerencias.get(1);
        assertThat(edificio.titulo()).isEqualTo("Archivo de Protocolos");
        assertThat(edificio.direccion()).isEqualTo("Calle Feria, 25");
        assertThat(edificio.nombreSugerido()).as("un edificio no es una cancha").isNull();

        DireccionSugerencia pista = sugerencias.get(2);
        assertThat(pista.titulo()).isEqualTo("Pista de baloncesto");
        assertThat(pista.detalle()).isEqualTo("Avenida de los Deportes, Gandía");
        assertThat(pista.ciudad()).as("sin city usamos county").isEqualTo("Gandía");
        assertThat(pista.nombreSugerido()).isEqualTo("Pista de baloncesto");
    }

    @Test
    void conPocasLetrasNoPreguntaAPhoton() {
        assertThat(servicio.buscar("ca", null, null)).isEmpty();
        assertThat(servicio.buscar("   ", null, null)).isEmpty();
        assertThat(consulta.get()).isNull();
    }

    @Test
    void sinUbicacionNoMandaLatNiLon() {
        servicio.buscar("Nervión", null, null);

        assertThat(consulta.get()).contains("q=Nervión").doesNotContain("lat=").doesNotContain("lon=");
    }

    @Test
    void siPhotonFallaDevuelveUnErrorClaro() {
        estado = 500;

        assertThatThrownBy(() -> servicio.buscar("calle feria", null, null))
                .isInstanceOf(ExcepcionApi.class)
                .hasMessageContaining("marcar el sitio en el mapa");
    }
}
