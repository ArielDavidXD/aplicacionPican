package cu.pican.repository.memory;

import cu.pican.model.Producto;
import cu.pican.repository.ProductoRepository;
import java.util.Optional;
import java.util.UUID;

public class ProductoRepositoryMemory extends InMemoryRepository<Producto, UUID> implements ProductoRepository {

    @Override
    protected UUID getId(Producto entity) {
        return entity.getIdProducto();
    }

    @Override
    public Optional<Producto> findByCodigo(String codigo) {
        return CatalogoMemoryQueries.findByCodigo(store.values(), Producto::getCodigo, codigo);
    }
}
