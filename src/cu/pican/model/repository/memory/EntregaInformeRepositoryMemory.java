package cu.pican.model.repository.memory;

import cu.pican.model.DestinatarioInstitucional;
import cu.pican.model.EntregaInforme;
import cu.pican.model.repository.EntregaInformeRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class EntregaInformeRepositoryMemory extends InMemoryRepository<EntregaInforme, UUID> implements EntregaInformeRepository {

    @Override
    protected UUID getId(EntregaInforme entity) {
        return entity.getIdEntrega();
    }

    @Override
    public List<EntregaInforme> findByIdInforme(UUID idInforme) {
        if (idInforme == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(e -> Objects.equals(idInforme, e.getIdInforme()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByIdInformeAndDestinatario(UUID idInforme, DestinatarioInstitucional destinatario) {
        if (idInforme == null || destinatario == null) {
            return false;
        }
        return store.values().stream()
                .anyMatch(e -> Objects.equals(idInforme, e.getIdInforme()) && destinatario == e.getDestinatario());
    }
}