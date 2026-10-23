import { useEffect, useState } from 'react';
import { Pressable, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { API_URL } from '@/api/config';
import { comprobarSalud, Salud } from '@/api/salud';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { useSesion } from '@/sesion/sesion-context';

// En React, la pantalla se "dibuja" a partir del estado.
// Cuando cambia el estado (con setEstado), React vuelve a dibujarla sola.
type Estado =
  | { tipo: 'cargando' }
  | { tipo: 'ok'; salud: Salud }
  | { tipo: 'error'; mensaje: string };

// Llama a la API y traduce la respuesta (o el fallo) a un estado de la pantalla.
async function pedirEstado(): Promise<Estado> {
  try {
    const salud = await comprobarSalud();
    return { tipo: 'ok', salud };
  } catch (e) {
    return { tipo: 'error', mensaje: e instanceof Error ? e.message : String(e) };
  }
}

export default function InicioScreen() {
  const { usuario } = useSesion();
  const [estado, setEstado] = useState<Estado>({ tipo: 'cargando' });

  // useEffect con [] se ejecuta una sola vez, al abrir la pantalla.
  // El estado ya empieza en "cargando"; cuando llega la respuesta, la guardamos.
  useEffect(() => {
    pedirEstado().then(setEstado);
  }, []);

  // Al pulsar el botón: mostramos "Comprobando…" y volvemos a preguntar.
  function comprobar() {
    setEstado({ tipo: 'cargando' });
    pedirEstado().then(setEstado);
  }

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ThemedText type="title" style={styles.titulo}>
          🏀 Pachanga
        </ThemedText>
        <ThemedText style={styles.subtitulo}>
          ¡Hola, {usuario?.nombre}! Encuentra cancha, encuentra partido
        </ThemedText>

        <ThemedView type="backgroundElement" style={styles.tarjeta}>
          <ThemedText type="subtitle">Conexión con la API</ThemedText>
          <ThemedText type="small">{API_URL}</ThemedText>

          {estado.tipo === 'cargando' && <ThemedText>Comprobando…</ThemedText>}

          {estado.tipo === 'ok' && (
            <>
              <ThemedText>✅ Conectado</ThemedText>
              <ThemedText type="small">PostgreSQL {estado.salud.postgresql}</ThemedText>
              <ThemedText type="small">PostGIS {estado.salud.postgis}</ThemedText>
            </>
          )}

          {estado.tipo === 'error' && (
            <>
              <ThemedText>❌ No se pudo conectar</ThemedText>
              <ThemedText type="small">{estado.mensaje}</ThemedText>
              <ThemedText type="small">
                ¿Está arrancado el backend? ¿El móvil y el PC están en la misma WiFi?
              </ThemedText>
            </>
          )}

          <Pressable onPress={comprobar} style={styles.boton}>
            <ThemedText style={styles.textoBoton}>Volver a comprobar</ThemedText>
          </Pressable>
        </ThemedView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
  },
  safeArea: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingBottom: BottomTabInset + Spacing.three,
    maxWidth: MaxContentWidth,
  },
  titulo: {
    textAlign: 'center',
  },
  subtitulo: {
    textAlign: 'center',
    marginBottom: Spacing.four,
  },
  tarjeta: {
    alignSelf: 'stretch',
    gap: Spacing.two,
    padding: Spacing.four,
    borderRadius: Spacing.four,
  },
  boton: {
    marginTop: Spacing.two,
    paddingVertical: Spacing.two,
    borderRadius: Spacing.three,
    backgroundColor: '#E8772E',
    alignItems: 'center',
  },
  textoBoton: {
    color: '#ffffff',
    fontWeight: '600',
  },
});
