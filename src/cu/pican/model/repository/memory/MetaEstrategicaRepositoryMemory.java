package cu.pican.model.repository.memory;

import cu.pican.model.MetaEstrategica;
import cu.pican.model.repository.MetaEstrategicaRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Almacenamiento en memoria de las metas estratégicas oficiales (solo lectura). */
public class MetaEstrategicaRepositoryMemory implements MetaEstrategicaRepository {

    private final Map<String, MetaEstrategica> store = new LinkedHashMap<>();

    @Override
    public List<MetaEstrategica> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<MetaEstrategica> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<MetaEstrategica> findByIndicador(String indicador) {
        if (indicador == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(m -> Objects.equals(indicador, m.getIndicador()))
                .collect(Collectors.toList());
    }

    /**
     * Inserción controlada usada por CU-18. No forma parte de la interfaz de solo lectura
     * que consumen el análisis y los informes; el servicio comprueba antes la clave natural.
     */
    public void guardar(MetaEstrategica meta) {
        if (meta == null) {
            return;
        }
        store.put(meta.getId(), meta);
    }

    /** Carga controlada e idempotente, reservada al inicializador de referencias. */
    public void cargarSiVacio(List<MetaEstrategica> datos) {
        if (!store.isEmpty()) {
            return;
        }
        for (MetaEstrategica meta : datos) {
            store.put(meta.getId(), meta);
        }
    }
}
