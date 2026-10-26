import { useRef } from 'react';
import { Modal, Platform, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type Opcion = {
  texto: string;
  onPress: () => void;
  /** En rojo: acciones serias como bloquear. */
  peligro?: boolean;
};

type Props = {
  visible: boolean;
  titulo?: string;
  opciones: Opcion[];
  onCerrar: () => void;
};

/**
 * Menú que sube desde abajo con unas cuantas opciones ("Denunciar", "Bloquear"...).
 * Modal es una "capa" que se pone encima de toda la pantalla; transparent deja ver lo de detrás
 * oscurecido, y tocar fuera del menú lo cierra.
 */
export function MenuOpciones({ visible, titulo, opciones, onCerrar }: Props) {
  const tema = useTheme();
  const margenes = useSafeAreaInsets();
  // La opción elegida, a la espera de que el menú termine de cerrarse.
  const pendiente = useRef<(() => void) | null>(null);

  /**
   * En iPhone no se puede abrir otra ventana (una alerta, la de denunciar...) mientras esta se
   * está cerrando: se quedaría sin abrir. Por eso allí esperamos a onDismiss ("ya se ha cerrado").
   * En Android onDismiss no existe y no hace falta esperar.
   */
  function elegir(opcion: Opcion) {
    if (Platform.OS === 'ios') pendiente.current = opcion.onPress;
    onCerrar();
    if (Platform.OS !== 'ios') opcion.onPress();
  }

  function alCerrarse() {
    pendiente.current?.();
    pendiente.current = null;
  }

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onCerrar}
      onDismiss={alCerrarse}>
      <Pressable style={styles.fondo} onPress={onCerrar}>
        {/* Este Pressable vacío evita que tocar dentro del menú lo cierre */}
        <Pressable
          style={[styles.hoja, { backgroundColor: tema.background, paddingBottom: margenes.bottom + Spacing.three }]}
          onPress={() => {}}>
          {titulo ? (
            <ThemedText type="small" themeColor="textSecondary" style={styles.titulo}>
              {titulo}
            </ThemedText>
          ) : null}
          {opciones.map((opcion) => (
            <Pressable
              key={opcion.texto}
              onPress={() => elegir(opcion)}
              style={({ pressed }) => [styles.opcion, { borderColor: tema.borde }, pressed && styles.pulsado]}>
              <ThemedText style={[styles.texto, opcion.peligro && { color: tema.error }]}>{opcion.texto}</ThemedText>
            </Pressable>
          ))}
          <View style={styles.separador} />
          <Pressable
            onPress={onCerrar}
            style={({ pressed }) => [styles.opcion, styles.sinBorde, pressed && styles.pulsado]}>
            <ThemedText style={[styles.texto, { color: tema.primario }]}>Cancelar</ThemedText>
          </Pressable>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/** El botón "⋯" que va arriba a la derecha de la barra y abre el menú. */
export function BotonMenu({ onPress }: { onPress: () => void }) {
  const tema = useTheme();
  return (
    <Pressable onPress={onPress} hitSlop={12} accessibilityLabel="Más opciones" style={styles.botonMenu}>
      <ThemedText style={[styles.puntos, { color: tema.primario }]}>⋯</ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  botonMenu: {
    paddingHorizontal: Spacing.two,
  },
  puntos: {
    fontSize: 24,
    lineHeight: 28,
    fontWeight: '700',
  },
  fondo: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  hoja: {
    borderTopLeftRadius: Spacing.four,
    borderTopRightRadius: Spacing.four,
    paddingTop: Spacing.three,
    paddingHorizontal: Spacing.four,
  },
  titulo: {
    textAlign: 'center',
    marginBottom: Spacing.two,
  },
  opcion: {
    paddingVertical: Spacing.three,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  sinBorde: {
    borderBottomWidth: 0,
  },
  texto: {
    textAlign: 'center',
    fontWeight: '600',
  },
  separador: {
    height: Spacing.two,
  },
  pulsado: {
    opacity: 0.6,
  },
});
