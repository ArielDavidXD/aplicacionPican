package cu.pican.model.repository;

import cu.pican.model.DecisionDirectivo;
import java.util.List;
import java.util.UUID;

public interface DecisionDirectivoRepository extends Repository<DecisionDirectivo, UUID> {
    List<DecisionDirectivo> findByIdInforme(UUID idInforme);
}