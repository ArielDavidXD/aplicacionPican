package cu.pican.model.repository.memory;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.repository.ConsolidadoUEBRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConsolidadoUEBRepositoryMemory extends InMemoryRepository<ConsolidadoUEB, UUID>
        implements ConsolidadoUEBRepository {

    private final Map<UUID, List<UUID>> partesNave = new HashMap<>();
    private final Map<UUID, List<UUID>> partesUnidad = new HashMap<>();
    private final Map<UUID, List<UUID>> carnes = new HashMap<>();

    @Override
    protected UUID getId(ConsolidadoUEB entity) {
        return entity.getIdConsolidado();
    }

    @Override
    public List<ConsolidadoUEB> findByUEB(UUID idUEB) {
        return store.values().stream()
                .filter(c -> Objects.equals(c.getIdUEB(), idUEB))
                .collect(Collectors.toList());
    }

    @Override
    public void guardarRelaciones(UUID idConsolidado, UUID idUEB,
                                  List<UUID> partesNave, List<UUID> partesUnidad, List<UUID> carnes) {
        this.partesNave.put(idConsolidado, new ArrayList<>(partesNave));
        this.partesUnidad.put(idConsolidado, new ArrayList<>(partesUnidad));
        this.carnes.put(idConsolidado, new ArrayList<>(carnes));
    }

    @Override
    public List<UUID> findPartesNave(UUID idConsolidado) {
        return new ArrayList<>(partesNave.getOrDefault(idConsolidado, List.of()));
    }

    @Override
    public List<UUID> findPartesUnidad(UUID idConsolidado) {
        return new ArrayList<>(partesUnidad.getOrDefault(idConsolidado, List.of()));
    }

    @Override
    public List<UUID> findProduccionesCarne(UUID idConsolidado) {
        return new ArrayList<>(carnes.getOrDefault(idConsolidado, List.of()));
    }

    @Override
    public void deleteById(UUID id) {
        super.deleteById(id);
        partesNave.remove(id);
        partesUnidad.remove(id);
        carnes.remove(id);
    }
}
