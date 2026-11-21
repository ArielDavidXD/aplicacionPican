package cu.pican.repository.memory;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.repository.ConsolidadoUEBRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConsolidadoUEBRepositoryMemory extends InMemoryRepository<ConsolidadoUEB, UUID> implements ConsolidadoUEBRepository {

    @Override
    protected UUID getId(ConsolidadoUEB entity) {
        return entity.getIdConsolidado();
    }

    @Override
    public List<ConsolidadoUEB> findByUEB(UUID idUEB) {
        return store.values().stream()
                .filter(c -> Objects.equals(c.getIdUEB(), idUEB))
                .collect(Collectors.toList());
    }
}
