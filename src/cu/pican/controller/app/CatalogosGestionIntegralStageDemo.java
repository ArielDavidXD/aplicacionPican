package cu.pican.controller.app;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Destino;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.model.ValeEntrega;
import cu.pican.controller.service.DestinoService;
import cu.pican.controller.service.NaveService;
import cu.pican.controller.service.ParteNaveService;
import cu.pican.controller.service.ParteUnidadAsociadaService;
import cu.pican.controller.service.ProductoDestinoService;
import cu.pican.controller.service.ProductoService;
import cu.pican.controller.service.ProduccionCarneService;
import cu.pican.controller.service.ConsolidadoUEBService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.UnidadAsociadaService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.controller.service.ValeEntregaService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Verificación automatizada de la Etapa 4.13 (CU-17: gestión consistente y validada de catálogos).
 * Contiene 14 comprobaciones numeradas. No genera datos oficiales ni precarga las 28 unidades:
 * todos los registros son de demostración y se crean sólo dentro de esta clase.
 */
public class CatalogosGestionIntegralStageDemo {

    public static void main(String[] args) {
        AppContext ctx = new AppContext();
        UEBService uebs = ctx.getUebService();
        NaveService naves = ctx.getNaveService();
        UnidadAsociadaService unidades = ctx.getUnidadAsociadaService();
        ProductoService productos = ctx.getProductoService();
        DestinoService destinos = ctx.getDestinoService();
        ProductoDestinoService relaciones = ctx.getProductoDestinoService();
        ValeEntregaService vales = ctx.getValeEntregaService();
        ParteNaveService partesNave = ctx.getParteNaveService();
        ParteUnidadAsociadaService partesUnidad = ctx.getParteUnidadAsociadaService();
        ProduccionCarneService carnes = ctx.getProduccionCarneService();
        ConsolidadoUEBService consolidados = ctx.getConsolidadoUEBService();

        // 1) UEB: registro, consulta por ID y por código.
        System.out.println("1) UEB: registro, consulta por ID y por código");
        UUID idUebA = UUID.randomUUID();
        uebs.registrar(new UEB(idUebA, "UEB Demo A", "UEB-A", true));
        check(uebs.buscarPorId(idUebA).isPresent(), "UEB registrada y consultable por UUID");
        check(uebs.buscarPorCodigo("ueb-a").isPresent(), "UEB consultable por código (sin mayúsculas)");
        check(uebs.listarTodas().size() == 1, "Listado de UEB devuelve el registro creado");

        // 2) UEB: código único (también ignora mayúsculas y espacios).
        System.out.println("2) UEB: código único y campos obligatorios");
        expectValidation(() -> uebs.registrar(new UEB(UUID.randomUUID(), "Otra UEB", " UEB-A ", true)),
                "código UEB duplicado");
        expectValidation(() -> uebs.registrar(new UEB(UUID.randomUUID(), "Sin código", "   ", true)),
                "código UEB vacío");
        check(uebs.listarTodas().size() == 1, "El rechazo no agregó UEB duplicada");

        // 3) Nave: registro, consulta por UEB y conservación de UEB al editar.
        System.out.println("3) Nave: registro y edición conservando su UEB");
        UUID idNave = UUID.randomUUID();
        naves.registrar(new Nave(idNave, idUebA, "Nave Demo 1", "NAV-1", true));
        check(naves.buscarPorUEB(idUebA).size() == 1, "Nave consultable por UEB");
        naves.actualizar(new Nave(idNave, UUID.randomUUID(), "Nave Demo 1 editada", "NAV-1-E", true));
        Nave naveEditada = naves.buscarPorId(idNave).orElseThrow();
        check(idUebA.equals(naveEditada.getIdUEB()), "La edición normal conserva la UEB asociada");
        check("Nave Demo 1 editada".equals(naveEditada.getNombre()), "Nave editada correctamente");

        // 4) Nave: UEB existente y activa; cambio explícito validado.
        System.out.println("4) Nave: UEB activa y cambio explícito de UEB");
        UUID idUebB = UUID.randomUUID();
        UUID idUebInactiva = UUID.randomUUID();
        uebs.registrar(new UEB(idUebB, "UEB Demo B", "UEB-B", true));
        uebs.registrar(new UEB(idUebInactiva, "UEB Demo C", "UEB-C", false));
        expectValidation(() -> naves.registrar(new Nave(UUID.randomUUID(), UUID.randomUUID(), "Nave X", "NAV-X", true)),
                "UEB inexistente en nave");
        expectValidation(() -> naves.registrar(new Nave(UUID.randomUUID(), idUebInactiva, "Nave Y", "NAV-Y", true)),
                "UEB desactivada en nave");
        naves.cambiarUEB(idNave, idUebB);
        check(idUebB.equals(naves.buscarPorId(idNave).orElseThrow().getIdUEB()), "Cambio explícito de UEB aplicado");
        expectValidation(() -> naves.cambiarUEB(idNave, idUebInactiva), "cambio a UEB desactivada");
        check(naves.buscarPorUEB(idUebA).isEmpty(), "La nave ya no figura en la UEB anterior");

        // 5) Unidad Asociada: registro sin vínculo UEB ni precarga inventada.
        System.out.println("5) Unidad Asociada: registro sin asignación a UEB");
        UUID idUnidad = UUID.randomUUID();
        unidades.registrar(new UnidadAsociada(idUnidad, "Unidad Demo 1", "UA-1", true));
        check(unidades.buscarPorCodigo("UA-1").isPresent(), "Unidad asociada consultable por código");
        check(unidades.listarTodas().size() == 1, "Sólo la unidad creada en la demo; no se inventaron las 28");
        expectValidation(() -> unidades.registrar(new UnidadAsociada(UUID.randomUUID(), "Otra unidad", "ua-1", true)),
                "código de unidad duplicado");

        // 6) Producto y Destino: registro con estado y unidad de medida.
        System.out.println("6) Producto y Destino: registro con estado y unidad de medida");
        UUID idProd = UUID.randomUUID();
        UUID idDest = UUID.randomUUID();
        productos.registrar(new Producto(idProd, "PROD-1", "Producto Demo", true, "kg"));
        destinos.registrar(new Destino(idDest, "DEST-1", "Destino Demo", true));
        Producto prod = productos.buscarPorId(idProd).orElseThrow();
        Destino dest = destinos.buscarPorId(idDest).orElseThrow();
        check("kg".equals(prod.getUnidadMedida()), "Unidad de medida del producto conservada");
        check(prod.isActiva() && dest.isActiva(), "Producto y destino registrados activos");
        check(productos.buscarPorCodigo("prod-1").isPresent() && destinos.buscarPorCodigo("dest-1").isPresent(),
                "Producto y destino consultables por código");

        // 7) Unicidad por catálogo: el mismo código puede existir en catálogos distintos.
        System.out.println("7) Código repetido en catálogos diferentes");
        UUID idUebMismo = UUID.randomUUID();
        UUID idProdMismo = UUID.randomUUID();
        UUID idDestMismo = UUID.randomUUID();
        uebs.registrar(new UEB(idUebMismo, "UEB Mismo", "MISMO", true));
        productos.registrar(new Producto(idProdMismo, "MISMO", "Producto Mismo", true, null));
        destinos.registrar(new Destino(idDestMismo, "MISMO", "Destino Mismo", true));
        check(uebs.buscarPorCodigo("MISMO").get().getIdUEB().equals(idUebMismo), "Código MISMO en UEB");
        check(productos.buscarPorCodigo("MISMO").get().getIdProducto().equals(idProdMismo), "Código MISMO en Producto");
        check(destinos.buscarPorCodigo("MISMO").get().getIdDestino().equals(idDestMismo), "Código MISMO en Destino");

        // 8) Activación/desactivación: baja lógica sin pérdida de consulta.
        System.out.println("8) Activación y desactivación de catálogos (baja lógica)");
        uebs.desactivar(idUebMismo);
        productos.desactivar(idProdMismo);
        destinos.desactivar(idDestMismo);
        check(!uebs.buscarPorId(idUebMismo).orElseThrow().isActiva(), "UEB desactivada conservada");
        check(!productos.buscarPorId(idProdMismo).orElseThrow().isActiva(), "Producto desactivado conservado");
        check(!destinos.buscarPorId(idDestMismo).orElseThrow().isActiva(), "Destino desactivado conservado");
        uebs.activar(idUebMismo);
        check(uebs.buscarPorId(idUebMismo).orElseThrow().isActiva(), "UEB reactivada correctamente");

        // 9) Elementos desactivados no se seleccionan para nuevas operaciones.
        System.out.println("9) Catálogos desactivados bloquean nuevas operaciones");
        UUID idParteNave = UUID.randomUUID();
        ParteNave pn = new ParteNave();
        pn.setIdParte(idParteNave);
        pn.setIdNave(idNave);
        pn.setFechaProduccion(LocalDate.of(2026, 1, 10));
        pn.setGallinasVivas(0);
        pn.setGallinasMuertas(0);
        pn.setGallinasEnfermas(0);
        pn.setHuevosProducidos(0);
        pn.setHuevosRotos(0);
        partesNave.registrar(pn);
        UUID idParteUnidad = UUID.randomUUID();
        ParteUnidadAsociada pu = new ParteUnidadAsociada();
        pu.setIdParteAsociado(idParteUnidad);
        pu.setIdUnidad(idUnidad);
        pu.setFechaProduccion(LocalDate.of(2026, 1, 10));
        partesUnidad.registrar(pu);
        UUID idCarne = UUID.randomUUID();
        ProduccionCarne pc = new ProduccionCarne();
        pc.setIdProduccion(idCarne);
        pc.setIdUEB(idUebA);
        pc.setFechaProduccion(LocalDate.of(2026, 1, 10));
        pc.setCantidadToneladas(BigDecimal.ONE);
        carnes.registrar(pc);
        UUID idConsolidado = UUID.randomUUID();
        ConsolidadoUEB con = new ConsolidadoUEB();
        con.setIdConsolidado(idConsolidado);
        con.setIdUEB(idUebA);
        con.setFechaInicio(LocalDate.of(2026, 1, 1));
        con.setFechaFin(LocalDate.of(2026, 1, 31));
        consolidados.registrar(con);
        check(partesNave.buscarPorNave(idNave).size() == 1, "Parte de nave de demostración registrado");
        check(partesUnidad.buscarPorUnidad(idUnidad).size() == 1, "Parte de unidad de demostración registrado");

        naves.desactivar(idNave);
        unidades.desactivar(idUnidad);
        uebs.desactivar(idUebA);
        expectValidation(() -> {
            ParteNave p = new ParteNave();
            p.setIdParte(UUID.randomUUID());
            p.setIdNave(idNave);
            p.setFechaProduccion(LocalDate.of(2026, 1, 11));
            p.setGallinasVivas(0);
            p.setGallinasMuertas(0);
            p.setGallinasEnfermas(0);
            p.setHuevosProducidos(0);
            p.setHuevosRotos(0);
            partesNave.registrar(p);
        }, "nuevo parte con nave desactivada");
        expectValidation(() -> {
            ParteUnidadAsociada p = new ParteUnidadAsociada();
            p.setIdParteAsociado(UUID.randomUUID());
            p.setIdUnidad(idUnidad);
            p.setFechaProduccion(LocalDate.of(2026, 1, 11));
            partesUnidad.registrar(p);
        }, "nuevo parte con unidad desactivada");
        expectValidation(() -> {
            ProduccionCarne p = new ProduccionCarne();
            p.setIdProduccion(UUID.randomUUID());
            p.setIdUEB(idUebA);
            p.setFechaProduccion(LocalDate.of(2026, 1, 11));
            p.setCantidadToneladas(BigDecimal.ONE);
            carnes.registrar(p);
        }, "nueva carne con UEB desactivada");
        expectValidation(() -> {
            ConsolidadoUEB c = new ConsolidadoUEB();
            c.setIdConsolidado(UUID.randomUUID());
            c.setIdUEB(idUebA);
            c.setFechaInicio(LocalDate.of(2026, 1, 1));
            c.setFechaFin(LocalDate.of(2026, 1, 31));
            consolidados.registrar(c);
        }, "nuevo consolidado con UEB desactivada");
        check(partesNave.buscarPorId(idParteNave).isPresent(), "El parte histórico sigue consultable");

        // 10) Relaciones Producto-Destino: activos, duplicados y catálogos inactivos.
        System.out.println("10) Relaciones Producto-Destino válidas y rechazadas");
        UUID idRel = UUID.randomUUID();
        relaciones.registrar(new ProductoDestino(idRel, idProd, idDest, true));
        check(relaciones.buscarPorId(idRel).isPresent(), "Relación registrada con producto y destino activos");
        expectValidation(() -> relaciones.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true)),
                "relación duplicada");
        expectValidation(() -> relaciones.registrar(new ProductoDestino(UUID.randomUUID(), idProdMismo, idDest, true)),
                "relación con producto desactivado");
        expectValidation(() -> relaciones.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDestMismo, true)),
                "relación con destino desactivado");
        check(relaciones.listarTodas().size() == 1, "Sólo la relación válida quedó registrada");

        // 11) Vales: exige catálogos y relación activos; el histórico permanece.
        System.out.println("11) Vales con catálogos y relación activos");
        ValeEntrega vale = new ValeEntrega();
        vale.setIdVale(UUID.randomUUID());
        vale.setIdUEB(idUebB);
        vale.setIdProducto(idProd);
        vale.setIdDestino(idDest);
        vale.setCantidadDespachada(BigDecimal.TEN);
        vale.setUnidadMedida("kg");
        vale.setFechaHoraEmision(LocalDateTime.of(2026, 1, 12, 9, 0));
        ValeEntrega emitido = vales.emitir(vale);
        check(ValeEntrega.ESTADO_EMITIDO.equals(emitido.getEstado()), "Vale emitido con estado EMITIDO");
        check(vales.buscarPorId(emitido.getIdVale()).isPresent(), "Vale histórico conservado");

        relaciones.desactivar(idRel);
        expectValidation(() -> emitirValeDuplicado(vales, idUebB, idProd, idDest), "relación inactiva en nuevo vale");
        relaciones.activar(idRel);
        productos.desactivar(idProd);
        expectValidation(() -> emitirValeDuplicado(vales, idUebB, idProd, idDest), "producto inactivo en nuevo vale");
        productos.activar(idProd);
        destinos.desactivar(idDest);
        expectValidation(() -> emitirValeDuplicado(vales, idUebB, idProd, idDest), "destino inactivo en nuevo vale");
        destinos.activar(idDest);
        check(vales.buscarPorId(emitido.getIdVale()).isPresent(), "El vale histórico no se alteró");

        // 12) Baja física bloqueada para registros con operaciones asociadas.
        System.out.println("12) Protección de baja física de catálogos usados");
        expectValidation(() -> uebs.eliminar(idUebA), "eliminar UEB con operaciones");
        expectValidation(() -> naves.eliminar(idNave), "eliminar nave con partes");
        expectValidation(() -> unidades.eliminar(idUnidad), "eliminar unidad con partes");
        expectValidation(() -> productos.eliminar(idProd), "eliminar producto con vales/relaciones");
        expectValidation(() -> destinos.eliminar(idDest), "eliminar destino con vales/relaciones");
        expectValidation(() -> relaciones.eliminar(idRel), "eliminar relación con vales");
        check(uebs.buscarPorId(idUebA).isPresent() && productos.buscarPorId(idProd).isPresent()
                && relaciones.buscarPorId(idRel).isPresent(), "Los registros protegidos siguen existiendo");

        // 13) Relación usada por vale: se puede desactivar, no cambiar de producto/destino.
        System.out.println("13) Relación histórica: cambios de asociación bloqueados");
        expectValidation(() -> relaciones.actualizar(
                new ProductoDestino(idRel, idProd, UUID.randomUUID(), true)), "cambiar destino de relación con vale");
        relaciones.desactivar(idRel);
        check(!relaciones.buscarPorId(idRel).orElseThrow().isActiva(), "Relación usada por vale desactivada (baja lógica)");
        check(relaciones.buscarPorId(idRel).isPresent(), "Relación usada por vale no eliminada");
        ValeEntrega historico = vales.buscarPorId(emitido.getIdVale()).orElseThrow();
        check(idProd.equals(historico.getIdProducto()) && idDest.equals(historico.getIdDestino()),
                "El vale histórico conserva producto y destino originales");

        // 14) La aplicación real no precarga catálogos ni las 28 unidades.
        System.out.println("14) Catálogos reales sin datos de demostración al iniciar");
        AppContext fresh = new AppContext();
        if (fresh.esMemoria()) {
            check(fresh.getUebService().listarTodas().isEmpty(), "UEB vacías al iniciar");
            check(fresh.getNaveService().listarTodas().isEmpty(), "Naves vacías al iniciar");
            check(fresh.getUnidadAsociadaService().listarTodas().isEmpty(), "Unidades asociadas vacías al iniciar");
            check(fresh.getProductoService().listarTodos().isEmpty(), "Productos vacíos al iniciar");
            check(fresh.getDestinoService().listarTodos().isEmpty(), "Destinos vacíos al iniciar");
            check(fresh.getProductoDestinoService().listarTodas().isEmpty(), "Relaciones vacías al iniciar");
        } else {
            // En PostgreSQL los catálogos contienen lo ya persistido por este propio demo;
            // lo que se verifica es que AppContext NO siembre datos ficticios DEMO-.
            check(fresh.getUebService().listarTodas().stream()
                            .noneMatch(u -> u.getCodigo() != null && u.getCodigo().startsWith("DEMO-")),
                    "AppContext sembró UEB DEMO- en PostgreSQL");
            check(fresh.getNaveService().listarTodas().stream()
                            .noneMatch(n -> n.getCodigo() != null && n.getCodigo().startsWith("DEMO-")),
                    "AppContext sembró naves DEMO- en PostgreSQL");
            check(fresh.getUnidadAsociadaService().listarTodas().stream()
                            .noneMatch(u -> u.getCodigo() != null && u.getCodigo().startsWith("DEMO-")),
                    "AppContext sembró unidades DEMO- en PostgreSQL");
            check(fresh.getProductoService().listarTodos().stream()
                            .noneMatch(p -> p.getCodigo() != null && p.getCodigo().startsWith("DEMO-")),
                    "AppContext sembró productos DEMO- en PostgreSQL");
            check(fresh.getDestinoService().listarTodos().stream()
                            .noneMatch(d -> d.getCodigo() != null && d.getCodigo().startsWith("DEMO-")),
                    "AppContext sembró destinos DEMO- en PostgreSQL");
        }

        System.out.println("\n==> Verificación automatizada de Etapa 4.13 finalizada correctamente (14/14).");
    }

    private static void emitirValeDuplicado(ValeEntregaService vales, UUID idUeb, UUID idProd, UUID idDest) {
        ValeEntrega v = new ValeEntrega();
        v.setIdVale(UUID.randomUUID());
        v.setIdUEB(idUeb);
        v.setIdProducto(idProd);
        v.setIdDestino(idDest);
        v.setCantidadDespachada(BigDecimal.ONE);
        v.setUnidadMedida("kg");
        v.setFechaHoraEmision(LocalDateTime.of(2026, 1, 13, 9, 0));
        vales.emitir(v);
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
