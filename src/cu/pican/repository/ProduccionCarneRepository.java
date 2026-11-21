package cu.pican.repository;

import cu.pican.model.ProduccionCarne;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProduccionCarneRepository extends Repository<ProduccionCarne, UUID> {
    List<ProduccionCarne> findByUEB(UUID idUEB);
    List<ProduccionCarne> findByFecha(LocalDate fechaProduccion);
}
