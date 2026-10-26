import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ErrorApi } from '@/api/cliente';
import { misEventos, type MisEventos } from '@/api/eventos';
import { Boton } from '@/components/boton';
import { TarjetaEvento } from '@/components/tarjeta-evento';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';

export default function PerfilScreen() {
  const { usuario, token, cerrarSesion } = useSesion();
  const tema = useTheme();
  const [saliendo, setSaliendo] = useState(false);
  const [eventos, setEventos] = useState<MisEventos | null>(null);
  const [errorEventos, setErrorEventos] = useState<string | null>(null);

  // Cada vez que entras en la pestaña pedimos tus eventos (por si te has apuntado a alguno).
  useFocusEffect(
    useCallback(() => {
      if (!token) return;
      let cancelado = false;
      misEventos(token)
        .then((datos) => {
          if (cancelado) return;
          setEventos(datos);
          setErrorEventos(null);
        })
        .catch((e) => {
          if (cancelado) return;
          if (e instanceof ErrorApi && e.estado === 401) cerrarSesion();
          else setErrorEventos(e instanceof Error ? e.message : 'No se pudieron cargar tus eventos');
        });
      return () => {
        cancelado = true;
      };
    }, [token, cerrarSesion]),
  );

  // Esta pantalla solo se ve con sesión, pero TypeScript no lo sabe: comprobamos por si acaso.
  if (!usuario) return null;

  const inicial = usuario.nombre.charAt(0).toUpperCase();

  async function salir() {
    setSaliendo(true);
    await cerrarSesion(); // al quedarse sin sesión, el layout raíz vuelve solo al login
  }

  return (
    <ThemedView style={styles.fondo}>
      <SafeAreaView style={styles.fondo} edges={['top']}>
        <ScrollView contentContainerStyle={styles.contenido}>
          {/* Avatar con la inicial; más adelante, la foto de perfil */}
          <View style={[styles.avatar, { backgroundColor: tema.primario }]}>
            <ThemedText type="subtitle" style={styles.inicial}>
              {inicial}
            </ThemedText>
          </View>

          <ThemedText type="subtitle" style={styles.centrado}>
            {usuario.nombre}
          </ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.centrado}>
            {usuario.email}
          </ThemedText>

          <View style={styles.seccion}>
            <ThemedText type="smallBold" style={styles.tituloSeccion}>
              MIS PRÓXIMOS EVENTOS
            </ThemedText>
            {errorEventos ? (
              <ThemedText style={{ color: tema.error }}>{errorEventos}</ThemedText>
            ) : !eventos ? (
              <ActivityIndicator color={tema.primario} />
            ) : eventos.proximos.length === 0 ? (
              <ThemedText themeColor="textSecondary">
                Aún no tienes ninguno. Busca una cancha en el mapa y apúntate a una pachanga u organiza la tuya.
              </ThemedText>
            ) : (
              eventos.proximos.map((e) => <TarjetaEvento key={e.id} evento={e} mostrarCancha miId={usuario.id} />)
            )}
          </View>

          {eventos && eventos.pasados.length > 0 && (
            <View style={styles.seccion}>
              <ThemedText type="smallBold" style={styles.tituloSeccion}>
                YA JUGADOS
              </ThemedText>
              {eventos.pasados.map((e) => (
                <TarjetaEvento key={e.id} evento={e} mostrarCancha miId={usuario.id} />
              ))}
            </View>
          )}

          <View style={styles.acciones}>
            <Boton texto="Cerrar sesión" variante="secundario" onPress={salir} cargando={saliendo} />
          </View>
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
  },
  contenido: {
    alignItems: 'center',
    gap: Spacing.two,
    padding: Spacing.four,
    paddingBottom: BottomTabInset + Spacing.four,
  },
  avatar: {
    width: 96,
    height: 96,
    borderRadius: 48,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
  },
  inicial: {
    color: '#ffffff',
  },
  centrado: {
    textAlign: 'center',
  },
  seccion: {
    alignSelf: 'stretch',
    gap: Spacing.two,
    marginTop: Spacing.four,
  },
  tituloSeccion: {
    letterSpacing: 1,
  },
  acciones: {
    alignSelf: 'stretch',
    maxWidth: MaxContentWidth / 2,
    width: '100%',
    marginTop: Spacing.four,
  },
});
