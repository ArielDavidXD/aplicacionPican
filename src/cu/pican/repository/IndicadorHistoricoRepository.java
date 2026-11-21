package cu.pican.repository;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.TablaHistorica;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de solo lectura de las tablas históricas oficiales.
 * No extiende {@link Repository} deliberadamente: no expone save/update/delete.
 */
public interface IndicadorHistoricoRepository {

    List<IndicadorHistorico> findAll();

    Optional<IndicadorHistorico> findById(String id);

    List<IndicadorHistorico> findByTabla(TablaHistorica tabla);

    List<IndicadorHistorico> findByAnio(int anio);

    List<IndicadorHistorico> findByTablaAndAnio(TablaHistorica tabla, int anio);

    List<IndicadorHistorico> findByIndicador(String indicador);
}
