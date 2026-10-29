import { peticion } from './cliente';

/** Una sugerencia del buscador de direcciones (DireccionSugerencia.java). */
export type DireccionSugerencia = {
  titulo: string; // "Calle Feria, 25" o "Parque de María Luisa"
  detalle: string; // "Sevilla, Andalucía"
  direccion: string | null; // lo que se guarda como dirección de la cancha
  ciudad: string | null;
  nombreSugerido: string | null; // solo si es un parque o una pista: se propone como nombre de la cancha
  latitud: number;
  longitud: number;
};

/** Con menos letras el servidor no busca (DireccionService.MINIMO_LETRAS). */
export const MINIMO_LETRAS = 3;

/**
 * Busca direcciones o sitios por texto. Si le pasamos dónde está el usuario,
 * salen primero los resultados cercanos.
 */
export function buscarDirecciones(
  token: string,
  texto: string,
  cerca?: { latitud: number; longitud: number } | null,
) {
  let ruta = `/api/direcciones?texto=${encodeURIComponent(texto)}`;
  if (cerca) ruta += `&lat=${cerca.latitud}&lng=${cerca.longitud}`;
  return peticion<DireccionSugerencia[]>(ruta, { token });
}
