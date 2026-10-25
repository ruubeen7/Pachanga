import { Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  texto: string;
  /** Marcado: fondo naranja. */
  activo?: boolean;
  /** Si no se pasa, el chip es solo informativo (no se puede pulsar). */
  onPress?: () => void;
};

/** Etiqueta redondeada. Sirve para elegir una opción (superficie) o para mostrar datos (“Con luz”). */
export function Chip({ texto, activo = false, onPress }: Props) {
  const tema = useTheme();

  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? 'button' : 'text'}
      accessibilityState={{ selected: activo }}
      style={({ pressed }) => [
        styles.chip,
        activo
          ? { backgroundColor: tema.primario, borderColor: tema.primario }
          : { backgroundColor: tema.backgroundElement, borderColor: tema.borde },
        pressed && styles.pulsado,
      ]}>
      <ThemedText type="small" style={activo ? styles.textoActivo : undefined}>
        {texto}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.three,
    borderRadius: Spacing.four,
    borderWidth: 1,
  },
  textoActivo: {
    color: '#ffffff',
  },
  pulsado: {
    opacity: 0.7,
  },
});
