import { Link } from 'expo-router';
import { useState } from 'react';

import { ErrorApi } from '@/api/cliente';
import { Boton } from '@/components/boton';
import { CampoTexto } from '@/components/campo-texto';
import { PantallaFormulario } from '@/components/pantalla-formulario';
import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';

export default function LoginScreen() {
  const { iniciarSesion } = useSesion();
  const tema = useTheme();

  // Cada campo del formulario es un estado: al escribir, se actualiza y React repinta.
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});

  async function entrar() {
    setEnviando(true);
    setError(null);
    setErrores({});
    try {
      await iniciarSesion(email, password);
      // No hace falta navegar: al haber sesión, el layout raíz cambia solo a las pestañas.
    } catch (e) {
      if (e instanceof ErrorApi) {
        setError(e.message);
        setErrores(e.campos);
      } else {
        setError('Algo ha fallado. Inténtalo de nuevo.');
      }
      setEnviando(false);
    }
  }

  return (
    <PantallaFormulario titulo="Pachanga" subtitulo="Inicia sesión para encontrar partido">
      <CampoTexto
        etiqueta="Email"
        value={email}
        onChangeText={setEmail}
        error={errores.email}
        placeholder="tu@email.com"
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        textContentType="emailAddress"
      />
      <CampoTexto
        etiqueta="Contraseña"
        value={password}
        onChangeText={setPassword}
        error={errores.password}
        placeholder="Tu contraseña"
        secureTextEntry
        autoComplete="current-password"
        textContentType="password"
        onSubmitEditing={entrar}
      />

      {error ? <ThemedText style={{ color: tema.error }}>{error}</ThemedText> : null}

      <Boton texto="Entrar" onPress={entrar} cargando={enviando} />

      {/* replace: cambia de pantalla sin dejar el login "debajo" para volver atrás */}
      <Link href="/registro" replace asChild>
        <ThemedText type="linkPrimary" style={{ textAlign: 'center', color: tema.primario }}>
          ¿No tienes cuenta? Regístrate
        </ThemedText>
      </Link>
    </PantallaFormulario>
  );
}
