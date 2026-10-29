import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { canchasConEventos, type CanchaResumen } from '@/api/canchas';
import { ErrorApi } from '@/api/cliente';
import { Boton } from '@/components/boton';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';
import { dia, hora, hoyAMedianoche, sumarDias } from '@/utils/fechas';
import { obtenerUbicacion, textoDistancia } from '@/utils/ubicacion';

/**
 * Pestaña "Partidos": canchas cerca de ti que tienen eventos creados,
 * de la más cercana a la más lejana. Tocar una abre su ficha con los eventos.
 */
export default function PartidosScreen() {
  const { token, cerrarSesion } = useSesion();
  const tema = useTheme();

  const [canchas, setCanchas] = useState<CanchaResumen[] | null>(null); // null = cargando la primera vez
  const [error, setError] = useState<string | null>(null);
  const [sinUbicacion, setSinUbicacion] = useState(false);
  const [recargando, setRecargando] = useState(false);

  const cargar = useCallback(async () => {
    if (!token) return;
    const aqui = await obtenerUbicacion();
    if (!aqui) {
      setSinUbicacion(true);
      setCanchas([]);
      return;
    }
    setSinUbicacion(false);
    try {
      setCanchas(await canchasConEventos(token, aqui.latitud, aqui.longitud));
      setError(null);
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 401) {
        cerrarSesion();
        return;
      }
      setError(e instanceof Error ? e.message : 'No se pudieron cargar las canchas');
      setCanchas((anteriores) => anteriores ?? []);
    }
  }, [token, cerrarSesion]);

  // Cada vez que entras en la pestaña recargamos (por si alguien ha creado un evento nuevo).
  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  // Deslizar la lista hacia abajo para recargar
  async function recargar() {
    setRecargando(true);
    await cargar();
    setRecargando(false);
  }

  return (
    <ThemedView style={styles.fondo}>
      <SafeAreaView style={styles.fondo} edges={['top']}>
        <FlatList
          data={canchas ?? []}
          keyExtractor={(c) => String(c.id)}
          contentContainerStyle={styles.contenido}
          refreshControl={<RefreshControl refreshing={recargando} onRefresh={recargar} tintColor={tema.primario} />}
          ListHeaderComponent={
            <View style={styles.cabecera}>
              <ThemedText type="subtitle" style={styles.titulo}>
                Partidos cerca de ti
              </ThemedText>
              <ThemedText themeColor="textSecondary">
                Canchas con pachangas o entrenos ya creados, de la más cercana a la más lejana.
              </ThemedText>
              {error ? <ThemedText style={{ color: tema.error }}>{error}</ThemedText> : null}
            </View>
          }
          ListEmptyComponent={
            canchas === null ? (
              <ActivityIndicator color={tema.primario} style={styles.cargando} />
            ) : sinUbicacion ? (
              <View style={styles.vacio}>
                <ThemedText themeColor="textSecondary">
                  Para ver los partidos cerca de ti necesitamos saber dónde estás. Activa la ubicación y vuelve a
                  intentarlo.
                </ThemedText>
                <Boton texto="Intentarlo otra vez" variante="secundario" onPress={recargar} />
              </View>
            ) : error ? undefined : (
              <View style={styles.vacio}>
                <ThemedText themeColor="textSecondary">
                  No hay partidos creados cerca de ti. ¡Busca una cancha en el mapa y organiza el primero!
                </ThemedText>
                <Boton texto="Ir al mapa" variante="secundario" onPress={() => router.navigate('/')} />
              </View>
            )
          }
          renderItem={({ item }) => <TarjetaCancha cancha={item} />}
        />
      </SafeAreaView>
    </ThemedView>
  );
}

function TarjetaCancha({ cancha }: { cancha: CanchaResumen }) {
  const tema = useTheme();
  const lugar = cancha.direccion ? `${cancha.direccion}, ${cancha.ciudad}` : cancha.ciudad;

  return (
    <Pressable
      onPress={() => router.push(`/cancha/${cancha.id}`)}
      style={({ pressed }) => [
        styles.tarjeta,
        { backgroundColor: tema.backgroundElement, borderColor: tema.borde },
        pressed && styles.pulsado,
      ]}>
      <View style={[styles.icono, { backgroundColor: tema.primario }]}>
        <ThemedText style={styles.emoji}>🏀</ThemedText>
      </View>

      <View style={styles.textos}>
        <ThemedText style={styles.negrita} numberOfLines={1}>
          {cancha.nombre}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
          {cancha.distanciaMetros != null ? `${textoDistancia(cancha.distanciaMetros)} · ` : ''}
          {lugar}
        </ThemedText>
        <ThemedText type="small" style={{ color: tema.primario }}>
          {cancha.eventosProximos === 1 ? '1 evento' : `${cancha.eventosProximos} eventos`}
          {cancha.proximoEvento ? ` · el próximo ${cuando(new Date(cancha.proximoEvento))}` : ''}
        </ThemedText>
      </View>

      <ThemedText themeColor="textSecondary">›</ThemedText>
    </Pressable>
  );
}

/** "hoy a las 18:00", "mañana a las 10:30" o "sáb, 1 nov a las 18:00". Si ya ha empezado, "ahora". */
function cuando(fecha: Date) {
  if (fecha.getTime() <= Date.now()) return 'ahora mismo';
  const hoy = hoyAMedianoche();
  if (fecha < sumarDias(hoy, 1)) return `hoy a las ${hora(fecha)}`;
  if (fecha < sumarDias(hoy, 2)) return `mañana a las ${hora(fecha)}`;
  return `${dia(fecha)} a las ${hora(fecha)}`;
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
  },
  contenido: {
    padding: Spacing.four,
    paddingBottom: BottomTabInset + Spacing.four,
    gap: Spacing.three,
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
  },
  cabecera: {
    gap: Spacing.two,
    marginBottom: Spacing.one,
  },
  titulo: {
    fontSize: 26,
    lineHeight: 32,
  },
  cargando: {
    marginTop: Spacing.five,
  },
  vacio: {
    gap: Spacing.three,
    marginTop: Spacing.three,
  },
  tarjeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    padding: Spacing.three,
    borderRadius: Spacing.three,
    borderWidth: 1,
  },
  pulsado: {
    opacity: 0.7,
  },
  icono: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emoji: {
    fontSize: 22,
    lineHeight: 28,
  },
  textos: {
    flex: 1,
    gap: Spacing.half,
  },
  negrita: {
    fontWeight: '600',
  },
});
