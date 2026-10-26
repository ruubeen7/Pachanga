-- =====================================================================
-- V3: chat de los eventos.
-- Cada evento tiene su conversación; solo pueden leerla y escribir los apuntados.
-- =====================================================================

CREATE TABLE mensaje (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_id  BIGINT       NOT NULL REFERENCES evento (id) ON DELETE CASCADE,  -- si se borra el evento, su chat también
    autor_id   BIGINT       NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    texto      VARCHAR(500) NOT NULL CHECK (length(trim(texto)) > 0),          -- nada de mensajes vacíos
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- La app pide "los mensajes de este evento con id mayor que X": este índice hace esa consulta rápida.
CREATE INDEX idx_mensaje_evento ON mensaje (evento_id, id);

-- Los mensajes también se pueden denunciar. Un CHECK no se puede editar: se borra y se vuelve a crear.
ALTER TABLE denuncia DROP CONSTRAINT denuncia_tipo_contenido_check;
ALTER TABLE denuncia ADD CONSTRAINT denuncia_tipo_contenido_check
    CHECK (tipo_contenido IN ('CANCHA', 'FOTO', 'EVENTO', 'USUARIO', 'MENSAJE'));
