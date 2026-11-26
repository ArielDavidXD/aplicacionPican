package cu.pican.model.repository.memory;

import cu.pican.model.ProduccionCarne;
import cu.pican.model.repository.ProduccionCarneRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProduccionCarneRepositoryMemory extends InMemoryRepository<ProduccionCarne, UUID> implements ProduccionCarneRepository {

    @Override
    protected UUID getId(ProduccionCarne entity) {
        return entity.getIdProduccion();
    }

    @Override
    public List<ProduccionCarne> findByUEB(UUID idUEB) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.getIdUEB(), idUEB))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProduccionCarne> findByFecha(LocalDate fechaProduccion) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.getFechaProduccion(), fechaProduccion))
                .collect(Collectors.toList());
    }
}
