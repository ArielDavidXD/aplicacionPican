package cu.pican.model.repository;

import cu.pican.model.Informe;
import java.util.List;
import java.util.UUID;

public interface InformeRepository extends Repository<Informe, UUID> {
    List<Informe> findByEstado(String estado);
    List<Informe> findByIdInformeRaiz(UUID idInformeRaiz);
}