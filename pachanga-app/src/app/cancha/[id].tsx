import { Image } from 'expo-image';
import { router, Stack, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  NOMBRES_SUPERFICIE,
  obtenerCancha,
  subirFoto,
  urlDeFoto,
  type CanchaDetalle,
} from '@/api/canchas';
import { ErrorApi } from '@/api/cliente';
import { Boton } from '@/components/boton';
import { Chip } from '@/components/chip';
import { TarjetaEvento } from '@/components/tarjeta-evento';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';
import { elegirFotos } from '@/utils/fotos';
import { abrirIndicaciones } from '@/utils/indicaciones';

/** Igual que FotoService.MAX_FOTOS_POR_CANCHA en la API (allí es donde de verdad se comprueba). */
const MAX_FOTOS = 10;

/**
 * Ficha de una cancha. La ruta es /cancha/5: el nombre del archivo, [id], hace que
 * Expo Router acepte cualquier valor ahí y nos lo dé con useLocalSearchParams.
 */
export default function FichaCanchaScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { token, usuario, cerrarSesion } = useSesion();
  const tema = useTheme();
  const margenes = useSafeAreaInsets();
  const { width: anchoPantalla } = useWindowDimensions();

  const [cancha, setCancha] = useState<CanchaDetalle | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [subiendo, setSubiendo] = useState(false);
  const [avisoFotos, setAvisoFotos] = useState<string | null>(null);

  // Cargamos la ficha cada vez que la pantalla se ve (al abrirla y al volver de un evento,
  // para que salgan los cambios). useCallback hace que la función solo cambie si cambia id o token.
  // El estado se cambia dentro de .then(), cuando llega la respuesta: así lo recomienda React.
  useFocusEffect(
    useCallback(() => {
      if (!token) return;
      let cancelado = false; // si sales de la pantalla antes de que llegue, no tocamos el estado
      obtenerCancha(token, Number(id))
        .then((datos) => {
          if (!cancelado) setCancha(datos);
        })
        .catch((e) => {
          if (cancelado) return;
          if (e instanceof ErrorApi && e.estado === 401) cerrarSesion();
          else setError(e instanceof Error ? e.message : 'No se pudo cargar la cancha');
        });
      return () => {
        cancelado = true;
      };
    }, [id, token, cerrarSesion]),
  );

  /** Vuelve a pedir la ficha (botón "Reintentar" y después de subir fotos). */
  async function cargar() {
    if (!token) return;
    try {
      setCancha(await obtenerCancha(token, Number(id)));
      setError(null);
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 401) cerrarSesion();
      else setError(e instanceof Error ? e.message : 'No se pudo cargar la cancha');
    }
  }

  async function anadirFotos() {
    if (!cancha || !token) return;
    setAvisoFotos(null);
    const fotos = await elegirFotos(Math.min(3, MAX_FOTOS - cancha.fotos.length));
    if (fotos.length === 0) return;

    setSubiendo(true);
    try {
      for (const foto of fotos) await subirFoto(token, cancha.id, foto);
    } catch (e) {
      setAvisoFotos(e instanceof Error ? e.message : 'No se pudo subir la foto');
    }
    await cargar();
    setSubiendo(false);
  }

  if (error) {
    return (
      <ThemedView style={[styles.centro, { paddingBottom: margenes.bottom }]}>
        <Stack.Screen options={{ title: 'Cancha' }} />
        <ThemedText style={{ color: tema.error, textAlign: 'center' }}>{error}</ThemedText>
        <Boton texto="Reintentar" variante="secundario" onPress={cargar} />
      </ThemedView>
    );
  }

  if (!cancha) {
    return (
      <ThemedView style={styles.centro}>
        <Stack.Screen options={{ title: 'Cancha' }} />
        <ActivityIndicator color={tema.primario} size="large" />
      </ThemedView>
    );
  }

  const quedanFotos = cancha.fotos.length < MAX_FOTOS;

  return (
    <ThemedView style={styles.fondo}>
      {/* Desde la propia pantalla cambiamos el título de la barra de arriba */}
      <Stack.Screen options={{ title: cancha.nombre }} />

      <ScrollView contentContainerStyle={{ paddingBottom: margenes.bottom + Spacing.four }}>
        {/* Fotos: se pasan deslizando de lado (pagingEnabled hace que se pare en cada una) */}
        {cancha.fotos.length > 0 ? (
          <ScrollView horizontal pagingEnabled showsHorizontalScrollIndicator={false}>
            {cancha.fotos.map((foto) => (
              <Image
                key={foto.id}
                source={{ uri: urlDeFoto(foto.url) }}
                style={{ width: anchoPantalla, height: 260 }}
                contentFit="cover"
                transition={200}
              />
            ))}
          </ScrollView>
        ) : (
          <ThemedView type="backgroundElement" style={styles.sinFotos}>
            <ThemedText type="title">🏀</ThemedText>
            <ThemedText themeColor="textSecondary">Aún no hay fotos de esta cancha</ThemedText>
          </ThemedView>
        )}

        <View style={styles.contenido}>
          <View>
            <ThemedText type="subtitle" style={styles.nombre}>
              {cancha.nombre}
            </ThemedText>
            <ThemedText themeColor="textSecondary">
              {cancha.direccion ? `${cancha.direccion}, ${cancha.ciudad}` : cancha.ciudad}
            </ThemedText>
          </View>

          <View style={styles.chips}>
            {cancha.superficie && <Chip texto={NOMBRES_SUPERFICIE[cancha.superficie]} />}
            {cancha.numCanastas != null && (
              <Chip texto={cancha.numCanastas === 1 ? '1 canasta' : `${cancha.numCanastas} canastas`} />
            )}
            <Chip texto={cancha.cubierta ? 'Cubierta' : 'Al aire libre'} />
            <Chip texto={cancha.iluminacion ? 'Con luz' : 'Sin luz'} />
            <Chip texto={cancha.gratuita ? 'Gratis' : 'De pago'} />
          </View>

          {cancha.descripcion ? <ThemedText>{cancha.descripcion}</ThemedText> : null}

          <View style={styles.botones}>
            <Boton texto="Cómo llegar" onPress={() => abrirIndicaciones(cancha.latitud, cancha.longitud)} />
            {quedanFotos && (
              <Boton texto="Añadir fotos" variante="secundario" onPress={anadirFotos} cargando={subiendo} />
            )}
            {avisoFotos ? (
              <ThemedText type="small" style={{ color: tema.error }}>
                {avisoFotos}
              </ThemedText>
            ) : null}
          </View>

          <ThemedText type="smallBold" style={styles.seccion}>
            PRÓXIMOS EVENTOS
          </ThemedText>
          {cancha.proximosEventos.length === 0 ? (
            <ThemedText themeColor="textSecondary">
              No hay pachangas ni entrenos programados aquí. ¡Organiza el primero!
            </ThemedText>
          ) : (
            cancha.proximosEventos.map((evento) => (
              <TarjetaEvento key={evento.id} evento={evento} miId={usuario?.id} />
            ))
          )}
          <Boton
            texto="Organizar pachanga o entreno"
            onPress={() =>
              router.push({
                pathname: '/evento/nuevo',
                params: { canchaId: String(cancha.id), canchaNombre: cancha.nombre },
              })
            }
          />

          {cancha.creadaPor ? (
            <ThemedText type="small" themeColor="textSecondary" style={styles.autor}>
              Añadida por {cancha.creadaPor}
            </ThemedText>
          ) : null}
        </View>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
  },
  centro: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.three,
    padding: Spacing.four,
  },
  sinFotos: {
    height: 200,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  contenido: {
    padding: Spacing.four,
    gap: Spacing.three,
  },
  nombre: {
    fontSize: 26,
    lineHeight: 32,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  botones: {
    gap: Spacing.two,
  },
  seccion: {
    marginTop: Spacing.three,
    letterSpacing: 1,
  },
  autor: {
    marginTop: Spacing.three,
    textAlign: 'center',
  },
});
