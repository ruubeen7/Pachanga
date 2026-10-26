import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Modal, Platform, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorApi } from '@/api/cliente';
import { denunciar, NOMBRES_MOTIVO, type MotivoDenuncia, type TipoContenido } from '@/api/moderacion';
import { Boton } from '@/components/boton';
import { CampoTexto } from '@/components/campo-texto';
import { Chip } from '@/components/chip';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';

const MOTIVOS = Object.keys(NOMBRES_MOTIVO) as MotivoDenuncia[];

/** Qué se denuncia, o null si la ventana está cerrada. */
export type Denunciable = { tipo: TipoContenido; id: number; nombre: string } | null;

type Props = {
  contenido: Denunciable;
  onCerrar: () => void;
};

/**
 * Ventana para denunciar algo: eliges un motivo y, si quieres, escribes un comentario.
 * La denuncia llega a la API y la revisa un administrador.
 */
export function VentanaDenuncia({ contenido, onCerrar }: Props) {
  const { token } = useSesion();
  const tema = useTheme();
  const margenes = useSafeAreaInsets();

  const [motivo, setMotivo] = useState<MotivoDenuncia | null>(null);
  const [comentario, setComentario] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function cerrar() {
    setMotivo(null);
    setComentario('');
    setError(null);
    onCerrar();
  }

  async function enviar() {
    if (!token || !contenido) return;
    if (!motivo) {
      setError('Elige un motivo');
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await denunciar(token, { tipo: contenido.tipo, contenidoId: contenido.id, motivo, comentario });
      cerrar();
      Alert.alert('Gracias por avisar', 'Revisaremos tu denuncia lo antes posible.');
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo enviar. Inténtalo de nuevo.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal visible={contenido !== null} transparent animationType="slide" onRequestClose={cerrar}>
      <KeyboardAvoidingView style={styles.fondo} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <Pressable style={styles.fuera} onPress={cerrar} />
        <View style={[styles.hoja, { backgroundColor: tema.background, paddingBottom: margenes.bottom + Spacing.three }]}>
          <ThemedText type="smallBold">Denunciar</ThemedText>
          <ThemedText themeColor="textSecondary" numberOfLines={2}>
            {contenido?.nombre}
          </ThemedText>

          <View style={styles.chips}>
            {MOTIVOS.map((m) => (
              <Chip key={m} texto={NOMBRES_MOTIVO[m]} activo={motivo === m} onPress={() => setMotivo(m)} />
            ))}
          </View>

          <CampoTexto
            etiqueta="Cuéntanos más (opcional)"
            value={comentario}
            onChangeText={setComentario}
            multiline
            maxLength={500}
            style={styles.multilinea}
          />

          {error ? <ThemedText style={{ color: tema.error }}>{error}</ThemedText> : null}

          <Boton texto="Enviar denuncia" onPress={enviar} cargando={enviando} />
          <Boton texto="Cancelar" variante="secundario" onPress={cerrar} />
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  fuera: {
    flex: 1,
  },
  hoja: {
    borderTopLeftRadius: Spacing.four,
    borderTopRightRadius: Spacing.four,
    padding: Spacing.four,
    gap: Spacing.three,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  multilinea: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
});
