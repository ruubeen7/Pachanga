package com.pachanga.api.config;

import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.modelo.Superficie;
import com.pachanga.api.repositorio.CanchaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mete unas cuantas canchas de ejemplo para poder probar el mapa en local
 * mientras aún no se pueden crear canchas desde la app.
 *
 * @Profile("dev"): esta clase SOLO existe cuando la API arranca con el perfil "dev"
 * (el de tu PC, ver application.properties). En producción no se ejecuta nunca.
 *
 * ApplicationRunner: Spring llama a run() una vez, justo después de arrancar.
 * Solo inserta si la tabla está vacía, así no se duplican al reiniciar.
 *
 * Las ubicaciones son aproximadas: son datos de prueba, no canchas comprobadas.
 */
@Component
@Profile("dev")
public class DatosDePrueba implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDePrueba.class);

    private final CanchaRepository canchas;

    public DatosDePrueba(CanchaRepository canchas) {
        this.canchas = canchas;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (canchas.count() > 0) return;

        List<Cancha> ejemplo = List.of(
                cancha("Pista del Parque de la Buhaira", "Sevilla", 37.3830, -5.9800, Superficie.CEMENTO, 2, true),
                cancha("Canchas del Parque de María Luisa", "Sevilla", 37.3760, -5.9880, Superficie.ASFALTO, 4, false),
                cancha("Pista del Parque de los Príncipes", "Sevilla", 37.3775, -6.0045, Superficie.CEMENTO, 2, true),
                cancha("Canchas del Parque del Alamillo", "Sevilla", 37.4170, -5.9900, Superficie.SINTETICO, 4, true),
                cancha("Pista de la Alameda", "Sevilla", 37.3990, -5.9930, Superficie.CEMENTO, 1, true),
                cancha("Pista del Parque Amate", "Sevilla", 37.3770, -5.9560, Superficie.ASFALTO, 2, false),
                cancha("Pista de Triana", "Sevilla", 37.3840, -6.0060, Superficie.CEMENTO, 2, true),
                cancha("Canchas de Madrid Río", "Madrid", 40.4020, -3.7200, Superficie.SINTETICO, 6, true),
                cancha("Pista del Retiro", "Madrid", 40.4153, -3.6845, Superficie.CEMENTO, 2, false),
                cancha("Pista del Paseo Marítimo", "Málaga", 36.7190, -4.4100, Superficie.CEMENTO, 2, true));

        canchas.saveAll(ejemplo);
        log.info("Perfil dev: añadidas {} canchas de prueba", ejemplo.size());
    }

    private static Cancha cancha(String nombre, String ciudad, double latitud, double longitud,
                                 Superficie superficie, int canastas, boolean iluminacion) {
        Cancha cancha = new Cancha(nombre, ciudad, latitud, longitud, null); // sin autor: la ha puesto el sistema
        cancha.setDescripcion("Cancha de prueba para desarrollo (ubicación aproximada).");
        cancha.setSuperficie(superficie);
        cancha.setNumCanastas(canastas);
        cancha.setIluminacion(iluminacion);
        return cancha;
    }
}
