-- Etapa 4.16 · CU-15: ocho secciones del informe y flujo de estados BORRADOR -> EN_REVISION -> APROBADO/DEVUELTO -> ENTREGADO.
-- Las migraciones V1..V4 no se modifican: solo se añaden columnas y se renombra el estado antiguo.

ALTER TABLE informe ADD COLUMN IF NOT EXISTS seccion_produccion_metas TEXT;
ALTER TABLE informe ADD COLUMN IF NOT EXISTS seccion_entregas TEXT;
ALTER TABLE informe ADD COLUMN IF NOT EXISTS seccion_infraestructura TEXT;
ALTER TABLE informe ADD COLUMN IF NOT EXISTS seccion_calidad_datos TEXT;
ALTER TABLE informe ADD COLUMN IF NOT EXISTS seccion_alertas TEXT;
ALTER TABLE informe ADD COLUMN IF NOT EXISTS seccion_causas_medidas TEXT;

UPDATE informe SET estado = 'BORRADOR' WHERE estado = 'ELABORADO';

-- Control de paquetes de bajada (DN) y configuración de la central.
-- La secuencia es independiente por UEB y por sentido (subida/bajada), como exige el DCUS.

CREATE TABLE IF NOT EXISTS sync_secuencia (
    id_ueb UUID NOT NULL,
    sentido VARCHAR(20) NOT NULL,
    ultima BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_ueb, sentido)
);

CREATE TABLE IF NOT EXISTS sync_configuracion (
    clave VARCHAR(80) PRIMARY KEY,
    valor TEXT NOT NULL,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
