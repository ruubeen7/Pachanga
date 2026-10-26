package com.pachanga.api.servicio;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que AlmacenFotosSupabase llama a Supabase Storage como debe, sin conectarse a Supabase:
 * levantamos un mini servidor HTTP en el propio test que hace de "Supabase falso"
 * y apunta lo que le llega.
 */
class AlmacenFotosSupabaseTest {

    private HttpServer servidor;
    private String urlFalsa;
    private int respuesta = 200;

    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> apikey = new AtomicReference<>();
    private final AtomicReference<String> autorizacion = new AtomicReference<>();
    private final AtomicReference<String> tipo = new AtomicReference<>();
    private final AtomicReference<byte[]> cuerpo = new AtomicReference<>();

    @BeforeEach
    void arrancarSupabaseFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0); // puerto 0 = uno libre cualquiera
        servidor.createContext("/", intercambio -> {
            ruta.set(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath());
            apikey.set(intercambio.getRequestHeaders().getFirst("apikey"));
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            tipo.set(intercambio.getRequestHeaders().getFirst("Content-Type"));
            cuerpo.set(intercambio.getRequestBody().readAllBytes());
            intercambio.sendResponseHeaders(respuesta, -1);
            intercambio.close();
        });
        servidor.start();
        urlFalsa = "http://localhost:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void pararSupabaseFalso() {
        servidor.stop(0);
    }

    @Test
    void subeLaFotoAlCuboYDevuelveSuUrlPublica() {
        AlmacenFotos almacen = new AlmacenFotosSupabase(urlFalsa + "/", "sb_secret_prueba", "fotos");
        byte[] imagen = {1, 2, 3};

        String url = almacen.guardar(imagen, "png");

        assertThat(ruta.get()).startsWith("POST /storage/v1/object/fotos/").endsWith(".png");
        assertThat(apikey.get()).isEqualTo("sb_secret_prueba");
        assertThat(autorizacion.get()).as("las claves nuevas no van en Authorization").isNull();
        assertThat(tipo.get()).isEqualTo("image/png");
        assertThat(cuerpo.get()).containsExactly(1, 2, 3);

        String nombre = ruta.get().substring(ruta.get().lastIndexOf('/') + 1);
        assertThat(url).isEqualTo(urlFalsa + "/storage/v1/object/public/fotos/" + nombre);
    }

    @Test
    void laClaveAntiguaTambienVaEnAuthorization() {
        AlmacenFotos almacen = new AlmacenFotosSupabase(urlFalsa, "eyJhbGciOi.clave.antigua", "fotos");

        almacen.guardar(new byte[]{9}, "jpg");

        assertThat(autorizacion.get()).isEqualTo("Bearer eyJhbGciOi.clave.antigua");
        assertThat(tipo.get()).isEqualTo("image/jpeg");
    }

    @Test
    void siSupabaseFallaLanzaError() {
        respuesta = 403; // por ejemplo, clave equivocada
        AlmacenFotos almacen = new AlmacenFotosSupabase(urlFalsa, "sb_secret_mala", "fotos");

        assertThatThrownBy(() -> almacen.guardar(new byte[]{1}, "jpg")).isInstanceOf(RuntimeException.class);
    }
}
