package cu.pican.repository.memory;

import cu.pican.model.Destino;
import cu.pican.repository.DestinoRepository;
import java.util.Optional;
import java.util.UUID;

public class DestinoRepositoryMemory extends InMemoryRepository<Destino, UUID> implements DestinoRepository {

    @Override
    protected UUID getId(Destino entity) {
        return entity.getIdDestino();
    }

    @Override
    public Optional<Destino> findByCodigo(String codigo) {
        return CatalogoMemoryQueries.findByCodigo(store.values(), Destino::getCodigo, codigo);
    }
}
