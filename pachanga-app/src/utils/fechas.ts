/**
 * Formatos de fecha en español. La API manda las fechas en UTC ("2026-11-01T17:00:00Z");
 * new Date() las entiende y toLocale...String las enseña en la hora del móvil
 * (así un evento a las 18:00 en Sevilla se ve a las 17:00 en Canarias, que es correcto).
 */

/** "sáb, 1 nov" */
export function dia(fecha: Date) {
  return fecha.toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short' });
}

/** "sábado, 1 de noviembre" */
export function diaLargo(fecha: Date) {
  return fecha.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
}

/** "18:00" */
export function hora(fecha: Date) {
  return fecha.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
}

/** Minutos desde medianoche → "18:30". */
export function minutosAHora(minutos: number) {
  const h = Math.floor(minutos / 60);
  const m = minutos % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/** 90 → "1 h 30 min" */
export function duracion(minutos: number) {
  const h = Math.floor(minutos / 60);
  const m = minutos % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

/** Hoy a las 00:00 (hora del móvil). */
export function hoyAMedianoche() {
  const ahora = new Date();
  return new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
}

/** El mismo día que `base` más `dias` días (a medianoche). */
export function sumarDias(base: Date, dias: number) {
  return new Date(base.getFullYear(), base.getMonth(), base.getDate() + dias);
}
