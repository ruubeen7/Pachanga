import { useSyncExternalStore } from 'react';
import { useColorScheme as useRNColorScheme } from 'react-native';

// No hay nada a lo que suscribirse: solo queremos saber si estamos en el navegador.
const sinSuscripcion = () => () => {};

/**
 * Solo para la versión web: al generar la página en el servidor no se sabe
 * si el usuario tiene modo oscuro, así que ahí devolvemos 'light'
 * y en el navegador ya devolvemos el valor real.
 */
export function useColorScheme() {
  const enNavegador = useSyncExternalStore(
    sinSuscripcion,
    () => true, // en el navegador
    () => false, // en el servidor
  );

  const colorScheme = useRNColorScheme();

  return enNavegador ? colorScheme : 'light';
}
