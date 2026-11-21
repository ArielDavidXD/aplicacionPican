package cu.pican.repository;

import cu.pican.model.FuenteInforme;
import java.util.List;
import java.util.UUID;

public interface FuenteInformeRepository extends Repository<FuenteInforme, UUID> {
    List<FuenteInforme> findByIdInforme(UUID idInforme);
}