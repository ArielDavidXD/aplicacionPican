-- PICAN · ETAPA 4.14 · Esquema lógico único para bases locales y central
-- El mismo script se ejecuta en una base vacía de UEB o en la central.
-- No contiene datos DEMO ni inventa las 28 unidades asociadas.

CREATE OR REPLACE FUNCTION pican_touch_actualizado_en() RETURNS trigger AS $$
BEGIN
    NEW.actualizado_en = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- -----------------------------------------------------------------------------
-- Segmentación organizativa y catálogos operativos (CU-17)
-- -----------------------------------------------------------------------------

CREATE TABLE ueb (
    id_ueb UUID PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_ueb_codigo ON ueb (lower(btrim(codigo)));

CREATE TABLE nave (
    id_nave UUID PRIMARY KEY,
    id_ueb UUID NOT NULL REFERENCES ueb (id_ueb),
    nombre VARCHAR(200) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_nave_codigo ON nave (lower(btrim(codigo)));
CREATE INDEX idx_nave_ueb ON nave (id_ueb);

CREATE TABLE unidad_asociada (
    id_unidad UUID PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_unidad_asociada_codigo ON unidad_asociada (lower(btrim(codigo)));

CREATE TABLE producto (
    id_producto UUID PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    unidad_medida VARCHAR(60),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_producto_codigo ON producto (lower(btrim(codigo)));

CREATE TABLE destino (
    id_destino UUID PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_destino_codigo ON destino (lower(btrim(codigo)));

CREATE TABLE producto_destino (
    id_relacion UUID PRIMARY KEY,
    id_producto UUID NOT NULL REFERENCES producto (id_producto),
    id_destino UUID NOT NULL REFERENCES destino (id_destino),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_producto_destino UNIQUE (id_producto, id_destino)
);
CREATE INDEX idx_producto_destino_producto ON producto_destino (id_producto);
CREATE INDEX idx_producto_destino_destino ON producto_destino (id_destino);

-- -----------------------------------------------------------------------------
-- Seguridad (CU-16). Las contraseñas se guardan solo como hash PBKDF2 codificado.
-- -----------------------------------------------------------------------------

CREATE TABLE rol (
    id_rol UUID PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    descripcion VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_rol_nombre ON rol (lower(btrim(nombre)));

CREATE TABLE usuario (
    id_usuario UUID PRIMARY KEY,
    nombre_usuario VARCHAR(120) NOT NULL,
    nombre_completo VARCHAR(200) NOT NULL,
    password_hash VARCHAR(500) NOT NULL,
    id_rol UUID NOT NULL REFERENCES rol (id_rol),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    cambio_contrasena_requerido BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_usuario_nombre ON usuario (lower(btrim(nombre_usuario)));

INSERT INTO rol (id_rol, nombre, descripcion) VALUES
    ('fdee8845-ed80-3042-8ad8-441341980265', 'ADMINISTRADOR',
     'Gestiona usuarios, roles y catálogos; no sustituye al directivo externo.'),
    ('7cefba4d-49a3-3482-9744-b7f7f5ecdea6', 'JEFE_DE_UEB',
     'Registra partes, producción, consolidaciones, vales, recepciones e incidencias de su UEB.'),
    ('5f01fad0-c0ae-3e97-8154-93be97da914b', 'ESPECIALISTA_DE_PRODUCCION',
     'Consulta referencias oficiales, metas e infraestructura y apoya el registro de producción.'),
    ('0c72fb1c-2e6e-38f2-bacc-ac68fdb098bc', 'ESPECIALISTA_DE_ESTADISTICA',
     'Analiza resultados y gestiona informes; registra la decisión del directivo externo.')
ON CONFLICT (id_rol) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Operaciones
-- -----------------------------------------------------------------------------

CREATE TABLE parte_nave (
    id_parte UUID PRIMARY KEY,
    id_nave UUID NOT NULL REFERENCES nave (id_nave),
    fecha_produccion DATE NOT NULL,
    gallinas_vivas INTEGER NOT NULL,
    gallinas_muertas INTEGER NOT NULL,
    gallinas_enfermas INTEGER NOT NULL,
    huevos_producidos INTEGER NOT NULL,
    huevos_rotos INTEGER NOT NULL,
    pienso_kg NUMERIC(18,3),
    observaciones TEXT,
    estado VARCHAR(60),
    id_usuario UUID REFERENCES usuario (id_usuario),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_parte_nave_nave ON parte_nave (id_nave);
CREATE INDEX idx_parte_nave_fecha ON parte_nave (fecha_produccion);

CREATE TABLE parte_unidad_asociada (
    id_parte_asociado UUID PRIMARY KEY,
    id_unidad UUID NOT NULL REFERENCES unidad_asociada (id_unidad),
    fecha_produccion DATE NOT NULL,
    referencia_papel VARCHAR(120),
    observaciones TEXT,
    estado VARCHAR(60),
    id_usuario UUID REFERENCES usuario (id_usuario),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_parte_unidad_unidad ON parte_unidad_asociada (id_unidad);
CREATE INDEX idx_parte_unidad_fecha ON parte_unidad_asociada (fecha_produccion);

CREATE TABLE produccion_carne (
    id_produccion UUID PRIMARY KEY,
    id_ueb UUID NOT NULL REFERENCES ueb (id_ueb),
    fecha_produccion DATE NOT NULL,
    cantidad_toneladas NUMERIC(18,3) NOT NULL,
    observaciones TEXT,
    id_usuario UUID REFERENCES usuario (id_usuario),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_produccion_carne_ueb ON produccion_carne (id_ueb);
CREATE INDEX idx_produccion_carne_fecha ON produccion_carne (fecha_produccion);

CREATE TABLE consolidado_ueb (
    id_consolidado UUID PRIMARY KEY,
    id_ueb UUID NOT NULL REFERENCES ueb (id_ueb),
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    estado VARCHAR(60),
    motivo_parcial TEXT,
    id_usuario UUID REFERENCES usuario (id_usuario),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_consolidado_ueb ON consolidado_ueb (id_ueb);

CREATE TABLE vale_entrega (
    id_vale UUID PRIMARY KEY,
    numero_vale VARCHAR(80) NOT NULL,
    id_ueb UUID NOT NULL REFERENCES ueb (id_ueb),
    id_destino UUID NOT NULL REFERENCES destino (id_destino),
    id_producto UUID NOT NULL REFERENCES producto (id_producto),
    cantidad_despachada NUMERIC(18,3) NOT NULL,
    unidad_medida VARCHAR(60) NOT NULL,
    fecha_hora_emision TIMESTAMP NOT NULL,
    estado VARCHAR(60) NOT NULL,
    id_usuario UUID REFERENCES usuario (id_usuario),
    motivo_anulacion TEXT,
    fecha_hora_anulacion TIMESTAMP,
    id_usuario_anulacion UUID REFERENCES usuario (id_usuario),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_vale_numero UNIQUE (numero_vale)
);
CREATE INDEX idx_vale_ueb ON vale_entrega (id_ueb);
CREATE INDEX idx_vale_destino ON vale_entrega (id_destino);
CREATE INDEX idx_vale_producto ON vale_entrega (id_producto);
CREATE INDEX idx_vale_fecha ON vale_entrega (fecha_hora_emision);
CREATE INDEX idx_vale_estado ON vale_entrega (estado);

CREATE TABLE recepcion (
    id_recepcion UUID PRIMARY KEY,
    id_vale UUID NOT NULL REFERENCES vale_entrega (id_vale),
    fecha_hora_recepcion TIMESTAMP NOT NULL,
    cantidad_recibida NUMERIC(18,3) NOT NULL,
    unidad_medida VARCHAR(60) NOT NULL,
    resultado VARCHAR(40) NOT NULL,
    observaciones TEXT,
    id_usuario UUID REFERENCES usuario (id_usuario),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_recepcion_vale UNIQUE (id_vale)
);
CREATE INDEX idx_recepcion_fecha ON recepcion (fecha_hora_recepcion);

CREATE TABLE incidencia (
    id_incidencia UUID PRIMARY KEY,
    id_vale UUID NOT NULL REFERENCES vale_entrega (id_vale),
    id_recepcion UUID NOT NULL REFERENCES recepcion (id_recepcion),
    tipo VARCHAR(40),
    cantidad_afectada NUMERIC(18,3),
    descripcion TEXT,
    medida_acordada TEXT,
    fecha_compromiso TIMESTAMP,
    estado VARCHAR(40) NOT NULL,
    observaciones TEXT NOT NULL,
    fecha_hora_registro TIMESTAMP NOT NULL,
    id_usuario UUID REFERENCES usuario (id_usuario),
    fecha_cierre TIMESTAMP,
    resultado_solucion TEXT,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_incidencia_vale ON incidencia (id_vale);
CREATE INDEX idx_incidencia_recepcion ON incidencia (id_recepcion);
CREATE INDEX idx_incidencia_estado ON incidencia (estado);

-- -----------------------------------------------------------------------------
-- Referencias históricas oficiales (solo lectura desde los servicios, CU-14)
-- -----------------------------------------------------------------------------

CREATE TABLE indicador_historico (
    id VARCHAR(120) PRIMARY KEY,
    tabla VARCHAR(120) NOT NULL,
    indicador VARCHAR(300) NOT NULL,
    anio INTEGER NOT NULL,
    valor NUMERIC(18,4) NOT NULL,
    unidad_medida VARCHAR(120) NOT NULL,
    fuente VARCHAR(500) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_indicador_tabla ON indicador_historico (tabla);
CREATE INDEX idx_indicador_anio ON indicador_historico (anio);
CREATE INDEX idx_indicador_nombre ON indicador_historico (indicador);

CREATE TABLE meta_estrategica (
    id VARCHAR(120) PRIMARY KEY,
    indicador VARCHAR(300) NOT NULL,
    valor_real_2019 NUMERIC(18,4) NOT NULL,
    valor_estimado_2030 NUMERIC(18,4) NOT NULL,
    incremento_porcentual NUMERIC(10,4) NOT NULL,
    unidad_medida VARCHAR(120) NOT NULL,
    fuente VARCHAR(500) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_meta_indicador ON meta_estrategica (indicador);

-- Resultados de análisis: tablas de apoyo para futuras consultas sincronizables.
-- El UUID técnico no existe en el modelo de dominio y no se expone en la interfaz.
CREATE TABLE registro_analisis (
    id_registro UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha DATE,
    elemento VARCHAR(300),
    fuente VARCHAR(300) NOT NULL,
    cantidad NUMERIC(18,3),
    unidad_medida VARCHAR(120) NOT NULL,
    observacion VARCHAR(1000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_registro_analisis_fecha ON registro_analisis (fecha);

CREATE TABLE comparacion_meta (
    id_comparacion UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    indicador VARCHAR(300) NOT NULL,
    valor_historico_2019 NUMERIC(18,4),
    valor_meta_2030 NUMERIC(18,4),
    incremento_porcentual_aprobado NUMERIC(10,4),
    unidad_historica VARCHAR(120),
    produccion_operativa NUMERIC(18,3),
    unidad_operativa VARCHAR(120),
    periodo_operativo VARCHAR(120),
    aclaracion VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);

-- -----------------------------------------------------------------------------
-- Informes y sus relaciones (CU-15)
-- -----------------------------------------------------------------------------

CREATE TABLE informe (
    id_informe UUID PRIMARY KEY,
    id_informe_raiz UUID NOT NULL REFERENCES informe (id_informe),
    id_informe_anterior UUID REFERENCES informe (id_informe),
    numero_informe VARCHAR(80),
    titulo VARCHAR(300) NOT NULL,
    periodo_desde DATE NOT NULL,
    periodo_hasta DATE NOT NULL,
    fecha_elaboracion DATE NOT NULL,
    especialista_responsable VARCHAR(200) NOT NULL,
    id_usuario UUID REFERENCES usuario (id_usuario),
    contenido_resumen TEXT NOT NULL,
    estado VARCHAR(40) NOT NULL,
    numero_revision INTEGER NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_informe_estado ON informe (estado);
CREATE INDEX idx_informe_raiz ON informe (id_informe_raiz);
CREATE INDEX idx_informe_fecha ON informe (creado_en);

CREATE TABLE fuente_informe (
    id_fuente UUID PRIMARY KEY,
    id_informe UUID NOT NULL REFERENCES informe (id_informe),
    tipo VARCHAR(80) NOT NULL,
    id_registro_origen VARCHAR(200) NOT NULL,
    descripcion VARCHAR(500) NOT NULL,
    fuente VARCHAR(500) NOT NULL,
    detalle_consulta TEXT,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_fuente_informe UNIQUE (id_informe, tipo, id_registro_origen)
);
CREATE INDEX idx_fuente_informe_informe ON fuente_informe (id_informe);

CREATE TABLE decision_directivo (
    id_decision UUID PRIMARY KEY,
    id_informe UUID NOT NULL REFERENCES informe (id_informe),
    nombre_directivo VARCHAR(200) NOT NULL,
    decision VARCHAR(40) NOT NULL,
    fecha_decision DATE NOT NULL,
    observacion TEXT,
    fecha_registro TIMESTAMP NOT NULL,
    numero_decision INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_decision_numero UNIQUE (id_informe, numero_decision)
);
CREATE INDEX idx_decision_informe ON decision_directivo (id_informe);

CREATE TABLE destinatario_institucional (
    codigo VARCHAR(40) PRIMARY KEY,
    descripcion VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE entrega_informe (
    id_entrega UUID PRIMARY KEY,
    id_informe UUID NOT NULL REFERENCES informe (id_informe),
    destinatario VARCHAR(40) NOT NULL REFERENCES destinatario_institucional (codigo),
    fecha_entrega DATE NOT NULL,
    responsable_registro VARCHAR(200) NOT NULL,
    observaciones TEXT,
    fecha_registro TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_ueb_origen UUID,
    sincronizado_en TIMESTAMP,
    id_lote_sincronizacion UUID,
    eliminado_logico BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_entrega_destinatario UNIQUE (id_informe, destinatario)
);
CREATE INDEX idx_entrega_informe ON entrega_informe (id_informe);

INSERT INTO destinatario_institucional (codigo, descripcion) VALUES
    ('GEGAN', 'Dirección de Ganadería y Geometría Agrícola'),
    ('MINAG', 'Ministerio de la Agricultura')
ON CONFLICT (codigo) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Metadatos técnicos de sincronización futura (USB, no implementada en esta etapa)
-- actualizado_en se mantiene por disparador; los demás campos quedan nulos hasta esa etapa.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    tabla TEXT;
BEGIN
    FOREACH tabla IN ARRAY ARRAY[
        'ueb', 'nave', 'unidad_asociada', 'producto', 'destino', 'producto_destino',
        'rol', 'usuario', 'parte_nave', 'parte_unidad_asociada', 'produccion_carne',
        'consolidado_ueb', 'vale_entrega', 'recepcion', 'incidencia',
        'indicador_historico', 'meta_estrategica', 'registro_analisis', 'comparacion_meta',
        'informe', 'fuente_informe', 'decision_directivo',
        'destinatario_institucional', 'entrega_informe'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%I_actualizado BEFORE UPDATE ON %I '
            'FOR EACH ROW EXECUTE FUNCTION pican_touch_actualizado_en()', tabla, tabla);
        EXECUTE format('CREATE INDEX idx_%I_sync_origen ON %I (id_ueb_origen, actualizado_en)', tabla, tabla);
        EXECUTE format('CREATE INDEX idx_%I_sync_lote ON %I (id_lote_sincronizacion)', tabla, tabla);
    END LOOP;
END $$;
