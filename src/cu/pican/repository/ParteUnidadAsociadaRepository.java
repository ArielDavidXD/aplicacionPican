package cu.pican.repository;

import cu.pican.model.ParteUnidadAsociada;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ParteUnidadAsociadaRepository extends Repository<ParteUnidadAsociada, UUID> {
    List<ParteUnidadAsociada> findByUnidad(UUID idUnidad);
    List<ParteUnidadAsociada> findByFecha(LocalDate fechaProduccion);
}
