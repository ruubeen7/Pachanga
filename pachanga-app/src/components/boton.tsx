import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  texto: string;
  onPress: () => void;
  cargando?: boolean; // muestra una ruedita y no deja pulsar
  variante?: 'principal' | 'secundario';
};

/** Botón de Pachanga. "principal" es naranja; "secundario" es solo con borde. */
export function Boton({ texto, onPress, cargando = false, variante = 'principal' }: Props) {
  const tema = useTheme();
  const principal = variante === 'principal';

  return (
    <Pressable
      onPress={onPress}
      disabled={cargando}
      accessibilityRole="button"
      style={({ pressed }) => [
        styles.boton,
        principal
          ? { backgroundColor: tema.primario }
          : { borderWidth: 1, borderColor: tema.primario },
        (pressed || cargando) && styles.apagado,
      ]}>
      {cargando ? (
        <ActivityIndicator color={principal ? '#ffffff' : tema.primario} />
      ) : (
        <ThemedText style={[styles.texto, { color: principal ? '#ffffff' : tema.primario }]}>
          {texto}
        </ThemedText>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  boton: {
    minHeight: 48,
    paddingHorizontal: Spacing.three,
    borderRadius: Spacing.three,
    alignItems: 'center',
    justifyContent: 'center',
  },
  texto: {
    fontWeight: '600',
  },
  apagado: {
    opacity: 0.7,
  },
});
