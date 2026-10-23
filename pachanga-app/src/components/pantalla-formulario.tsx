import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';

type Props = {
  titulo: string;
  subtitulo: string;
  children: ReactNode;
};

/**
 * Esqueleto común de las pantallas de login y registro:
 * título arriba, formulario debajo, y que el teclado no tape los campos.
 */
export function PantallaFormulario({ titulo, subtitulo, children }: Props) {
  return (
    <ThemedView style={styles.fondo}>
      <SafeAreaView style={styles.fondo}>
        {/* Empuja el contenido hacia arriba cuando sale el teclado (en iPhone hace falta) */}
        <KeyboardAvoidingView
          style={styles.fondo}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <ScrollView
            contentContainerStyle={styles.contenido}
            keyboardShouldPersistTaps="handled">
            <ThemedText type="title" style={styles.centrado}>
              🏀
            </ThemedText>
            <ThemedText type="subtitle" style={styles.centrado}>
              {titulo}
            </ThemedText>
            <ThemedText themeColor="textSecondary" style={[styles.centrado, styles.subtitulo]}>
              {subtitulo}
            </ThemedText>
            {children}
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
  },
  contenido: {
    flexGrow: 1,
    justifyContent: 'center',
    gap: Spacing.three,
    padding: Spacing.four,
    width: '100%',
    maxWidth: MaxContentWidth / 2,
    alignSelf: 'center',
  },
  centrado: {
    textAlign: 'center',
  },
  subtitulo: {
    marginBottom: Spacing.two,
  },
});
