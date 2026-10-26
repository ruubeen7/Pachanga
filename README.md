# 🏀 Pachanga

**Encuentra cancha, encuentra partido.**

App móvil para encontrar canchas de baloncesto en España y organizar pachangas y entrenamientos con otros jugadores.

> 🚧 **En desarrollo.** Proyecto de fin de grado de 2º de DAM (Desarrollo de Aplicaciones Multiplataforma), curso 2026-2027.

## ¿Qué hará?

- 🗺️ **Mapa de canchas** cercanas y búsqueda por ciudad.
- 📸 **Añadir canchas** con fotos y ubicación. Antes de crearla se avisa si ya existe una a menos de ~50 m, para evitar duplicados.
- 🧭 **Cómo llegar**: abre Google Maps o Apple Maps con la ubicación de la cancha.
- 📅 **Eventos**: crea una pachanga o un entreno (fecha, hora, plazas, nivel) y apúntate a los de otros.
- 👤 **Perfil** con tus eventos, notificaciones y herramientas para denunciar contenido o bloquear usuarios.

## Estado actual

- [x] Base de datos, backend y app conectados y probados en un móvil real.
- [x] Diseño de la base de datos y modelo de datos ([ver diseño](docs/base-de-datos.md)).
- [x] Registro e inicio de sesión (con token JWT y contraseñas cifradas).
- [x] Mapa con las canchas cercanas, tu ubicación y búsqueda por ciudad.
- [x] Crear canchas (con aviso de duplicados a menos de 50 m y fotos) y ficha de cancha.
- [x] Eventos (pachangas y entrenos): organizarlos, apuntarse, desapuntarse, cancelarlos y "mis eventos" en el perfil.
- [x] Denunciar contenido y bloquear usuarios.
- [x] Chat en cada evento para los apuntados.
- [ ] Notificaciones push.

## Tecnologías

| Parte | Tecnología |
|---|---|
| App móvil | React Native + Expo, TypeScript, Expo Router |
| Mapas | MapLibre GL JS + OpenFreeMap (datos de OpenStreetMap) |
| Backend (API REST) | Java 21, Spring Boot, Spring Data JPA |
| Base de datos | PostgreSQL + PostGIS (consultas por distancia) |
| Entorno local | Docker |

La app y el backend están separados y se comunican mediante una API REST. Toda la lógica de negocio está en el backend; la app solo muestra datos y envía acciones.

## Estructura

```
pachanga/
├── pachanga-api/        Backend (Spring Boot)
├── pachanga-app/        App móvil (Expo)
└── docker-compose.yml   Base de datos local
```

## Cómo arrancarlo en local

Necesitas Docker Desktop, Java 21, Node.js y la app **Expo Go** en el móvil.

1. **Base de datos** (en la raíz):
   ```bash
   docker compose up -d
   ```
2. **Backend**:
   ```bash
   cd pachanga-api
   ./mvnw spring-boot:run
   ```
   Comprueba que funciona en http://localhost:8080/api/salud
3. **App**:
   ```bash
   cd pachanga-app
   npm install
   npx expo start
   ```
   Escanea el QR con Expo Go (el móvil y el PC en la misma WiFi).

## Autor

**Rubén Fernández Castillo**: Escuelas Salesianas María Auxiliadora Nervión (Sevilla).
