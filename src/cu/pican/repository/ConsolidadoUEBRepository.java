package cu.pican.repository;

import cu.pican.model.ConsolidadoUEB;
import java.util.List;
import java.util.UUID;

public interface ConsolidadoUEBRepository extends Repository<ConsolidadoUEB, UUID> {
    List<ConsolidadoUEB> findByUEB(UUID idUEB);
}
