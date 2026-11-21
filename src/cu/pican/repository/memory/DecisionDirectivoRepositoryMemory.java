package cu.pican.repository.memory;

import cu.pican.model.DecisionDirectivo;
import cu.pican.repository.DecisionDirectivoRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class DecisionDirectivoRepositoryMemory extends InMemoryRepository<DecisionDirectivo, UUID> implements DecisionDirectivoRepository {

    @Override
    protected UUID getId(DecisionDirectivo entity) {
        return entity.getIdDecision();
    }

    @Override
    public List<DecisionDirectivo> findByIdInforme(UUID idInforme) {
        if (idInforme == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(d -> Objects.equals(idInforme, d.getIdInforme()))
                .sorted(Comparator.comparingInt(d -> d.getNumeroDecision() == null ? 0 : d.getNumeroDecision()))
                .collect(Collectors.toList());
    }
}