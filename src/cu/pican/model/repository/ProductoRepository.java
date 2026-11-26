package cu.pican.model.repository;

import cu.pican.model.Producto;
import java.util.Optional;
import java.util.UUID;

public interface ProductoRepository extends Repository<Producto, UUID> {
    /** CU-17: búsqueda por código (sin distinción de mayúsculas) para validar unicidad. */
    Optional<Producto> findByCodigo(String codigo);
}
