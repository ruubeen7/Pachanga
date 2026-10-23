import * as SecureStore from 'expo-secure-store';

import type { Sesion } from '@/api/auth';

/**
 * Guarda la sesión en el almacenamiento seguro del móvil (Keychain en iPhone,
 * Keystore en Android). Así, al cerrar y volver a abrir la app, sigues dentro.
 */
const CLAVE = 'pachanga.sesion';

export async function guardarSesion(sesion: Sesion): Promise<void> {
  await SecureStore.setItemAsync(CLAVE, JSON.stringify(sesion));
}

export async function leerSesion(): Promise<Sesion | null> {
  try {
    const texto = await SecureStore.getItemAsync(CLAVE);
    return texto ? (JSON.parse(texto) as Sesion) : null;
  } catch {
    return null; // si algo está corrupto, hacemos como si no hubiera sesión
  }
}

export async function borrarSesion(): Promise<void> {
  await SecureStore.deleteItemAsync(CLAVE);
}
