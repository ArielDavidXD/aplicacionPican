package cu.pican.model.repository;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;

/**
 * Escritura restringida a CU-18 sobre las tablas oficiales ya existentes.
 * No sustituye a los repositorios de solo lectura usados por análisis.
 */
public interface ImportacionReferenciasRepository {

    boolean existeIndicador(TablaHistorica tabla, String indicador, int anio);

    boolean existeMeta(String indicador);

    /** Devuelve {@code true} solo cuando se insertó; nunca sobrescribe una clave existente. */
    boolean guardarIndicadorSiAusente(IndicadorHistorico indicador);

    /** Devuelve {@code true} solo cuando se insertó; nunca sobrescribe una clave existente. */
    boolean guardarMetaSiAusente(MetaEstrategica meta);
}
