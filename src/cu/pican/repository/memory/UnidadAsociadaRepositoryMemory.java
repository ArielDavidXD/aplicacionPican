package cu.pican.repository.memory;

import cu.pican.model.UnidadAsociada;
import cu.pican.repository.UnidadAsociadaRepository;
import java.util.Optional;
import java.util.UUID;

public class UnidadAsociadaRepositoryMemory extends InMemoryRepository<UnidadAsociada, UUID> implements UnidadAsociadaRepository {

    @Override
    protected UUID getId(UnidadAsociada entity) {
        return entity.getIdUnidad();
    }

    @Override
    public Optional<UnidadAsociada> findByCodigo(String codigo) {
        return CatalogoMemoryQueries.findByCodigo(store.values(), UnidadAsociada::getCodigo, codigo);
    }
}
