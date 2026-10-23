import { useState } from 'react';
import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Boton } from '@/components/boton';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';

export default function PerfilScreen() {
  const { usuario, cerrarSesion } = useSesion();
  const tema = useTheme();
  const [saliendo, setSaliendo] = useState(false);

  // Esta pantalla solo se ve con sesión, pero TypeScript no lo sabe: comprobamos por si acaso.
  if (!usuario) return null;

  const inicial = usuario.nombre.charAt(0).toUpperCase();

  async function salir() {
    setSaliendo(true);
    await cerrarSesion(); // al quedarse sin sesión, el layout raíz vuelve solo al login
  }

  return (
    <ThemedView style={styles.fondo}>
      <SafeAreaView style={styles.contenido}>
        {/* Avatar con la inicial; más adelante, la foto de perfil */}
        <ThemedView style={[styles.avatar, { backgroundColor: tema.primario }]}>
          <ThemedText type="subtitle" style={styles.inicial}>
            {inicial}
          </ThemedText>
        </ThemedView>

        <ThemedText type="subtitle" style={styles.centrado}>
          {usuario.nombre}
        </ThemedText>
        <ThemedText themeColor="textSecondary" style={styles.centrado}>
          {usuario.email}
        </ThemedText>

        <ThemedView style={styles.acciones}>
          <Boton texto="Cerrar sesión" variante="secundario" onPress={salir} cargando={saliendo} />
        </ThemedView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
  },
  contenido: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingBottom: BottomTabInset + Spacing.three,
  },
  avatar: {
    width: 96,
    height: 96,
    borderRadius: 48,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.two,
  },
  inicial: {
    color: '#ffffff',
  },
  centrado: {
    textAlign: 'center',
  },
  acciones: {
    alignSelf: 'stretch',
    maxWidth: MaxContentWidth / 2,
    width: '100%',
    marginTop: Spacing.four,
  },
});
