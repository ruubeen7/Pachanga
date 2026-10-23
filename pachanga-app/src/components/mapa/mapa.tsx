import { useImperativeHandle, useRef, useState, type Ref } from 'react';
import { StyleSheet } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

import type { CanchaResumen } from '@/api/canchas';

import { crearHtmlMapa } from './html-mapa';

/** Lo que la pantalla puede pedirle al mapa (como los métodos públicos de una clase en Java). */
export type ControlMapa = {
  mostrarCanchas: (canchas: CanchaResumen[]) => void;
  centrar: (latitud: number, longitud: number, zoom?: number) => void;
  encuadrar: (canchas: CanchaResumen[]) => void;
  mostrarMiUbicacion: (latitud: number, longitud: number) => void;
  elegir: (id: number | null) => void;
};

/** Mensajes que manda la página del mapa (ver html-mapa.ts). */
type MensajeMapa =
  | { tipo: 'listo' }
  | { tipo: 'movido'; lat: number; lng: number; radio: number }
  | { tipo: 'cancha'; id: number | null }
  | { tipo: 'error'; mensaje: string };

/**
 * Los tipos de react-native-webview aún no están al día con React Native 0.88 y dicen que su
 * ref es una View normal. En realidad es el WebView, que tiene injectJavaScript; lo indicamos aquí.
 */
type WebViewConInyeccion = { injectJavaScript: (codigo: string) => void };

type Props = {
  ref?: Ref<ControlMapa>;
  latitudInicial: number;
  longitudInicial: number;
  zoomInicial: number;
  /** El usuario ha movido el mapa: centro y radio visible en metros. */
  onMovido: (latitud: number, longitud: number, radio: number) => void;
  /** Han tocado una chincheta (id) o el mapa vacío (null). */
  onCanchaPulsada: (id: number | null) => void;
  onError: (mensaje: string) => void;
};

/**
 * Mapa de canchas. Por dentro es un WebView (un navegador metido en la app) con MapLibre.
 *
 * Con `ref` la pantalla obtiene un ControlMapa para mover el mapa o pintar canchas;
 * cada método se traduce a una llamada JavaScript dentro de la página.
 */
export function Mapa({
  ref,
  latitudInicial,
  longitudInicial,
  zoomInicial,
  onMovido,
  onCanchaPulsada,
  onError,
}: Props) {
  const webView = useRef<WebViewConInyeccion | null>(null);
  // La página tarda un poco en cargar; lo que se pida antes se guarda aquí y se envía al estar lista.
  const listo = useRef(false);
  const pendientes = useRef<string[]>([]);

  // El HTML se crea una sola vez: si cambiara, el WebView recargaría el mapa entero.
  const [html] = useState(() => crearHtmlMapa(latitudInicial, longitudInicial, zoomInicial));

  function ejecutar(codigo: string) {
    // El "; true" final lo pide react-native-webview para no dar avisos en iOS.
    const js = `${codigo}; true;`;
    if (listo.current) webView.current?.injectJavaScript(js);
    else pendientes.current.push(js);
  }

  useImperativeHandle(ref, () => ({
    mostrarCanchas: (canchas) => ejecutar(`window.pachanga.canchas(${JSON.stringify(canchas)})`),
    centrar: (latitud, longitud, zoom) =>
      ejecutar(`window.pachanga.centrar(${latitud}, ${longitud}, ${zoom ?? 'null'})`),
    encuadrar: (canchas) => ejecutar(`window.pachanga.encuadrar(${JSON.stringify(canchas)})`),
    mostrarMiUbicacion: (latitud, longitud) => ejecutar(`window.pachanga.yo(${latitud}, ${longitud})`),
    elegir: (id) => ejecutar(`window.pachanga.elegir(${id ?? 'null'})`),
  }));

  function recibirMensaje(evento: WebViewMessageEvent) {
    const mensaje = JSON.parse(evento.nativeEvent.data) as MensajeMapa;
    switch (mensaje.tipo) {
      case 'listo':
        listo.current = true;
        pendientes.current.forEach((js) => webView.current?.injectJavaScript(js));
        pendientes.current = [];
        break;
      case 'movido':
        onMovido(mensaje.lat, mensaje.lng, mensaje.radio);
        break;
      case 'cancha':
        onCanchaPulsada(mensaje.id);
        break;
      case 'error':
        onError(mensaje.mensaje);
        break;
    }
  }

  return (
    <WebView
      ref={(elemento) => {
        webView.current = elemento as unknown as WebViewConInyeccion | null;
      }}
      source={{ html }}
      originWhitelist={['*']}
      onMessage={recibirMensaje}
      onError={() => onError('No se pudo cargar el mapa. Revisa tu conexión.')}
      style={styles.mapa}
      // El mapa ya gestiona sus gestos: que el WebView no haga scroll ni "rebote".
      scrollEnabled={false}
      bounces={false}
      overScrollMode="never"
      setBuiltInZoomControls={false}
      webviewDebuggingEnabled={__DEV__}
    />
  );
}

const styles = StyleSheet.create({
  mapa: {
    flex: 1,
    backgroundColor: '#e8e4dc',
  },
});
