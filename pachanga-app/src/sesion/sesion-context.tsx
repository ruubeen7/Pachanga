import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import * as api from '@/api/auth';
import { ErrorApi } from '@/api/cliente';

import { borrarSesion, guardarSesion, leerSesion } from './almacen';

/**
 * CONTEXTO DE SESIÓN
 *
 * En React, los datos pasan de padres a hijos. Si muchas pantallas necesitan saber quién
 * ha iniciado sesión, en vez de pasarlo pantalla a pantalla lo ponemos en un "Context":
 * una especie de variable global a la que cualquier pantalla accede con useSesion().
 */

type EstadoSesion =
  | { tipo: 'cargando' } // al abrir la app, mientras leemos si había sesión guardada
  | { tipo: 'sin-sesion' }
  | { tipo: 'con-sesion'; sesion: api.Sesion };

type ValorSesion = {
  estado: EstadoSesion;
  /** Atajos: el usuario y el token si hay sesión, o null si no. */
  usuario: api.Usuario | null;
  token: string | null;
  iniciarSesion: (email: string, password: string) => Promise<void>;
  registrarse: (email: string, nombre: string, password: string) => Promise<void>;
  cerrarSesion: () => Promise<void>;
};

const SesionContext = createContext<ValorSesion | null>(null);

export function SesionProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<EstadoSesion>({ tipo: 'cargando' });

  // Al abrir la app: ¿había una sesión guardada?
  useEffect(() => {
    restaurarSesion().then(setEstado);
  }, []);

  async function entrar(sesion: api.Sesion) {
    await guardarSesion(sesion);
    setEstado({ tipo: 'con-sesion', sesion });
  }

  const valor: ValorSesion = {
    estado,
    usuario: estado.tipo === 'con-sesion' ? estado.sesion.usuario : null,
    token: estado.tipo === 'con-sesion' ? estado.sesion.token : null,
    iniciarSesion: async (email, password) => entrar(await api.iniciarSesion({ email, password })),
    registrarse: async (email, nombre, password) =>
      entrar(await api.registrar({ email, nombre, password })),
    cerrarSesion: async () => {
      await borrarSesion();
      setEstado({ tipo: 'sin-sesion' });
    },
  };

  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>;
}

/** Lo usan las pantallas: const { usuario, cerrarSesion } = useSesion(); */
export function useSesion(): ValorSesion {
  const valor = useContext(SesionContext);
  if (!valor) throw new Error('useSesion debe usarse dentro de <SesionProvider>');
  return valor;
}

/**
 * Lee la sesión guardada y pregunta a la API si el token sigue valiendo.
 * - Si la API dice 401 (token caducado o usuario borrado): se borra y hay que volver a entrar.
 * - Si no hay conexión: seguimos con los datos guardados, para no echar al usuario por un mal WiFi.
 */
async function restaurarSesion(): Promise<EstadoSesion> {
  const guardada = await leerSesion();
  if (!guardada) return { tipo: 'sin-sesion' };

  try {
    const usuario = await api.obtenerUsuarioActual(guardada.token);
    const actualizada = { ...guardada, usuario };
    await guardarSesion(actualizada);
    return { tipo: 'con-sesion', sesion: actualizada };
  } catch (e) {
    if (e instanceof ErrorApi && e.estado === 401) {
      await borrarSesion();
      return { tipo: 'sin-sesion' };
    }
    return { tipo: 'con-sesion', sesion: guardada };
  }
}
