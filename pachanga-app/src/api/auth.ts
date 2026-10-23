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

/** Datos del usuario dueño del token. Sirve para comprobar que la sesión sigue siendo válida. */
export function obtenerUsuarioActual(token: string) {
  return peticion<Usuario>('/api/usuarios/yo', { token });
}
