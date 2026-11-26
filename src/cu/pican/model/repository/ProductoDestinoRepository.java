package cu.pican.model.repository;

import cu.pican.model.ProductoDestino;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductoDestinoRepository extends Repository<ProductoDestino, UUID> {
    List<ProductoDestino> findByProducto(UUID idProducto);
    List<ProductoDestino> findByDestino(UUID idDestino);
    Optional<ProductoDestino> findByProductoAndDestino(UUID idProducto, UUID idDestino);
}
