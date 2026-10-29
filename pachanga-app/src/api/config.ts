import Constants from 'expo-constants';

/**
 * Dirección de la API publicada en Render. No es secreta: cualquiera que use la app la ve.
 * Render duerme el servidor gratuito tras un rato sin uso; la primera petición tarda ~1 minuto.
 */
const API_PRODUCCION = 'https://pachanga-api-59o0.onrender.com';

/**
 * Dirección de la API (el backend de Spring Boot).
 *
 * - Desarrollando con Expo Go (__DEV__ es true): la app corre en tu móvil y la API en tu PC.
 *   Expo ya sabe la IP de tu PC en la WiFi (es desde donde descarga la app),
 *   así que la reutilizamos y solo cambiamos el puerto al 8080 de Spring Boot.
 * - En el APK (__DEV__ es false): se usa la API publicada en Render.
 * - EXPO_PUBLIC_API_URL tiene prioridad sobre todo, por si alguna vez quieres probar otra.
 */
function calcularApiUrl(): string {
  const desdeEntorno = process.env.EXPO_PUBLIC_API_URL;
  if (desdeEntorno) return desdeEntorno;

  if (!__DEV__ && API_PRODUCCION) return API_PRODUCCION;

  const hostUri = Constants.expoConfig?.hostUri; // por ejemplo "192.168.1.35:8081"
  const ipDelPc = hostUri?.split(':')[0] ?? 'localhost';
  return `http://${ipDelPc}:8080`;
}

export const API_URL = calcularApiUrl();
