package cu.pican.repository.memory;

import cu.pican.model.Incidencia;
import cu.pican.repository.IncidenciaRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class IncidenciaRepositoryMemory extends InMemoryRepository<Incidencia, UUID> implements IncidenciaRepository {

    @Override
    protected UUID getId(Incidencia entity) {
        return entity.getIdIncidencia();
    }

    @Override
    public List<Incidencia> findByRecepcion(UUID idRecepcion) {
        if (idRecepcion == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(i -> Objects.equals(i.getIdRecepcion(), idRecepcion))
                .collect(Collectors.toList());
    }

    @Override
    public List<Incidencia> findByVale(UUID idVale) {
        if (idVale == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(i -> Objects.equals(i.getIdVale(), idVale))
                .collect(Collectors.toList());
    }

    @Override
    public List<Incidencia> findByEstado(String estado) {
        if (estado == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(i -> Objects.equals(i.getEstado(), estado))
                .collect(Collectors.toList());
    }
}
