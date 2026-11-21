-- Etapa 4.15 · Control persistente de sincronización UEB ↔ Dirección de Producción.
-- V1 no se modifica: esta migración solo añade tablas nuevas y es compatible con bases que ya aplicaron V1.

CREATE TABLE IF NOT EXISTS sync_paquete (
    id_paquete UUID PRIMARY KEY,
    tipo VARCHAR(60) NOT NULL,
    id_ueb_origen UUID,
    id_destino VARCHAR(160) NOT NULL,
    generado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_protocolo VARCHAR(20) NOT NULL,
    version_esquema VARCHAR(20) NOT NULL,
    id_lote UUID NOT NULL,
    cantidad_registros INTEGER NOT NULL DEFAULT 0,
    hash_integridad VARCHAR(64),
    estado VARCHAR(30) NOT NULL DEFAULT 'GENERADO',
    procesado_en TIMESTAMP,
    mensaje TEXT,
    id_usuario UUID,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_item (
    id_item UUID PRIMARY KEY,
    id_paquete UUID NOT NULL REFERENCES sync_paquete(id_paquete),
    tabla VARCHAR(80) NOT NULL,
    id_registro VARCHAR(80) NOT NULL,
    accion VARCHAR(20) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    detalle TEXT,
    version BIGINT,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sync_item UNIQUE (id_paquete, tabla, id_registro)
);

CREATE TABLE IF NOT EXISTS sync_conflicto (
    id_conflicto UUID PRIMARY KEY,
    tabla VARCHAR(80) NOT NULL,
    id_registro VARCHAR(80) NOT NULL,
    id_paquete UUID,
    version_recibida BIGINT,
    version_local BIGINT,
    payload_recibido TEXT,
    payload_local TEXT,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    detectado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_exportacion (
    tabla VARCHAR(80) NOT NULL,
    id_registro VARCHAR(80) NOT NULL,
    version_confirmada BIGINT NOT NULL DEFAULT 0,
    confirmado_en TIMESTAMP,
    id_lote UUID,
    PRIMARY KEY (tabla, id_registro)
);

CREATE INDEX IF NOT EXISTS idx_sync_paquete_estado ON sync_paquete (estado, generado_en);
CREATE INDEX IF NOT EXISTS idx_sync_item_registro ON sync_item (tabla, id_registro);
CREATE INDEX IF NOT EXISTS idx_sync_conflicto_pendiente ON sync_conflicto (estado, detectado_en);
