import Constants from 'expo-constants';

/**
 * Dirección de la API (el backend de Spring Boot).
 *
 * Mientras desarrollamos, la app corre en tu móvil y la API en tu PC.
 * Expo ya sabe la IP de tu PC en la WiFi (es desde donde descarga la app),
 * así que la reutilizamos y solo cambiamos el puerto al 8080 de Spring Boot.
 *
 * Cuando subamos la API a internet, bastará con definir EXPO_PUBLIC_API_URL.
 */
function calcularApiUrl(): string {
  const desdeEntorno = process.env.EXPO_PUBLIC_API_URL;
  if (desdeEntorno) return desdeEntorno;

  const hostUri = Constants.expoConfig?.hostUri; // por ejemplo "192.168.1.35:8081"
  const ipDelPc = hostUri?.split(':')[0] ?? 'localhost';
  return `http://${ipDelPc}:8080`;
}

export const API_URL = calcularApiUrl();
