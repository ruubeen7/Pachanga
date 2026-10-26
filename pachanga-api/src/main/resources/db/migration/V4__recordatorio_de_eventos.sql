-- =====================================================================
-- V4: recordatorio de eventos.
-- Una hora antes de que empiece un evento se avisa a los apuntados. Esta columna
-- apunta si ya se ha enviado, para no mandar el mismo recordatorio dos veces.
-- =====================================================================

ALTER TABLE evento ADD COLUMN recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE;
