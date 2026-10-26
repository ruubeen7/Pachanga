import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useColorScheme } from 'react-native';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { SesionProvider, useSesion } from '@/sesion/sesion-context';

SplashScreen.preventAutoHideAsync();

/**
 * Layout raíz: envuelve toda la app.
 * SesionProvider va arriba del todo para que cualquier pantalla pueda usar useSesion().
 */
export default function RootLayout() {
  const colorScheme = useColorScheme();
  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <SesionProvider>
        <AnimatedSplashOverlay />
        <Navegacion />
      </SesionProvider>
    </ThemeProvider>
  );
}

/**
 * Decide qué pantallas se pueden ver según haya sesión o no.
 * Stack.Protected: si "guard" es false, esas pantallas no existen y Expo Router
 * lleva al usuario a la primera que sí pueda ver. Por eso, al iniciar sesión pasa solo
 * a las pestañas, y al cerrar sesión vuelve solo al login.
 */
function Navegacion() {
  const { estado } = useSesion();

  // Mientras se lee la sesión guardada no pintamos nada (se sigue viendo la pantalla de carga).
  if (estado.tipo === 'cargando') return null;

  const conSesion = estado.tipo === 'con-sesion';

  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Protected guard={conSesion}>
        <Stack.Screen name="(tabs)" />
        {/* Pantallas que se abren encima de las pestañas, con barra arriba y botón de volver */}
        <Stack.Screen
          name="cancha/[id]"
          options={{ headerShown: true, title: 'Cancha', headerBackTitle: 'Atrás', headerTintColor: '#E8772E' }}
        />
        <Stack.Screen
          name="cancha/nueva"
          options={{
            headerShown: true,
            title: 'Nueva cancha',
            headerBackTitle: 'Atrás',
            headerTintColor: '#E8772E',
          }}
        />
        <Stack.Screen
          name="evento/[id]"
          options={{ headerShown: true, title: 'Evento', headerBackTitle: 'Atrás', headerTintColor: '#E8772E' }}
        />
        <Stack.Screen
          name="evento/nuevo"
          options={{
            headerShown: true,
            title: 'Organizar',
            headerBackTitle: 'Atrás',
            headerTintColor: '#E8772E',
          }}
        />
        <Stack.Screen
          name="chat/[id]"
          options={{ headerShown: true, title: 'Chat', headerBackTitle: 'Atrás', headerTintColor: '#E8772E' }}
        />
      </Stack.Protected>

      <Stack.Protected guard={!conSesion}>
        <Stack.Screen name="login" />
        <Stack.Screen name="registro" />
      </Stack.Protected>
    </Stack>
  );
}
