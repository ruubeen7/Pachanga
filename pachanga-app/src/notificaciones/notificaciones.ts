import { isRunningInExpoGo } from 'expo';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { Platform } from 'react-native';

/**
 * NOTIFICACIONES PUSH
 *
 * Cómo funcionan:
 * 1. La app pide permiso y le pide a Expo un "token": la dirección de este móvil para enviarle avisos.
 * 2. La app manda ese token a nuestra API (PUT /api/usuarios/yo/push-token), que lo guarda en el usuario.
 * 3. Cuando pasa algo (alguien se apunta, un mensaje...), la API le pide a Expo que avise a ese token.
 *
 * Limitaciones mientras desarrollamos con Expo Go:
 * - En Android, Expo Go no trae las piezas nativas de expo-notifications (desde el SDK 53):
 *   solo IMPORTAR la librería ya rompe la app. Por eso la cargamos solo donde existe.
 *   En el APK de EAS Build sí estarán.
 * - En iPhone sí funciona, pero hace falta el "projectId" de Expo (se consigue gratis con `npx eas-cli init`).
 * Si falta algo, simplemente no pedimos el token: el resto de la app funciona igual.
 */

/** ¿Este móvil puede usar notificaciones push con la app tal y como se está ejecutando? */
export const pushDisponible = !(Platform.OS === 'android' && isRunningInExpoGo());

/**
 * La librería, o null si no está disponible.
 * Usamos require() en vez de "import" arriba del todo porque un import se ejecuta SIEMPRE al abrir
 * la app; require() dentro de una condición solo se ejecuta si la condición se cumple.
 * "typeof import(...)" le dice a TypeScript qué tipo tiene, para que siga avisando de errores.
 */
export const Notificaciones: typeof import('expo-notifications') | null = pushDisponible
  ? // eslint-disable-next-line @typescript-eslint/no-require-imports
    require('expo-notifications')
  : null;

/** La pantalla que se está viendo ahora (por ejemplo "/chat/7"), para no avisar de lo que ya tienes delante. */
let rutaVisible: string | null = null;

export function marcarRutaVisible(ruta: string | null) {
  rutaVisible = ruta;
}

/**
 * Qué hacer si llega una notificación con la app ABIERTA (si está cerrada, la enseña el sistema).
 * La enseñamos como un aviso normal, salvo que sea del chat que estás leyendo en ese momento.
 */
Notificaciones?.setNotificationHandler({
  handleNotification: async (notificacion) => {
    const ruta = notificacion.request.content.data?.ruta;
    const mostrar = !(typeof ruta === 'string' && ruta === rutaVisible);
    return { shouldShowBanner: mostrar, shouldShowList: mostrar, shouldPlaySound: mostrar, shouldSetBadge: false };
  },
});

/** Pide permiso y devuelve el token de Expo de este móvil, o null si no se puede. */
export async function pedirTokenDeNotificaciones(): Promise<string | null> {
  if (!Notificaciones) return null; // Expo Go en Android: ver arriba
  if (!Device.isDevice) return null; // en un simulador no hay notificaciones push

  if (Platform.OS === 'android') {
    // Android agrupa los avisos en "canales"; hay que crear uno antes de pedir permiso.
    await Notificaciones.setNotificationChannelAsync('default', {
      name: 'Avisos de Pachanga',
      importance: Notificaciones.AndroidImportance.HIGH,
    });
  }

  let { granted } = await Notificaciones.getPermissionsAsync();
  if (!granted) ({ granted } = await Notificaciones.requestPermissionsAsync());
  if (!granted) return null; // ha dicho que no: respetamos su decisión

  const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) {
    console.warn('Notificaciones: falta el projectId de Expo. Ejecuta "npx eas-cli init" en pachanga-app.');
    return null;
  }

  try {
    const { data } = await Notificaciones.getExpoPushTokenAsync({ projectId });
    return data;
  } catch {
    return null; // sin conexión con Expo: se volverá a intentar al abrir la app otra vez
  }
}

/** Solo abrimos rutas que conocemos, por si llegase algo raro en la notificación. */
export function rutaValida(ruta: unknown): ruta is string {
  return typeof ruta === 'string' && /^\/(evento|chat)\/\d+$/.test(ruta);
}
