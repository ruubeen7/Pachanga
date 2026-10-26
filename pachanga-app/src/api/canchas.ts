import { File } from 'expo-file-system';

import { peticion } from './cliente';
import { API_URL } from './config';
import type { EventoResumen } from './eventos';

/** Tipo de suelo (Superficie.java). */
export type Superficie = 'CEMENTO' | 'ASFALTO' | 'PARQUET' | 'SINTETICO' | 'OTRA';

/** Textos para enseñar cada superficie en pantalla. */
export const NOMBRES_SUPERFICIE: Record<Superficie, string> = {
  CEMENTO: 'Cemento',
  ASFALTO: 'Asfalto',
  PARQUET: 'Parquet',
  SINTETICO: 'Sintético',
  OTRA: 'Otra',
};

/** Lo que devuelve la API de cada cancha en el mapa (CanchaResumen.java). */
export type CanchaResumen = {
  id: number;
  nombre: string;
  ciudad: string;
  direccion: string | null;
  latitud: number;
  longitud: number;
  distanciaMetros: number | null; // solo en las búsquedas por distancia
};

export type FotoCancha = { id: number; url: string };

/** La ficha completa (CanchaDetalle.java). */
export type CanchaDetalle = {
  id: number;
  nombre: string;
  descripcion: string | null;
  direccion: string | null;
  ciudad: string;
  latitud: number;
  longitud: number;
  superficie: Superficie | null;
  numCanastas: number | null;
  cubierta: boolean;
  iluminacion: boolean;
  gratuita: boolean;
  creadaPor: string | null;
  fotos: FotoCancha[];
  proximosEventos: EventoResumen[];
};

/** Lo que se manda para crear una cancha (CrearCanchaPeticion.java). */
export type NuevaCancha = {
  nombre: string;
  ciudad: string;
  latitud: number;
  longitud: number;
  direccion?: string;
  descripcion?: string;
  superficie?: Superficie | null;
  numCanastas?: number | null;
  cubierta: boolean;
  iluminacion: boolean;
  gratuita: boolean;
};

/** Canchas a menos de `radio` metros de un punto, la más cercana primero. */
export function canchasCercanas(token: string, latitud: number, longitud: number, radio: number) {
  // URLSearchParams monta "lat=37.38&lng=-5.97&radio=5000" escapando lo que haga falta.
  const parametros = new URLSearchParams({
    lat: String(latitud),
    lng: String(longitud),
    radio: String(Math.round(radio)),
  });
  return peticion<CanchaResumen[]>(`/api/canchas/cercanas?${parametros}`, { token });
}

/** Canchas de una ciudad ("sevilla" encuentra "Sevilla"). */
export function canchasDeCiudad(token: string, ciudad: string) {
  const parametros = new URLSearchParams({ ciudad });
  return peticion<CanchaResumen[]>(`/api/canchas?${parametros}`, { token });
}

/** Canchas que ya existen a menos de 50 m (para preguntar "¿es esta?" antes de crear otra). */
export function posiblesDuplicados(token: string, latitud: number, longitud: number) {
  const parametros = new URLSearchParams({ lat: String(latitud), lng: String(longitud) });
  return peticion<CanchaResumen[]>(`/api/canchas/posibles-duplicados?${parametros}`, { token });
}

export function obtenerCancha(token: string, id: number) {
  return peticion<CanchaDetalle>(`/api/canchas/${id}`, { token });
}

export function crearCancha(token: string, datos: NuevaCancha) {
  return peticion<CanchaDetalle>('/api/canchas', { metodo: 'POST', cuerpo: datos, token });
}

/**
 * Sube una foto (un archivo del móvil, ya reducida) a una cancha.
 * Va como formulario con archivo ("multipart"), en el campo "foto" que espera la API.
 */
export function subirFoto(token: string, canchaId: number, uriArchivo: string) {
  const formulario = new FormData();
  // La ruta tiene que empezar por "file://" para que se encuentre el archivo.
  const uri = uriArchivo.includes('://') ? uriArchivo : `file://${uriArchivo}`;
  // Desde el SDK 54, el fetch de Expo solo acepta archivos "de verdad" (que sepan dar sus bytes),
  // no el antiguo { uri, name, type } de React Native. File de expo-file-system es uno de ellos:
  // representa el archivo del móvil, y fetch lo lee y lo manda. Su nombre y tipo salen de la ruta (.jpg).
  formulario.append('foto', new File(uri));
  return peticion<FotoCancha>(`/api/canchas/${canchaId}/fotos`, {
    metodo: 'POST',
    cuerpo: formulario,
    token,
  });
}

/**
 * Las fotos guardadas en local vienen como "/fotos/abc.jpg" (relativas a la API);
 * las de Supabase vendrán ya con "https://...". Esto devuelve siempre una URL completa.
 */
export function urlDeFoto(url: string) {
  return url.startsWith('/') ? `${API_URL}${url}` : url;
}
