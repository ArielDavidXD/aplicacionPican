package cu.pican.model.repository;

import cu.pican.model.DestinatarioInstitucional;
import cu.pican.model.EntregaInforme;
import java.util.List;
import java.util.UUID;

public interface EntregaInformeRepository extends Repository<EntregaInforme, UUID> {
    List<EntregaInforme> findByIdInforme(UUID idInforme);
    boolean existsByIdInformeAndDestinatario(UUID idInforme, DestinatarioInstitucional destinatario);
}