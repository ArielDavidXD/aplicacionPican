package cu.pican.model.repository;

import cu.pican.model.Recepcion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecepcionRepository extends Repository<Recepcion, UUID> {
    Optional<Recepcion> findByVale(UUID idVale);
    List<Recepcion> findByPeriodo(LocalDateTime desde, LocalDateTime hasta);
}
