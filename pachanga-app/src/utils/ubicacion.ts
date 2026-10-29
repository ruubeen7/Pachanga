import * as Location from 'expo-location';

export type Coordenadas = { latitud: number; longitud: number };

/**
 * Pide permiso y devuelve dónde está el usuario, o null si no da permiso o no se puede saber.
 * Si el móvil sabe dónde estábamos hace menos de 2 minutos, usa eso (es instantáneo);
 * si no, pide una posición nueva (tarda un poco más).
 */
export async function obtenerUbicacion(): Promise<Coordenadas | null> {
  try {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') return null;
    const posicion =
      (await Location.getLastKnownPositionAsync({ maxAge: 2 * 60 * 1000 })) ??
      (await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }));
    return { latitud: posicion.coords.latitude, longitud: posicion.coords.longitude };
  } catch {
    return null;
  }
}

/** 350 → "a 350 m"; 1234 → "a 1,2 km"; 25000 → "a 25 km". */
export function textoDistancia(metros: number) {
  if (metros < 1000) return `a ${metros} m`;
  const km = metros / 1000;
  return `a ${km < 10 ? km.toFixed(1).replace('.', ',') : Math.round(km)} km`;
}
