import { router, Stack, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useCallback, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  KeyboardAvoidingView,
  Pressable,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorApi } from '@/api/cliente';
import { enviarMensaje, leerMensajes, type Mensaje, type UsuarioPublico } from '@/api/eventos';
import { bloquear } from '@/api/moderacion';
import { Boton } from '@/components/boton';
import { MenuOpciones, type Opcion } from '@/components/menu-opciones';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { VentanaDenuncia, type Denunciable } from '@/components/ventana-denuncia';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';
import { dia, hora } from '@/utils/fechas';

/** Cada cuánto se pregunta a la API si hay mensajes nuevos mientras el chat está abierto. */
const INTERVALO_MS = 4000;

/**
 * Chat de un evento: /chat/7?titulo=Pachanga...
 *
 * Cómo llegan los mensajes nuevos: mientras la pantalla está abierta, cada 4 segundos
 * pedimos "los mensajes posteriores al último que tengo" (polling). Al salir de la pantalla
 * se para. No es instantáneo como WhatsApp, pero es sencillo y gratis.
 */
export default function ChatScreen() {
  const { id, titulo } = useLocalSearchParams<{ id: string; titulo?: string }>();
  const { token, cerrarSesion } = useSesion();
  const tema = useTheme();
  const margenes = useSafeAreaInsets();
  const alturaCabecera = useHeaderHeight();

  const [mensajes, setMensajes] = useState<Mensaje[] | null>(null); // null = cargando
  const [error, setError] = useState<string | null>(null);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [menu, setMenu] = useState<{ titulo: string; opciones: Opcion[] } | null>(null);
  const [denuncia, setDenuncia] = useState<Denunciable>(null);

  /**
   * El id del último mensaje recibido en una consulta. Es un useRef y no un useState porque
   * cambiarlo no tiene que redibujar nada: solo lo usamos para la siguiente consulta.
   */
  const ultimoId = useRef<number | null>(null);

  /** Junta mensajes nuevos con los que ya hay, sin repetir y ordenados por id (= por llegada). */
  const juntar = useCallback((nuevos: Mensaje[]) => {
    setMensajes((actuales) => {
      const porId = new Map((actuales ?? []).map((m) => [m.id, m]));
      for (const m of nuevos) porId.set(m.id, m);
      return [...porId.values()].sort((a, b) => a.id - b.id);
    });
  }, []);

  // Al entrar en la pantalla empezamos a preguntar; al salir (return), paramos.
  useFocusEffect(
    useCallback(() => {
      if (!token) return;
      const miToken = token; // copia que TypeScript sabe que no es null dentro de consultar()
      let activo = true;
      let temporizador: ReturnType<typeof setInterval> | undefined;

      async function consultar() {
        try {
          const nuevos = await leerMensajes(miToken, Number(id), ultimoId.current);
          if (!activo) return;
          if (nuevos.length > 0) ultimoId.current = nuevos[nuevos.length - 1].id;
          juntar(nuevos);
          setError(null);
        } catch (e) {
          if (!activo) return;
          if (e instanceof ErrorApi && e.estado === 401) {
            cerrarSesion();
          } else if (e instanceof ErrorApi && e.estado !== 0) {
            // 403 (ya no estás apuntado) o 404 (evento borrado o bloqueado): dejamos de preguntar.
            activo = false;
            clearInterval(temporizador);
            setError(e.message);
          }
          // Si es un fallo de conexión (estado 0), no hacemos nada: se reintenta en 4 segundos.
        }
      }

      consultar();
      temporizador = setInterval(consultar, INTERVALO_MS);
      return () => {
        activo = false;
        clearInterval(temporizador);
      };
    }, [id, token, cerrarSesion, juntar]),
  );

  async function enviar() {
    const limpio = texto.trim();
    if (!limpio || !token) return;
    setEnviando(true);
    try {
      const mensaje = await enviarMensaje(token, Number(id), limpio);
      setTexto('');
      // Lo añadimos ya, sin esperar a la próxima consulta. No tocamos ultimoId: si alguien escribió
      // justo antes que nosotros, la próxima consulta todavía tiene que traer su mensaje.
      juntar([mensaje]);
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 401) cerrarSesion();
      else Alert.alert('No se pudo enviar', e instanceof Error ? e.message : 'Inténtalo de nuevo.');
    } finally {
      setEnviando(false);
    }
  }

  function confirmarBloqueo(persona: UsuarioPublico) {
    Alert.alert(
      `¿Bloquear a ${persona.nombre}?`,
      'Dejarás de ver sus mensajes, sus eventos y sus fotos. Puedes desbloquearle desde tu perfil.',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Bloquear',
          style: 'destructive',
          onPress: async () => {
            if (!token) return;
            try {
              await bloquear(token, persona.id);
              setMensajes((actuales) => (actuales ?? []).filter((m) => m.autor.id !== persona.id));
            } catch (e) {
              Alert.alert('No se pudo bloquear', e instanceof Error ? e.message : 'Inténtalo de nuevo.');
            }
          },
        },
      ],
    );
  }

  /** Mantener pulsado un mensaje de otro: denunciarlo o bloquear a quien lo escribió. */
  function abrirMenu(mensaje: Mensaje) {
    if (mensaje.mio) return;
    setMenu({
      titulo: mensaje.autor.nombre,
      opciones: [
        {
          texto: 'Denunciar mensaje',
          onPress: () => setDenuncia({ tipo: 'MENSAJE', id: mensaje.id, nombre: `«${mensaje.texto}»` }),
        },
        {
          texto: `Bloquear a ${mensaje.autor.nombre}`,
          peligro: true,
          onPress: () => confirmarBloqueo(mensaje.autor),
        },
      ],
    });
  }

  const cabecera = <Stack.Screen options={{ title: titulo ? `Chat · ${titulo}` : 'Chat' }} />;

  if (error) {
    return (
      <ThemedView style={styles.centro}>
        {cabecera}
        <ThemedText style={{ color: tema.error, textAlign: 'center' }}>{error}</ThemedText>
        <Boton texto="Volver" variante="secundario" onPress={() => router.back()} />
      </ThemedView>
    );
  }

  /*
   * La lista va "invertida" (inverted): empieza abajo, como en cualquier app de mensajería,
   * y al llegar un mensaje nuevo se queda pegada abajo sola. Por eso le pasamos los mensajes
   * al revés: el primero de la lista es el más nuevo.
   */
  const alReves = mensajes ? [...mensajes].reverse() : [];

  return (
    <ThemedView style={styles.fondo}>
      {cabecera}
      <KeyboardAvoidingView style={styles.fondo} behavior="padding" keyboardVerticalOffset={alturaCabecera}>
        {mensajes === null ? (
          <View style={styles.centro}>
            <ActivityIndicator color={tema.primario} size="large" />
          </View>
        ) : mensajes.length === 0 ? (
          <View style={styles.centro}>
            <ThemedText type="title">💬</ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.textoCentrado}>
              Aún no hay mensajes. ¡Rompe el hielo y saluda a los que van!
            </ThemedText>
          </View>
        ) : (
          <FlatList
            inverted
            data={alReves}
            keyExtractor={(m) => String(m.id)}
            contentContainerStyle={styles.lista}
            renderItem={({ item, index }) => (
              // En la lista al revés, el mensaje anterior en el tiempo es el siguiente del array.
              <Burbuja mensaje={item} anterior={alReves[index + 1]} onMantener={() => abrirMenu(item)} />
            )}
          />
        )}

        <View
          style={[
            styles.barra,
            { borderColor: tema.borde, backgroundColor: tema.background, paddingBottom: margenes.bottom + Spacing.two },
          ]}>
          <TextInput
            value={texto}
            onChangeText={setTexto}
            placeholder="Escribe un mensaje…"
            placeholderTextColor={tema.textSecondary}
            multiline
            maxLength={500}
            style={[styles.entrada, { color: tema.text, backgroundColor: tema.backgroundElement, borderColor: tema.borde }]}
          />
          <Pressable
            onPress={enviar}
            disabled={enviando || texto.trim().length === 0}
            accessibilityLabel="Enviar"
            style={({ pressed }) => [
              styles.enviar,
              { backgroundColor: tema.primario },
              (pressed || enviando || texto.trim().length === 0) && styles.apagado,
            ]}>
            {enviando ? (
              <ActivityIndicator color="#ffffff" />
            ) : (
              <ThemedText style={styles.textoEnviar}>Enviar</ThemedText>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>

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

/**
 * Un mensaje. Los míos, a la derecha y en naranja; los de los demás, a la izquierda con el nombre
 * (solo si el mensaje anterior es de otra persona, para no repetirlo). Si cambia el día, se pone
 * una separación con la fecha.
 */
function Burbuja({
  mensaje,
  anterior,
  onMantener,
}: {
  mensaje: Mensaje;
  anterior?: Mensaje;
  onMantener: () => void;
}) {
  const tema = useTheme();
  const fecha = new Date(mensaje.creadoEn);
  const nuevoDia = !anterior || new Date(anterior.creadoEn).toDateString() !== fecha.toDateString();
  const mostrarNombre = !mensaje.mio && (nuevoDia || anterior?.autor.id !== mensaje.autor.id);

  return (
    <View>
      {nuevoDia && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.separadorDia}>
          {dia(fecha)}
        </ThemedText>
      )}
      <Pressable
        onLongPress={onMantener}
        style={[
          styles.burbuja,
          mensaje.mio
            ? [styles.mia, { backgroundColor: tema.primario }]
            : [styles.ajena, { backgroundColor: tema.backgroundElement }],
        ]}>
        {mostrarNombre && (
          <ThemedText type="smallBold" style={{ color: tema.primario }}>
            {mensaje.autor.nombre}
          </ThemedText>
        )}
        <ThemedText style={mensaje.mio ? styles.textoBlanco : undefined}>{mensaje.texto}</ThemedText>
        <ThemedText
          type="small"
          themeColor="textSecondary"
          style={[styles.hora, mensaje.mio && styles.horaMia]}>
          {hora(fecha)}
        </ThemedText>
      </Pressable>
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
  textoCentrado: {
    textAlign: 'center',
  },
  lista: {
    padding: Spacing.three,
    gap: Spacing.one,
  },
  separadorDia: {
    textAlign: 'center',
    marginVertical: Spacing.two,
  },
  burbuja: {
    maxWidth: '80%',
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: Spacing.three,
  },
  mia: {
    alignSelf: 'flex-end',
    borderBottomRightRadius: Spacing.one,
  },
  ajena: {
    alignSelf: 'flex-start',
    borderBottomLeftRadius: Spacing.one,
  },
  textoBlanco: {
    color: '#ffffff',
  },
  hora: {
    fontSize: 11,
    lineHeight: 14,
    alignSelf: 'flex-end',
  },
  horaMia: {
    color: 'rgba(255,255,255,0.8)',
  },
  barra: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.two,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  entrada: {
    flex: 1,
    maxHeight: 120,
    borderWidth: 1,
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.two,
    paddingBottom: Spacing.two,
    fontSize: 16,
  },
  enviar: {
    minHeight: 42,
    paddingHorizontal: Spacing.three,
    borderRadius: Spacing.three,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textoEnviar: {
    color: '#ffffff',
    fontWeight: '600',
  },
  apagado: {
    opacity: 0.5,
  },
});
