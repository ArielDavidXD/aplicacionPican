-- Etapa 4.18 / CU-19. Migración nueva; V1 y V2 no se modifican.
CREATE TABLE IF NOT EXISTS pican_backup_auditoria (
    id_auditoria UUID PRIMARY KEY,
    id_usuario UUID,
    nombre_usuario VARCHAR(80) NOT NULL,
    operacion VARCHAR(20) NOT NULL CHECK (operacion IN ('CREACION', 'RESTAURACION')),
    archivo VARCHAR(1024) NOT NULL,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resultado VARCHAR(20) NOT NULL CHECK (resultado IN ('EXITOSO', 'FALLIDO', 'CANCELADO')),
    motivo_fallo TEXT,
    tamano_bytes BIGINT,
    sha256 VARCHAR(64),
    version_aplicacion VARCHAR(40) NOT NULL,
    version_esquema VARCHAR(255) NOT NULL,
    id_instalacion VARCHAR(120) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_backup_auditoria_fecha ON pican_backup_auditoria (fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_backup_auditoria_operacion ON pican_backup_auditoria (operacion, resultado);

CREATE TABLE IF NOT EXISTS pican_restauracion_control (
    id_control SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id_control = 1),
    bloqueado BOOLEAN NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    fecha_bloqueo TIMESTAMP,
    id_auditoria UUID,
    id_instalacion VARCHAR(120) NOT NULL
);
