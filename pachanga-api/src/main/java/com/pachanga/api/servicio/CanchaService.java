package com.pachanga.api.servicio;

import com.pachanga.api.dto.CanchaDetalle;
import com.pachanga.api.dto.CanchaResumen;
import com.pachanga.api.dto.CrearCanchaPeticion;
import com.pachanga.api.dto.EventoResumen;
import com.pachanga.api.dto.FotoRespuesta;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.modelo.EstadoEvento;
import com.pachanga.api.modelo.Usuario;
import com.pachanga.api.repositorio.CanchaRepository;
import com.pachanga.api.repositorio.EventoRepository;
import com.pachanga.api.repositorio.FotoCanchaRepository;
import com.pachanga.api.repositorio.InscripcionRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Canchas: búsquedas del mapa, alta de canchas y ficha. */
@Service
@Transactional(readOnly = true) // por defecto solo lee; los métodos que escriben lo indican con @Transactional
public class CanchaService {

    /** Si la app no dice el radio, buscamos a 10 km. */
    public static final int RADIO_POR_DEFECTO = 10_000;
    /** Más de 50 km no tiene sentido para "canchas cerca" y haría la consulta pesada. */
    public static final int RADIO_MAXIMO = 50_000;
    /** Tope de canchas por respuesta, para que el mapa no se llene de miles de chinchetas. */
    public static final int MAX_RESULTADOS = 200;
    /**
     * Dos canchas a menos de esta distancia probablemente son la misma.
     * Antes de crear una, la app pregunta "¿es esta?" con las que haya en este radio.
     */
    public static final int RADIO_DUPLICADO = 50;

    /** Radio medio de la Tierra en metros, para calcular distancias. */
    private static final double RADIO_TIERRA = 6_371_000;

    private final CanchaRepository canchas;
    private final FotoCanchaRepository fotos;
    private final EventoRepository eventos;
    private final InscripcionRepository inscripciones;
    private final UsuarioRepository usuarios;
    private final BloqueoService bloqueos;

    public CanchaService(CanchaRepository canchas, FotoCanchaRepository fotos, EventoRepository eventos,
                         InscripcionRepository inscripciones, UsuarioRepository usuarios, BloqueoService bloqueos) {
        this.canchas = canchas;
        this.fotos = fotos;
        this.eventos = eventos;
        this.inscripciones = inscripciones;
        this.usuarios = usuarios;
        this.bloqueos = bloqueos;
    }

    /** Canchas a menos de {@code radio} metros del punto, la más cercana primero. */
    public List<CanchaResumen> cercanas(double latitud, double longitud, Integer radio) {
        comprobarCoordenadas(latitud, longitud);
        int metros = radio == null ? RADIO_POR_DEFECTO : radio;
        if (metros < 1 || metros > RADIO_MAXIMO) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "El radio debe estar entre 1 y " + RADIO_MAXIMO + " metros");
        }
        return buscar(latitud, longitud, metros);
    }

    /** Canchas que ya existen a menos de 50 m del punto donde se quiere crear una nueva. */
    public List<CanchaResumen> posiblesDuplicados(double latitud, double longitud) {
        comprobarCoordenadas(latitud, longitud);
        return buscar(latitud, longitud, RADIO_DUPLICADO);
    }

    /** Canchas de una ciudad, ordenadas por nombre. No distingue mayúsculas ni tildes. */
    public List<CanchaResumen> porCiudad(String ciudad) {
        String limpia = ciudad == null ? "" : ciudad.trim();
        if (limpia.length() < 2) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "Escribe el nombre de una ciudad");
        }
        return canchas.buscarPorCiudad(limpia).stream()
                .limit(MAX_RESULTADOS)
                .map(c -> CanchaResumen.de(c, null))
                .toList();
    }

    /** Da de alta una cancha. Quien la crea queda guardado como su autor. */
    @Transactional
    public CanchaDetalle crear(Long usuarioId, CrearCanchaPeticion datos) {
        Usuario autor = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));

        Cancha cancha = new Cancha(datos.nombre(), datos.ciudad(), datos.latitud(), datos.longitud(), autor);
        cancha.setDescripcion(datos.descripcion());
        cancha.setDireccion(datos.direccion());
        cancha.setSuperficie(datos.superficie());
        cancha.setNumCanastas(datos.numCanastas());
        // Si la app no lo dice: descubierta, sin luz y gratis (lo más habitual en una pista de calle).
        cancha.setCubierta(Boolean.TRUE.equals(datos.cubierta()));
        cancha.setIluminacion(Boolean.TRUE.equals(datos.iluminacion()));
        cancha.setGratuita(!Boolean.FALSE.equals(datos.gratuita()));

        return detalle(canchas.save(cancha), usuarioId);
    }

    /**
     * La ficha de una cancha, con sus fotos y sus próximos eventos, vista por {@code usuarioId}:
     * no salen las fotos ni los eventos de usuarios con los que hay un bloqueo. 404 si no existe.
     */
    public CanchaDetalle detalle(Long id, Long usuarioId) {
        return detalle(buscarCancha(id), usuarioId);
    }

    /** Devuelve la cancha o lanza un 404. Lo usa también FotoService. */
    Cancha buscarCancha(Long id) {
        return canchas.findById(id)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.NOT_FOUND, "Esa cancha no existe"));
    }

    private CanchaDetalle detalle(Cancha cancha, Long usuarioId) {
        Set<Long> ocultos = bloqueos.ocultosPara(usuarioId);

        List<FotoRespuesta> listaFotos = fotos.findByCanchaIdOrderByCreadoEnAsc(cancha.getId()).stream()
                .filter(f -> f.getSubidaPor() == null || !ocultos.contains(f.getSubidaPor().getId()))
                .map(FotoRespuesta::de)
                .toList();

        List<EventoResumen> proximos = eventos
                .findByCanchaIdAndEstadoAndFinAfterOrderByInicioAsc(cancha.getId(), EstadoEvento.ACTIVO, Instant.now())
                .stream()
                .filter(e -> !ocultos.contains(e.getOrganizador().getId()))
                .map(e -> EventoResumen.de(e, inscripciones.countByEventoId(e.getId())))
                .toList();

        // getCreadaPor() es LAZY: aquí, dentro de la transacción, Hibernate lo carga al pedir el nombre.
        Usuario creador = cancha.getCreadaPor();
        String autor = creador == null || ocultos.contains(creador.getId()) ? null : creador.getNombre();

        return new CanchaDetalle(cancha.getId(), cancha.getNombre(), cancha.getDescripcion(), cancha.getDireccion(),
                cancha.getCiudad(), cancha.getLatitud(), cancha.getLongitud(), cancha.getSuperficie(),
                cancha.getNumCanastas(), cancha.isCubierta(), cancha.isIluminacion(), cancha.isGratuita(),
                autor, listaFotos, proximos);
    }

    private List<CanchaResumen> buscar(double latitud, double longitud, int metros) {
        return canchas.buscarCercanas(latitud, longitud, metros, MAX_RESULTADOS).stream()
                .map(c -> CanchaResumen.de(c, distanciaMetros(latitud, longitud, c)))
                .toList();
    }

    private static void comprobarCoordenadas(double latitud, double longitud) {
        if (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "Las coordenadas no son válidas");
        }
    }

    /**
     * Distancia en línea recta entre el punto y la cancha, con la fórmula del haversine
     * (tiene en cuenta que la Tierra es una esfera). Para mostrar "a 350 m" es más que suficiente.
     */
    private static int distanciaMetros(double latitud, double longitud, Cancha cancha) {
        double dLat = Math.toRadians(cancha.getLatitud() - latitud);
        double dLng = Math.toRadians(cancha.getLongitud() - longitud);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latitud)) * Math.cos(Math.toRadians(cancha.getLatitud()))
                * Math.pow(Math.sin(dLng / 2), 2);
        return (int) Math.round(2 * RADIO_TIERRA * Math.asin(Math.sqrt(a)));
    }
}
