package cu.pican.repository.memory;

import cu.pican.model.ParteNave;
import cu.pican.repository.ParteNaveRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ParteNaveRepositoryMemory extends InMemoryRepository<ParteNave, UUID> implements ParteNaveRepository {

    @Override
    protected UUID getId(ParteNave entity) {
        return entity.getIdParte();
    }

    @Override
    public List<ParteNave> findByNave(UUID idNave) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.getIdNave(), idNave))
                .collect(Collectors.toList());
    }

    @Override
    public List<ParteNave> findByFecha(LocalDate fechaProduccion) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.getFechaProduccion(), fechaProduccion))
                .collect(Collectors.toList());
    }

    @Override
    public List<ParteNave> findByNaveYFecha(UUID idNave, LocalDate fechaProduccion) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.getIdNave(), idNave)
                        && Objects.equals(p.getFechaProduccion(), fechaProduccion))
                .collect(Collectors.toList());
    }
}
