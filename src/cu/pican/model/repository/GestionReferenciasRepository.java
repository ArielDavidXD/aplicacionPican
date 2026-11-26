package cu.pican.model.repository;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;

/**
 * Escritura restringida a CU-17 sobre las tablas oficiales: permite registrar
 * nuevos valores oficiales y corregir los existentes (alta y edición).
 * No sustituye a los repositorios de solo lectura usados por análisis e informes.
 */
public interface GestionReferenciasRepository {

    /** True si ya existe un indicador con la misma tabla+indicador+año, excluyendo el id indicado en ediciones. */
    boolean existeIndicador(TablaHistorica tabla, String indicador, int anio, String idExcluido);

    /** True si ya existe una meta con ese indicador, excluyendo el id indicado en ediciones. */
    boolean existeMeta(String indicador, String idExcluido);

    /** Inserta o actualiza por id (upsert). Devuelve el registro tal como quedó guardado. */
    IndicadorHistorico guardarIndicador(IndicadorHistorico indicador);

    /** Inserta o actualiza por id (upsert). Devuelve el registro tal como quedó guardado. */
    MetaEstrategica guardarMeta(MetaEstrategica meta);
}
