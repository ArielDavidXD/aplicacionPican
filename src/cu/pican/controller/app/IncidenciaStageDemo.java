package cu.pican.controller.app;

import cu.pican.model.Destino;
import cu.pican.model.Incidencia;
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
 * Verificación automatizada de la Etapa 4.8 (Gestión de incidencias de entrega, CU-12).
 * Usa repositorios aislados. No modifica datos de la aplicación ni inventa catálogos oficiales.
 */
public class IncidenciaStageDemo {

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
        RecepcionService recepcionService = new RecepcionService(recepRepo, valeService, null);
        IncidenciaService incidenciaService = new IncidenciaService(incRepo, recepcionService, valeService);
        recepcionService.setIncidenciaService(incidenciaService);

        UUID idUeb = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb, "UEB Incidencias", "UEBINC", true));
        UUID idProd = UUID.randomUUID();
        productoService.registrar(new Producto(idProd, "PINC", "Producto incidencias", true, "kg"));
        UUID idDest = UUID.randomUUID();
        destinoService.registrar(new Destino(idDest, "DINC", "Destino incidencias", true));
        relService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true));

        LocalDateTime base = LocalDateTime.of(2026, 11, 19, 8, 0, 0);

        ValeEntrega valeA = emitir(valeService, idUeb, idDest, idProd, "10.00", base.plusHours(1));
        ValeEntrega valeB = emitir(valeService, idUeb, idDest, idProd, "5.00", base.plusHours(2));
        ValeEntrega valeC = emitir(valeService, idUeb, idDest, idProd, "8.00", base.plusHours(3));

        Recepcion recA = recibir(recepcionService, valeA, "8.00", base.plusHours(4));
        Recepcion recB = recibir(recepcionService, valeB, "4.00", base.plusHours(5));
        Recepcion recC = recibir(recepcionService, valeC, "7.00", base.plusHours(6));

        Incidencia incA = unica(incidenciaService.buscarPorRecepcion(recA.getIdRecepcion()));
        Incidencia incB1 = unica(incidenciaService.buscarPorRecepcion(recB.getIdRecepcion()));
        Incidencia incC = unica(incidenciaService.buscarPorRecepcion(recC.getIdRecepcion()));

        // 15. No duplicar la incidencia mínima creada por CU-11
        System.out.println("15) No duplicar incidencia mínima de CU-11");
        check(incidenciaService.buscarPorVale(valeA.getIdVale()).size() == 1, "Una sola incidencia automática por recepción NO_CONFORME");
        check(Incidencia.ESTADO_ABIERTA.equals(incA.getEstado()), "Estado inicial ABIERTA");
        check(incA.getTipo() == null && incA.getMedidaAcordada() == null, "Incidencia mínima pendiente de completar en CU-12");
        check("Faltan 2.00 unidades en el empaque".equals(incA.getObservaciones()), "Se conservan las observaciones originales de la recepción");

        // 2 a 6. Rechazos de validación al completar
        System.out.println("2-6) Validaciones al completar incidencia");
        expectValidation(() -> incidenciaService.completar(incA.getIdIncidencia(), "DESCONOCIDO",
                new BigDecimal("2.00"), "Faltan unidades en el empaque", "Reponer producto", null), "tipo no permitido");
        expectValidation(() -> incidenciaService.completar(incA.getIdIncidencia(), Incidencia.TIPO_FALTANTE,
                BigDecimal.ZERO, "Faltan unidades en el empaque", "Reponer producto", null), "cantidad cero");
        expectValidation(() -> incidenciaService.completar(incA.getIdIncidencia(), Incidencia.TIPO_FALTANTE,
                new BigDecimal("-1.00"), "Faltan unidades en el empaque", "Reponer producto", null), "cantidad negativa");
        expectValidation(() -> incidenciaService.completar(incA.getIdIncidencia(), Incidencia.TIPO_FALTANTE,
                new BigDecimal("11.00"), "Faltan unidades en el empaque", "Reponer producto", null), "cantidad superior a la despachada");
        expectValidation(() -> incidenciaService.completar(incA.getIdIncidencia(), Incidencia.TIPO_FALTANTE,
                new BigDecimal("2.00"), "corto", "Reponer producto", null), "descripción con menos de 10 caracteres");
        expectValidation(() -> incidenciaService.completar(incA.getIdIncidencia(), Incidencia.TIPO_FALTANTE,
                new BigDecimal("2.00"), "Faltan unidades en el empaque", "   ", null), "medida acordada vacía");

        // 1 y 7. Completar incidencia de CU-11 con fecha compromiso vacía
        System.out.println("1 y 7) Completar incidencia generada por CU-11; fecha compromiso vacía aceptada");
        Incidencia incACompleta = incidenciaService.completar(incA.getIdIncidencia(), Incidencia.TIPO_FALTANTE,
                new BigDecimal("2.00"), "Faltan dos unidades en el empaque", "Reponer producto", null);
        check(incACompleta.getIdIncidencia().equals(incA.getIdIncidencia()), "Se conserva el UUID original");
        check(incACompleta.getIdVale().equals(valeA.getIdVale()), "Se conserva la asociación con el vale");
        check(incACompleta.getIdRecepcion().equals(recA.getIdRecepcion()), "Se conserva la asociación con la recepción");
        check("Faltan 2.00 unidades en el empaque".equals(incACompleta.getObservaciones()), "No se borran las observaciones originales");
        check(Incidencia.ESTADO_ABIERTA.equals(incACompleta.getEstado()), "Tras completar sigue ABIERTA");
        check(incACompleta.getFechaCompromiso() == null, "Fecha compromiso vacía aceptada");
        check(incidenciaService.buscarPorVale(valeA.getIdVale()).size() == 1, "Completar no duplica la incidencia");

        // 8. Fecha compromiso válida, sin exigir futura
        System.out.println("8) Fecha compromiso pasada aceptada");
        LocalDateTime fechaPasada = LocalDateTime.of(2020, 1, 2, 10, 30);
        Incidencia incBCompleta = incidenciaService.completar(incB1.getIdIncidencia(), Incidencia.TIPO_CALIDAD,
                new BigDecimal("1.00"), "Empaque con defecto visible", "Reponer empaque", fechaPasada);
        check(fechaPasada.equals(incBCompleta.getFechaCompromiso()), "Fecha compromiso pasada conservada sin exigir futura");

        // 10. Rechazar cierre sin resultado
        System.out.println("10) Rechazar cierre sin resultado de solución");
        expectValidation(() -> incidenciaService.cerrar(incB1.getIdIncidencia(), base.plusDays(1), "   "),
                "cierre sin resultado");
        check(Incidencia.ESTADO_ABIERTA.equals(incidenciaService.buscarPorId(incB1.getIdIncidencia()).getEstado()),
                "Incidencia sigue ABIERTA tras cierre rechazado");

        // 9. Cerrar incidencia con fecha y resultado válidos
        System.out.println("9) Cerrar incidencia con fecha y resultado válidos");
        Incidencia incACerrada = incidenciaService.cerrar(incA.getIdIncidencia(), base.plusDays(2), "Reposición entregada y verificada");
        check(Incidencia.ESTADO_CERRADA.equals(incACerrada.getEstado()), "Incidencia cerrada");
        check(incACerrada.getFechaCierre() != null && "Reposición entregada y verificada".equals(incACerrada.getResultadoSolucion()),
                "Se registran fecha de cierre y resultado");
        check(ValeEntrega.ESTADO_CERRADO.equals(valeService.buscarPorId(valeA.getIdVale()).orElseThrow().getEstado()),
                "Vale A cerrado al cerrar su única incidencia");

        // 11. No reabrir incidencia cerrada
        System.out.println("11) No reabrir incidencia cerrada");
        expectValidation(() -> incidenciaService.cerrar(incA.getIdIncidencia(), base.plusDays(3), "Otro resultado"),
                "cierre de incidencia ya cerrada");
        expectValidation(() -> incidenciaService.completar(incA.getIdIncidencia(), Incidencia.TIPO_OTRO,
                new BigDecimal("1.00"), "Intento de modificación posterior", "Otra medida", null),
                "modificación de incidencia cerrada");

        // 14 y 12. Varias incidencias por vale; el vale sigue con incidencias abiertas
        System.out.println("14 y 12) Varias incidencias por vale y cierre parcial");
        Incidencia incB2 = incidenciaService.registrarOtraParaLaMismaRecepcion(recB.getIdRecepcion(), "Sobrante detectado al descargar");
        check(incB2.getIdIncidencia() != incB1.getIdIncidencia(), "Incidencia adicional con identidad propia");
        check(incB2.getIdVale().equals(valeB.getIdVale()) && incB2.getIdRecepcion().equals(recB.getIdRecepcion()),
                "Incidencia adicional conserva las asociaciones originales");
        check(incidenciaService.buscarPorVale(valeB.getIdVale()).size() == 2, "Dos incidencias asociadas al mismo vale");
        Incidencia incB2Completa = incidenciaService.completar(incB2.getIdIncidencia(), Incidencia.TIPO_SOBRANTE,
                new BigDecimal("0.50"), "Sobrante de producto al descargar", "Ajustar registro", null);
        check(Incidencia.ESTADO_ABIERTA.equals(incB2Completa.getEstado()), "Incidencia adicional ABIERTA");
        incidenciaService.cerrar(incB1.getIdIncidencia(), base.plusDays(2), "Empaque repuesto");
        check(ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(valeService.buscarPorId(valeB.getIdVale()).orElseThrow().getEstado()),
                "Vale permanece RECIBIDO_CON_INCIDENCIA mientras quede una incidencia ABIERTA");

        // 13. Cerrar la última incidencia cierra el vale
        System.out.println("13) Cierre de la última incidencia cierra el vale");
        incidenciaService.cerrar(incB2.getIdIncidencia(), base.plusDays(3), "Sobrante conciliado");
        check(ValeEntrega.ESTADO_CERRADO.equals(valeService.buscarPorId(valeB.getIdVale()).orElseThrow().getEstado()),
                "Vale B cerrado al cerrar su última incidencia");

        // 16. No modificar cantidades originales
        System.out.println("16) Cantidades originales intactas");
        check(new BigDecimal("10.00").compareTo(valeService.buscarPorId(valeA.getIdVale()).orElseThrow().getCantidadDespachada()) == 0,
                "Cantidad despachada del vale A intacta");
        check(new BigDecimal("8.00").compareTo(recepcionService.buscarPorId(recA.getIdRecepcion()).orElseThrow().getCantidadRecibida()) == 0,
                "Cantidad recibida de la recepción A intacta");

        // 18. Consultas por vale y estado
        System.out.println("18) Consultas por vale y estado");
        check(incidenciaService.buscarPorVale(valeA.getIdVale()).size() == 1, "Consulta por vale A");
        check(incidenciaService.buscarPorVale(valeB.getIdVale()).size() == 2, "Consulta por vale B");
        check(incidenciaService.buscarPorVale(valeC.getIdVale()).size() == 1, "Consulta por vale C");
        List<Incidencia> cerradas = incidenciaService.buscarPorEstado(Incidencia.ESTADO_CERRADA);
        check(cerradas.size() == 3, "Consulta por estado CERRADA (3 incidencias)");
        check(incidenciaService.buscarPorEstado(Incidencia.ESTADO_ABIERTA).size() == 1, "Consulta por estado ABIERTA (1 incidencia)");
        check(incidenciaService.buscarPorRecepcion(recB.getIdRecepcion()).size() == 2, "Consulta por recepción con varias incidencias");

        // 17. Coherencia ante fallos de guardado
        System.out.println("17) Coherencia ante fallos de guardado");
        verificarFalloGuardadoIncidencia(base);
        verificarFalloCierreVale(base);

        System.out.println("\n==> Verificación automatizada de Etapa 4.8 (CU-12) finalizada correctamente.");
        System.out.println("Nota: no existe mecanismo de eventos/historial del vale; se usa el campo version existente y se documenta la propuesta de log inmutable.");
    }

    private static void verificarFalloGuardadoIncidencia(LocalDateTime base) {
        UEBRepository uebRepo = new UEBRepositoryMemory();
        ProductoRepository prodRepo = new ProductoRepositoryMemory();
        DestinoRepository destRepo = new DestinoRepositoryMemory();
        ProductoDestinoRepository relRepo = new ProductoDestinoRepositoryMemory();
        ValeEntregaRepository valeRepo = new ValeEntregaRepositoryMemory();
        RecepcionRepository recepRepo = new RecepcionRepositoryMemory();
        IncidenciaRepository incRepo = new IncidenciaRepositoryMemory() {
            @Override
            public Incidencia update(Incidencia entity) {
                throw new RuntimeException("Fallo simulado al actualizar incidencia");
            }
        };

        UEBService uebService = new UEBService(uebRepo);
        ProductoService productoService = new ProductoService(prodRepo);
        DestinoService destinoService = new DestinoService(destRepo);
        ProductoDestinoService relService = new ProductoDestinoService(relRepo, prodRepo, destRepo);
        ValeEntregaService valeService = new ValeEntregaService(valeRepo, uebService, productoService, destinoService, relService);
        RecepcionService recepcionService = new RecepcionService(recepRepo, valeService, null);
        IncidenciaService incidenciaService = new IncidenciaService(incRepo, recepcionService, valeService);
        recepcionService.setIncidenciaService(incidenciaService);

        UUID idUeb = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb, "UEB Fallo", "UEBFALLO", true));
        UUID idProd = UUID.randomUUID();
        productoService.registrar(new Producto(idProd, "PFALLO", "Producto fallo", true, "kg"));
        UUID idDest = UUID.randomUUID();
        destinoService.registrar(new Destino(idDest, "DFALLO", "Destino fallo", true));
        relService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true));

        ValeEntrega vale = emitir(valeService, idUeb, idDest, idProd, "4.00", base.plusHours(10));
        Recepcion recepcion = recibir(recepcionService, vale, "3.00", base.plusHours(11));
        Incidencia incidencia = unica(incidenciaService.buscarPorRecepcion(recepcion.getIdRecepcion()));

        try {
            incidenciaService.completar(incidencia.getIdIncidencia(), Incidencia.TIPO_FALTANTE,
                    new BigDecimal("1.00"), "Falta una unidad en el empaque", "Reponer producto", null);
            throw new IllegalStateException("Se esperaba fallo al actualizar incidencia");
        } catch (RuntimeException ex) {
            check("Fallo simulado al actualizar incidencia".equals(ex.getMessage()), "Se propaga fallo al guardar incidencia");
        }
        Incidencia almacenada = incRepo.findById(incidencia.getIdIncidencia()).orElseThrow();
        check(Incidencia.ESTADO_ABIERTA.equals(almacenada.getEstado()) && almacenada.getTipo() == null,
                "Incidencia almacenada permanece ABIERTA y sin cambios tras fallo de guardado");
    }

    private static void verificarFalloCierreVale(LocalDateTime base) {
        UEBRepository uebRepo = new UEBRepositoryMemory();
        ProductoRepository prodRepo = new ProductoRepositoryMemory();
        DestinoRepository destRepo = new DestinoRepositoryMemory();
        ProductoDestinoRepository relRepo = new ProductoDestinoRepositoryMemory();
        ValeEntregaRepository valeRepo = new ValeEntregaRepositoryMemory() {
            @Override
            public ValeEntrega update(ValeEntrega entity) {
                if (ValeEntrega.ESTADO_CERRADO.equals(entity.getEstado())) {
                    throw new RuntimeException("Fallo simulado al cerrar vale");
                }
                return super.update(entity);
            }
        };
        RecepcionRepository recepRepo = new RecepcionRepositoryMemory();
        IncidenciaRepository incRepo = new IncidenciaRepositoryMemory();

        UEBService uebService = new UEBService(uebRepo);
        ProductoService productoService = new ProductoService(prodRepo);
        DestinoService destinoService = new DestinoService(destRepo);
        ProductoDestinoService relService = new ProductoDestinoService(relRepo, prodRepo, destRepo);
        ValeEntregaService valeService = new ValeEntregaService(valeRepo, uebService, productoService, destinoService, relService);
        RecepcionService recepcionService = new RecepcionService(recepRepo, valeService, null);
        IncidenciaService incidenciaService = new IncidenciaService(incRepo, recepcionService, valeService);
        recepcionService.setIncidenciaService(incidenciaService);

        UUID idUeb = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb, "UEB Fallo Vale", "UEBFV", true));
        UUID idProd = UUID.randomUUID();
        productoService.registrar(new Producto(idProd, "PFV", "Producto fallo vale", true, "kg"));
        UUID idDest = UUID.randomUUID();
        destinoService.registrar(new Destino(idDest, "DFV", "Destino fallo vale", true));
        relService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true));

        ValeEntrega vale = emitir(valeService, idUeb, idDest, idProd, "6.00", base.plusHours(12));
        Recepcion recepcion = recibir(recepcionService, vale, "5.00", base.plusHours(13));
        Incidencia incidencia = unica(incidenciaService.buscarPorRecepcion(recepcion.getIdRecepcion()));
        incidenciaService.completar(incidencia.getIdIncidencia(), Incidencia.TIPO_FALTANTE,
                new BigDecimal("1.00"), "Falta una unidad en el empaque", "Reponer producto", null);

        try {
            incidenciaService.cerrar(incidencia.getIdIncidencia(), base.plusDays(1), "Reposición confirmada");
            throw new IllegalStateException("Se esperaba fallo al cerrar vale");
        } catch (RuntimeException ex) {
            check("Fallo simulado al cerrar vale".equals(ex.getMessage()), "Se propaga fallo al cerrar vale");
        }
        check(Incidencia.ESTADO_ABIERTA.equals(incRepo.findById(incidencia.getIdIncidencia()).orElseThrow().getEstado()),
                "Incidencia permanece ABIERTA si falla el cierre del vale");
        check(ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(valeRepo.findById(vale.getIdVale()).orElseThrow().getEstado()),
                "Vale permanece RECIBIDO_CON_INCIDENCIA si falla su cierre");
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
                                     LocalDateTime fechaHora) {
        Recepcion r = new Recepcion();
        r.setIdVale(vale.getIdVale());
        r.setCantidadRecibida(new BigDecimal(cantidad));
        r.setUnidadMedida(vale.getUnidadMedida());
        r.setResultado(Recepcion.RESULTADO_NO_CONFORME);
        r.setObservaciones("Faltan " + new BigDecimal(vale.getCantidadDespachada().toPlainString())
                .subtract(new BigDecimal(cantidad)).toPlainString() + " unidades en el empaque");
        r.setFechaHoraRecepcion(fechaHora);
        return recepcionService.registrar(r);
    }

    private static Incidencia unica(List<Incidencia> incidencias) {
        if (incidencias.size() != 1) throw new IllegalStateException("Se esperaba una sola incidencia automática");
        return incidencias.get(0);
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
