import { peticion } from './cliente';
import type { UsuarioPublico } from './eventos';

/** Qué se denuncia (TipoContenido.java). */
export type TipoContenido = 'CANCHA' | 'FOTO' | 'EVENTO' | 'USUARIO';
export type MotivoDenuncia = 'SPAM' | 'INAPROPIADO' | 'FALSO' | 'OTRO';

export const NOMBRES_MOTIVO: Record<MotivoDenuncia, string> = {
  INAPROPIADO: 'Ofensivo o inapropiado',
  FALSO: 'Falso o no existe',
  SPAM: 'Spam o publicidad',
  OTRO: 'Otro motivo',
};

export function denunciar(
  token: string,
  datos: { tipo: TipoContenido; contenidoId: number; motivo: MotivoDenuncia; comentario?: string },
) {
  return peticion<null>('/api/denuncias', { metodo: 'POST', cuerpo: datos, token });
}

export function bloquear(token: string, usuarioId: number) {
  return peticion<null>(`/api/usuarios/${usuarioId}/bloqueo`, { metodo: 'POST', token });
}

export function desbloquear(token: string, usuarioId: number) {
  return peticion<null>(`/api/usuarios/${usuarioId}/bloqueo`, { metodo: 'DELETE', token });
}

export function usuariosBloqueados(token: string) {
  return peticion<UsuarioPublico[]>('/api/usuarios/bloqueados', { token });
}
