package cu.pican.service;

import cu.pican.model.Destino;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.repository.DestinoRepository;
import cu.pican.repository.ProductoDestinoRepository;
import cu.pican.repository.ProductoRepository;
import cu.pican.repository.ValeEntregaRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para relaciones Producto-Destino autorizadas (CU-17).
 * Valida existencia de producto/destino, evita duplicados, exige productos y destinos
 * activos al crear la relación y permite activar/desactivar sin baja física.
 * Las relaciones usadas por vales históricos no se eliminan ni cambian de producto/destino.
 */
public class ProductoDestinoService extends ServicioAutorizado {

    private final ProductoDestinoRepository productoDestinoRepository;
    private final ProductoRepository productoRepository;
    private final DestinoRepository destinoRepository;
    private ValeEntregaRepository valeEntregaRepository;

    public ProductoDestinoService(ProductoDestinoRepository productoDestinoRepository,
                                  ProductoRepository productoRepository,
                                  DestinoRepository destinoRepository) {
        this.productoDestinoRepository = productoDestinoRepository;
        this.productoRepository = productoRepository;
        this.destinoRepository = destinoRepository;
    }

    /** Inyectado desde AppContext; sin él no hay vales que proteger (demos aisladas). */
    public void setValeEntregaRepository(ValeEntregaRepository valeEntregaRepository) {
        this.valeEntregaRepository = valeEntregaRepository;
    }

    public ProductoDestino registrar(ProductoDestino relacion) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(relacion, true);
        return productoDestinoRepository.save(relacion);
    }

    public ProductoDestino actualizar(ProductoDestino relacion) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireNonNull(relacion, "La relación producto-destino es obligatoria.");
        ValidationUtils.requireUuid(relacion.getIdRelacion(), "El identificador UUID de la relación es obligatorio.");
        ProductoDestino actual = productoDestinoRepository.findById(relacion.getIdRelacion())
                .orElseThrow(() -> new ValidacionException("No existe la relación producto-destino indicada."));

        boolean cambioAsociacion = !Objects.equals(relacion.getIdProducto(), actual.getIdProducto())
                || !Objects.equals(relacion.getIdDestino(), actual.getIdDestino());
        if (cambioAsociacion && estaAsociadaAVales(actual.getIdRelacion())) {
            throw new ValidacionException(
                    "No se puede modificar el producto o el destino de una relación utilizada por vales históricos.");
        }
        validar(relacion, cambioAsociacion);
        return productoDestinoRepository.update(relacion);
    }

    public Optional<ProductoDestino> buscarPorId(UUID idRelacion) {
        autorizacion().requerirLecturaCatalogo();
        return productoDestinoRepository.findById(idRelacion);
    }

    public List<ProductoDestino> listarTodas() {
        autorizacion().requerirLecturaCatalogo();
        return productoDestinoRepository.findAll();
    }

    public List<ProductoDestino> buscarPorProducto(UUID idProducto) {
        autorizacion().requerirLecturaCatalogo();
        return productoDestinoRepository.findByProducto(idProducto);
    }

    public List<ProductoDestino> buscarPorDestino(UUID idDestino) {
        autorizacion().requerirLecturaCatalogo();
        return productoDestinoRepository.findByDestino(idDestino);
    }

    public ProductoDestino activar(UUID idRelacion) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idRelacion, true);
    }

    public ProductoDestino desactivar(UUID idRelacion) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idRelacion, false);
    }

    /** True cuando existe al menos un vale con el mismo producto y destino de la relación. */
    public boolean estaAsociadaAVales(UUID idRelacion) {
        autorizacion().requerirLecturaCatalogo();
        if (valeEntregaRepository == null) {
            return false;
        }
        ProductoDestino relacion = productoDestinoRepository.findById(idRelacion).orElse(null);
        if (relacion == null) {
            return false;
        }
        return valeEntregaRepository.findAll().stream().anyMatch(vale ->
                Objects.equals(relacion.getIdProducto(), vale.getIdProducto())
                        && Objects.equals(relacion.getIdDestino(), vale.getIdDestino()));
    }

    public void eliminar(UUID idRelacion) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireUuid(idRelacion, "El identificador UUID de la relación es obligatorio.");
        productoDestinoRepository.findById(idRelacion)
                .orElseThrow(() -> new ValidacionException("No existe la relación producto-destino indicada."));
        if (estaAsociadaAVales(idRelacion)) {
            throw new ValidacionException(
                    "No se puede eliminar una relación producto-destino asociada a vales históricos. Desactívela en su lugar.");
        }
        productoDestinoRepository.deleteById(idRelacion);
    }

    private ProductoDestino cambiarEstado(UUID idRelacion, boolean activa) {
        ValidationUtils.requireUuid(idRelacion, "El identificador UUID de la relación es obligatorio.");
        ProductoDestino actual = productoDestinoRepository.findById(idRelacion)
                .orElseThrow(() -> new ValidacionException("No existe la relación producto-destino indicada."));
        ProductoDestino copia = copiar(actual);
        copia.setActiva(activa);
        return productoDestinoRepository.update(copia);
    }

    private void validar(ProductoDestino relacion, boolean exigirActivos) {
        ValidationUtils.requireNonNull(relacion, "La relación producto-destino es obligatoria.");
        ValidationUtils.requireUuid(relacion.getIdRelacion(), "El identificador UUID de la relación es obligatorio.");
        ValidationUtils.requireUuid(relacion.getIdProducto(), "El producto de la relación es obligatorio.");
        ValidationUtils.requireUuid(relacion.getIdDestino(), "El destino de la relación es obligatorio.");

        Producto producto = productoRepository.findById(relacion.getIdProducto())
                .orElseThrow(() -> new ValidacionException("El producto indicado no existe."));
        Destino destino = destinoRepository.findById(relacion.getIdDestino())
                .orElseThrow(() -> new ValidacionException("El destino indicado no existe."));
        if (exigirActivos) {
            if (!producto.isActiva()) {
                throw new ValidacionException("No se puede crear una relación con un producto desactivado.");
            }
            if (!destino.isActiva()) {
                throw new ValidacionException("No se puede crear una relación con un destino desactivado.");
            }
        }

        Optional<ProductoDestino> existente = productoDestinoRepository
                .findByProductoAndDestino(relacion.getIdProducto(), relacion.getIdDestino());
        if (existente.isPresent() && !existente.get().getIdRelacion().equals(relacion.getIdRelacion())) {
            throw new ValidacionException("Ya existe una relación entre el producto y el destino seleccionados.");
        }
    }

    private ProductoDestino copiar(ProductoDestino actual) {
        return new ProductoDestino(actual.getIdRelacion(), actual.getIdProducto(),
                actual.getIdDestino(), actual.isActiva());
    }
}
