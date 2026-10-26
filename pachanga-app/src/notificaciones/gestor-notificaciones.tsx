import { router, type Href } from 'expo-router';
import { useEffect } from 'react';

import { guardarPushToken } from '@/api/auth';
import { useSesion } from '@/sesion/sesion-context';

import { Notificaciones, pedirTokenDeNotificaciones, rutaValida } from './notificaciones';

/**
 * Componente invisible (no pinta nada) que se encarga de las notificaciones mientras hay sesión:
 * 1. Al entrar, pide el token del móvil y se lo manda a la API.
 * 2. Si tocas una notificación, abre la pantalla que dice (la ficha del evento o el chat).
 */
export function GestorNotificaciones() {
  const { token } = useSesion();

  // 1. Registrar el móvil. Si algo falla, no molestamos al usuario: solo se queda sin avisos.
  useEffect(() => {
    if (!token) return;
    pedirTokenDeNotificaciones()
      .then((pushToken) => (pushToken ? guardarPushToken(token, pushToken) : null))
      .catch(() => {});
  }, [token]);

  // 2. Solo si la librería está disponible (no lo está en Expo Go para Android).
  //    Va en otro componente porque los hooks no se pueden llamar dentro de un "if".
  return Notificaciones ? <AlTocarNotificacion notificaciones={Notificaciones} /> : null;
}

/** Abre la pantalla de la última notificación tocada (también la que abrió la app si estaba cerrada). */
function AlTocarNotificacion({ notificaciones }: { notificaciones: typeof import('expo-notifications') }) {
  const ultimaTocada = notificaciones.useLastNotificationResponse();

  useEffect(() => {
    if (!ultimaTocada || ultimaTocada.actionIdentifier !== notificaciones.DEFAULT_ACTION_IDENTIFIER) return;
    const ruta = ultimaTocada.notification.request.content.data?.ruta;
    if (rutaValida(ruta)) router.push(ruta as Href);
    // La borramos para no volver a abrirla si este componente se vuelve a montar.
    notificaciones.clearLastNotificationResponse();
  }, [ultimaTocada, notificaciones]);

  return null;
}
