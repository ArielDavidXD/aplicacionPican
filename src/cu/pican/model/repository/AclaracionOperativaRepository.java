package cu.pican.model.repository;

import cu.pican.model.AclaracionOperativa;
import java.util.List;
import java.util.UUID;

public interface AclaracionOperativaRepository extends Repository<AclaracionOperativa, UUID> {
    List<AclaracionOperativa> findByOrigen(String tipoOrigen, UUID idOrigen);
    List<AclaracionOperativa> findPendientes();
}
