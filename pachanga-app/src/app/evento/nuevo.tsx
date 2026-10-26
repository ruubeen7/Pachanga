import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorApi } from '@/api/cliente';
import {
  crearEvento,
  NOMBRES_NIVEL,
  NOMBRES_TIPO,
  type NivelEvento,
  type TipoEvento,
} from '@/api/eventos';
import { Boton } from '@/components/boton';
import { CampoTexto } from '@/components/campo-texto';
import { Chip } from '@/components/chip';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';
import { dia, duracion, hora, hoyAMedianoche, minutosAHora, sumarDias } from '@/utils/fechas';

const TIPOS = Object.keys(NOMBRES_TIPO) as TipoEvento[];
const NIVELES = Object.keys(NOMBRES_NIVEL) as NivelEvento[];

/** Cuántos días se ofrecen para elegir (la API deja hasta 90, pero 30 es más cómodo de recorrer). */
const DIAS_A_ELEGIR = 30;
/** Las horas van de media en media hora, de 07:00 a 23:30 (en minutos desde medianoche). */
const PASO_HORA = 30;
const HORA_MINIMA = 7 * 60;
const HORA_MAXIMA = 23 * 60 + 30;
/** Duraciones típicas, en minutos. La API admite hasta 8 horas. */
const DURACIONES = [60, 90, 120, 180];

/**
 * Organizar una pachanga o un entreno en una cancha.
 * Se abre desde la ficha de la cancha: /evento/nuevo?canchaId=5&canchaNombre=Pista...
 *
 * Para la fecha y la hora no usamos un selector nativo: el día se elige con chips
 * (Hoy, Mañana, sáb 1...) y la hora con − / +. Es más rápido con el dedo y no necesita
 * instalar nada más.
 */
export default function NuevoEventoScreen() {
  const { canchaId, canchaNombre } = useLocalSearchParams<{ canchaId: string; canchaNombre?: string }>();
  const { token, cerrarSesion } = useSesion();
  const tema = useTheme();
  const margenes = useSafeAreaInsets();

  // React no deja leer la hora mientras dibuja (cada vez daría un valor distinto), así que
  // la guardamos una vez al abrir la pantalla. useState(() => ...) solo ejecuta la función esa primera vez.
  const [abiertaEn] = useState(() => Date.now());
  // Los días que se pueden elegir: hoy, mañana... (se calculan una vez al abrir la pantalla).
  const [dias] = useState(() => Array.from({ length: DIAS_A_ELEGIR }, (_, i) => sumarDias(hoyAMedianoche(), i)));

  const [tipo, setTipo] = useState<TipoEvento>('PACHANGA');
  const [titulo, setTitulo] = useState('');
  // Por defecto a las 18:00; si ya es tarde, mañana.
  const [diaElegido, setDiaElegido] = useState(() => (new Date().getHours() >= 17 ? 1 : 0));
  const [inicioMinutos, setInicioMinutos] = useState(18 * 60);
  const [duracionMinutos, setDuracionMinutos] = useState(90);
  const [plazas, setPlazas] = useState(10);
  const [nivel, setNivel] = useState<NivelEvento>('TODOS');
  const [descripcion, setDescripcion] = useState('');

  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});

  // new Date(año, mes, día, horas, minutos) usa la hora del móvil. Si los minutos pasan de 60*24
  // (un evento que acaba después de medianoche), JavaScript salta solo al día siguiente.
  const base = dias[diaElegido];
  const inicio = new Date(base.getFullYear(), base.getMonth(), base.getDate(), 0, inicioMinutos);
  const fin = new Date(base.getFullYear(), base.getMonth(), base.getDate(), 0, inicioMinutos + duracionMinutos);
  const yaHaPasado = inicio.getTime() < abiertaEn;

  function nombreDia(indice: number) {
    if (indice === 0) return 'Hoy';
    if (indice === 1) return 'Mañana';
    return dia(dias[indice]);
  }

  async function crear() {
    if (!token) return;
    setEnviando(true);
    setError(null);
    setErrores({});
    try {
      const creado = await crearEvento(token, {
        canchaId: Number(canchaId),
        tipo,
        titulo,
        descripcion,
        // toISOString() pasa la hora del móvil a UTC: "2026-11-01T17:00:00.000Z"
        inicio: inicio.toISOString(),
        fin: fin.toISOString(),
        plazas,
        nivel,
      });
      // replace: así "atrás" desde la ficha del evento vuelve a la cancha y no al formulario.
      router.replace(`/evento/${creado.id}`);
    } catch (e) {
      setEnviando(false);
      if (e instanceof ErrorApi && e.estado === 401) {
        cerrarSesion();
      } else if (e instanceof ErrorApi) {
        setErrores(e.campos);
        if (Object.keys(e.campos).length === 0) setError(e.message);
      } else {
        setError('Algo ha fallado. Inténtalo de nuevo.');
      }
    }
  }

  return (
    <ThemedView style={styles.fondo}>
      <KeyboardAvoidingView style={styles.fondo} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          contentContainerStyle={[styles.contenido, { paddingBottom: margenes.bottom + Spacing.four }]}
          keyboardShouldPersistTaps="handled">
          {canchaNombre ? (
            <ThemedText themeColor="textSecondary">📍 {canchaNombre}</ThemedText>
          ) : null}

          <View style={styles.grupo}>
            <ThemedText type="smallBold">¿Qué es?</ThemedText>
            <View style={styles.chips}>
              {TIPOS.map((t) => (
                <Chip key={t} texto={NOMBRES_TIPO[t]} activo={tipo === t} onPress={() => setTipo(t)} />
              ))}
            </View>
          </View>

          <CampoTexto
            etiqueta="Título"
            value={titulo}
            onChangeText={setTitulo}
            error={errores.titulo}
            placeholder={tipo === 'PACHANGA' ? 'Ej.: Pachanga 3x3 por la tarde' : 'Ej.: Entreno de tiro'}
            maxLength={100}
          />

          <View style={styles.grupo}>
            <ThemedText type="smallBold">Día</ThemedText>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.fila}>
              {dias.map((_, i) => (
                <Chip key={i} texto={nombreDia(i)} activo={diaElegido === i} onPress={() => setDiaElegido(i)} />
              ))}
            </ScrollView>
          </View>

          <View style={styles.grupo}>
            <ThemedText type="smallBold">Hora de inicio</ThemedText>
            <View style={styles.contador}>
              <Chip
                texto="−"
                onPress={() => setInicioMinutos(Math.max(HORA_MINIMA, inicioMinutos - PASO_HORA))}
              />
              <ThemedText type="subtitle" style={styles.numero}>
                {minutosAHora(inicioMinutos)}
              </ThemedText>
              <Chip
                texto="+"
                onPress={() => setInicioMinutos(Math.min(HORA_MAXIMA, inicioMinutos + PASO_HORA))}
              />
            </View>
            {yaHaPasado ? (
              <ThemedText type="small" style={{ color: tema.error }}>
                Esa hora ya ha pasado: elige otra o cambia de día.
              </ThemedText>
            ) : null}
          </View>

          <View style={styles.grupo}>
            <ThemedText type="smallBold">Duración</ThemedText>
            <View style={styles.chips}>
              {DURACIONES.map((d) => (
                <Chip key={d} texto={duracion(d)} activo={duracionMinutos === d} onPress={() => setDuracionMinutos(d)} />
              ))}
            </View>
            <ThemedText type="small" themeColor="textSecondary">
              Termina a las {hora(fin)}
            </ThemedText>
          </View>

          <View style={styles.grupo}>
            <ThemedText type="smallBold">Plazas (contándote a ti)</ThemedText>
            <View style={styles.contador}>
              <Chip texto="−" onPress={() => setPlazas(Math.max(2, plazas - 1))} />
              <ThemedText type="subtitle" style={styles.numero}>
                {plazas}
              </ThemedText>
              <Chip texto="+" onPress={() => setPlazas(Math.min(50, plazas + 1))} />
            </View>
            {errores.plazas ? (
              <ThemedText type="small" style={{ color: tema.error }}>
                {errores.plazas}
              </ThemedText>
            ) : null}
          </View>

          <View style={styles.grupo}>
            <ThemedText type="smallBold">Nivel</ThemedText>
            <View style={styles.chips}>
              {NIVELES.map((n) => (
                <Chip key={n} texto={NOMBRES_NIVEL[n]} activo={nivel === n} onPress={() => setNivel(n)} />
              ))}
            </View>
          </View>

          <CampoTexto
            etiqueta="Descripción (opcional)"
            value={descripcion}
            onChangeText={setDescripcion}
            error={errores.descripcion}
            placeholder="Qué traer, cómo se hacen los equipos, si hay que llevar balón…"
            multiline
            maxLength={1000}
            style={styles.multilinea}
          />

          {error ? <ThemedText style={{ color: tema.error }}>{error}</ThemedText> : null}

          <Boton texto={tipo === 'PACHANGA' ? 'Crear pachanga' : 'Crear entreno'} onPress={crear} cargando={enviando} />
        </ScrollView>
      </KeyboardAvoidingView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
  },
  contenido: {
    padding: Spacing.four,
    gap: Spacing.three,
  },
  grupo: {
    gap: Spacing.two,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  fila: {
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
  multilinea: {
    minHeight: 90,
    textAlignVertical: 'top',
  },
});
