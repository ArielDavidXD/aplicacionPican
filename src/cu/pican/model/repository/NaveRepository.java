package cu.pican.model.repository;

import cu.pican.model.Nave;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NaveRepository extends Repository<Nave, UUID> {
    List<Nave> findByUEB(UUID idUEB);

    /** CU-17: búsqueda por código (sin distinción de mayúsculas) para validar unicidad. */
    Optional<Nave> findByCodigo(String codigo);
}
