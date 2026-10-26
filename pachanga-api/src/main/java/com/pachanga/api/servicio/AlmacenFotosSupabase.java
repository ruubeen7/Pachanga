package com.pachanga.api.servicio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Guarda las fotos en Supabase Storage (el "disco en la nube" gratuito de Supabase).
 * Se usa en producción, porque en Render el disco del servidor se borra en cada despliegue.
 *
 * Se activa con pachanga.fotos.almacen=supabase (variable FOTOS_ALMACEN). Entonces Spring
 * crea esta clase en vez de AlmacenFotosLocal, y FotoService ni se entera: solo conoce la interfaz.
 *
 * Supabase Storage se usa con peticiones HTTP normales:
 *   POST {url}/storage/v1/object/{cubo}/{nombre}          → sube el archivo (necesita la clave secreta)
 *   GET  {url}/storage/v1/object/public/{cubo}/{nombre}   → cualquiera puede verlo (el cubo es público)
 * Un "cubo" (bucket) es como una carpeta principal; el nuestro se llama "fotos".
 */
@Component
@ConditionalOnProperty(name = "pachanga.fotos.almacen", havingValue = "supabase")
public class AlmacenFotosSupabase implements AlmacenFotos {

    private final RestClient http;
    private final String url;
    private final String cubo;

    public AlmacenFotosSupabase(@Value("${pachanga.supabase.url}") String url,
                                @Value("${pachanga.supabase.clave-secreta}") String claveSecreta,
                                @Value("${pachanga.supabase.cubo-fotos}") String cubo) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.cubo = cubo;

        RestClient.Builder builder = RestClient.builder().defaultHeader("apikey", claveSecreta);
        // Las claves antiguas de Supabase (service_role) son un JWT y también van en Authorization.
        // Las nuevas (empiezan por "sb_secret_") solo van en "apikey".
        if (!claveSecreta.startsWith("sb_")) {
            builder.defaultHeader("Authorization", "Bearer " + claveSecreta);
        }
        this.http = builder.build();
    }

    @Override
    public String guardar(byte[] datos, String extension) {
        // Igual que en local: nombre aleatorio, nunca el que manda el móvil.
        String nombre = UUID.randomUUID() + "." + extension;
        http.post()
                .uri(url + "/storage/v1/object/" + cubo + "/" + nombre)
                .contentType(tipo(extension))
                .body(datos)
                .retrieve()
                .toBodilessEntity(); // si Supabase responde con error, lanza una excepción y no se guarda nada
        return url + "/storage/v1/object/public/" + cubo + "/" + nombre;
    }

    private static MediaType tipo(String extension) {
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
    }
}
