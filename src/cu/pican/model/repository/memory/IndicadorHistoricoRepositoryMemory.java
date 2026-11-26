package cu.pican.model.repository.memory;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.TablaHistorica;
import cu.pican.model.repository.IndicadorHistoricoRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Almacenamiento en memoria de las referencias históricas oficiales.
 * La única operación de carga está reservada al inicializador controlado
 * ({@code ReferenciasHistoricasInitializer}) y es idempotente.
 */
public class IndicadorHistoricoRepositoryMemory implements IndicadorHistoricoRepository {

    private final Map<String, IndicadorHistorico> store = new LinkedHashMap<>();

    @Override
    public List<IndicadorHistorico> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<IndicadorHistorico> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<IndicadorHistorico> findByTabla(TablaHistorica tabla) {
        if (tabla == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(i -> i.getTabla() == tabla)
                .collect(Collectors.toList());
    }

    @Override
    public List<IndicadorHistorico> findByAnio(int anio) {
        return store.values().stream()
                .filter(i -> i.getAnio() == anio)
                .collect(Collectors.toList());
    }

    @Override
    public List<IndicadorHistorico> findByTablaAndAnio(TablaHistorica tabla, int anio) {
        return store.values().stream()
                .filter(i -> i.getTabla() == tabla && i.getAnio() == anio)
                .collect(Collectors.toList());
    }

    @Override
    public List<IndicadorHistorico> findByIndicador(String indicador) {
        if (indicador == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(i -> Objects.equals(indicador, i.getIndicador()))
                .collect(Collectors.toList());
    }

    /**
     * Inserción controlada usada por CU-18. No forma parte de la interfaz de solo lectura
     * que consumen el análisis y los informes; el servicio comprueba antes la clave natural.
     */
    public void guardar(IndicadorHistorico indicador) {
        if (indicador == null) {
            return;
        }
        store.put(indicador.getId(), indicador);
    }

    /** Carga controlada: solo la realizada por el inicializador; no duplica si ya hay datos. */
    public void cargarSiVacio(List<IndicadorHistorico> datos) {
        if (!store.isEmpty()) {
            return;
        }
        for (IndicadorHistorico indicador : datos) {
            store.put(indicador.getId(), indicador);
        }
    }
}
