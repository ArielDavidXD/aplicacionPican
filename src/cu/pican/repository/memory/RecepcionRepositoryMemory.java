package cu.pican.repository.memory;

import cu.pican.model.Recepcion;
import cu.pican.repository.RecepcionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class RecepcionRepositoryMemory extends InMemoryRepository<Recepcion, UUID> implements RecepcionRepository {

    @Override
    protected UUID getId(Recepcion entity) {
        return entity.getIdRecepcion();
    }

    @Override
    public Recepcion save(Recepcion entity) {
        // Impedir que un vale tenga más de una recepción
        if (entity != null && entity.getIdVale() != null) {
            Optional<Recepcion> existente = findByVale(entity.getIdVale());
            if (existente.isPresent() && !Objects.equals(existente.get().getIdRecepcion(), entity.getIdRecepcion())) {
                throw new IllegalArgumentException("El vale ya tiene una recepción registrada.");
            }
        }
        return super.save(entity);
    }

    @Override
    public Optional<Recepcion> findByVale(UUID idVale) {
        if (idVale == null) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(r -> Objects.equals(r.getIdVale(), idVale))
                .findFirst();
    }

    @Override
    public List<Recepcion> findByPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        return store.values().stream()
                .filter(r -> enPeriodo(r.getFechaHoraRecepcion(), desde, hasta))
                .collect(Collectors.toList());
    }

    private boolean enPeriodo(LocalDateTime fecha, LocalDateTime desde, LocalDateTime hasta) {
        if (fecha == null) return false;
        if (desde != null && fecha.isBefore(desde)) return false;
        if (hasta != null && fecha.isAfter(hasta)) return false;
        return true;
    }
}
