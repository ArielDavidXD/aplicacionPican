-- CU-05/CU-06: los borradores pueden estar incompletos; el registro oficial no.
ALTER TABLE parte_nave
    ALTER COLUMN gallinas_vivas DROP NOT NULL,
    ALTER COLUMN gallinas_muertas DROP NOT NULL,
    ALTER COLUMN gallinas_enfermas DROP NOT NULL,
    ALTER COLUMN huevos_producidos DROP NOT NULL,
    ALTER COLUMN huevos_rotos DROP NOT NULL,
    ADD COLUMN advertencia_revisada BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE parte_nave
    ADD CONSTRAINT ck_parte_nave_registro_completo CHECK (
        estado IS DISTINCT FROM 'REGISTRADO' OR (
            gallinas_vivas IS NOT NULL AND gallinas_muertas IS NOT NULL
            AND gallinas_enfermas IS NOT NULL AND huevos_producidos IS NOT NULL
            AND huevos_rotos IS NOT NULL
        )
    ) NOT VALID;

ALTER TABLE parte_nave
    ADD CONSTRAINT ck_parte_nave_cantidades_no_negativas CHECK (
        (gallinas_vivas IS NULL OR gallinas_vivas >= 0)
        AND (gallinas_muertas IS NULL OR gallinas_muertas >= 0)
        AND (gallinas_enfermas IS NULL OR gallinas_enfermas >= 0)
        AND (huevos_producidos IS NULL OR huevos_producidos >= 0)
        AND (huevos_rotos IS NULL OR huevos_rotos >= 0)
        AND (pienso_kg IS NULL OR pienso_kg >= 0)
    ) NOT VALID;

ALTER TABLE parte_nave
    ADD CONSTRAINT ck_parte_nave_coherencia CHECK (
        (huevos_producidos IS NULL OR huevos_rotos IS NULL OR huevos_rotos <= huevos_producidos)
        AND (gallinas_vivas IS NULL OR gallinas_muertas IS NULL OR gallinas_muertas <= gallinas_vivas)
        AND (gallinas_vivas IS NULL OR gallinas_enfermas IS NULL OR gallinas_enfermas <= gallinas_vivas)
    ) NOT VALID;

ALTER TABLE parte_nave
    ADD CONSTRAINT ck_parte_nave_advertencia_revisada CHECK (
        estado IS DISTINCT FROM 'REGISTRADO'
        OR gallinas_vivas IS NULL OR huevos_producidos IS NULL
        OR huevos_producidos <= gallinas_vivas OR advertencia_revisada = TRUE
    ) NOT VALID;

ALTER TABLE produccion_carne
    ADD CONSTRAINT ck_produccion_carne_positiva CHECK (cantidad_toneladas > 0) NOT VALID;
