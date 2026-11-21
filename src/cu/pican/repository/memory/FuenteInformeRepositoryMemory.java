package cu.pican.repository.memory;

import cu.pican.model.FuenteInforme;
import cu.pican.repository.FuenteInformeRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class FuenteInformeRepositoryMemory extends InMemoryRepository<FuenteInforme, UUID> implements FuenteInformeRepository {

    @Override
    protected UUID getId(FuenteInforme entity) {
        return entity.getIdFuente();
    }

    @Override
    public List<FuenteInforme> findByIdInforme(UUID idInforme) {
        if (idInforme == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(f -> Objects.equals(idInforme, f.getIdInforme()))
                .collect(Collectors.toList());
    }
}