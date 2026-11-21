package cu.pican.repository;

import cu.pican.model.MetaEstrategica;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de solo lectura de las metas estratégicas oficiales.
 * No extiende {@link Repository} deliberadamente: no expone save/update/delete.
 */
public interface MetaEstrategicaRepository {

    List<MetaEstrategica> findAll();

    Optional<MetaEstrategica> findById(String id);

    List<MetaEstrategica> findByIndicador(String indicador);
}
