package cu.pican.repository;

import cu.pican.model.ValeEntrega;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ValeEntregaRepository extends Repository<ValeEntrega, UUID> {
    Optional<ValeEntrega> findByNumeroVale(String numeroVale);
    List<ValeEntrega> findByUEB(UUID idUEB);
    List<ValeEntrega> findByDestino(UUID idDestino);
    List<ValeEntrega> findByProducto(UUID idProducto);
    List<ValeEntrega> findByPeriodo(LocalDateTime desde, LocalDateTime hasta);
    List<ValeEntrega> findByUEBOrdenadosPorFecha(UUID idUEB);
    List<ValeEntrega> findByEstado(String estado);
}
