package cu.pican.model.repository;

import cu.pican.model.ConsolidadoUEB;
import java.util.List;
import java.util.UUID;

public interface ConsolidadoUEBRepository extends Repository<ConsolidadoUEB, UUID> {
    List<ConsolidadoUEB> findByUEB(UUID idUEB);

    void guardarRelaciones(UUID idConsolidado, UUID idUEB,
                           List<UUID> partesNave, List<UUID> partesUnidad, List<UUID> carnes);

    List<UUID> findPartesNave(UUID idConsolidado);
    List<UUID> findPartesUnidad(UUID idConsolidado);
    List<UUID> findProduccionesCarne(UUID idConsolidado);
}
