package cu.pican.model.repository;

import cu.pican.model.Incidencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidenciaRepository extends Repository<Incidencia, UUID> {
    List<Incidencia> findByRecepcion(UUID idRecepcion);
    List<Incidencia> findByVale(UUID idVale);
    List<Incidencia> findByEstado(String estado);
}
