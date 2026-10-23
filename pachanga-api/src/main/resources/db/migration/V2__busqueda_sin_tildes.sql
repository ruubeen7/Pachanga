-- =====================================================================
-- V2: búsqueda por ciudad sin tener en cuenta las tildes.
--
-- "unaccent" es una extensión oficial de PostgreSQL (viene incluida, también en Supabase)
-- que quita las tildes: unaccent('Málaga') = 'Malaga'.
-- Así quien escribe "malaga" encuentra las canchas de "Málaga".
-- La usa CanchaRepository.buscarPorCiudad.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS unaccent;
