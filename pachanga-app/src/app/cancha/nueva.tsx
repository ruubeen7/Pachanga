import { Image } from 'expo-image';
import * as Location from 'expo-location';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  canchasCercanas,
  crearCancha,
  NOMBRES_SUPERFICIE,
  posiblesDuplicados,
  subirFoto,
  type CanchaResumen,
  type Superficie,
} from '@/api/canchas';
import { ErrorApi } from '@/api/cliente';
import { buscarDirecciones, MINIMO_LETRAS, type DireccionSugerencia } from '@/api/direcciones';
import { Boton } from '@/components/boton';
import { CampoTexto } from '@/components/campo-texto';
import { Chip } from '@/components/chip';
import { Mapa, type ControlMapa } from '@/components/mapa/mapa';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';
import { elegirFotos } from '@/utils/fotos';

const SEVILLA = { latitud: 37.3891, longitud: -5.9845 };
const MAX_FOTOS_AL_CREAR = 5;
const SUPERFICIES = Object.keys(NOMBRES_SUPERFICIE) as Superficie[];

type Punto = { latitud: number; longitud: number };
/** Dirección y ciudad de un punto, para rellenar el formulario. */
type DatosDireccion = { direccion: string; ciudad: string; nombre?: string };
/** Dónde está el usuario ahora mismo, con su dirección para enseñarla. */
type UbicacionActual = Punto & DatosDireccion;

/**
 * Los tres pasos para dar de alta una cancha:
 *  1. "ubicacion": como al pedir comida a domicilio. Arriba sale tu ubicación actual y un buscador
 *     para escribir la calle o el sitio. Si prefieres, también puedes mover el mapa hasta que
 *     el 📍 del centro quede encima de la cancha.
 *  2. "duplicados": si ya hay canchas a menos de 50 m, preguntamos "¿es alguna de estas?".
 *  3. "datos": nombre, ciudad, características y fotos.
 * El mapa se queda siempre montado debajo; los pasos 2 y 3 se dibujan encima.
 * Así, al volver a "Cambiar ubicación", el mapa sigue donde estaba.
 */
type Paso = 'ubicacion' | 'duplicados' | 'datos';

export default function NuevaCanchaScreen() {
  // El mapa principal nos pasa su centro al abrir esta pantalla (/cancha/nueva?lat=..&lng=..).
  const parametros = useLocalSearchParams<{ lat?: string; lng?: string }>();
  const inicial = {
    latitud: Number(parametros.lat ?? SEVILLA.latitud),
    longitud: Number(parametros.lng ?? SEVILLA.longitud),
  };

  const { token, cerrarSesion } = useSesion();
  const tema = useTheme();
  const margenes = useSafeAreaInsets();

  const mapa = useRef<ControlMapa>(null);
  const centro = useRef(inicial); // el centro del mapa (donde está el 📍), actualizado al moverlo

  const [paso, setPaso] = useState<Paso>('ubicacion');
  const [punto, setPunto] = useState(inicial); // la ubicación elegida
  const [duplicados, setDuplicados] = useState<CanchaResumen[]>([]);
  const [comprobando, setComprobando] = useState(false);

  // Buscador de direcciones y ubicación actual
  const [texto, setTexto] = useState('');
  const [sugerencias, setSugerencias] = useState<DireccionSugerencia[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [errorBusqueda, setErrorBusqueda] = useState<string | null>(null);
  const [actual, setActual] = useState<UbicacionActual | null>(null);
  const [estadoActual, setEstadoActual] = useState<'buscando' | 'listo' | 'sin-permiso' | 'error'>('buscando');

  // Formulario
  const [nombre, setNombre] = useState('');
  const [ciudad, setCiudad] = useState('');
  const [direccion, setDireccion] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [superficie, setSuperficie] = useState<Superficie | null>(null);
  const [numCanastas, setNumCanastas] = useState<number | null>(null);
  const [cubierta, setCubierta] = useState(false);
  const [iluminacion, setIluminacion] = useState(false);
  const [gratuita, setGratuita] = useState(true);
  const [fotos, setFotos] = useState<string[]>([]);

  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});

  function manejarError(e: unknown) {
    if (e instanceof ErrorApi && e.estado === 401) {
      cerrarSesion();
    } else if (e instanceof ErrorApi) {
      setErrores(e.campos);
      // Los errores de ubicación no tienen campo en el formulario: los enseñamos abajo.
      const sinCampo = Object.keys(e.campos).length === 0 || e.campos.latitud || e.campos.longitud;
      setError(sinCampo ? (e.campos.latitud ?? e.campos.longitud ?? e.message) : null);
    } else {
      setError('Algo ha fallado. Inténtalo de nuevo.');
    }
  }

  /** Al mover el mapa: guardamos el centro y pintamos las canchas que ya hay por la zona. */
  async function alMoverMapa(latitud: number, longitud: number, radio: number) {
    centro.current = { latitud, longitud };
    if (!token) return;
    try {
      const cercanas = await canchasCercanas(token, latitud, longitud, Math.min(radio, 50_000));
      mapa.current?.mostrarCanchas(cercanas);
    } catch {
      // No es importante: solo sirve de ayuda visual.
    }
  }

  // Al abrir la pantalla buscamos dónde está el usuario y llevamos el mapa allí.
  useEffect(() => {
    let cancelado = false;

    async function buscarDondeEstoy() {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== 'granted') {
          if (!cancelado) setEstadoActual('sin-permiso');
          return;
        }
        // Si el móvil sabe dónde estábamos hace menos de 2 minutos, eso vale y es instantáneo.
        const posicion =
          (await Location.getLastKnownPositionAsync({ maxAge: 2 * 60 * 1000 })) ??
          (await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }));
        if (cancelado) return;

        const aqui = { latitud: posicion.coords.latitude, longitud: posicion.coords.longitude };
        mapa.current?.centrar(aqui.latitud, aqui.longitud, 17);
        mapa.current?.mostrarMiUbicacion(aqui.latitud, aqui.longitud);

        const datos = await direccionDe(aqui);
        if (cancelado) return;
        setActual({ ...aqui, direccion: datos?.direccion ?? '', ciudad: datos?.ciudad ?? '' });
        setEstadoActual('listo');
      } catch {
        if (!cancelado) setEstadoActual('error');
      }
    }

    buscarDondeEstoy();
    // Si el usuario sale de la pantalla antes de terminar, no tocamos nada.
    return () => {
      cancelado = true;
    };
  }, []);

  // Cada vez que cambia el texto del buscador, esperamos 400 ms (por si sigue escribiendo) y buscamos.
  useEffect(() => {
    const limpio = texto.trim();
    if (!token || limpio.length < MINIMO_LETRAS) return;

    let cancelado = false;
    const temporizador = setTimeout(async () => {
      setBuscando(true);
      try {
        // Le pasamos dónde estamos para que salgan primero los sitios cercanos.
        const encontradas = await buscarDirecciones(token, limpio, actual ?? centro.current);
        if (cancelado) return;
        setSugerencias(encontradas);
        setErrorBusqueda(null);
      } catch (e) {
        if (cancelado) return;
        if (e instanceof ErrorApi && e.estado === 401) {
          cerrarSesion();
          return;
        }
        setSugerencias([]);
        setErrorBusqueda(e instanceof ErrorApi ? e.message : 'No se ha podido buscar. Inténtalo de nuevo.');
      } finally {
        if (!cancelado) setBuscando(false);
      }
    }, 400);

    // Si el texto cambia antes de los 400 ms, se cancela esta búsqueda y empieza otra.
    return () => {
      cancelado = true;
      clearTimeout(temporizador);
    };
  }, [texto, token, actual, cerrarSesion]);

  function cambiarTexto(nuevo: string) {
    setTexto(nuevo);
    if (nuevo.trim().length < MINIMO_LETRAS) {
      setSugerencias([]);
      setBuscando(false);
      setErrorBusqueda(null);
    }
  }

  /** Han tocado una sugerencia del buscador. */
  function elegirSugerencia(s: DireccionSugerencia) {
    // Si es un parque o una pista, el servidor nos propone su nombre para la cancha.
    elegirPunto(
      { latitud: s.latitud, longitud: s.longitud },
      { direccion: s.direccion ?? '', ciudad: s.ciudad ?? '', nombre: s.nombreSugerido ?? undefined },
    );
  }

  /** Botón de abajo: el punto donde está el 📍 del mapa. */
  function usarEstaUbicacion() {
    elegirPunto(centro.current);
  }

  /**
   * Paso 1 → 2 o 3: guardamos el punto y comprobamos si ya hay canchas a menos de 50 m.
   * Si ya sabemos la dirección (viene del buscador), la usamos; si no, la buscamos por las coordenadas.
   */
  async function elegirPunto(elegido: Punto, datos?: DatosDireccion) {
    if (!token || comprobando) return;
    Keyboard.dismiss();
    setPunto(elegido);
    // Movemos el mapa al punto, por si luego vuelve con "Cambiar ubicación" para ajustarlo.
    mapa.current?.centrar(elegido.latitud, elegido.longitud, 18);
    setComprobando(true);
    setError(null);

    if (datos) {
      setDireccion(datos.direccion);
      setCiudad(datos.ciudad);
      if (datos.nombre && !nombre) setNombre(datos.nombre);
    } else {
      rellenarDireccion(elegido.latitud, elegido.longitud); // en paralelo; no esperamos por ella
    }

    try {
      const cerca = await posiblesDuplicados(token, elegido.latitud, elegido.longitud);
      setDuplicados(cerca);
      setPaso(cerca.length > 0 ? 'duplicados' : 'datos');
    } catch (e) {
      manejarError(e);
    } finally {
      setComprobando(false);
    }
  }

  /** Rellena ciudad y dirección del formulario a partir de las coordenadas (ver direccionDe). */
  async function rellenarDireccion(latitud: number, longitud: number) {
    const datos = await direccionDe({ latitud, longitud });
    if (!datos) return; // sin dirección automática: el usuario la escribe
    setCiudad(datos.ciudad);
    setDireccion(datos.direccion);
  }

  async function anadirFotos() {
    const nuevas = await elegirFotos(MAX_FOTOS_AL_CREAR - fotos.length);
    setFotos([...fotos, ...nuevas]);
  }

  async function crear() {
    if (!token) return;
    setEnviando(true);
    setError(null);
    setErrores({});
    try {
      const creada = await crearCancha(token, {
        nombre,
        ciudad,
        latitud: punto.latitud,
        longitud: punto.longitud,
        direccion,
        descripcion,
        superficie,
        numCanastas,
        cubierta,
        iluminacion,
        gratuita,
      });

      // La cancha ya existe; ahora subimos las fotos una a una.
      let fallidas = 0;
      for (const foto of fotos) {
        try {
          await subirFoto(token, creada.id, foto);
        } catch {
          fallidas++;
        }
      }
      if (fallidas > 0) {
        Alert.alert(
          'Cancha creada',
          'Algunas fotos no se pudieron subir. Puedes añadirlas desde la ficha de la cancha.',
        );
      }

      // replace: cambiamos esta pantalla por la ficha, así "atrás" vuelve al mapa y no al formulario.
      router.replace(`/cancha/${creada.id}`);
    } catch (e) {
      manejarError(e);
      setEnviando(false);
    }
  }

  return (
    <View style={styles.fondo}>
      <Mapa
        ref={mapa}
        latitudInicial={inicial.latitud}
        longitudInicial={inicial.longitud}
        zoomInicial={17}
        onMovido={alMoverMapa}
        onCanchaPulsada={() => {}}
        onError={setError}
      />

      {/* El 📍 fijo en el centro: lo que se mueve es el mapa. pointerEvents="none" deja tocar el mapa a través. */}
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <View style={styles.centroMapa}>
          <ThemedText style={styles.chincheta}>📍</ThemedText>
        </View>
      </View>

      {/* Abajo va primero para que el buscador de arriba (con sus sugerencias) quede por encima. */}
      <View style={[styles.abajo, { bottom: margenes.bottom + Spacing.three }]}>
        {error && paso === 'ubicacion' ? (
          <ThemedText style={[styles.errorFlotante, { color: tema.error, backgroundColor: tema.background }]}>
            {error}
          </ThemedText>
        ) : null}
        <Boton texto="Usar el punto del mapa" onPress={usarEstaUbicacion} cargando={comprobando} />
      </View>

      {/* Buscador, como al pedir comida a domicilio */}
      <View style={[styles.buscador, { backgroundColor: tema.background, borderColor: tema.borde }]}>
        <View style={[styles.cajaBusqueda, { backgroundColor: tema.backgroundElement, borderColor: tema.borde }]}>
          <ThemedText>🔍</ThemedText>
          <TextInput
            value={texto}
            onChangeText={cambiarTexto}
            placeholder="Escribe la calle o el sitio de la cancha"
            placeholderTextColor={tema.textSecondary}
            style={[styles.entrada, { color: tema.text }]}
            autoCorrect={false}
            returnKeyType="search"
          />
          {texto ? (
            <Pressable onPress={() => cambiarTexto('')} hitSlop={8} accessibilityLabel="Borrar búsqueda">
              <ThemedText themeColor="textSecondary">✕</ThemedText>
            </Pressable>
          ) : null}
        </View>

        {texto.trim().length < MINIMO_LETRAS ? (
          // Sin texto: ofrecemos la ubicación actual
          <Pressable
            onPress={() => actual && elegirPunto(actual, actual)}
            disabled={!actual || comprobando}
            style={({ pressed }) => [styles.opcion, pressed && styles.pulsado]}>
            <ThemedText style={styles.negrita}>📍 Usar mi ubicación actual</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {estadoActual === 'buscando'
                ? 'Buscando dónde estás…'
                : estadoActual === 'sin-permiso'
                  ? 'No tenemos permiso para ver tu ubicación. Busca la calle o mueve el mapa.'
                  : estadoActual === 'error'
                    ? 'No hemos podido saber dónde estás. Busca la calle o mueve el mapa.'
                    : [actual?.direccion, actual?.ciudad].filter(Boolean).join(', ') || 'Estás aquí'}
            </ThemedText>
          </Pressable>
        ) : (
          <ScrollView style={styles.listaSugerencias} keyboardShouldPersistTaps="handled">
            {buscando && sugerencias.length === 0 ? <ActivityIndicator style={styles.opcion} /> : null}
            {sugerencias.map((s) => (
              <Pressable
                key={`${s.latitud},${s.longitud},${s.titulo}`}
                onPress={() => elegirSugerencia(s)}
                disabled={comprobando}
                style={({ pressed }) => [styles.opcion, { borderTopColor: tema.borde }, styles.separador, pressed && styles.pulsado]}>
                <ThemedText style={styles.negrita}>{s.titulo}</ThemedText>
                {s.detalle ? (
                  <ThemedText type="small" themeColor="textSecondary">
                    {s.detalle}
                  </ThemedText>
                ) : null}
              </Pressable>
            ))}
            {!buscando && errorBusqueda ? (
              <ThemedText type="small" style={[styles.opcion, { color: tema.error }]}>
                {errorBusqueda}
              </ThemedText>
            ) : null}
            {!buscando && !errorBusqueda && sugerencias.length === 0 ? (
              <ThemedText type="small" themeColor="textSecondary" style={styles.opcion}>
                No encontramos nada con ese nombre. Prueba con la calle, o mueve el mapa y pon el 📍 encima.
              </ThemedText>
            ) : null}
          </ScrollView>
        )}

        <ThemedText type="small" themeColor="textSecondary">
          También puedes mover el mapa hasta que el 📍 quede encima de la cancha.
        </ThemedText>
      </View>

      {paso === 'duplicados' && (
        <ThemedView style={[StyleSheet.absoluteFill, styles.capa]}>
          <ScrollView contentContainerStyle={[styles.contenido, { paddingBottom: margenes.bottom + Spacing.four }]}>
            <ThemedText type="subtitle" style={styles.titulo}>
              ¿Es alguna de estas?
            </ThemedText>
            <ThemedText themeColor="textSecondary">
              Ya hay canchas a menos de 50 m del punto que has marcado. Si es una de ellas, no hace falta
              crearla otra vez: puedes añadirle fotos desde su ficha.
            </ThemedText>

            {duplicados.map((c) => (
              <Pressable
                key={c.id}
                onPress={() => router.replace(`/cancha/${c.id}`)}
                style={({ pressed }) => [
                  styles.duplicado,
                  { backgroundColor: tema.backgroundElement, borderColor: tema.borde },
                  pressed && styles.pulsado,
                ]}>
                <ThemedText style={styles.negrita}>{c.nombre}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {c.direccion ? `${c.direccion}, ${c.ciudad}` : c.ciudad}
                  {c.distanciaMetros != null ? ` · a ${c.distanciaMetros} m` : ''}
                </ThemedText>
                <ThemedText type="small" style={{ color: tema.primario }}>
                  Sí, es esta →
                </ThemedText>
              </Pressable>
            ))}

            <Boton texto="No, es otra cancha" onPress={() => setPaso('datos')} />
            <Boton texto="Cambiar ubicación" variante="secundario" onPress={() => setPaso('ubicacion')} />
          </ScrollView>
        </ThemedView>
      )}

      {paso === 'datos' && (
        <ThemedView style={[StyleSheet.absoluteFill, styles.capa]}>
          <KeyboardAvoidingView style={styles.fondo} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
            <ScrollView
              contentContainerStyle={[styles.contenido, { paddingBottom: margenes.bottom + Spacing.four }]}
              keyboardShouldPersistTaps="handled">
              <Pressable onPress={() => setPaso('ubicacion')} hitSlop={8}>
                <ThemedText type="small" style={{ color: tema.primario }}>
                  ← Cambiar ubicación
                </ThemedText>
              </Pressable>

              <CampoTexto
                etiqueta="Nombre"
                value={nombre}
                onChangeText={setNombre}
                error={errores.nombre}
                placeholder="Ej.: Pista del parque de la Buhaira"
                maxLength={100}
              />
              <CampoTexto
                etiqueta="Ciudad"
                value={ciudad}
                onChangeText={setCiudad}
                error={errores.ciudad}
                placeholder="Ej.: Sevilla"
                maxLength={100}
              />
              <CampoTexto
                etiqueta="Dirección (opcional)"
                value={direccion}
                onChangeText={setDireccion}
                error={errores.direccion}
                placeholder="Calle y número, o una referencia"
                maxLength={255}
              />

              <View style={styles.grupo}>
                <ThemedText type="smallBold">Superficie</ThemedText>
                <View style={styles.chips}>
                  {SUPERFICIES.map((s) => (
                    <Chip
                      key={s}
                      texto={NOMBRES_SUPERFICIE[s]}
                      activo={superficie === s}
                      // Pulsar la elegida otra vez la quita (es opcional)
                      onPress={() => setSuperficie(superficie === s ? null : s)}
                    />
                  ))}
                </View>
              </View>

              <View style={styles.grupo}>
                <ThemedText type="smallBold">Canastas</ThemedText>
                <View style={styles.contador}>
                  <Chip
                    texto="−"
                    onPress={() => setNumCanastas(numCanastas && numCanastas > 1 ? numCanastas - 1 : null)}
                  />
                  <ThemedText style={styles.numero}>{numCanastas ?? 'Sin indicar'}</ThemedText>
                  <Chip texto="+" onPress={() => setNumCanastas(Math.min((numCanastas ?? 0) + 1, 20))} />
                </View>
                {errores.numCanastas ? (
                  <ThemedText type="small" style={{ color: tema.error }}>
                    {errores.numCanastas}
                  </ThemedText>
                ) : null}
              </View>

              <View style={styles.grupo}>
                <Interruptor texto="Cubierta" valor={cubierta} onCambio={setCubierta} />
                <Interruptor texto="Tiene luz por la noche" valor={iluminacion} onCambio={setIluminacion} />
                <Interruptor texto="Gratis" valor={gratuita} onCambio={setGratuita} />
              </View>

              <CampoTexto
                etiqueta="Descripción (opcional)"
                value={descripcion}
                onChangeText={setDescripcion}
                error={errores.descripcion}
                placeholder="Estado de las canastas, horario, si suele haber gente…"
                multiline
                maxLength={1000}
                style={styles.multilinea}
              />

              <View style={styles.grupo}>
                <ThemedText type="smallBold">
                  Fotos ({fotos.length}/{MAX_FOTOS_AL_CREAR})
                </ThemedText>
                <ScrollView horizontal contentContainerStyle={styles.miniaturas}>
                  {fotos.map((uri) => (
                    <Pressable
                      key={uri}
                      onPress={() => setFotos(fotos.filter((f) => f !== uri))}
                      accessibilityLabel="Quitar foto">
                      <Image source={{ uri }} style={styles.miniatura} contentFit="cover" />
                      <View style={styles.quitar}>
                        <ThemedText type="small" style={styles.textoBlanco}>
                          ✕
                        </ThemedText>
                      </View>
                    </Pressable>
                  ))}
                </ScrollView>
                {fotos.length < MAX_FOTOS_AL_CREAR && (
                  <Boton texto="Añadir fotos" variante="secundario" onPress={anadirFotos} />
                )}
              </View>

              {error ? <ThemedText style={{ color: tema.error }}>{error}</ThemedText> : null}

              <Boton texto="Crear cancha" onPress={crear} cargando={enviando} />
            </ScrollView>
          </KeyboardAvoidingView>
        </ThemedView>
      )}
    </View>
  );
}

/**
 * Dirección y ciudad de unas coordenadas ("geocodificación inversa").
 * Lo hace el propio sistema del móvil (Apple o Google), gratis. Si falla, devuelve null.
 */
async function direccionDe(punto: Punto): Promise<DatosDireccion | null> {
  try {
    const [lugar] = await Location.reverseGeocodeAsync({ latitude: punto.latitud, longitude: punto.longitud });
    if (!lugar) return null;
    return {
      ciudad: lugar.city ?? lugar.subregion ?? '',
      direccion: [lugar.street, lugar.streetNumber].filter(Boolean).join(', '),
    };
  } catch {
    return null;
  }
}

function Interruptor({
  texto,
  valor,
  onCambio,
}: {
  texto: string;
  valor: boolean;
  onCambio: (valor: boolean) => void;
}) {
  const tema = useTheme();
  return (
    <View style={styles.interruptor}>
      <ThemedText>{texto}</ThemedText>
      <Switch value={valor} onValueChange={onCambio} trackColor={{ true: tema.primario }} />
    </View>
  );
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
  },
  centroMapa: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chincheta: {
    fontSize: 40,
    lineHeight: 48,
    marginBottom: 44, // sube el emoji para que su punta (no su centro) marque el punto
  },
  buscador: {
    position: 'absolute',
    top: Spacing.three,
    left: Spacing.three,
    right: Spacing.three,
    padding: Spacing.three,
    borderRadius: Spacing.three,
    borderWidth: 1,
    gap: Spacing.two,
  },
  cajaBusqueda: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
  },
  entrada: {
    flex: 1,
    paddingVertical: Spacing.two + Spacing.one,
    fontSize: 16,
  },
  listaSugerencias: {
    maxHeight: 280,
  },
  opcion: {
    paddingVertical: Spacing.two,
    gap: Spacing.half,
  },
  separador: {
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  abajo: {
    position: 'absolute',
    left: Spacing.four,
    right: Spacing.four,
    gap: Spacing.two,
  },
  errorFlotante: {
    padding: Spacing.two,
    borderRadius: Spacing.two,
    textAlign: 'center',
  },
  capa: {
    flex: 1,
  },
  contenido: {
    padding: Spacing.four,
    gap: Spacing.three,
  },
  titulo: {
    fontSize: 26,
    lineHeight: 32,
  },
  duplicado: {
    padding: Spacing.three,
    borderRadius: Spacing.three,
    borderWidth: 1,
    gap: Spacing.half,
  },
  negrita: {
    fontWeight: '600',
  },
  pulsado: {
    opacity: 0.7,
  },
  grupo: {
    gap: Spacing.two,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  contador: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  numero: {
    minWidth: 90,
    textAlign: 'center',
  },
  interruptor: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  multilinea: {
    minHeight: 90,
    textAlignVertical: 'top',
  },
  miniaturas: {
    gap: Spacing.two,
  },
  miniatura: {
    width: 80,
    height: 80,
    borderRadius: Spacing.two,
  },
  quitar: {
    position: 'absolute',
    top: 4,
    right: 4,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: 'rgba(0,0,0,0.6)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  textoBlanco: {
    color: '#ffffff',
    lineHeight: 18,
  },
});
