-- -----------------------------------------------------------------------------
-- V4: Secuencia persistente de numeración de vales por UEB (CU-10).
-- Sustituye al contador en memoria que se perdía al cerrar la aplicación.
-- Los números nunca se reutilizan: la secuencia solo avanza.
-- -----------------------------------------------------------------------------

CREATE TABLE vale_secuencia (
    id_ueb UUID PRIMARY KEY REFERENCES ueb (id_ueb),
    secuencia BIGINT NOT NULL
);
