import { StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = TextInputProps & {
  etiqueta: string;
  error?: string;
};

/**
 * Campo de formulario: etiqueta + caja de texto + mensaje de error debajo.
 * Acepta todas las propiedades de TextInput (value, onChangeText, secureTextEntry...).
 */
export function CampoTexto({ etiqueta, error, style, ...props }: Props) {
  const tema = useTheme();

  return (
    <View style={styles.contenedor}>
      <ThemedText type="smallBold">{etiqueta}</ThemedText>
      <TextInput
        placeholderTextColor={tema.textSecondary}
        style={[
          styles.caja,
          {
            color: tema.text,
            backgroundColor: tema.backgroundElement,
            borderColor: error ? tema.error : tema.borde,
          },
          style,
        ]}
        {...props}
      />
      {error ? (
        <ThemedText type="small" style={{ color: tema.error }}>
          {error}
        </ThemedText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  contenedor: {
    gap: Spacing.one,
  },
  caja: {
    borderWidth: 1,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
    fontSize: 16,
  },
});
