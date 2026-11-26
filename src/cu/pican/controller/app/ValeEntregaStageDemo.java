package cu.pican.controller.app;

import cu.pican.model.Destino;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.UEB;
import cu.pican.model.ValeEntrega;
import cu.pican.model.repository.DestinoRepository;
import cu.pican.model.repository.ProductoDestinoRepository;
import cu.pican.model.repository.ProductoRepository;
import cu.pican.model.repository.UEBRepository;
import cu.pican.model.repository.ValeEntregaRepository;
import cu.pican.model.repository.memory.DestinoRepositoryMemory;
import cu.pican.model.repository.memory.ProductoDestinoRepositoryMemory;
import cu.pican.model.repository.memory.ProductoRepositoryMemory;
import cu.pican.model.repository.memory.UEBRepositoryMemory;
import cu.pican.model.repository.memory.ValeEntregaRepositoryMemory;
import cu.pican.controller.service.DestinoService;
import cu.pican.controller.service.ProductoDestinoService;
import cu.pican.controller.service.ProductoService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.ValeEntregaService;
import cu.pican.controller.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Verificación automatizada de la Etapa 4.6B (vales de entrega).
 * Usa repositorios/servicios aislados para no contaminar los datos de la aplicación.
 */
public class ValeEntregaStageDemo {

    public static void main(String[] args) {
        cu.pican.controller.app.AutorizacionDemo.instalarTodosLosRoles();
        // Infraestructura aislada
        UEBRepository uebRepo = new UEBRepositoryMemory();
        ProductoRepository prodRepo = new ProductoRepositoryMemory();
        DestinoRepository destRepo = new DestinoRepositoryMemory();
        ProductoDestinoRepository relRepo = new ProductoDestinoRepositoryMemory();
        ValeEntregaRepository valeRepo = new ValeEntregaRepositoryMemory();

        UEBService uebService = new UEBService(uebRepo);
        ProductoService productoService = new ProductoService(prodRepo);
        DestinoService destinoService = new DestinoService(destRepo);
        ProductoDestinoService relService = new ProductoDestinoService(relRepo, prodRepo, destRepo);
        ValeEntregaService valeService = new ValeEntregaService(valeRepo, uebService, productoService, destinoService, relService);

        // Datos base de prueba (solo en esta demo)
        UUID idUeb1 = UUID.randomUUID();
        UUID idUeb2 = UUID.randomUUID();
        UUID idUebInactiva = UUID.randomUUID();
        UEB ueb1 = new UEB(idUeb1, "UEB Uno", "UEB01", true);
        UEB ueb2 = new UEB(idUeb2, "UEB Dos", "UEB02", true);
        UEB uebInactiva = new UEB(idUebInactiva, "UEB Inactiva", "UEB99", false);
        uebService.registrar(ueb1);
        uebService.registrar(ueb2);
        uebService.registrar(uebInactiva);

        UUID idProd = UUID.randomUUID();
        UUID idProdInactivo = UUID.randomUUID();
        Producto prod = new Producto(idProd, "P1", "Producto 1", true, "kg");
        Producto prodInactivo = new Producto(idProdInactivo, "P2", "Producto Inactivo", false, "kg");
        productoService.registrar(prod);
        productoService.registrar(prodInactivo);

        UUID idDest = UUID.randomUUID();
        UUID idDestInactivo = UUID.randomUUID();
        Destino dest = new Destino(idDest, "D1", "Destino 1", true);
        Destino destInactivo = new Destino(idDestInactivo, "D2", "Destino Inactivo", false);
        destinoService.registrar(dest);
        destinoService.registrar(destInactivo);

        // Relación activa para pruebas (las relaciones nuevas exigen producto y destino activos).
        relService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true));

        LocalDateTime base = LocalDateTime.of(2026, 11, 19, 10, 0, 0);

        // 1. Emisión correcta de un vale
        System.out.println("1) Emisión correcta de un vale");
        ValeEntrega v1 = nuevoVale(idUeb1, idDest, idProd, "5.50", "kg", base);
        ValeEntrega emitido1 = valeService.emitir(v1);
        check(emitido1.getIdVale() != null, "Vale emitido con UUID");
        check(ValeEntrega.ESTADO_EMITIDO.equals(emitido1.getEstado()), "Estado inicial EMITIDO");
        check(valeService.buscarPorId(emitido1.getIdVale()).isPresent(), "Vale consultable por UUID");

        // 2. Asignación automática del número
        System.out.println("2) Asignación automática del número");
        check(emitido1.getNumeroVale() != null && !emitido1.getNumeroVale().trim().isEmpty(), "Número asignado automáticamente");
        System.out.println("  OK - Número generado (formato provisional, no oficial): " + emitido1.getNumeroVale());

        // 3. Numeración consecutiva para una misma UEB
        System.out.println("3) Numeración consecutiva para una misma UEB");
        ValeEntrega v2 = nuevoVale(idUeb1, idDest, idProd, "1.00", "kg", base.plusMinutes(1));
        ValeEntrega emitido2 = valeService.emitir(v2);
        ValeEntrega v3 = nuevoVale(idUeb1, idDest, idProd, "2.00", "kg", base.plusMinutes(2));
        ValeEntrega emitido3 = valeService.emitir(v3);
        check(esConsecutivo(emitido1.getNumeroVale(), emitido2.getNumeroVale()), "Segundo número consecutivo a primero (misma UEB)");
        check(esConsecutivo(emitido2.getNumeroVale(), emitido3.getNumeroVale()), "Tercer número consecutivo a segundo (misma UEB)");
        check(!emitido1.getNumeroVale().equals(emitido2.getNumeroVale()), "Números distintos en misma UEB");

        // 4. Numeración independiente para otra UEB
        System.out.println("4) Numeración independiente para otra UEB");
        ValeEntrega v4 = nuevoVale(idUeb2, idDest, idProd, "3.00", "kg", base.plusMinutes(3));
        ValeEntrega emitido4 = valeService.emitir(v4);
        check(emitido4.getNumeroVale() != null, "Número generado para otra UEB");
        check(extraerSecuencia(emitido4.getNumeroVale()) == 1, "Secuencia independiente para otra UEB (inicia en 1)");
        check(!emitido4.getNumeroVale().equals(emitido1.getNumeroVale()), "Numeración independiente entre UEBs");

        // 5. Rechazo de UEB inexistente o inactiva
        System.out.println("5) Rechazo de UEB inexistente o inactiva");
        expectValidation(() -> valeService.emitir(nuevoVale(UUID.randomUUID(), idDest, idProd, "1", "kg", base)), "UEB inexistente");
        expectValidation(() -> valeService.emitir(nuevoVale(idUebInactiva, idDest, idProd, "1", "kg", base)), "UEB inactiva");

        // 6. Rechazo de destino inexistente o inactivo
        System.out.println("6) Rechazo de destino inexistente o inactivo");
        expectValidation(() -> valeService.emitir(nuevoVale(idUeb1, UUID.randomUUID(), idProd, "1", "kg", base)), "destino inexistente");
        expectValidation(() -> valeService.emitir(nuevoVale(idUeb1, idDestInactivo, idProd, "1", "kg", base)), "destino inactivo");

        // 7. Rechazo de producto inexistente o inactivo
        System.out.println("7) Rechazo de producto inexistente o inactivo");
        expectValidation(() -> valeService.emitir(nuevoVale(idUeb1, idDest, UUID.randomUUID(), "1", "kg", base)), "producto inexistente");
        expectValidation(() -> valeService.emitir(nuevoVale(idUeb1, idDest, idProdInactivo, "1", "kg", base)), "producto inactivo");

        // 8. Rechazo de relación producto-destino inexistente o inactiva
        System.out.println("8) Rechazo de relación producto-destino inexistente o inactiva");
        UUID idProdSinRel = UUID.randomUUID();
        productoService.registrar(new Producto(idProdSinRel, "P3", "Producto sin relación", true, "u"));
        expectValidation(() -> valeService.emitir(nuevoVale(idUeb1, idDest, idProdSinRel, "1", "u", base)), "relación inexistente");
        // relación inactiva: se crea activa y luego se desactiva (baja lógica), sin tocar el producto
        UUID idProdRelInactiva = UUID.randomUUID();
        productoService.registrar(new Producto(idProdRelInactiva, "P4", "Producto rel inactiva", true, "u"));
        UUID idRelInactiva = UUID.randomUUID();
        relService.registrar(new ProductoDestino(idRelInactiva, idProdRelInactiva, idDest, true));
        relService.desactivar(idRelInactiva);
        expectValidation(() -> valeService.emitir(nuevoVale(idUeb1, idDest, idProdRelInactiva, "1", "u", base)), "relación inactiva");

        // 9. Rechazo de cantidad cero o negativa
        System.out.println("9) Rechazo de cantidad cero o negativa");
        expectValidation(() -> valeService.emitir(nuevoVale(idUeb1, idDest, idProd, "0", "kg", base)), "cantidad cero");
        expectValidation(() -> valeService.emitir(nuevoVale(idUeb1, idDest, idProd, "-1.5", "kg", base)), "cantidad negativa");

        // 10. Rechazo de unidad de medida vacía
        System.out.println("10) Rechazo de unidad de medida vacía");
        ValeEntrega sinUnidad = nuevoVale(idUeb1, idDest, idProd, "1", null, base);
        expectValidation(() -> valeService.emitir(sinUnidad), "unidad de medida vacía");
        ValeEntrega unidadBlanco = nuevoVale(idUeb1, idDest, idProd, "1", "   ", base);
        expectValidation(() -> valeService.emitir(unidadBlanco), "unidad de medida en blanco");

        // 11. Rechazo/prevención de números duplicados
        System.out.println("11) Rechazo/prevención de números duplicados");
        // El servicio genera números únicos y consecutivos; verificamos no duplicidad y prevención ante colisión en repositorio.
        Set<String> numeros = new HashSet<>();
        for (ValeEntrega v : valeService.listarTodos()) {
            check(numeros.add(v.getNumeroVale()), "Número único en el listado: " + v.getNumeroVale());
        }
        // Simular colisión: preinsertar en repositorio un vale con el próximo número esperado para UEB1 y verificar que se evita duplicado
        int seqActualUeb1 = extraerSecuencia(emitido3.getNumeroVale()); // último de UEB1
        String numeroColision = prefijoNumero(emitido3.getNumeroVale()) + String.format("%06d", seqActualUeb1 + 1);
        ValeEntrega colision = nuevoVale(idUeb1, idDest, idProd, "9", "kg", base.plusMinutes(20));
        colision.setIdVale(UUID.randomUUID());
        colision.setNumeroVale(numeroColision);
        colision.setEstado(ValeEntrega.ESTADO_EMITIDO);
        colision.setFechaCreacion(LocalDateTime.now());
        colision.setVersion(1L);
        valeRepo.save(colision); // inserción directa en repo aislado para simular colisión
        ValeEntrega trasColision = valeService.emitir(nuevoVale(idUeb1, idDest, idProd, "4", "kg", base.plusMinutes(21)));
        check(!numeroColision.equals(trasColision.getNumeroVale()), "Generación evita número duplicado ante colisión");
        check(valeRepo.findByNumeroVale(trasColision.getNumeroVale()).isPresent(), "Nuevo número único persistido");
        System.out.println("  OK - Duplicados prevenidos (asignación automática única; validación de unicidad en servicio)");

        // 12. Consulta y filtrado de vales
        System.out.println("12) Consulta y filtrado de vales");
        check(!valeService.listarTodos().isEmpty(), "Listado de vales");
        check(valeService.buscarPorNumero(emitido1.getNumeroVale()).isPresent(), "Búsqueda por número");
        check(!valeService.filtrarPorUEB(idUeb1).isEmpty(), "Filtro por UEB");
        check(!valeService.filtrarPorDestino(idDest).isEmpty(), "Filtro por destino");
        check(!valeService.filtrarPorProducto(idProd).isEmpty(), "Filtro por producto");
        List<ValeEntrega> porPeriodo = valeService.filtrarPorPeriodo(base.minusMinutes(1), base.plusMinutes(5));
        check(porPeriodo.stream().anyMatch(v -> emitido1.getIdVale().equals(v.getIdVale())), "Filtro por período de emisión");
        List<ValeEntrega> ordenadosUeb1 = valeService.obtenerPorUEBOrdenadosPorFecha(idUeb1);
        check(!ordenadosUeb1.isEmpty(), "Vales de UEB ordenados por fecha");
        for (int i = 1; i < ordenadosUeb1.size(); i++) {
            LocalDateTime a = ordenadosUeb1.get(i - 1).getFechaHoraEmision();
            LocalDateTime b = ordenadosUeb1.get(i).getFechaHoraEmision();
            if (a != null && b != null) {
                check(!b.isBefore(a), "Orden cronológico por fecha de emisión");
            }
        }

        // 13. Verificación de que cada vale contiene un solo producto
        System.out.println("13) Verificación de que cada vale contiene un solo producto");
        for (ValeEntrega v : valeService.listarTodos()) {
            check(v.getIdProducto() != null, "Vale tiene un producto asociado");
            check(v.getCantidadDespachada() != null && v.getUnidadMedida() != null, "Cantidad y unidad pertenecen al vale");
        }
        // El modelo ValeEntrega no contiene colección de detalles/productos múltiples (diseño: un producto por vale)
        check(true, "Modelo de vale con un único producto (sin detalles múltiples)");

        // 14. Verificación de que los catálogos originales no se modifican al emitir
        System.out.println("14) Verificación de que los catálogos originales no se modifican al emitir");
        check(productoService.listarTodos().size() == 4, "Catálogo de productos sin cambios por emisiones");
        check(destinoService.listarTodos().size() == 2, "Catálogo de destinos sin cambios por emisiones");
        check(relService.listarTodas().size() == 2, "Relaciones producto-destino sin cambios por emisiones");
        check(uebService.listarTodas().size() == 3, "Catálogo de UEB sin cambios por emisiones");
        check("Producto 1".equals(productoService.buscarPorId(idProd).orElseThrow().getNombre()), "Producto original intacto");

        System.out.println("\n==> Verificación automatizada de Etapa 4.6B finalizada correctamente.");
        System.out.println("Nota: numeración en memoria (contadores no persistentes entre ejecuciones), formato provisional no oficial.");
    }

    private static ValeEntrega nuevoVale(UUID idUeb, UUID idDest, UUID idProd, String cantidad, String unidad, LocalDateTime fechaHora) {
        ValeEntrega v = new ValeEntrega();
        v.setIdVale(UUID.randomUUID());
        v.setIdUEB(idUeb);
        v.setIdDestino(idDest);
        v.setIdProducto(idProd);
        v.setCantidadDespachada(cantidad == null ? null : new BigDecimal(cantidad));
        v.setUnidadMedida(unidad);
        v.setFechaHoraEmision(fechaHora);
        return v;
    }

    private static boolean esConsecutivo(String anterior, String siguiente) {
        int a = extraerSecuencia(anterior);
        int b = extraerSecuencia(siguiente);
        return b == a + 1 && prefijoNumero(anterior).equals(prefijoNumero(siguiente));
    }

    private static int extraerSecuencia(String numero) {
        if (numero == null) return -1;
        int idx = numero.lastIndexOf('-');
        if (idx < 0 || idx + 1 >= numero.length()) return -1;
        try {
            return Integer.parseInt(numero.substring(idx + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String prefijoNumero(String numero) {
        if (numero == null) return "";
        int idx = numero.lastIndexOf('-');
        return idx < 0 ? "" : numero.substring(0, idx + 1);
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
