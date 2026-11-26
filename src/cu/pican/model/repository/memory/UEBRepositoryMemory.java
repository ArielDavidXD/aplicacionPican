package cu.pican.model.repository.memory;

import cu.pican.model.UEB;
import cu.pican.model.repository.UEBRepository;
import java.util.Optional;
import java.util.UUID;

public class UEBRepositoryMemory extends InMemoryRepository<UEB, UUID> implements UEBRepository {

    @Override
    protected UUID getId(UEB entity) {
        return entity.getIdUEB();
    }

    @Override
    public Optional<UEB> findByCodigo(String codigo) {
        return CatalogoMemoryQueries.findByCodigo(store.values(), UEB::getCodigo, codigo);
    }
}
