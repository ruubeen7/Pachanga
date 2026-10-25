import { Image } from 'expo-image';
import * as Location from 'expo-location';
import { router, useLocalSearchParams } from 'expo-router';
import { useRef, useState } from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
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

/**
 * Los tres pasos para dar de alta una cancha:
 *  1. "ubicacion": mueves el mapa hasta que el 📍 del centro quede encima de la cancha.
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

  /** Paso 1 → 2 o 3: comprobamos si ya hay canchas a menos de 50 m. */
  async function usarEstaUbicacion() {
    if (!token) return;
    const elegido = centro.current;
    setPunto(elegido);
    setComprobando(true);
    setError(null);
    rellenarDireccion(elegido.latitud, elegido.longitud); // en paralelo; no esperamos por ella
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

  /**
   * Rellena ciudad y dirección a partir de las coordenadas ("geocodificación inversa").
   * Lo hace el propio sistema del móvil (Apple o Google), gratis. Si falla, el usuario las escribe.
   */
  async function rellenarDireccion(latitud: number, longitud: number) {
    try {
      const [lugar] = await Location.reverseGeocodeAsync({ latitude: latitud, longitude: longitud });
      if (!lugar) return;
      setCiudad(lugar.city ?? lugar.subregion ?? '');
      setDireccion([lugar.street, lugar.streetNumber].filter(Boolean).join(', '));
    } catch {
      // Sin dirección automática: no pasa nada.
    }
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

      <View style={[styles.instrucciones, { backgroundColor: tema.background, borderColor: tema.borde }]}>
        <ThemedText type="small">
          Mueve el mapa hasta que el 📍 quede encima de la cancha. Acerca bien el mapa para ser preciso.
        </ThemedText>
      </View>

      <View style={[styles.abajo, { bottom: margenes.bottom + Spacing.three }]}>
        {error && paso === 'ubicacion' ? (
          <ThemedText style={[styles.errorFlotante, { color: tema.error, backgroundColor: tema.background }]}>
            {error}
          </ThemedText>
        ) : null}
        <Boton texto="Usar esta ubicación" onPress={usarEstaUbicacion} cargando={comprobando} />
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
  instrucciones: {
    position: 'absolute',
    top: Spacing.three,
    left: Spacing.three,
    right: Spacing.three,
    padding: Spacing.three,
    borderRadius: Spacing.three,
    borderWidth: 1,
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
