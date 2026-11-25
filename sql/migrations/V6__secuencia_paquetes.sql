-- V6: secuencia de los paquetes UP/DN en el historial de sincronización.
-- El manifiesto de UP y DN lleva una secuencia creciente por UEB; se conserva
-- en el historial para que el recibo de una bajada (DN) informe la secuencia UP recibida.
ALTER TABLE sync_paquete ADD COLUMN IF NOT EXISTS secuencia BIGINT;
