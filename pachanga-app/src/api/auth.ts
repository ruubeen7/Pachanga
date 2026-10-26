import { peticion } from './cliente';

/** Lo que devuelve la API de un usuario (UsuarioRespuesta.java). */
export type Usuario = {
  id: number;
  email: string;
  nombre: string;
  nivel: 'INICIACION' | 'MEDIO' | 'AVANZADO' | null;
  fotoUrl: string | null;
};

/** Lo que devuelve la API al registrarse o iniciar sesión (SesionRespuesta.java). */
export type Sesion = {
  token: string;
  usuario: Usuario;
};

export function registrar(datos: { email: string; nombre: string; password: string }) {
  return peticion<Sesion>('/api/auth/registro', { metodo: 'POST', cuerpo: datos });
}

export function iniciarSesion(datos: { email: string; password: string }) {
  return peticion<Sesion>('/api/auth/login', { metodo: 'POST', cuerpo: datos });
}

/** Guarda el token de notificaciones de este móvil en el usuario. */
export function guardarPushToken(token: string, pushToken: string) {
  return peticion<null>('/api/usuarios/yo/push-token', { metodo: 'PUT', cuerpo: { token: pushToken }, token });
}

/** Al cerrar sesión: este móvil deja de recibir avisos de esta cuenta. */
export function borrarPushToken(token: string) {
  return peticion<null>('/api/usuarios/yo/push-token', { metodo: 'DELETE', token });
}

/** Datos del usuario dueño del token. Sirve para comprobar que la sesión sigue siendo válida. */
export function obtenerUsuarioActual(token: string) {
  return peticion<Usuario>('/api/usuarios/yo', { token });
}
