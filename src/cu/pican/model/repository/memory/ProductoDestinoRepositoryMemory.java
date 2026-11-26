package cu.pican.model.repository.memory;

import cu.pican.model.ProductoDestino;
import cu.pican.model.repository.ProductoDestinoRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProductoDestinoRepositoryMemory extends InMemoryRepository<ProductoDestino, UUID> implements ProductoDestinoRepository {

    @Override
    protected UUID getId(ProductoDestino entity) {
        return entity.getIdRelacion();
    }

    @Override
    public List<ProductoDestino> findByProducto(UUID idProducto) {
        return store.values().stream()
                .filter(r -> Objects.equals(r.getIdProducto(), idProducto))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductoDestino> findByDestino(UUID idDestino) {
        return store.values().stream()
                .filter(r -> Objects.equals(r.getIdDestino(), idDestino))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ProductoDestino> findByProductoAndDestino(UUID idProducto, UUID idDestino) {
        return store.values().stream()
                .filter(r -> Objects.equals(r.getIdProducto(), idProducto)
                        && Objects.equals(r.getIdDestino(), idDestino))
                .findFirst();
    }
}
