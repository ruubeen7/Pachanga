import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorApi } from '@/api/cliente';
import {
  apuntarse,
  cancelarEvento,
  desapuntarse,
  NOMBRES_NIVEL,
  NOMBRES_TIPO,
  obtenerEvento,
  type EventoDetalle,
  type UsuarioPublico,
} from '@/api/eventos';
import { bloquear } from '@/api/moderacion';
import { Boton } from '@/components/boton';
import { BotonMenu, MenuOpciones, type Opcion } from '@/components/menu-opciones';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { VentanaDenuncia, type Denunciable } from '@/components/ventana-denuncia';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';
import { diaLargo, hora } from '@/utils/fechas';
import { abrirIndicaciones } from '@/utils/indicaciones';

/**
 * Ficha de un evento: cuándo y dónde es, quién va y el botón que toque
 * (Apuntarme, Desapuntarme o Cancelar evento). Qué botón se ve lo decide la API
 * con los campos "apuntado" y "organizo".
 */
export default function FichaEventoScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { token, usuario, cerrarSesion } = useSesion();
  const tema = useTheme();
  const margenes = useSafeAreaInsets();

  const [evento, setEvento] = useState<EventoDetalle | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null); // error de una acción (p. ej. "Ya no quedan plazas")
  const [ocupado, setOcupado] = useState(false);
  // La hora a la que se abrió la pantalla, para saber si el evento ya terminó
  // (React no deja leer el reloj mientras dibuja).
  const [abiertaEn] = useState(() => Date.now());
  // Menú "⋯" abierto (con su título y opciones) y lo que se está denunciando.
  const [menu, setMenu] = useState<{ titulo?: string; opciones: Opcion[] } | null>(null);
  const [denuncia, setDenuncia] = useState<Denunciable>(null);

  useEffect(() => {
    if (!token) return;
    let cancelado = false;
    obtenerEvento(token, Number(id))
      .then((datos) => {
        if (!cancelado) setEvento(datos);
      })
      .catch((e) => {
        if (cancelado) return;
        if (e instanceof ErrorApi && e.estado === 401) cerrarSesion();
        else setError(e instanceof Error ? e.message : 'No se pudo cargar el evento');
      });
    return () => {
      cancelado = true;
    };
  }, [id, token, cerrarSesion]);

  async function cargar() {
    if (!token) return;
    try {
      setEvento(await obtenerEvento(token, Number(id)));
      setError(null);
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 401) cerrarSesion();
      else setError(e instanceof Error ? e.message : 'No se pudo cargar el evento');
    }
  }

  /**
   * Ejecuta una acción (apuntarse, desapuntarse, cancelar). La API devuelve la ficha ya
   * actualizada, así que la pintamos directamente. Si falla (por ejemplo, alguien cogió la
   * última plaza un segundo antes), enseñamos el motivo y recargamos para ver el estado real.
   */
  async function hacer(accion: (token: string, id: number) => Promise<EventoDetalle>) {
    if (!token) return;
    setOcupado(true);
    setAviso(null);
    try {
      setEvento(await accion(token, Number(id)));
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 401) {
        cerrarSesion();
        return;
      }
      setAviso(e instanceof Error ? e.message : 'Algo ha fallado');
      await cargar();
    } finally {
      setOcupado(false);
    }
  }

  function confirmarCancelar() {
    // Alert.alert con botones: el estilo "destructive" lo pinta en rojo en iPhone.
    Alert.alert('¿Cancelar el evento?', 'Los apuntados verán que se ha cancelado. No se puede deshacer.', [
      { text: 'No', style: 'cancel' },
      { text: 'Sí, cancelar', style: 'destructive', onPress: () => hacer(cancelarEvento) },
    ]);
  }

  /** Pregunta antes de bloquear y, si dice que sí, bloquea. */
  function confirmarBloqueo(persona: UsuarioPublico) {
    Alert.alert(
      `¿Bloquear a ${persona.nombre}?`,
      'No veréis los eventos ni las fotos del otro, y saldréis de los eventos que organice el otro. ' +
        'Puedes desbloquearle desde tu perfil.',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Bloquear',
          style: 'destructive',
          onPress: async () => {
            if (!token || !evento) return;
            try {
              await bloquear(token, persona.id);
              // Si has bloqueado a quien organiza, ya no puedes ver este evento: volvemos atrás.
              if (persona.id === evento.organizador.id) router.back();
              else await cargar();
            } catch (e) {
              setAviso(e instanceof Error ? e.message : 'No se pudo bloquear');
            }
          },
        },
      ],
    );
  }

  /** Menú de la barra de arriba: denunciar el evento o bloquear a quien lo organiza. */
  function abrirMenuEvento() {
    if (!evento) return;
    const opciones: Opcion[] = [
      {
        texto: 'Denunciar evento',
        onPress: () => setDenuncia({ tipo: 'EVENTO', id: evento.id, nombre: evento.titulo }),
      },
    ];
    if (!evento.organizo) {
      opciones.push({
        texto: `Bloquear a ${evento.organizador.nombre}`,
        peligro: true,
        onPress: () => confirmarBloqueo(evento.organizador),
      });
    }
    setMenu({ opciones });
  }

  /** Al tocar a alguien de la lista "Quién va". */
  function abrirMenuPersona(persona: UsuarioPublico) {
    setMenu({
      titulo: persona.nombre,
      opciones: [
        {
          texto: `Denunciar a ${persona.nombre}`,
          onPress: () => setDenuncia({ tipo: 'USUARIO', id: persona.id, nombre: persona.nombre }),
        },
        { texto: `Bloquear a ${persona.nombre}`, peligro: true, onPress: () => confirmarBloqueo(persona) },
      ],
    });
  }

  if (error) {
    return (
      <ThemedView style={styles.centro}>
        <Stack.Screen options={{ title: 'Evento' }} />
        <ThemedText style={{ color: tema.error, textAlign: 'center' }}>{error}</ThemedText>
        <Boton texto="Reintentar" variante="secundario" onPress={cargar} />
      </ThemedView>
    );
  }

  if (!evento) {
    return (
      <ThemedView style={styles.centro}>
        <Stack.Screen options={{ title: 'Evento' }} />
        <ActivityIndicator color={tema.primario} size="large" />
      </ThemedView>
    );
  }

  const inicio = new Date(evento.inicio);
  const fin = new Date(evento.fin);
  const cancelado = evento.estado === 'CANCELADO';
  const terminado = fin.getTime() <= abiertaEn;
  const libres = evento.plazas - evento.inscritos;

  return (
    <ThemedView style={styles.fondo}>
      {/* headerRight: lo que se pinta a la derecha de la barra de arriba */}
      <Stack.Screen
        options={{ title: NOMBRES_TIPO[evento.tipo], headerRight: () => <BotonMenu onPress={abrirMenuEvento} /> }}
      />

      <ScrollView contentContainerStyle={[styles.contenido, { paddingBottom: margenes.bottom + Spacing.four }]}>
        {cancelado && (
          <View style={[styles.banda, { backgroundColor: tema.error }]}>
            <ThemedText style={styles.textoBlanco}>Este evento se ha cancelado</ThemedText>
          </View>
        )}

        <ThemedText type="subtitle" style={styles.titulo}>
          {evento.titulo}
        </ThemedText>

        <View style={styles.datos}>
          <Dato icono="📅" texto={diaLargo(inicio)} />
          <Dato icono="🕒" texto={`${hora(inicio)} – ${hora(fin)}`} />
          <Pressable onPress={() => router.push(`/cancha/${evento.cancha.id}`)}>
            <Dato icono="📍" texto={evento.cancha.nombre} enlace />
          </Pressable>
          <Dato icono="📶" texto={NOMBRES_NIVEL[evento.nivel]} />
          <Dato
            icono="👥"
            texto={`${evento.inscritos}/${evento.plazas} apuntados${
              libres > 0 ? ` · quedan ${libres}` : ' · completo'
            }`}
          />
        </View>

        {evento.descripcion ? <ThemedText>{evento.descripcion}</ThemedText> : null}

        <View style={styles.botones}>
          {terminado ? (
            <ThemedText themeColor="textSecondary">Este evento ya ha terminado.</ThemedText>
          ) : cancelado ? null : evento.organizo ? (
            <Boton texto="Cancelar evento" variante="secundario" onPress={confirmarCancelar} cargando={ocupado} />
          ) : evento.apuntado ? (
            <Boton texto="Desapuntarme" variante="secundario" onPress={() => hacer(desapuntarse)} cargando={ocupado} />
          ) : libres > 0 ? (
            <Boton texto="Apuntarme" onPress={() => hacer(apuntarse)} cargando={ocupado} />
          ) : (
            <ThemedText themeColor="textSecondary">No quedan plazas.</ThemedText>
          )}

          {aviso ? (
            <ThemedText type="small" style={{ color: tema.error }}>
              {aviso}
            </ThemedText>
          ) : null}

          {/* El chat solo es para los apuntados (la API también lo comprueba). */}
          {evento.apuntado && (
            <Boton
              texto="💬 Chat del evento"
              variante="secundario"
              onPress={() =>
                router.push({ pathname: '/chat/[id]', params: { id: String(evento.id), titulo: evento.titulo } })
              }
            />
          )}

          {!cancelado && !terminado && (
            <Boton
              texto="Cómo llegar"
              variante="secundario"
              onPress={() => abrirIndicaciones(evento.cancha.latitud, evento.cancha.longitud)}
            />
          )}
        </View>

        <ThemedText type="smallBold" style={styles.seccion}>
          QUIÉN VA
        </ThemedText>
        {evento.apuntados.map((persona) => (
          <Pressable
            key={persona.id}
            // Tocar a otra persona abre el menú para denunciarla o bloquearla (a ti mismo no).
            onPress={persona.id === usuario?.id ? undefined : () => abrirMenuPersona(persona)}
            style={[styles.persona, { borderColor: tema.borde }]}>
            <View style={[styles.avatar, { backgroundColor: tema.primario }]}>
              <ThemedText style={styles.textoBlanco}>{persona.nombre.charAt(0).toUpperCase()}</ThemedText>
            </View>
            <ThemedText style={styles.nombre}>
              {persona.nombre}
              {persona.id === usuario?.id ? ' (tú)' : ''}
            </ThemedText>
            {persona.id === evento.organizador.id && (
              <ThemedText type="small" style={{ color: tema.primario }}>
                Organiza
              </ThemedText>
            )}
          </Pressable>
        ))}
      </ScrollView>

      <MenuOpciones
        visible={menu !== null}
        titulo={menu?.titulo}
        opciones={menu?.opciones ?? []}
        onCerrar={() => setMenu(null)}
      />
      <VentanaDenuncia contenido={denuncia} onCerrar={() => setDenuncia(null)} />
    </ThemedView>
  );
}

function Dato({ icono, texto, enlace = false }: { icono: string; texto: string; enlace?: boolean }) {
  const tema = useTheme();
  return (
    <View style={styles.dato}>
      <ThemedText>{icono}</ThemedText>
      <ThemedText style={[styles.textoDato, enlace && { color: tema.primario }]}>
        {texto}
        {enlace ? ' →' : ''}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  fondo: {
    flex: 1,
  },
  centro: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.three,
    padding: Spacing.four,
  },
  contenido: {
    padding: Spacing.four,
    gap: Spacing.three,
  },
  banda: {
    padding: Spacing.three,
    borderRadius: Spacing.three,
  },
  titulo: {
    fontSize: 26,
    lineHeight: 32,
  },
  datos: {
    gap: Spacing.two,
  },
  dato: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  textoDato: {
    flex: 1,
  },
  botones: {
    gap: Spacing.two,
  },
  seccion: {
    marginTop: Spacing.three,
    letterSpacing: 1,
  },
  persona: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    paddingVertical: Spacing.two,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nombre: {
    flex: 1,
  },
  textoBlanco: {
    color: '#ffffff',
    fontWeight: '600',
  },
});
