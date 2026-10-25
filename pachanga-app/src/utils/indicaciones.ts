import { Linking, Platform } from 'react-native';

/**
 * Abre la app de mapas del móvil con la ruta hasta un punto.
 * iPhone → Apple Maps; Android → Google Maps (la app, no su SDK: no cuesta nada).
 */
export function abrirIndicaciones(latitud: number, longitud: number) {
  const destino = `${latitud},${longitud}`;
  const url =
    Platform.OS === 'ios'
      ? `https://maps.apple.com/?daddr=${destino}`
      : `https://www.google.com/maps/dir/?api=1&destination=${destino}`;
  Linking.openURL(url);
}
