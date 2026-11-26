-- CU-07: trazabilidad, cobertura y aclaraciones del consolidado.
ALTER TABLE parte_unidad_asociada
    ADD COLUMN id_ueb UUID REFERENCES ueb (id_ueb);
CREATE INDEX IF NOT EXISTS idx_parte_unidad_ueb ON parte_unidad_asociada (id_ueb, fecha_produccion);

ALTER TABLE consolidado_ueb
    ADD COLUMN total_huevos NUMERIC(18,3) NOT NULL DEFAULT 0,
    ADD COLUMN total_huevos_rotos NUMERIC(18,3) NOT NULL DEFAULT 0,
    ADD COLUMN total_gallinas_vivas BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN total_gallinas_muertas BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN total_gallinas_enfermas BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN total_pienso_kg NUMERIC(18,3) NOT NULL DEFAULT 0,
    ADD COLUMN total_carne NUMERIC(18,3) NOT NULL DEFAULT 0,
    ADD COLUMN partes_nave_incluidos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN partes_nave_excluidos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN partes_unidad_incluidos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN partes_unidad_excluidos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN registros_carne_incluidos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN naves_sin_parte INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN unidades_sin_parte INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN aclaraciones_pendientes INTEGER NOT NULL DEFAULT 0;

CREATE TABLE consolidado_parte_nave (
    id_vinculo UUID PRIMARY KEY,
    id_consolidado UUID NOT NULL REFERENCES consolidado_ueb (id_consolidado) ON DELETE CASCADE,
    id_parte UUID NOT NULL REFERENCES parte_nave (id_parte),
    CONSTRAINT uq_consolidado_parte_nave UNIQUE (id_consolidado, id_parte)
);
CREATE INDEX idx_consolidado_parte_nave_parte ON consolidado_parte_nave (id_parte);

CREATE TABLE consolidado_parte_unidad (
    id_vinculo UUID PRIMARY KEY,
    id_consolidado UUID NOT NULL REFERENCES consolidado_ueb (id_consolidado) ON DELETE CASCADE,
    id_parte_asociado UUID NOT NULL REFERENCES parte_unidad_asociada (id_parte_asociado),
    CONSTRAINT uq_consolidado_parte_unidad UNIQUE (id_consolidado, id_parte_asociado)
);
CREATE INDEX idx_consolidado_parte_unidad_parte ON consolidado_parte_unidad (id_parte_asociado);

CREATE TABLE consolidado_produccion_carne (
    id_vinculo UUID PRIMARY KEY,
    id_consolidado UUID NOT NULL REFERENCES consolidado_ueb (id_consolidado) ON DELETE CASCADE,
    id_produccion UUID NOT NULL REFERENCES produccion_carne (id_produccion),
    CONSTRAINT uq_consolidado_produccion_carne UNIQUE (id_consolidado, id_produccion)
);
CREATE INDEX idx_consolidado_produccion_carne_produccion ON consolidado_produccion_carne (id_produccion);

CREATE TABLE aclaracion_operativa (
    id_aclaracion UUID PRIMARY KEY,
    tipo_origen VARCHAR(40) NOT NULL,
    id_origen UUID NOT NULL,
    motivo TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    id_usuario UUID REFERENCES usuario (id_usuario),
    fecha_solicitud TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_resolucion TIMESTAMP,
    observacion_resolucion TEXT,
    CONSTRAINT ck_aclaracion_tipo CHECK (tipo_origen IN ('PARTE_NAVE', 'PARTE_UNIDAD', 'PRODUCCION_CARNE')),
    CONSTRAINT ck_aclaracion_estado CHECK (estado IN ('PENDIENTE', 'RESUELTA'))
);
CREATE INDEX idx_aclaracion_origen ON aclaracion_operativa (tipo_origen, id_origen, estado);

ALTER TABLE aclaracion_operativa
    ADD COLUMN id_ueb_origen UUID REFERENCES ueb (id_ueb),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN sincronizado_en TIMESTAMP,
    ADD COLUMN id_lote_sincronizacion UUID,
    ADD COLUMN eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE consolidado_parte_nave
    ADD COLUMN id_ueb_origen UUID REFERENCES ueb (id_ueb),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN sincronizado_en TIMESTAMP,
    ADD COLUMN id_lote_sincronizacion UUID,
    ADD COLUMN eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE consolidado_parte_unidad
    ADD COLUMN id_ueb_origen UUID REFERENCES ueb (id_ueb),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN sincronizado_en TIMESTAMP,
    ADD COLUMN id_lote_sincronizacion UUID,
    ADD COLUMN eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE consolidado_produccion_carne
    ADD COLUMN id_ueb_origen UUID REFERENCES ueb (id_ueb),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN sincronizado_en TIMESTAMP,
    ADD COLUMN id_lote_sincronizacion UUID,
    ADD COLUMN eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_aclaracion_sync_origen ON aclaracion_operativa (id_ueb_origen, actualizado_en);
CREATE INDEX idx_consolidado_parte_nave_sync_origen ON consolidado_parte_nave (id_ueb_origen, actualizado_en);
CREATE INDEX idx_consolidado_parte_unidad_sync_origen ON consolidado_parte_unidad (id_ueb_origen, actualizado_en);
CREATE INDEX idx_consolidado_produccion_carne_sync_origen ON consolidado_produccion_carne (id_ueb_origen, actualizado_en);

CREATE TRIGGER trg_aclaracion_actualizado BEFORE UPDATE ON aclaracion_operativa
    FOR EACH ROW EXECUTE FUNCTION pican_touch_actualizado_en();
CREATE TRIGGER trg_consolidado_parte_nave_actualizado BEFORE UPDATE ON consolidado_parte_nave
    FOR EACH ROW EXECUTE FUNCTION pican_touch_actualizado_en();
CREATE TRIGGER trg_consolidado_parte_unidad_actualizado BEFORE UPDATE ON consolidado_parte_unidad
    FOR EACH ROW EXECUTE FUNCTION pican_touch_actualizado_en();
CREATE TRIGGER trg_consolidado_produccion_carne_actualizado BEFORE UPDATE ON consolidado_produccion_carne
    FOR EACH ROW EXECUTE FUNCTION pican_touch_actualizado_en();
