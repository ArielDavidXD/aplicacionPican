package cu.pican.model.repository.memory;

import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.repository.ParteUnidadAsociadaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ParteUnidadAsociadaRepositoryMemory extends InMemoryRepository<ParteUnidadAsociada, UUID> implements ParteUnidadAsociadaRepository {

    @Override
    protected UUID getId(ParteUnidadAsociada entity) {
        return entity.getIdParteAsociado();
    }

    @Override
    public List<ParteUnidadAsociada> findByUnidad(UUID idUnidad) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.getIdUnidad(), idUnidad))
                .collect(Collectors.toList());
    }

    @Override
    public List<ParteUnidadAsociada> findByUEB(UUID idUEB) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.getIdUEB(), idUEB))
                .collect(Collectors.toList());
    }

    @Override
    public List<ParteUnidadAsociada> findByFecha(LocalDate fechaProduccion) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.getFechaProduccion(), fechaProduccion))
                .collect(Collectors.toList());
    }
}
