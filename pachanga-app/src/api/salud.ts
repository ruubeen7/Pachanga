import { API_URL } from './config';

/** Lo que devuelve GET /api/salud en el backend (SaludController.java). */
export type Salud = {
  estado: string;
  postgresql: string;
  postgis: string;
};

/** Pregunta a la API si está encendida y conectada a la base de datos. */
export async function comprobarSalud(): Promise<Salud> {
  const respuesta = await fetch(`${API_URL}/api/salud`);
  if (!respuesta.ok) {
    throw new Error(`La API respondió con error ${respuesta.status}`);
  }
  return respuesta.json();
}
