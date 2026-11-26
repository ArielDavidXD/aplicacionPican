package cu.pican.model.repository.memory;

import cu.pican.model.AclaracionOperativa;
import cu.pican.model.repository.AclaracionOperativaRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class AclaracionOperativaRepositoryMemory extends InMemoryRepository<AclaracionOperativa, UUID>
        implements AclaracionOperativaRepository {

    @Override
    protected UUID getId(AclaracionOperativa entity) {
        return entity.getIdAclaracion();
    }

    @Override
    public List<AclaracionOperativa> findByOrigen(String tipoOrigen, UUID idOrigen) {
        return store.values().stream()
                .filter(a -> Objects.equals(a.getTipoOrigen(), tipoOrigen))
                .filter(a -> Objects.equals(a.getIdOrigen(), idOrigen))
                .collect(Collectors.toList());
    }

    @Override
    public List<AclaracionOperativa> findPendientes() {
        return store.values().stream()
                .filter(a -> AclaracionOperativa.PENDIENTE.equals(a.getEstado()))
                .collect(Collectors.toList());
    }
}
