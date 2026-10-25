import * as Location from 'expo-location';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Pressable,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { canchasCercanas, canchasDeCiudad, type CanchaResumen } from '@/api/canchas';
import { ErrorApi } from '@/api/cliente';
import { Mapa, type ControlMapa } from '@/components/mapa/mapa';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';
import { abrirIndicaciones } from '@/utils/indicaciones';

/** Si no tenemos tu ubicación, el mapa empieza en el centro de Sevilla. */
const SEVILLA = { latitud: 37.3891, longitud: -5.9845 };
/** La API no deja buscar a más de 50 km (CanchaService.RADIO_MAXIMO). */
const RADIO_MAXIMO = 50_000;
/** Esperamos a que el mapa esté quieto este tiempo antes de pedir canchas (ver cargarZona). */
const ESPERA_MS = 400;

export default function MapaScreen() {
  const { token, cerrarSesion } = useSesion();
  const tema = useTheme();
  const margenes = useSafeAreaInsets(); // espacio de la "isla" del iPhone, la barra de estado...

  const mapa = useRef<ControlMapa>(null);
  const [canchas, setCanchas] = useState<CanchaResumen[]>([]);
  const [elegidaId, setElegidaId] = useState<number | null>(null);
  const [miUbicacion, setMiUbicacion] = useState<{ latitud: number; longitud: number } | null>(null);
  const [busqueda, setBusqueda] = useState('');
  const [buscando, setBuscando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);

  // useRef guarda un valor entre repintados SIN provocar un repintado al cambiarlo.
  const temporizador = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** Última zona que ha enseñado el mapa (centro y radio). */
  const zona = useRef<{ latitud: number; longitud: number; radio: number } | null>(null);

  // useFocusEffect se ejecuta cada vez que esta pantalla vuelve a verse (por ejemplo,
  // al volver de crear una cancha): recargamos la zona para que salga la nueva.
  useFocusEffect(
    useCallback(() => {
      if (zona.current) cargarZona(zona.current.latitud, zona.current.longitud, zona.current.radio);
      // eslint-disable-next-line react-hooks/exhaustive-deps -- solo queremos que se ejecute al volver
    }, []),
  );
  const ultimaPeticion = useRef(0);

  // Al abrir la pantalla: pedimos permiso de ubicación y, si nos lo dan, centramos el mapa en ti.
  useEffect(() => {
    let cancelado = false; // por si sales de la pantalla antes de que llegue la ubicación
    obtenerUbicacion().then((posicion) => {
      if (cancelado || !posicion) return;
      setMiUbicacion(posicion);
      mapa.current?.mostrarMiUbicacion(posicion.latitud, posicion.longitud);
      mapa.current?.centrar(posicion.latitud, posicion.longitud, 14);
    });
    return () => {
      cancelado = true;
      if (temporizador.current) clearTimeout(temporizador.current);
    };
  }, []);

  function manejarError(e: unknown) {
    if (e instanceof ErrorApi && e.estado === 401) {
      cerrarSesion(); // token caducado: vuelta al login
    } else {
      setAviso(e instanceof Error ? e.message : 'Algo ha fallado. Inténtalo de nuevo.');
    }
  }

  /**
   * El mapa avisa cada vez que se para. No pedimos canchas en cada pequeño movimiento:
   * esperamos ESPERA_MS y, si se ha vuelto a mover, cancelamos la anterior ("debounce").
   */
  function cargarZona(latitud: number, longitud: number, radio: number) {
    zona.current = { latitud, longitud, radio };
    if (temporizador.current) clearTimeout(temporizador.current);
    temporizador.current = setTimeout(async () => {
      if (!token) return;
      // Si llegan dos respuestas desordenadas, nos quedamos solo con la de la última petición.
      const numero = ++ultimaPeticion.current;
      try {
        const lista = await canchasCercanas(token, latitud, longitud, Math.min(radio, RADIO_MAXIMO));
        if (numero !== ultimaPeticion.current) return;
        setCanchas(lista);
        setAviso(null);
        mapa.current?.mostrarCanchas(lista);
      } catch (e) {
        manejarError(e);
      }
    }, ESPERA_MS);
  }

  async function buscarCiudad() {
    const texto = busqueda.trim();
    if (!texto || !token) return;
    Keyboard.dismiss();
    setBuscando(true);
    setAviso(null);
    try {
      const lista = await canchasDeCiudad(token, texto);
      if (lista.length === 0) {
        setAviso(`Todavía no hay canchas en "${texto}"`);
      } else {
        // Al moverse, el mapa avisa (onMovido) y se cargan las canchas de esa zona.
        mapa.current?.encuadrar(lista);
      }
    } catch (e) {
      manejarError(e);
    } finally {
      setBuscando(false);
    }
  }

  async function irAMiUbicacion() {
    const posicion = miUbicacion ?? (await obtenerUbicacion());
    if (!posicion) {
      setAviso('Activa la ubicación de Pachanga en los ajustes del móvil para ver las canchas cerca de ti.');
      return;
    }
    setMiUbicacion(posicion);
    mapa.current?.mostrarMiUbicacion(posicion.latitud, posicion.longitud);
    mapa.current?.centrar(posicion.latitud, posicion.longitud, 15);
  }

  /** Abre la pantalla de crear cancha, empezando donde está ahora el centro del mapa. */
  function anadirCancha() {
    const centro = zona.current ?? SEVILLA;
    router.push({
      pathname: '/cancha/nueva',
      params: { lat: String(centro.latitud), lng: String(centro.longitud) },
    });
  }

  function elegirCancha(id: number | null) {
    setElegidaId(id);
    mapa.current?.elegir(id);
  }

  const elegida = canchas.find((c) => c.id === elegidaId);
  const fondoTarjeta = { backgroundColor: tema.background, borderColor: tema.borde };

  return (
    <View style={styles.pantalla}>
      <Mapa
        ref={mapa}
        latitudInicial={SEVILLA.latitud}
        longitudInicial={SEVILLA.longitud}
        zoomInicial={12}
        onMovido={cargarZona}
        onCanchaPulsada={elegirCancha}
        onError={setAviso}
      />

      {/* Buscador, flotando encima del mapa */}
      <View style={[styles.arriba, { top: margenes.top + Spacing.two }]} pointerEvents="box-none">
        <View style={[styles.buscador, fondoTarjeta]}>
          <TextInput
            value={busqueda}
            onChangeText={setBusqueda}
            onSubmitEditing={buscarCiudad}
            placeholder="Busca una ciudad"
            placeholderTextColor={tema.textSecondary}
            returnKeyType="search"
            autoCorrect={false}
            style={[styles.cajaBusqueda, { color: tema.text }]}
          />
          {buscando ? (
            <ActivityIndicator color={tema.primario} />
          ) : (
            <Pressable onPress={buscarCiudad} hitSlop={8} accessibilityLabel="Buscar">
              <ThemedText style={{ color: tema.primario }}>Buscar</ThemedText>
            </Pressable>
          )}
        </View>

        {aviso ? (
          <View style={[styles.etiqueta, fondoTarjeta]}>
            <ThemedText type="small" style={{ color: tema.error }}>
              {aviso}
            </ThemedText>
          </View>
        ) : (
          <View style={[styles.etiqueta, fondoTarjeta]}>
            <ThemedText type="small" themeColor="textSecondary">
              {canchas.length === 1 ? '1 cancha en esta zona' : `${canchas.length} canchas en esta zona`}
            </ThemedText>
          </View>
        )}
      </View>

      {/* Parte de abajo: botón de "mi ubicación" y la cancha elegida */}
      <View
        style={[styles.abajo, { bottom: margenes.bottom + BottomTabInset + Spacing.two }]}
        pointerEvents="box-none">
        <View style={styles.filaBotones} pointerEvents="box-none">
          {/* Crear una cancha nueva: la pantalla empieza en el centro actual del mapa */}
          <Pressable
            onPress={anadirCancha}
            accessibilityLabel="Añadir una cancha"
            style={({ pressed }) => [
              styles.botonAnadir,
              { backgroundColor: tema.primario },
              pressed && styles.pulsado,
            ]}>
            <ThemedText style={styles.textoBlanco}>+ Añadir cancha</ThemedText>
          </Pressable>
          <Pressable
            onPress={irAMiUbicacion}
            accessibilityLabel="Ir a mi ubicación"
            style={({ pressed }) => [styles.botonUbicacion, fondoTarjeta, pressed && styles.pulsado]}>
            <ThemedText>📍</ThemedText>
          </Pressable>
        </View>

        {elegida ? (
          <ThemedView style={[styles.tarjeta, fondoTarjeta]}>
            <Pressable
              onPress={() => elegirCancha(null)}
              hitSlop={12}
              accessibilityLabel="Cerrar"
              style={styles.cerrar}>
              <ThemedText themeColor="textSecondary">✕</ThemedText>
            </Pressable>
            <ThemedText type="subtitle" style={styles.nombre} numberOfLines={2}>
              {elegida.nombre}
            </ThemedText>
            <ThemedText themeColor="textSecondary">
              {elegida.direccion ? `${elegida.direccion}, ${elegida.ciudad}` : elegida.ciudad}
            </ThemedText>
            <View style={styles.acciones}>
              <Pressable
                onPress={() => router.push(`/cancha/${elegida.id}`)}
                style={({ pressed }) => [
                  styles.botonTarjeta,
                  { backgroundColor: tema.primario },
                  pressed && styles.pulsado,
                ]}>
                <ThemedText style={styles.textoBlanco}>Ver ficha</ThemedText>
              </Pressable>
              <Pressable
                onPress={() => abrirIndicaciones(elegida.latitud, elegida.longitud)}
                style={({ pressed }) => [
                  styles.botonTarjeta,
                  { borderWidth: 1, borderColor: tema.borde },
                  pressed && styles.pulsado,
                ]}>
                <ThemedText>Cómo llegar</ThemedText>
              </Pressable>
            </View>
          </ThemedView>
        ) : null}
      </View>
    </View>
  );
}

/** Pide permiso y devuelve tu posición, o null si no hay permiso o no se puede obtener. */
async function obtenerUbicacion(): Promise<{ latitud: number; longitud: number } | null> {
  try {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') return null;
    // La última posición conocida es instantánea; si no hay, pedimos una nueva (tarda un poco más).
    const posicion =
      (await Location.getLastKnownPositionAsync()) ??
      (await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }));
    return { latitud: posicion.coords.latitude, longitud: posicion.coords.longitude };
  } catch {
    return null;
  }
}

const styles = StyleSheet.create({
  pantalla: {
    flex: 1,
  },
  arriba: {
    position: 'absolute',
    left: Spacing.three,
    right: Spacing.three,
    gap: Spacing.two,
    alignItems: 'center',
  },
  buscador: {
    alignSelf: 'stretch',
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    borderRadius: Spacing.four,
    borderWidth: 1,
    ...sombra(),
  },
  cajaBusqueda: {
    flex: 1,
    paddingVertical: Spacing.three,
    fontSize: 16,
  },
  etiqueta: {
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.three,
    borderRadius: Spacing.three,
    borderWidth: 1,
    ...sombra(),
  },
  abajo: {
    position: 'absolute',
    left: Spacing.three,
    right: Spacing.three,
    gap: Spacing.two,
  },
  filaBotones: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  botonAnadir: {
    height: 48,
    paddingHorizontal: Spacing.three,
    borderRadius: 24,
    justifyContent: 'center',
    ...sombra(),
  },
  cerrar: {
    position: 'absolute',
    top: Spacing.two,
    right: Spacing.three,
    zIndex: 1,
  },
  botonUbicacion: {
    width: 48,
    height: 48,
    borderRadius: 24,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    ...sombra(),
  },
  tarjeta: {
    padding: Spacing.three,
    borderRadius: Spacing.four,
    borderWidth: 1,
    gap: Spacing.one,
    ...sombra(),
  },
  nombre: {
    fontSize: 22,
    lineHeight: 28,
  },
  acciones: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
  botonTarjeta: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: Spacing.two,
    borderRadius: Spacing.three,
  },
  textoBlanco: {
    color: '#ffffff',
    fontWeight: '600',
  },
  pulsado: {
    opacity: 0.7,
  },
});

/** Sombra suave para que lo que flota se distinga del mapa. */
function sombra() {
  return {
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 4, // Android
  };
}
