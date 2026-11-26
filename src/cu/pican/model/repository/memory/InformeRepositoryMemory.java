package cu.pican.model.repository.memory;

import cu.pican.model.Informe;
import cu.pican.model.repository.InformeRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class InformeRepositoryMemory extends InMemoryRepository<Informe, UUID> implements InformeRepository {

    @Override
    protected UUID getId(Informe entity) {
        return entity.getIdInforme();
    }

    @Override
    public List<Informe> findByEstado(String estado) {
        if (estado == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(i -> Objects.equals(estado, i.getEstado()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Informe> findByIdInformeRaiz(UUID idInformeRaiz) {
        if (idInformeRaiz == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(i -> Objects.equals(idInformeRaiz, i.getIdInformeRaiz()))
                .collect(Collectors.toList());
    }
}