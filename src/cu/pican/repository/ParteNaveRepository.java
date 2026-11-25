package cu.pican.repository;

import cu.pican.model.ParteNave;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ParteNaveRepository extends Repository<ParteNave, UUID> {
    List<ParteNave> findByNave(UUID idNave);
    List<ParteNave> findByFecha(LocalDate fechaProduccion);
    List<ParteNave> findByNaveYFecha(UUID idNave, LocalDate fechaProduccion);
}
