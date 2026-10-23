package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.modelo.Evento;
import com.pachanga.api.modelo.Inscripcion;
import com.pachanga.api.modelo.TipoEvento;
import com.pachanga.api.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que las tablas (script de Flyway) y las clases Java encajan
 * y que las consultas importantes funcionan contra la base de datos de Docker.
 *
 * @Transactional: al acabar cada test se deshace todo (rollback), así no se queda basura en la base de datos.
 */
@SpringBootTest
@ActiveProfiles("test") // sin el perfil "dev": no se meten canchas de prueba
@Transactional
class ModeloDatosTest {

    // Punto inventado en medio del Atlántico: así ninguna cancha que ya haya en la base de datos
    // (por ejemplo, las de prueba del perfil dev) se cuela en los resultados de los tests.
    private static final double LAT_PRUEBAS = 30.0;
    private static final double LNG_PRUEBAS = -30.0;

    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private CanchaRepository canchas;
    @Autowired
    private EventoRepository eventos;
    @Autowired
    private InscripcionRepository inscripciones;

    @Test
    void guardaUnUsuarioConElEmailEnMinusculas() {
        Usuario guardado = usuarios.save(new Usuario("  Ruben@Ejemplo.COM ", "Rubén", "hash"));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getCreadoEn()).isNotNull();
        assertThat(usuarios.findByEmail("ruben@ejemplo.com")).isPresent();
    }

    @Test
    void buscaCanchasCercanasOrdenadasPorDistancia() {
        Usuario autor = usuarios.save(new Usuario("autor@ejemplo.com", "Autor", "hash"));
        // 0.0003 grados de latitud son unos 33 m; 0.01 son unos 1,1 km.
        Cancha a30m = canchas.save(new Cancha("A 30 m", "Villaprueba", LAT_PRUEBAS + 0.0003, LNG_PRUEBAS, autor));
        Cancha a1km = canchas.save(new Cancha("A 1 km", "Villaprueba", LAT_PRUEBAS + 0.01, LNG_PRUEBAS, autor));
        canchas.save(new Cancha("Lejos", "Pueblo Lejano", 31.0, -31.0, autor));
        canchas.flush(); // fuerza los INSERT antes de consultar con SQL nativo

        // Comprobación de duplicados: ¿hay alguna a menos de 50 m?
        List<Cancha> a50m = canchas.buscarCercanas(LAT_PRUEBAS, LNG_PRUEBAS, 50, 10);
        assertThat(a50m).extracting(Cancha::getId).containsExactly(a30m.getId());

        // Mapa: canchas a menos de 5 km, la más cercana primero.
        List<Cancha> a5km = canchas.buscarCercanas(LAT_PRUEBAS, LNG_PRUEBAS, 5_000, 10);
        assertThat(a5km).extracting(Cancha::getId).containsExactly(a30m.getId(), a1km.getId());

        // Las coordenadas vuelven tal cual se guardaron.
        assertThat(a5km.getFirst().getLatitud()).isEqualTo(LAT_PRUEBAS + 0.0003);
        assertThat(a5km.getFirst().getLongitud()).isEqualTo(LNG_PRUEBAS);
    }

    @Test
    void buscaPorCiudadSinImportarMayusculas() {
        Usuario autor = usuarios.save(new Usuario("autor@ejemplo.com", "Autor", "hash"));
        canchas.save(new Cancha("Pista del parque", "Villaprueba", LAT_PRUEBAS, LNG_PRUEBAS, autor));

        assertThat(canchas.buscarPorCiudad("VILLAPRUEBA")).hasSize(1);
        assertThat(canchas.buscarPorCiudad("Pueblo Lejano")).isEmpty();
    }

    @Test
    void noPermiteApuntarseDosVecesAlMismoEvento() {
        Usuario organizador = usuarios.save(new Usuario("orga@ejemplo.com", "Orga", "hash"));
        Usuario jugador = usuarios.save(new Usuario("jugador@ejemplo.com", "Jugador", "hash"));
        Cancha cancha = canchas.save(new Cancha("Pista", "Villaprueba", LAT_PRUEBAS, LNG_PRUEBAS, organizador));
        Instant inicio = Instant.now().plus(Duration.ofDays(1));
        Evento evento = eventos.save(new Evento(cancha, organizador, TipoEvento.PACHANGA, "Pachanga del jueves",
                inicio, inicio.plus(Duration.ofHours(2)), 10));

        inscripciones.saveAndFlush(new Inscripcion(evento, jugador));
        assertThat(inscripciones.countByEventoId(evento.getId())).isEqualTo(1);

        // La restricción UNIQUE (evento_id, usuario_id) de la base de datos lo impide.
        assertThatThrownBy(() -> inscripciones.saveAndFlush(new Inscripcion(evento, jugador)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
