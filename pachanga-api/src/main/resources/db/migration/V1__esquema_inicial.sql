-- =====================================================================
-- Pachanga - Esquema inicial de la base de datos
-- Flyway ejecuta este script una sola vez. Para cambiar algo más adelante
-- NO se edita este archivo: se crea uno nuevo (V2__..., V3__...).
-- =====================================================================

-- PostGIS añade tipos y funciones geográficas (puntos, distancias en metros...).
CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------
-- USUARIO: las personas registradas en la app.
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- como AUTO_INCREMENT en MySQL
    email         VARCHAR(255) NOT NULL UNIQUE,                    -- se guarda siempre en minúsculas
    nombre        VARCHAR(50)  NOT NULL,                           -- nombre visible en la app
    password_hash VARCHAR(100) NOT NULL,                           -- nunca la contraseña en claro, solo su hash (BCrypt)
    foto_url      VARCHAR(500),
    nivel         VARCHAR(20)  CHECK (nivel IN ('INICIACION', 'MEDIO', 'AVANZADO')),
    rol           VARCHAR(20)  NOT NULL DEFAULT 'USUARIO' CHECK (rol IN ('USUARIO', 'ADMIN')),
    push_token    VARCHAR(255),                                    -- para enviar notificaciones al móvil
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- CANCHA: una pista de baloncesto con su ubicación.
-- ---------------------------------------------------------------------
CREATE TABLE cancha (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre       VARCHAR(100)  NOT NULL,
    descripcion  VARCHAR(1000),
    -- Punto (longitud, latitud). "geography" hace que las distancias se calculen en metros.
    ubicacion    GEOGRAPHY(POINT, 4326) NOT NULL,
    direccion    VARCHAR(255),
    ciudad       VARCHAR(100)  NOT NULL,
    superficie   VARCHAR(20)   CHECK (superficie IN ('CEMENTO', 'ASFALTO', 'PARQUET', 'SINTETICO', 'OTRA')),
    num_canastas INTEGER       CHECK (num_canastas BETWEEN 1 AND 20),
    cubierta     BOOLEAN       NOT NULL DEFAULT FALSE,
    iluminacion  BOOLEAN       NOT NULL DEFAULT FALSE,
    gratuita     BOOLEAN       NOT NULL DEFAULT TRUE,
    creada_por   BIGINT        REFERENCES usuario (id) ON DELETE SET NULL, -- si se borra el usuario, la cancha se queda
    creado_en    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Índice espacial (GIST): hace rápidas las búsquedas "canchas a menos de X metros".
CREATE INDEX idx_cancha_ubicacion ON cancha USING GIST (ubicacion);
-- Para buscar por ciudad sin importar mayúsculas.
CREATE INDEX idx_cancha_ciudad ON cancha (lower(ciudad));

-- ---------------------------------------------------------------------
-- FOTO_CANCHA: fotos de cada cancha. La imagen está en Supabase Storage;
-- aquí solo guardamos su dirección (URL).
-- ---------------------------------------------------------------------
CREATE TABLE foto_cancha (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cancha_id   BIGINT       NOT NULL REFERENCES cancha (id) ON DELETE CASCADE, -- si se borra la cancha, sus fotos también
    url         VARCHAR(500) NOT NULL,
    subida_por  BIGINT       REFERENCES usuario (id) ON DELETE SET NULL,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_foto_cancha_cancha ON foto_cancha (cancha_id);

-- ---------------------------------------------------------------------
-- EVENTO: una pachanga o un entreno en una cancha, con día y hora.
-- ---------------------------------------------------------------------
CREATE TABLE evento (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cancha_id       BIGINT        NOT NULL REFERENCES cancha (id) ON DELETE CASCADE,
    organizador_id  BIGINT        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo            VARCHAR(20)   NOT NULL CHECK (tipo IN ('PACHANGA', 'ENTRENO')),
    titulo          VARCHAR(100)  NOT NULL,
    descripcion     VARCHAR(1000),
    inicio          TIMESTAMPTZ   NOT NULL,
    fin             TIMESTAMPTZ   NOT NULL,
    plazas          INTEGER       NOT NULL CHECK (plazas BETWEEN 2 AND 50), -- incluye al organizador
    nivel           VARCHAR(20)   NOT NULL DEFAULT 'TODOS'
                                  CHECK (nivel IN ('TODOS', 'INICIACION', 'MEDIO', 'AVANZADO')),
    estado          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'CANCELADO')),
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_evento_horas CHECK (fin > inicio)
);

-- Para sacar rápido "próximos eventos de esta cancha" y "eventos que organizo".
CREATE INDEX idx_evento_cancha_inicio ON evento (cancha_id, inicio);
CREATE INDEX idx_evento_organizador ON evento (organizador_id);

-- ---------------------------------------------------------------------
-- INSCRIPCION: qué usuario se ha apuntado a qué evento.
-- Es la tabla intermedia de la relación N:M entre usuario y evento.
-- ---------------------------------------------------------------------
CREATE TABLE inscripcion (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_id   BIGINT      NOT NULL REFERENCES evento (id) ON DELETE CASCADE,
    usuario_id  BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_inscripcion UNIQUE (evento_id, usuario_id) -- nadie se apunta dos veces al mismo evento
);

CREATE INDEX idx_inscripcion_usuario ON inscripcion (usuario_id);

-- ---------------------------------------------------------------------
-- DENUNCIA: un usuario avisa de contenido inapropiado.
-- tipo_contenido + contenido_id dicen QUÉ se denuncia (una cancha, una foto,
-- un evento o un usuario). No lleva clave foránea porque puede apuntar a tablas distintas.
-- ---------------------------------------------------------------------
CREATE TABLE denuncia (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    denunciante_id  BIGINT       NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo_contenido  VARCHAR(20)  NOT NULL CHECK (tipo_contenido IN ('CANCHA', 'FOTO', 'EVENTO', 'USUARIO')),
    contenido_id    BIGINT       NOT NULL,
    motivo          VARCHAR(20)  NOT NULL CHECK (motivo IN ('SPAM', 'INAPROPIADO', 'FALSO', 'OTRO')),
    comentario      VARCHAR(500),
    estado          VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'REVISADA', 'DESCARTADA')),
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_denuncia UNIQUE (denunciante_id, tipo_contenido, contenido_id) -- no denunciar lo mismo dos veces
);

-- ---------------------------------------------------------------------
-- BLOQUEO: un usuario deja de ver a otro (y el otro a él).
-- ---------------------------------------------------------------------
CREATE TABLE bloqueo (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bloqueador_id   BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    bloqueado_id    BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_bloqueo UNIQUE (bloqueador_id, bloqueado_id),
    CONSTRAINT ck_bloqueo_no_a_si_mismo CHECK (bloqueador_id <> bloqueado_id)
);

CREATE INDEX idx_bloqueo_bloqueado ON bloqueo (bloqueado_id);
