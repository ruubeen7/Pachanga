# Pachanga (TFG 2º DAM)

App móvil para encontrar canchas de baloncesto en España y organizar pachangas y entrenamientos.
Autor: Rubén Fernández Castillo. Entrega: mediados de abril de 2027 (mayo de colchón).
Propuesta (Google Doc): https://docs.google.com/document/d/1OZGATrf0cciCzewEIOLxGtIglwV6LVDvWbsPH2xYKdY/edit

## Funcionalidad (primera versión)
- Registro e inicio de sesión.
- Mapa con canchas cercanas y búsqueda por ciudad.
- Crear cancha con fotos y ubicación. Antes de crearla se buscan canchas a menos de ~50 m y se pregunta "¿es esta?" para evitar duplicados.
- Ficha de cancha: fotos, eventos próximos, botón "Cómo llegar" (abre Google Maps / Apple Maps con las coordenadas).
- Eventos: pachanga o entreno, fecha, hora inicio y fin, plazas, nivel. Apuntarse y desapuntarse.
- Perfil con mis eventos. Denunciar contenido y bloquear usuarios. Notificaciones push.

## Estructura
- `pachanga-api/`: backend. Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL + PostGIS. Más adelante Spring Security con JWT e hibernate-spatial.
- `pachanga-app/`: app móvil. React Native + Expo + TypeScript. Mapas con MapLibre GL JS dentro de un WebView + OpenFreeMap (datos de OpenStreetMap).
- `docker-compose.yml`: PostgreSQL 17 + PostGIS en local, **puerto 5433** (el 5432 lo ocupa un PostgreSQL de Odoo instalado en Windows; no tocarlo).

## Reglas del proyecto
- **Coste 0 €** durante el TFG y sin tarjeta en ningún servicio. Nada de Google Maps SDK, Google Play ni hosting de pago.
  Servicios previstos: Supabase gratis (BD + fotos), Render gratis (backend), EAS Build gratis (APK), Expo Push.
- Toda la lógica de negocio va en el backend; la app solo muestra datos y envía acciones (así el plan B, Android nativo en Java, solo rehace la app).
- Rubén domina Java y MySQL; es nuevo en TypeScript, React y Spring Boot. Explicar cada pieza nueva con palabras sencillas y en español.
- Textos de la app, comentarios y mensajes en español.

## Notas
- Expo SDK 58 con Expo Router (pantallas en `pachanga-app/src/app/`). Leer `pachanga-app/AGENTS.md` antes de tocar APIs de Expo.
- `npx expo lint` sale limpio. `npx tsc --noEmit` da 1 error en `app-tabs.web.tsx`: viene de la plantilla de Expo y solo afecta a la versión web, que no usamos.

## Cómo arrancar en local
1. Base de datos: `docker compose up -d` (en la raíz).
2. Backend: abrir `pachanga-api` en IntelliJ y ejecutar `PachangaApiApplication`, o `mvnw.cmd spring-boot:run`. Prueba: http://localhost:8080/api/salud
3. App: en `pachanga-app`, `npx expo start` y escanear el QR con Expo Go (móvil y PC en la misma WiFi).

## Base de datos
- Esquema con Flyway: `pachanga-api/src/main/resources/db/migration/V1__esquema_inicial.sql`. **Nunca editar un script ya aplicado**; cada cambio va en un `V2__...`, `V3__...` nuevo. Hibernate está en `ddl-auto=validate`.
- Entidades en `com.pachanga.api.modelo`, repositorios en `com.pachanga.api.repositorio`. Ids `BIGINT IDENTITY`, fechas `Instant`/`timestamptz` (UTC), enums como texto con `CHECK`.
- Ubicación de canchas: `geography(Point,4326)` con JTS `Point` (longitud, latitud). Búsqueda por distancia en `CanchaRepository.buscarCercanas`.
- Diseño y diagrama E-R: `docs/base-de-datos.md`.
- Los esquemas `tiger` y `topology` de la BD local vienen de la imagen de PostGIS; ignorarlos.

## Sesión (registro y login)
- Backend: Spring Security + `spring-boot-starter-oauth2-resource-server` (JWT HS256 firmado con `pachanga.jwt.secreto`; en producción variable `JWT_SECRETO`). Config en `config/SeguridadConfig`. Rutas públicas: `/api/salud`, `/api/auth/**`; el resto pide `Authorization: Bearer <token>`. El `sub` del token es el id del usuario (`@AuthenticationPrincipal Jwt`).
- Errores de la API siempre como `{ mensaje, campos }` (`error/ManejadorErrores`). Lanzar `ExcepcionApi(HttpStatus, mensaje)` para errores esperados.
- Capas: `controller` → `servicio` → `repositorio`; entrada/salida JSON con records en `dto` (nunca devolver entidades).
- App: `src/api/cliente.ts` (`peticion<T>()` y `ErrorApi`), sesión en `src/sesion/` (Context + expo-secure-store). `app/_layout.tsx` usa `Stack.Protected`: con sesión se ve `(tabs)`, sin sesión `login` y `registro`.
- Si `tsc` se queja de rutas nuevas (`"/ruta" is not assignable...`), es que `.expo/types/router.d.ts` está desactualizado: se regenera al hacer `npx expo start`.

## Mapa de canchas
- API: `GET /api/canchas/cercanas?lat&lng&radio` (radio en metros, por defecto 10 km, máximo 50 km) y `GET /api/canchas?ciudad=` (sin mayúsculas ni tildes, con la extensión `unaccent` de V2). Devuelven `CanchaResumen`.
- Perfil `dev` por defecto (`spring.profiles.active=${PERFIL:dev}`): `config/DatosDePrueba` mete 10 canchas de ejemplo si la tabla está vacía. En producción, `PERFIL=prod`.
- Los tests usan `@ActiveProfiles("test")` y datos en un punto inventado del Atlántico (30, -30) para no chocar con lo que haya en la BD.
- App: el mapa es MapLibre GL JS dentro de un `react-native-webview` (`src/components/mapa/`), porque MapLibre nativo no está en Expo Go y compilar para iPhone exige cuenta de Apple de pago. Estilo de OpenFreeMap (gratis, sin clave). La app habla con la página con `injectJavaScript` (`window.pachanga.*`) y la página responde con `postMessage` (`listo`, `movido`, `cancha`, `error`).
- La pantalla (`app/(tabs)/index.tsx`) pide canchas cada vez que el mapa se para (con espera de 400 ms). "Cómo llegar" abre Apple Maps (iOS) o Google Maps (Android) por URL, sin SDK.
