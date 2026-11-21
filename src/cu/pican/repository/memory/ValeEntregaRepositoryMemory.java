package cu.pican.repository.memory;

import cu.pican.model.ValeEntrega;
import cu.pican.repository.ValeEntregaRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ValeEntregaRepositoryMemory extends InMemoryRepository<ValeEntrega, UUID> implements ValeEntregaRepository {

    @Override
    protected UUID getId(ValeEntrega entity) {
        return entity.getIdVale();
    }

    @Override
    public Optional<ValeEntrega> findByNumeroVale(String numeroVale) {
        if (numeroVale == null) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(v -> Objects.equals(v.getNumeroVale(), numeroVale))
                .findFirst();
    }

    @Override
    public List<ValeEntrega> findByUEB(UUID idUEB) {
        return store.values().stream()
                .filter(v -> Objects.equals(v.getIdUEB(), idUEB))
                .collect(Collectors.toList());
    }

    @Override
    public List<ValeEntrega> findByDestino(UUID idDestino) {
        return store.values().stream()
                .filter(v -> Objects.equals(v.getIdDestino(), idDestino))
                .collect(Collectors.toList());
    }

    @Override
    public List<ValeEntrega> findByProducto(UUID idProducto) {
        return store.values().stream()
                .filter(v -> Objects.equals(v.getIdProducto(), idProducto))
                .collect(Collectors.toList());
    }

    @Override
    public List<ValeEntrega> findByPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        return store.values().stream()
                .filter(v -> enPeriodo(v.getFechaHoraEmision(), desde, hasta))
                .collect(Collectors.toList());
    }

    @Override
    public List<ValeEntrega> findByUEBOrdenadosPorFecha(UUID idUEB) {
        return store.values().stream()
                .filter(v -> Objects.equals(v.getIdUEB(), idUEB))
                .sorted(Comparator.comparing(ValeEntrega::getFechaHoraEmision,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public List<ValeEntrega> findByEstado(String estado) {
        if (estado == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(v -> Objects.equals(estado, v.getEstado()))
                .collect(Collectors.toList());
    }

    private boolean enPeriodo(LocalDateTime fecha, LocalDateTime desde, LocalDateTime hasta) {
        if (fecha == null) return false;
        if (desde != null && fecha.isBefore(desde)) return false;
        if (hasta != null && fecha.isAfter(hasta)) return false;
        return true;
    }
}
