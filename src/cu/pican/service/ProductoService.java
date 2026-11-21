package cu.pican.service;

import cu.pican.model.Producto;
import cu.pican.repository.ProductoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Servicio de negocio para el catálogo operativo de Productos (CU-17).
 * Registro, consulta, edición, activación/desactivación (baja lógica) y bloqueo de
 * eliminación física cuando existen vales o relaciones producto-destino asociadas.
 * No se precargan productos: los nombres y códigos deben ser confirmados por PICAN.
 */
public class ProductoService extends ServicioAutorizado {

    private final ProductoRepository productoRepository;
    private Predicate<UUID> verificadorOperaciones = id -> false;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /** Inyectado desde AppContext; por defecto no bloquea para no romper demos aisladas. */
    public void setVerificadorOperaciones(Predicate<UUID> verificadorOperaciones) {
        this.verificadorOperaciones = verificadorOperaciones;
    }

    public Producto registrar(Producto producto) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(producto);
        return productoRepository.save(producto);
    }

    public Producto actualizar(Producto producto) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(producto);
        return productoRepository.update(producto);
    }

    public Optional<Producto> buscarPorId(UUID idProducto) {
        autorizacion().requerirLecturaCatalogo();
        return productoRepository.findById(idProducto);
    }

    public Optional<Producto> buscarPorCodigo(String codigo) {
        autorizacion().requerirLecturaCatalogo();
        return productoRepository.findByCodigo(codigo);
    }

    public List<Producto> listarTodos() {
        autorizacion().requerirLecturaCatalogo();
        return productoRepository.findAll();
    }

    public Producto activar(UUID idProducto) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idProducto, true);
    }

    public Producto desactivar(UUID idProducto) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idProducto, false);
    }

    public void eliminar(UUID idProducto) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireUuid(idProducto, "El identificador UUID del producto es obligatorio.");
        productoRepository.findById(idProducto)
                .orElseThrow(() -> new ValidacionException("No existe el producto indicado."));
        if (verificadorOperaciones.test(idProducto)) {
            throw new ValidacionException(
                    "No se puede eliminar el producto porque tiene vales o relaciones asociadas. Desactívelo en su lugar.");
        }
        productoRepository.deleteById(idProducto);
    }

    private Producto cambiarEstado(UUID idProducto, boolean activa) {
        ValidationUtils.requireUuid(idProducto, "El identificador UUID del producto es obligatorio.");
        Producto actual = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ValidacionException("No existe el producto indicado."));
        Producto copia = copiar(actual);
        copia.setActiva(activa);
        return productoRepository.update(copia);
    }

    private void validar(Producto producto) {
        ValidationUtils.requireNonNull(producto, "El producto es obligatorio.");
        ValidationUtils.requireUuid(producto.getIdProducto(), "El identificador UUID del producto es obligatorio.");
        ValidationUtils.requireNotBlank(producto.getCodigo(), "El código del producto es obligatorio.");
        ValidationUtils.requireNotBlank(producto.getNombre(), "El nombre del producto es obligatorio.");
        // unidadMedida es opcional (obligatoriedad pendiente de confirmación).

        Optional<Producto> porCodigo = productoRepository.findByCodigo(producto.getCodigo());
        if (porCodigo.isPresent() && !porCodigo.get().getIdProducto().equals(producto.getIdProducto())) {
            throw new ValidacionException("Ya existe un producto con el código '" + producto.getCodigo().trim() + "'.");
        }
    }

    private Producto copiar(Producto actual) {
        return new Producto(actual.getIdProducto(), actual.getCodigo(), actual.getNombre(),
                actual.isActiva(), actual.getUnidadMedida());
    }
}
