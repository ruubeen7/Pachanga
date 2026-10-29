package com.pachanga.api.servicio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pachanga.api.dto.DireccionSugerencia;
import com.pachanga.api.error.ExcepcionApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Buscador de direcciones: escribes "calle feria 25 sevilla" y devuelve sitios con sus coordenadas.
 *
 * Usa Photon (https://photon.komoot.io), un buscador gratis y sin clave que funciona con los datos
 * de OpenStreetMap, los mismos del mapa. El buscador de Google (Places) es de pago y pide tarjeta.
 *
 * La app no llama a Photon directamente, sino a nuestra API (GET /api/direcciones). Así, si un día
 * cambiamos de buscador, solo se toca esta clase y la app sigue igual.
 */
@Service
public class DireccionService {

    /** Con menos letras salen resultados sin sentido, así que no buscamos. */
    public static final int MINIMO_LETRAS = 3;
    private static final int MAXIMO_SUGERENCIAS = 6;
    /** Rectángulo que cubre España, Baleares y Canarias: oeste, sur, este, norte. */
    private static final String ESPANA = "-18.5,27.5,4.5,44";
    /**
     * Tipos de sitio de OpenStreetMap que pueden ser una cancha (osm_value). Solo de estos usamos
     * su nombre como nombre de la cancha; si no, al buscar "calle feria 25" saldría el nombre de una tienda.
     */
    private static final Set<String> SITIOS_DEPORTIVOS = Set.of(
            "park", "pitch", "sports_centre", "playground", "recreation_ground", "stadium", "school");

    private final RestClient http;
    private final String url;

    public DireccionService(@Value("${pachanga.direcciones.url}") String url) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

        // Si Photon tarda mucho, mejor cortar y que el usuario use el mapa.
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(Duration.ofSeconds(5));
        tiempos.setReadTimeout(Duration.ofSeconds(8));

        this.http = RestClient.builder()
                .requestFactory(tiempos)
                // Photon pide que cada aplicación se identifique.
                .defaultHeader("User-Agent", "Pachanga (TFG 2 DAM)")
                .build();
    }

    /**
     * Busca sitios que coincidan con el texto. Si se pasa la ubicación del usuario (lat y lng),
     * salen primero los que están cerca, como en las apps de comida a domicilio.
     */
    public List<DireccionSugerencia> buscar(String texto, Double lat, Double lng) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.length() < MINIMO_LETRAS) {
            return List.of();
        }

        RespuestaPhoton respuesta;
        try {
            respuesta = http.get()
                    .uri(url + "/api/", uri -> {
                        uri.queryParam("q", limpio)
                                .queryParam("limit", 10)
                                .queryParam("bbox", ESPANA);
                        if (lat != null && lng != null) {
                            uri.queryParam("lat", lat).queryParam("lon", lng);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(RespuestaPhoton.class);
        } catch (RestClientException e) {
            throw new ExcepcionApi(HttpStatus.SERVICE_UNAVAILABLE,
                    "El buscador de direcciones no responde. Puedes marcar el sitio en el mapa.");
        }

        List<DireccionSugerencia> sugerencias = new ArrayList<>();
        if (respuesta == null || respuesta.features() == null) {
            return sugerencias;
        }

        // Photon a veces devuelve la misma calle varias veces (un trozo por cada tramo).
        // Guardamos "título + detalle" de las que ya hemos metido para no repetirlas.
        Set<String> vistas = new LinkedHashSet<>();
        for (Lugar lugar : respuesta.features()) {
            DireccionSugerencia sugerencia = convertir(lugar);
            if (sugerencia == null) {
                continue;
            }
            String clave = sugerencia.titulo() + "|" + sugerencia.detalle();
            if (vistas.add(clave)) {
                sugerencias.add(sugerencia);
            }
            if (sugerencias.size() == MAXIMO_SUGERENCIAS) {
                break;
            }
        }
        return sugerencias;
    }

    /** Pasa un resultado de Photon a nuestra sugerencia. Devuelve null si le falta lo básico. */
    private static DireccionSugerencia convertir(Lugar lugar) {
        if (lugar.properties() == null || lugar.geometry() == null
                || lugar.geometry().coordinates() == null || lugar.geometry().coordinates().size() < 2) {
            return null;
        }
        Propiedades p = lugar.properties();

        // Calle con número, si los tiene: "Calle Feria, 25".
        String calle = null;
        if (tieneTexto(p.street())) {
            calle = tieneTexto(p.housenumber()) ? p.street() + ", " + p.housenumber() : p.street();
        } else if ("street".equals(p.type())) {
            calle = p.name(); // el propio resultado es una calle
        }

        String titulo = tieneTexto(p.name()) ? p.name() : calle;
        if (!tieneTexto(titulo)) {
            return null;
        }

        String ciudad = tieneTexto(p.city()) ? p.city() : p.county();

        // Línea de debajo: la calle (si el título es el nombre de un sitio), la ciudad y la comunidad.
        List<String> partes = new ArrayList<>();
        if (calle != null && !calle.equals(titulo)) {
            partes.add(calle);
        }
        if (tieneTexto(ciudad)) {
            partes.add(ciudad);
        }
        if (tieneTexto(p.state())) {
            partes.add(p.state());
        }

        // En GeoJSON las coordenadas van al revés de lo normal: primero longitud y luego latitud.
        double longitud = lugar.geometry().coordinates().get(0);
        double latitud = lugar.geometry().coordinates().get(1);

        // Ojo: un Set.of(...) lanza un error si le preguntas por null, por eso miramos antes tieneTexto.
        boolean esDeportivo = tieneTexto(p.osmValue()) && SITIOS_DEPORTIVOS.contains(p.osmValue());
        String nombreSugerido = tieneTexto(p.name()) && esDeportivo ? p.name() : null;

        return new DireccionSugerencia(titulo, String.join(", ", partes), calle, ciudad, nombreSugerido,
                latitud, longitud);
    }

    private static boolean tieneTexto(String s) {
        return s != null && !s.isBlank();
    }

    // --- Lo que contesta Photon (formato GeoJSON). Solo ponemos los campos que usamos. ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaPhoton(List<Lugar> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Lugar(Propiedades properties, Geometria geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Propiedades(String name, String type, String street, String housenumber,
                       String city, String county, String state,
                       @JsonProperty("osm_value") String osmValue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometria(List<Double> coordinates) {
    }
}
