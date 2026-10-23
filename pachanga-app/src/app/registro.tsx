import { Link } from 'expo-router';
import { useState } from 'react';

import { ErrorApi } from '@/api/cliente';
import { Boton } from '@/components/boton';
import { CampoTexto } from '@/components/campo-texto';
import { PantallaFormulario } from '@/components/pantalla-formulario';
import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import { useSesion } from '@/sesion/sesion-context';

export default function RegistroScreen() {
  const { registrarse } = useSesion();
  const tema = useTheme();

  const [nombre, setNombre] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});

  async function crearCuenta() {
    setEnviando(true);
    setError(null);
    setErrores({});
    try {
      await registrarse(email, nombre, password);
      // Al registrarse ya hay sesión: el layout raíz pasa solo a las pestañas.
    } catch (e) {
      if (e instanceof ErrorApi) {
        // Los errores de cada campo (400) salen debajo de su campo; el resto, abajo del todo.
        setErrores(e.campos);
        if (Object.keys(e.campos).length === 0) setError(e.message);
      } else {
        setError('Algo ha fallado. Inténtalo de nuevo.');
      }
      setEnviando(false);
    }
  }

  return (
    <PantallaFormulario titulo="Crea tu cuenta" subtitulo="Y empieza a organizar pachangas">
      <CampoTexto
        etiqueta="Nombre"
        value={nombre}
        onChangeText={setNombre}
        error={errores.nombre}
        placeholder="Cómo te verán los demás"
        autoComplete="name"
        textContentType="name"
        maxLength={50}
      />
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
        placeholder="Mínimo 8 caracteres"
        secureTextEntry
        autoComplete="new-password"
        textContentType="newPassword"
        onSubmitEditing={crearCuenta}
      />

      {error ? <ThemedText style={{ color: tema.error }}>{error}</ThemedText> : null}

      <Boton texto="Crear cuenta" onPress={crearCuenta} cargando={enviando} />

      <Link href="/login" replace asChild>
        <ThemedText type="linkPrimary" style={{ textAlign: 'center', color: tema.primario }}>
          ¿Ya tienes cuenta? Inicia sesión
        </ThemedText>
      </Link>
    </PantallaFormulario>
  );
}
