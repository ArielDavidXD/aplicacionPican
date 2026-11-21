package cu.pican.app;

import cu.pican.model.Destino;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.repository.DestinoRepository;
import cu.pican.repository.ProductoDestinoRepository;
import cu.pican.repository.ProductoRepository;
import cu.pican.repository.memory.DestinoRepositoryMemory;
import cu.pican.repository.memory.ProductoDestinoRepositoryMemory;
import cu.pican.repository.memory.ProductoRepositoryMemory;
import cu.pican.service.DestinoService;
import cu.pican.service.ProductoDestinoService;
import cu.pican.service.ProductoService;
import cu.pican.service.ValidacionException;

import java.util.UUID;

/**
 * Verificación automatizada de la Etapa 4.6A (catálogos operativos: Producto, Destino, ProductoDestino).
 * Los datos de prueba se crean exclusivamente dentro de esta demo, sin incorporarse a la inicialización de la aplicación.
 * La relación se crea con catálogos activos; las desactivaciones se comprueban después (CU-17).
 */
public class CatalogosOperativosStageDemo {

    public static void main(String[] args) {
        ProductoRepository productoRepo = new ProductoRepositoryMemory();
        DestinoRepository destinoRepo = new DestinoRepositoryMemory();
        ProductoDestinoRepository relacionRepo = new ProductoDestinoRepositoryMemory();
        ProductoService productoService = new ProductoService(productoRepo);
        DestinoService destinoService = new DestinoService(destinoRepo);
        ProductoDestinoService relacionService = new ProductoDestinoService(relacionRepo, productoRepo, destinoRepo);

        // 1. Registrar un producto
        System.out.println("1) Registrar un producto");
        UUID idProd = UUID.randomUUID();
        Producto prod = new Producto(idProd, "P-001", "Producto Demo", true, null); // unidad de medida opcional
        productoService.registrar(prod);
        check(productoService.buscarPorId(idProd).isPresent(), "Producto registrado");

        // 2. Consultar y editar un producto conservando su UUID
        System.out.println("2) Consultar y editar un producto conservando su UUID");
        Producto consultado = productoService.buscarPorId(idProd).orElseThrow();
        consultado.setNombre("Producto Demo Editado");
        consultado.setCodigo("P-001-E");
        productoService.actualizar(consultado);
        Producto actualizado = productoService.buscarPorId(idProd).orElseThrow();
        check(idProd.equals(actualizado.getIdProducto()), "UUID de producto conservado");
        check("Producto Demo Editado".equals(actualizado.getNombre()), "Producto editado (nombre)");
        check(productoService.listarTodos().size() == 1, "No se duplicó el producto al editar");

        // 3. Registrar un destino
        System.out.println("3) Registrar un destino");
        UUID idDest = UUID.randomUUID();
        Destino dest = new Destino(idDest, "D-001", "Destino Demo", true);
        destinoService.registrar(dest);
        check(destinoService.buscarPorId(idDest).isPresent(), "Destino registrado");

        // 4. Consultar y editar un destino conservando su UUID
        System.out.println("4) Consultar y editar un destino conservando su UUID");
        Destino destConsultado = destinoService.buscarPorId(idDest).orElseThrow();
        destConsultado.setNombre("Destino Demo Editado");
        destinoService.actualizar(destConsultado);
        Destino destActualizado = destinoService.buscarPorId(idDest).orElseThrow();
        check(idDest.equals(destActualizado.getIdDestino()), "UUID de destino conservado");
        check("Destino Demo Editado".equals(destActualizado.getNombre()), "Destino editado");
        check(destinoService.listarTodos().size() == 1, "No se duplicó el destino al editar");

        // 5. Crear una relación producto-destino (activos)
        System.out.println("5) Crear una relación producto-destino");
        UUID idRel = UUID.randomUUID();
        ProductoDestino rel = new ProductoDestino(idRel, idProd, idDest, true);
        relacionService.registrar(rel);
        check(relacionService.buscarPorId(idRel).isPresent(), "Relación registrada");
        check(relacionService.buscarPorProducto(idProd).size() == 1, "Relación consultable por producto");
        check(relacionService.buscarPorDestino(idDest).size() == 1, "Relación consultable por destino");

        // 6. Rechazar una relación duplicada
        System.out.println("6) Rechazar una relación duplicada");
        expectValidation(() -> relacionService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true)),
                "relación duplicada");

        // 7. Rechazar relaciones con productos o destinos inexistentes
        System.out.println("7) Rechazar relaciones con productos o destinos inexistentes");
        expectValidation(() -> relacionService.registrar(new ProductoDestino(UUID.randomUUID(), UUID.randomUUID(), idDest, true)),
                "producto inexistente");
        expectValidation(() -> relacionService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, UUID.randomUUID(), true)),
                "destino inexistente");

        // 8. Consultar las relaciones existentes
        System.out.println("8) Consultar las relaciones existentes");
        check(relacionService.listarTodas().size() == 1, "Listado de relaciones existentes");
        check(relacionService.buscarPorId(idRel).orElseThrow().getIdProducto().equals(idProd), "Relación consulta producto correcto");
        check(relacionService.buscarPorId(idRel).orElseThrow().getIdDestino().equals(idDest), "Relación consulta destino correcto");

        // 9. Desactivar el producto sin eliminarlo
        System.out.println("9) Desactivar el producto sin eliminarlo");
        productoService.desactivar(idProd);
        check(!productoService.buscarPorId(idProd).orElseThrow().isActiva(), "Producto desactivado");
        check(productoService.buscarPorId(idProd).isPresent(), "Producto desactivado sigue existiendo");

        // 10. Desactivar el destino sin eliminarlo
        System.out.println("10) Desactivar el destino sin eliminarlo");
        destinoService.desactivar(idDest);
        check(!destinoService.buscarPorId(idDest).orElseThrow().isActiva(), "Destino desactivado");
        check(destinoService.buscarPorId(idDest).isPresent(), "Destino desactivado sigue existiendo");

        // 11. Desactivar la relación no elimina su historial ni sus referencias
        System.out.println("11) Desactivar un elemento no elimina su historial ni sus referencias");
        relacionService.desactivar(idRel);
        check(relacionService.buscarPorId(idRel).isPresent(), "Relación desactivada sigue existiendo");
        check(!relacionService.buscarPorId(idRel).orElseThrow().isActiva(), "Relación desactivada");
        check(relacionService.listarTodas().size() == 1, "Referencias/historial de relaciones no eliminadas");

        // 12. Comprobar que los catálogos reales de la aplicación comienzan vacíos
        System.out.println("12) Comprobar que los catálogos reales de la aplicación comienzan vacíos");
        AppContext appContext = new AppContext();
        if (appContext.esMemoria()) {
            DemoDataInitializer.initialize(appContext);
        }
        check(appContext.getProductoService().listarTodos().isEmpty(), "Catálogo de productos vacío al iniciar la aplicación");
        check(appContext.getDestinoService().listarTodos().isEmpty(), "Catálogo de destinos vacío al iniciar la aplicación");
        check(appContext.getProductoDestinoService().listarTodas().isEmpty(), "Catálogo de relaciones producto-destino vacío al iniciar la aplicación");

        System.out.println("\n==> Verificación automatizada de Etapa 4.6A finalizada correctamente.");
    }

    private static void expectValidation(Runnable r, String caso) {
        try {
            r.run();
            throw new IllegalStateException("Se esperaba rechazo: " + caso);
        } catch (ValidacionException ex) {
            System.out.println("  OK - Rechazado (" + caso + "): " + ex.getMessage());
        }
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException("Fallo: " + msg);
        System.out.println("  OK - " + msg);
    }
}
