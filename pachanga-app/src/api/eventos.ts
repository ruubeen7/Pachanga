import type { CanchaResumen } from './canchas';
import { peticion } from './cliente';

export type TipoEvento = 'PACHANGA' | 'ENTRENO';
export type NivelEvento = 'TODOS' | 'INICIACION' | 'MEDIO' | 'AVANZADO';
export type EstadoEvento = 'ACTIVO' | 'CANCELADO';

export const NOMBRES_TIPO: Record<TipoEvento, string> = {
  PACHANGA: '🏀 Pachanga',
  ENTRENO: '🏋️ Entreno',
};

export const NOMBRES_NIVEL: Record<NivelEvento, string> = {
  TODOS: 'Todos los niveles',
  INICIACION: 'Iniciación',
  MEDIO: 'Medio',
  AVANZADO: 'Avanzado',
};

/** Alguien que no eres tú: solo id y nombre (UsuarioPublico.java). */
export type UsuarioPublico = { id: number; nombre: string };

/** Un evento en una lista (EventoResumen.java). Las fechas llegan como texto ISO en UTC. */
export type EventoResumen = {
  id: number;
  tipo: TipoEvento;
  titulo: string;
  inicio: string;
  fin: string;
  plazas: number;
  inscritos: number;
  nivel: NivelEvento;
  estado: EstadoEvento;
  canchaId: number;
  canchaNombre: string;
  organizadorId: number;
};

/** La ficha de un evento (EventoDetalle.java). apuntado y organizo dependen de quién pregunta. */
export type EventoDetalle = {
  id: number;
  tipo: TipoEvento;
  titulo: string;
  descripcion: string | null;
  inicio: string;
  fin: string;
  plazas: number;
  nivel: NivelEvento;
  estado: EstadoEvento;
  cancha: CanchaResumen;
  organizador: UsuarioPublico;
  apuntados: UsuarioPublico[];
  apuntado: boolean;
  organizo: boolean;
};

export type MisEventos = { proximos: EventoResumen[]; pasados: EventoResumen[] };

/** Lo que se manda para organizar un evento (CrearEventoPeticion.java). */
export type NuevoEvento = {
  canchaId: number;
  tipo: TipoEvento;
  titulo: string;
  descripcion?: string;
  inicio: string; // ISO en UTC: new Date(...).toISOString()
  fin: string;
  plazas: number;
  nivel: NivelEvento;
};

export function crearEvento(token: string, datos: NuevoEvento) {
  return peticion<EventoDetalle>('/api/eventos', { metodo: 'POST', cuerpo: datos, token });
}

export function obtenerEvento(token: string, id: number) {
  return peticion<EventoDetalle>(`/api/eventos/${id}`, { token });
}

export function apuntarse(token: string, id: number) {
  return peticion<EventoDetalle>(`/api/eventos/${id}/inscripcion`, { metodo: 'POST', token });
}

export function desapuntarse(token: string, id: number) {
  return peticion<EventoDetalle>(`/api/eventos/${id}/inscripcion`, { metodo: 'DELETE', token });
}

export function cancelarEvento(token: string, id: number) {
  return peticion<EventoDetalle>(`/api/eventos/${id}/cancelar`, { metodo: 'POST', token });
}

export function misEventos(token: string) {
  return peticion<MisEventos>('/api/eventos/mios', { token });
}

