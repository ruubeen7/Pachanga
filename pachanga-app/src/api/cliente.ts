import { API_URL } from './config';

/**
 * Error que devuelve la API, con el mismo formato que ErrorRespuesta.java:
 * { "mensaje": "...", "campos": { "email": "..." } }
 *
 * estado es el código HTTP (400, 401, 409...). Si vale 0, es que ni siquiera se pudo
 * conectar con el servidor (sin internet, backend apagado...).
 */
export class ErrorApi extends Error {
  constructor(
    public readonly estado: number,
    mensaje: string,
    public readonly campos: Record<string, string> = {},
  ) {
    super(mensaje);
  }
}

type Opciones = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  cuerpo?: unknown; // se envía como JSON, salvo si es un FormData (archivos), que va tal cual
  token?: string | null; // si hay sesión, se manda en la cabecera Authorization
};

/**
 * Hace una petición a la API y devuelve el JSON de la respuesta.
 * Todas las llamadas al backend pasan por aquí, así el manejo de errores está en un solo sitio.
 *
 * <T> es un "genérico", como en Java: quien llama dice qué tipo espera recibir.
 * Ejemplo: peticion<Usuario>('/api/usuarios/yo', { token })
 */
export async function peticion<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const { metodo = 'GET', cuerpo, token } = opciones;

  // Con un FormData no ponemos Content-Type: fetch lo pone solo ("multipart/form-data; boundary=...").
  const esFormulario = cuerpo instanceof FormData;

  const cabeceras: Record<string, string> = { Accept: 'application/json' };
  if (cuerpo !== undefined && !esFormulario) cabeceras['Content-Type'] = 'application/json';
  if (token) cabeceras.Authorization = `Bearer ${token}`;

  let respuesta: Response;
  try {
    respuesta = await fetch(`${API_URL}${ruta}`, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : esFormulario ? cuerpo : JSON.stringify(cuerpo),
    });
  } catch {
    throw new ErrorApi(0, 'No se pudo conectar con el servidor. Revisa tu conexión.');
  }

  // Leemos el cuerpo como texto primero porque algunas respuestas vienen vacías (por ejemplo, un 401).
  const texto = await respuesta.text();
  const datos = texto ? JSON.parse(texto) : null;

  if (!respuesta.ok) {
    const mensaje =
      datos?.mensaje ??
      (respuesta.status === 401 ? 'Tu sesión ha caducado' : `Error del servidor (${respuesta.status})`);
    throw new ErrorApi(respuesta.status, mensaje, datos?.campos ?? {});
  }

  return datos as T;
}
