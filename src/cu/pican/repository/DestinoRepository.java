package cu.pican.repository;

import cu.pican.model.Destino;
import java.util.Optional;
import java.util.UUID;

public interface DestinoRepository extends Repository<Destino, UUID> {
    /** CU-17: búsqueda por código (sin distinción de mayúsculas) para validar unicidad. */
    Optional<Destino> findByCodigo(String codigo);
}
