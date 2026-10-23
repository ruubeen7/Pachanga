import { peticion } from './cliente';

/** Lo que devuelve la API de cada cancha en el mapa (CanchaResumen.java). */
export type CanchaResumen = {
  id: number;
  nombre: string;
  ciudad: string;
  direccion: string | null;
  latitud: number;
  longitud: number;
  distanciaMetros: number | null; // solo en la búsqueda "cercanas"
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
