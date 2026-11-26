package cu.pican.controller.app;

import cu.pican.model.Destino;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.Recepcion;
import cu.pican.model.UEB;
import cu.pican.model.ValeEntrega;
import cu.pican.model.repository.DestinoRepository;
import cu.pican.model.repository.IncidenciaRepository;
import cu.pican.model.repository.ProductoDestinoRepository;
import cu.pican.model.repository.ProductoRepository;
import cu.pican.model.repository.RecepcionRepository;
import cu.pican.model.repository.UEBRepository;
import cu.pican.model.repository.ValeEntregaRepository;
import cu.pican.model.repository.memory.DestinoRepositoryMemory;
import cu.pican.model.repository.memory.IncidenciaRepositoryMemory;
import cu.pican.model.repository.memory.ProductoDestinoRepositoryMemory;
import cu.pican.model.repository.memory.ProductoRepositoryMemory;
import cu.pican.model.repository.memory.RecepcionRepositoryMemory;
import cu.pican.model.repository.memory.UEBRepositoryMemory;
import cu.pican.model.repository.memory.ValeEntregaRepositoryMemory;
import cu.pican.controller.service.DestinoService;
import cu.pican.controller.service.IncidenciaService;
import cu.pican.controller.service.ProductoDestinoService;
import cu.pican.controller.service.ProductoService;
import cu.pican.controller.service.RecepcionService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.ValeEntregaService;
import cu.pican.controller.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Verificación automatizada de la Etapa 4.9 (Anulación de vales de entrega, CU-13).
 * Usa repositorios aislados; no modifica los datos de la aplicación.
 */
public class ValeAnulacionStageDemo {

    public static void main(String[] args) {
        cu.pican.controller.app.AutorizacionDemo.instalarTodosLosRoles();
        UEBRepository uebRepo = new UEBRepositoryMemory();
        ProductoRepository prodRepo = new ProductoRepositoryMemory();
        DestinoRepository destRepo = new DestinoRepositoryMemory();
        ProductoDestinoRepository relRepo = new ProductoDestinoRepositoryMemory();
        ValeEntregaRepository valeRepo = new ValeEntregaRepositoryMemory();
        RecepcionRepository recepRepo = new RecepcionRepositoryMemory();
        IncidenciaRepository incRepo = new IncidenciaRepositoryMemory();

        UEBService uebService = new UEBService(uebRepo);
        ProductoService productoService = new ProductoService(prodRepo);
        DestinoService destinoService = new DestinoService(destRepo);
        ProductoDestinoService relService = new ProductoDestinoService(relRepo, prodRepo, destRepo);
        ValeEntregaService valeService = new ValeEntregaService(valeRepo, uebService, productoService, destinoService, relService);
        valeService.setRecepcionRepository(recepRepo);
        RecepcionService recepcionService = new RecepcionService(recepRepo, valeService, null);
        IncidenciaService incidenciaService = new IncidenciaService(incRepo, recepcionService, valeService);
        recepcionService.setIncidenciaService(incidenciaService);

        UUID idUeb = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb, "UEB Anulación", "UEBANU", true));
        UUID idProd = UUID.randomUUID();
        productoService.registrar(new Producto(idProd, "PANU", "Producto anulación", true, "kg"));
        UUID idDest = UUID.randomUUID();
        destinoService.registrar(new Destino(idDest, "DANU", "Destino anulación", true));
        relService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true));

        LocalDateTime base = LocalDateTime.of(2026, 11, 19, 8, 0, 0);

        ValeEntrega valeAnular = emitir(valeService, idUeb, idDest, idProd, "10.00", base.plusHours(1));
        ValeEntrega valeCerrado = emitir(valeService, idUeb, idDest, idProd, "8.00", base.plusHours(2));
        ValeEntrega valeConIncidencia = emitir(valeService, idUeb, idDest, idProd, "7.00", base.plusHours(3));
        ValeEntrega valeConRecepcion = emitir(valeService, idUeb, idDest, idProd, "6.00", base.plusHours(4));
        ValeEntrega valeControl = emitir(valeService, idUeb, idDest, idProd, "5.00", base.plusHours(5));

        recibir(recepcionService, valeCerrado, "8.00", Recepcion.RESULTADO_CONFORME, null, base.plusHours(6));
        recibir(recepcionService, valeConIncidencia, "6.00", Recepcion.RESULTADO_NO_CONFORME,
                "Falta una unidad en el empaque", base.plusHours(7));

        // Recepción registrada directamente para dejar el vale en EMITIDO y probar la regla 4 por separado
        Recepcion recepcionDirecta = new Recepcion();
        recepcionDirecta.setIdRecepcion(UUID.randomUUID());
        recepcionDirecta.setIdVale(valeConRecepcion.getIdVale());
        recepcionDirecta.setCantidadRecibida(new BigDecimal("6.00"));
        recepcionDirecta.setUnidadMedida("kg");
        recepcionDirecta.setResultado(Recepcion.RESULTADO_CONFORME);
        recepcionDirecta.setFechaHoraRecepcion(base.plusHours(8));
        recepcionDirecta.setFechaCreacion(LocalDateTime.now());
        recepcionDirecta.setVersion(1L);
        recepRepo.save(recepcionDirecta);

        String numeroOriginal = valeAnular.getNumeroVale();
        UUID idUebOriginal = valeAnular.getIdUEB();
        UUID idDestinoOriginal = valeAnular.getIdDestino();
        UUID idProductoOriginal = valeAnular.getIdProducto();
        BigDecimal cantidadOriginal = valeAnular.getCantidadDespachada();
        String unidadOriginal = valeAnular.getUnidadMedida();
        LocalDateTime emisionOriginal = valeAnular.getFechaHoraEmision();
        LocalDateTime creacionOriginal = valeAnular.getFechaCreacion();
        long versionOriginal = valeAnular.getVersion();
        check(versionOriginal == 1L, "Versión inicial del vale: 1");

        // 1. Anulación correcta
        System.out.println("1) Anular vale EMITIDO correctamente");
        String motivo = "Error detectado en la emisión del vale";
        ValeEntrega anulado = valeService.anular(valeAnular.getIdVale(), motivo, true, null);
        check(ValeEntrega.ESTADO_ANULADO.equals(anulado.getEstado()), "El vale queda en estado ANULADO");
        check(valeService.buscarPorId(valeAnular.getIdVale()).isPresent(), "El vale sigue existiendo (anulación lógica)");

        // 2. Identificador inexistente
        System.out.println("2) Rechazar identificador inexistente");
        expectValidation(() -> valeService.anular(UUID.randomUUID(), motivo, true, null), "vale inexistente");

        // 3 y 4. Motivo vacío / solo espacios
        System.out.println("3 y 4) Rechazar motivo vacío y solo espacios");
        expectValidation(() -> valeService.anular(valeControl.getIdVale(), "", true, null), "motivo vacío");
        expectValidation(() -> valeService.anular(valeControl.getIdVale(), "   ", true, null), "motivo con solo espacios");

        // 5. Vale CERRADO
        System.out.println("5) Rechazar vale CERRADO");
        expectValidation(() -> valeService.anular(valeCerrado.getIdVale(), motivo, true, null), "vale CERRADO");

        // 6. Vale RECIBIDO_CON_INCIDENCIA
        System.out.println("6) Rechazar vale RECIBIDO_CON_INCIDENCIA");
        expectValidation(() -> valeService.anular(valeConIncidencia.getIdVale(), motivo, true, null),
                "vale RECIBIDO_CON_INCIDENCIA");

        // 7. Vale con recepción registrada
        System.out.println("7) Rechazar vale con recepción asociada");
        expectValidation(() -> valeService.anular(valeConRecepcion.getIdVale(), motivo, true, null),
                "vale con recepción registrada");

        // 8. Sin confirmación explícita de salida física
        System.out.println("8) Rechazar sin confirmación de salida física");
        expectValidation(() -> valeService.anular(valeControl.getIdVale(), motivo, false, null),
                "sin confirmación de que el producto no salió");
        check(ValeEntrega.ESTADO_EMITIDO.equals(valeService.buscarPorId(valeControl.getIdVale()).orElseThrow().getEstado()),
                "El vale sigue EMITIDO tras rechazar la confirmación");

        // 9. Vale previamente anulado
        System.out.println("9) Rechazar vale previamente anulado");
        expectValidation(() -> valeService.anular(valeAnular.getIdVale(), motivo, true, null), "vale ya anulado");

        // 10. Datos originales conservados
        System.out.println("10) Datos originales del vale conservados");
        ValeEntrega consultado = valeService.buscarPorId(valeAnular.getIdVale()).orElseThrow();
        check(valeAnular.getIdVale().equals(consultado.getIdVale()), "UUID del vale conservado");
        check(numeroOriginal.equals(consultado.getNumeroVale()), "Número de vale conservado");
        check(idUebOriginal.equals(consultado.getIdUEB()), "UEB original conservada");
        check(idDestinoOriginal.equals(consultado.getIdDestino()), "Destino original conservado");
        check(idProductoOriginal.equals(consultado.getIdProducto()), "Producto original conservado");
        check(cantidadOriginal.compareTo(consultado.getCantidadDespachada()) == 0, "Cantidad despachada conservada");
        check(unidadOriginal.equals(consultado.getUnidadMedida()), "Unidad de medida conservada");
        check(emisionOriginal.equals(consultado.getFechaHoraEmision()), "Fecha de emisión conservada");
        check(creacionOriginal.equals(consultado.getFechaCreacion()), "Fecha de creación conservada");
        check(consultado.getIdUsuario() == null, "Usuario de emisión (opcional) conservado");
        expectValidation(() -> {
            ValeEntrega intento = new ValeEntrega();
            intento.setIdVale(valeAnular.getIdVale());
            valeService.emitir(intento);
        }, "intentar regrabar el vale anulado");
        check(ValeEntrega.ESTADO_ANULADO.equals(valeService.buscarPorId(valeAnular.getIdVale()).orElseThrow().getEstado()),
                "El vale anulado no se modifica al intentar regrabarlo");

        // 11. Motivo y fecha guardados
        System.out.println("11) Motivo y fecha de anulación guardados");
        check(motivo.equals(consultado.getMotivoAnulacion()), "Motivo guardado");
        check(consultado.getFechaHoraAnulacion() != null, "Fecha y hora de anulación guardadas");
        check(consultado.getIdUsuarioAnulacion() == null, "Usuario de anulación opcional sin sesión");

        // 12. Incremento de versión
        System.out.println("12) Incremento de versión");
        check(consultado.getVersion() == versionOriginal + 1, "Versión incrementada de 1 a 2");

        // 13. Vale anulado no puede recibirse
        System.out.println("13) Vale anulado no puede registrarse como recibido");
        int recepcionesAntes = recepRepo.findAll().size();
        expectValidation(() -> recibir(recepcionService, consultado, "10.00", Recepcion.RESULTADO_CONFORME,
                null, base.plusHours(9)), "recepción de vale anulado");
        check(recepRepo.findAll().size() == recepcionesAntes, "No se guarda recepción para el vale anulado");

        // Consulta por estado (incluye ANULADO)
        System.out.println("Consulta por estado");
        List<ValeEntrega> anulados = valeService.filtrarPorEstado(ValeEntrega.ESTADO_ANULADO);
        check(anulados.size() == 1 && anulados.get(0).getIdVale().equals(valeAnular.getIdVale()),
                "Filtro por estado ANULADO devuelve el vale anulado");
        check(valeService.filtrarPorEstado(ValeEntrega.ESTADO_EMITIDO).stream()
                        .noneMatch(v -> v.getIdVale().equals(valeAnular.getIdVale())),
                "El vale anulado no aparece como EMITIDO (pendiente de recepción)");

        // 14. Fallo de guardado: el vale no queda parcialmente modificado
        System.out.println("14) Fallo de guardado sin modificación parcial");
        verificarFalloGuardado(base);

        System.out.println("\n==> Verificación automatizada de Etapa 4.9 (CU-13) finalizada correctamente.");
        System.out.println("Nota: anulación lógica en memoria; no se implementa auditoría general ni sesión de usuario.");
    }

    private static void verificarFalloGuardado(LocalDateTime base) {
        UEBRepository uebRepo = new UEBRepositoryMemory();
        ProductoRepository prodRepo = new ProductoRepositoryMemory();
        DestinoRepository destRepo = new DestinoRepositoryMemory();
        ProductoDestinoRepository relRepo = new ProductoDestinoRepositoryMemory();
        ValeEntregaRepository valeRepo = new ValeEntregaRepositoryMemory() {
            @Override
            public ValeEntrega update(ValeEntrega entity) {
                throw new RuntimeException("Fallo simulado al guardar la anulación");
            }
        };
        RecepcionRepository recepRepo = new RecepcionRepositoryMemory();

        UEBService uebService = new UEBService(uebRepo);
        ProductoService productoService = new ProductoService(prodRepo);
        DestinoService destinoService = new DestinoService(destRepo);
        ProductoDestinoService relService = new ProductoDestinoService(relRepo, prodRepo, destRepo);
        ValeEntregaService valeService = new ValeEntregaService(valeRepo, uebService, productoService, destinoService, relService);
        valeService.setRecepcionRepository(recepRepo);

        UUID idUeb = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb, "UEB Fallo", "UEBFALLO", true));
        UUID idProd = UUID.randomUUID();
        productoService.registrar(new Producto(idProd, "PFALLO", "Producto fallo", true, "kg"));
        UUID idDest = UUID.randomUUID();
        destinoService.registrar(new Destino(idDest, "DFALLO", "Destino fallo", true));
        relService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true));

        ValeEntrega vale = emitir(valeService, idUeb, idDest, idProd, "4.00", base.plusHours(10));
        try {
            valeService.anular(vale.getIdVale(), "Motivo de prueba", true, null);
            throw new IllegalStateException("Se esperaba fallo al guardar la anulación");
        } catch (RuntimeException ex) {
            check("Fallo simulado al guardar la anulación".equals(ex.getMessage()), "Se propaga el fallo de guardado");
        }
        ValeEntrega almacenado = valeRepo.findById(vale.getIdVale()).orElseThrow();
        check(ValeEntrega.ESTADO_EMITIDO.equals(almacenado.getEstado()), "El vale conserva su estado EMITIDO");
        check(almacenado.getMotivoAnulacion() == null && almacenado.getFechaHoraAnulacion() == null,
                "No se registran motivo ni fecha ante el fallo");
        check(almacenado.getVersion() == 1L, "La versión no se incrementa ante el fallo");
    }

    private static ValeEntrega emitir(ValeEntregaService valeService, UUID idUeb, UUID idDest, UUID idProd,
                                      String cantidad, LocalDateTime fechaHora) {
        ValeEntrega v = new ValeEntrega();
        v.setIdVale(UUID.randomUUID());
        v.setIdUEB(idUeb);
        v.setIdDestino(idDest);
        v.setIdProducto(idProd);
        v.setCantidadDespachada(new BigDecimal(cantidad));
        v.setUnidadMedida("kg");
        v.setFechaHoraEmision(fechaHora);
        return valeService.emitir(v);
    }

    private static Recepcion recibir(RecepcionService recepcionService, ValeEntrega vale, String cantidad,
                                     String resultado, String observaciones, LocalDateTime fechaHora) {
        Recepcion r = new Recepcion();
        r.setIdVale(vale.getIdVale());
        r.setCantidadRecibida(new BigDecimal(cantidad));
        r.setUnidadMedida(vale.getUnidadMedida());
        r.setResultado(resultado);
        r.setObservaciones(observaciones);
        r.setFechaHoraRecepcion(fechaHora);
        return recepcionService.registrar(r);
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
