package cu.pican.model.repository;

import cu.pican.model.UEB;
import java.util.Optional;
import java.util.UUID;

public interface UEBRepository extends Repository<UEB, UUID> {
    /** CU-17: búsqueda por código (sin distinción de mayúsculas) para validar unicidad. */
    Optional<UEB> findByCodigo(String codigo);
}
