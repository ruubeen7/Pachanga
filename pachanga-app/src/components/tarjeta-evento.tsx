import { router } from 'expo-router';
import { Pressable, StyleSheet } from 'react-native';

import { NOMBRES_NIVEL, NOMBRES_TIPO, type EventoResumen } from '@/api/eventos';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { dia, hora } from '@/utils/fechas';

type Props = {
  evento: EventoResumen;
  /** En el perfil enseñamos en qué cancha es; en la ficha de la cancha no hace falta. */
  mostrarCancha?: boolean;
  /** Para marcar "Organizas tú". */
  miId?: number;
};

/** Un evento en una lista. Al pulsarlo se abre su ficha. */
export function TarjetaEvento({ evento, mostrarCancha = false, miId }: Props) {
  const tema = useTheme();
  const inicio = new Date(evento.inicio);
  const fin = new Date(evento.fin);
  const cancelado = evento.estado === 'CANCELADO';
  const lleno = evento.inscritos >= evento.plazas;

  return (
    <Pressable
      onPress={() => router.push(`/evento/${evento.id}`)}
      style={({ pressed }) => [
        styles.tarjeta,
        { backgroundColor: tema.backgroundElement, borderColor: tema.borde },
        (pressed || cancelado) && styles.apagada,
      ]}>
      <ThemedText type="smallBold" style={{ color: cancelado ? tema.error : tema.primario }}>
        {cancelado ? 'CANCELADO' : NOMBRES_TIPO[evento.tipo].toUpperCase()}
        {!cancelado && evento.organizadorId === miId ? ' · ORGANIZAS TÚ' : ''}
      </ThemedText>
      <ThemedText style={styles.titulo}>{evento.titulo}</ThemedText>
      {mostrarCancha && (
        <ThemedText type="small" themeColor="textSecondary">
          📍 {evento.canchaNombre}
        </ThemedText>
      )}
      <ThemedText type="small" themeColor="textSecondary">
        {dia(inicio)} · {hora(inicio)} – {hora(fin)}
      </ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {lleno ? 'Completo' : `${evento.inscritos}/${evento.plazas} apuntados`}
        {evento.nivel !== 'TODOS' ? ` · ${NOMBRES_NIVEL[evento.nivel]}` : ''}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  tarjeta: {
    padding: Spacing.three,
    borderRadius: Spacing.three,
    borderWidth: 1,
    gap: Spacing.half,
  },
  titulo: {
    fontWeight: '600',
  },
  apagada: {
    opacity: 0.7,
  },
});
