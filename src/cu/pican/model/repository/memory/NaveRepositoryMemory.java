package cu.pican.model.repository.memory;

import cu.pican.model.Nave;
import cu.pican.model.repository.NaveRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class NaveRepositoryMemory extends InMemoryRepository<Nave, UUID> implements NaveRepository {

    @Override
    protected UUID getId(Nave entity) {
        return entity.getIdNave();
    }

    @Override
    public List<Nave> findByUEB(UUID idUEB) {
        return store.values().stream()
                .filter(n -> Objects.equals(n.getIdUEB(), idUEB))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Nave> findByCodigo(String codigo) {
        return CatalogoMemoryQueries.findByCodigo(store.values(), Nave::getCodigo, codigo);
    }
}
