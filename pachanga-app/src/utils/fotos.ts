import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';

/** Lado más largo de la foto después de reducirla. Suficiente para verla bien en el móvil. */
const LADO_MAXIMO = 1600;

/**
 * Abre la galería para elegir hasta `maximo` fotos y las devuelve ya reducidas
 * (las del móvil pueden ocupar 5 MB; reducidas, unos 300 KB). Así se suben antes
 * y no gastamos el almacenamiento gratuito.
 *
 * Devuelve las rutas (uri) de los archivos reducidos, o [] si el usuario cancela.
 */
export async function elegirFotos(maximo: number): Promise<string[]> {
  if (maximo <= 0) return [];

  // En iPhone y Android modernos la galería no pide permiso: el usuario solo comparte lo que elige.
  const resultado = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ['images'],
    allowsMultipleSelection: maximo > 1,
    selectionLimit: maximo,
    quality: 1, // la calidad la ajustamos nosotros al reducir
  });
  if (resultado.canceled) return [];

  const reducidas: string[] = [];
  for (const foto of resultado.assets.slice(0, maximo)) {
    reducidas.push(await reducir(foto));
  }
  return reducidas;
}

/** Reduce la foto a LADO_MAXIMO píxeles por su lado más largo y la guarda como JPEG. */
async function reducir(foto: ImagePicker.ImagePickerAsset): Promise<string> {
  const contexto = ImageManipulator.manipulate(foto.uri);
  if (Math.max(foto.width, foto.height) > LADO_MAXIMO) {
    // Si solo damos un lado, el otro se calcula para no deformar la foto.
    contexto.resize(foto.width >= foto.height ? { width: LADO_MAXIMO } : { height: LADO_MAXIMO });
  }
  const imagen = await contexto.renderAsync();
  const guardada = await imagen.saveAsync({ compress: 0.7, format: SaveFormat.JPEG });
  return guardada.uri;
}
