package cu.pican.repository;

import cu.pican.model.UnidadAsociada;
import java.util.Optional;
import java.util.UUID;

public interface UnidadAsociadaRepository extends Repository<UnidadAsociada, UUID> {
    /** CU-17: búsqueda por código (sin distinción de mayúsculas) para validar unicidad. */
    Optional<UnidadAsociada> findByCodigo(String codigo);
}
