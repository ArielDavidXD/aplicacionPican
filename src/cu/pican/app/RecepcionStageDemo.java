package cu.pican.app;

import cu.pican.model.Destino;
import cu.pican.model.Incidencia;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.Recepcion;
import cu.pican.model.UEB;
import cu.pican.model.ValeEntrega;
import cu.pican.repository.DestinoRepository;
import cu.pican.repository.IncidenciaRepository;
import cu.pican.repository.ProductoDestinoRepository;
import cu.pican.repository.ProductoRepository;
import cu.pican.repository.RecepcionRepository;
import cu.pican.repository.UEBRepository;
import cu.pican.repository.ValeEntregaRepository;
import cu.pican.repository.memory.DestinoRepositoryMemory;
import cu.pican.repository.memory.IncidenciaRepositoryMemory;
import cu.pican.repository.memory.ProductoDestinoRepositoryMemory;
import cu.pican.repository.memory.ProductoRepositoryMemory;
import cu.pican.repository.memory.RecepcionRepositoryMemory;
import cu.pican.repository.memory.UEBRepositoryMemory;
import cu.pican.repository.memory.ValeEntregaRepositoryMemory;
import cu.pican.service.DestinoService;
import cu.pican.service.IncidenciaService;
import cu.pican.service.ProductoDestinoService;
import cu.pican.service.ProductoService;
import cu.pican.service.RecepcionService;
import cu.pican.service.UEBService;
import cu.pican.service.ValeEntregaService;
import cu.pican.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Verificación automatizada de la Etapa 4.7 (Recepción de vales, CU-11).
 * Usa repositorios/servicios aislados para no contaminar los datos de la aplicación.
 * Incluye verificación mínima de generación automática de incidencia en recepciones NO_CONFORME.
 */
public class RecepcionStageDemo {

    public static void main(String[] args) {
        cu.pican.app.AutorizacionDemo.instalarTodosLosRoles();
        // Infraestructura aislada
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

        // Datos base
        UUID idUeb = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb, "UEB Demo", "UEB01", true));
        UUID idProd = UUID.randomUUID();
        productoService.registrar(new Producto(idProd, "P1", "Producto 1", true, "kg"));
        UUID idDest = UUID.randomUUID();
        destinoService.registrar(new Destino(idDest, "D1", "Destino 1", true));
        relService.registrar(new ProductoDestino(UUID.randomUUID(), idProd, idDest, true));

        LocalDateTime base = LocalDateTime.of(2026, 11, 19, 8, 0, 0);

        // Vales de prueba
        ValeEntrega valeConforme = emitirVale(valeService, idUeb, idDest, idProd, "10.00", "kg", base.plusHours(1));
        ValeEntrega valeNoConforme = emitirVale(valeService, idUeb, idDest, idProd, "5.00", "kg", base.plusHours(2));
        ValeEntrega valeParaValidaciones = emitirVale(valeService, idUeb, idDest, idProd, "7.00", "kg", base.plusHours(3));
        ValeEntrega valeParaSegunda = emitirVale(valeService, idUeb, idDest, idProd, "3.00", "kg", base.plusHours(4));
        ValeEntrega valeFalloIncidencia = emitirVale(valeService, idUeb, idDest, idProd, "4.00", "kg", base.plusHours(5));

        // 1 y 2. Recepción conforme y transición EMITIDO -> CERRADO
        System.out.println("1) Recepción conforme");
        Recepcion rConf = nuevaRecepcion(valeConforme.getIdVale(), "10.00", "kg",
                Recepcion.RESULTADO_CONFORME, null, base.plusHours(5));
        Recepcion rConfGuardada = recepcionService.registrar(rConf);
        check(rConfGuardada.getIdRecepcion() != null, "Recepción conforme registrada");
        check(recepcionService.buscarPorVale(valeConforme.getIdVale()).isPresent(), "Recepción consultable por vale");
        check(incidenciaService.buscarPorRecepcion(rConfGuardada.getIdRecepcion()).isEmpty(),
                "Recepción conforme no genera incidencia");
        check(incidenciaService.buscarPorVale(valeConforme.getIdVale()).isEmpty(),
                "Vale de recepción conforme sin incidencias asociadas");

        System.out.println("2) Transición EMITIDO -> CERRADO");
        ValeEntrega valeConfPost = valeService.buscarPorId(valeConforme.getIdVale()).orElseThrow();
        check(ValeEntrega.ESTADO_CERRADO.equals(valeConfPost.getEstado()), "Vale conforme cerrado tras recepción");

        // 3 y 4. Recepción no conforme y transición EMITIDO -> RECIBIDO_CON_INCIDENCIA
        System.out.println("3) Recepción no conforme");
        String obsNoConforme = "Faltante y empaque dañado";
        Recepcion rNoConf = nuevaRecepcion(valeNoConforme.getIdVale(), "4.50", "kg",
                Recepcion.RESULTADO_NO_CONFORME, obsNoConforme, base.plusHours(6));
        Recepcion rNoConfGuardada = recepcionService.registrar(rNoConf);
        check(rNoConfGuardada.getIdRecepcion() != null, "Recepción no conforme registrada");

        System.out.println("4) Transición EMITIDO -> RECIBIDO_CON_INCIDENCIA");
        ValeEntrega valeNoConfPost = valeService.buscarPorId(valeNoConforme.getIdVale()).orElseThrow();
        check(ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(valeNoConfPost.getEstado()),
                "Vale no conforme queda en RECIBIDO_CON_INCIDENCIA");

        System.out.println("4.b) Generación automática de incidencia asociada (mínima, para CU-12)");
        Incidencia incidencia = incidenciaService.buscarPorRecepcion(rNoConfGuardada.getIdRecepcion()).stream().findFirst().orElse(null);
        check(incidencia != null, "Recepción no conforme genera incidencia obligatoriamente");
        check(valeNoConforme.getIdVale().equals(incidencia.getIdVale()), "Incidencia asociada al vale correcto");
        check(rNoConfGuardada.getIdRecepcion().equals(incidencia.getIdRecepcion()), "Incidencia asociada a la recepción que la originó");
        check(obsNoConforme.equals(incidencia.getObservaciones()), "Incidencia conserva observaciones de la recepción");
        check(incidencia.getFechaHoraRegistro() != null, "Incidencia conserva fecha/hora de registro");
        check(!incidenciaService.buscarPorVale(valeNoConforme.getIdVale()).isEmpty(), "Incidencia consultable por vale");

        // 5. Rechazo de vale inexistente
        System.out.println("5) Rechazo de vale inexistente");
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(UUID.randomUUID(), "1.00", "kg", Recepcion.RESULTADO_CONFORME, null, base.plusHours(7))),
                "vale inexistente");

        // 6. Rechazo de vale que no está EMITIDO
        System.out.println("6) Rechazo de vale que no está EMITIDO");
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(valeConforme.getIdVale(), "10.00", "kg", Recepcion.RESULTADO_CONFORME, null, base.plusHours(7))),
                "vale no EMITIDO (cerrado)");

        // 7. Rechazo de segunda recepción del mismo vale
        System.out.println("7) Rechazo de segunda recepción del mismo vale");
        // Simular estado inconsistente controlado (vale EMITIDO pero con recepción ya guardada en repositorio aislado)
        Recepcion existenteParaSegunda = nuevaRecepcion(valeParaSegunda.getIdVale(), "3.00", "kg",
                Recepcion.RESULTADO_CONFORME, null, base.plusHours(8));
        existenteParaSegunda.setIdRecepcion(UUID.randomUUID());
        existenteParaSegunda.setFechaCreacion(LocalDateTime.now());
        existenteParaSegunda.setVersion(1L);
        recepRepo.save(existenteParaSegunda);
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(valeParaSegunda.getIdVale(), "3.00", "kg", Recepcion.RESULTADO_CONFORME, null, base.plusHours(9))),
                "segunda recepción del mismo vale");

        // 8. Rechazo de cantidad cero o negativa
        System.out.println("8) Rechazo de cantidad cero o negativa");
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(valeParaValidaciones.getIdVale(), "0", "kg", Recepcion.RESULTADO_NO_CONFORME, "obs", base.plusHours(7))),
                "cantidad cero");
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(valeParaValidaciones.getIdVale(), "-1.00", "kg", Recepcion.RESULTADO_NO_CONFORME, "obs", base.plusHours(7))),
                "cantidad negativa");

        // 9. Rechazo de unidad diferente
        System.out.println("9) Rechazo de unidad diferente");
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(valeParaValidaciones.getIdVale(), "7.00", "lb", Recepcion.RESULTADO_CONFORME, null, base.plusHours(7))),
                "unidad diferente");

        // 10. Rechazo de recepción conforme con cantidad distinta
        System.out.println("10) Rechazo de recepción conforme con cantidad distinta");
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(valeParaValidaciones.getIdVale(), "6.00", "kg", Recepcion.RESULTADO_CONFORME, null, base.plusHours(7))),
                "conforme con cantidad distinta");

        // 11. Rechazo de recepción no conforme sin observaciones
        System.out.println("11) Rechazo de recepción no conforme sin observaciones");
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(valeParaValidaciones.getIdVale(), "6.00", "kg", Recepcion.RESULTADO_NO_CONFORME, null, base.plusHours(7))),
                "no conforme sin observaciones");
        expectValidation(() -> recepcionService.registrar(
                nuevaRecepcion(valeParaValidaciones.getIdVale(), "6.00", "kg", Recepcion.RESULTADO_NO_CONFORME, "   ", base.plusHours(7))),
                "no conforme con observaciones en blanco");

        // 12. Verificación de que la cantidad despachada original no cambia
        System.out.println("12) Verificación de que la cantidad despachada original no cambia");
        check(new BigDecimal("10.00").compareTo(valeService.buscarPorId(valeConforme.getIdVale()).orElseThrow().getCantidadDespachada()) == 0,
                "Cantidad despachada original intacta (conforme)");
        check(new BigDecimal("5.00").compareTo(valeService.buscarPorId(valeNoConforme.getIdVale()).orElseThrow().getCantidadDespachada()) == 0,
                "Cantidad despachada original intacta (no conforme)");

        // 13. Verificación de que una validación fallida no altera el estado del vale
        System.out.println("13) Verificación de que una validación fallida no altera el estado del vale");
        ValeEntrega antes = valeService.buscarPorId(valeParaValidaciones.getIdVale()).orElseThrow();
        check(ValeEntrega.ESTADO_EMITIDO.equals(antes.getEstado()), "Vale de control sigue EMITIDO antes de intento fallido");
        int recepcionesAntes = recepRepo.findAll().size();
        int incidenciasAntes = incRepo.findAll().size();
        try {
            recepcionService.registrar(nuevaRecepcion(valeParaValidaciones.getIdVale(), "6.00", "kg",
                    Recepcion.RESULTADO_CONFORME, null, base.plusHours(10)));
            throw new IllegalStateException("Fallo: se esperaba rechazo por validación");
        } catch (ValidacionException expected) {
            // esperado
        }
        ValeEntrega despues = valeService.buscarPorId(valeParaValidaciones.getIdVale()).orElseThrow();
        check(ValeEntrega.ESTADO_EMITIDO.equals(despues.getEstado()), "Estado del vale no cambia tras validación fallida");
        check(recepRepo.findAll().size() == recepcionesAntes, "No se guarda recepción tras validación fallida");
        check(incRepo.findAll().size() == incidenciasAntes, "No se genera incidencia tras validación fallida");
        check(!recepcionService.buscarPorVale(valeParaValidaciones.getIdVale()).isPresent(), "Vale sin recepción tras validación fallida");

        // 14. Consultas por vale y período
        System.out.println("14) Consultas por vale y período");
        check(recepcionService.buscarPorId(rConfGuardada.getIdRecepcion()).isPresent(), "Consulta recepción por UUID");
        check(recepcionService.buscarPorVale(valeConforme.getIdVale()).isPresent(), "Consulta recepción por vale");
        List<Recepcion> porPeriodo = recepcionService.listarPorPeriodo(base.plusHours(4), base.plusHours(7));
        check(porPeriodo.stream().anyMatch(r -> r.getIdRecepcion().equals(rConfGuardada.getIdRecepcion())), "Consulta por período incluye recepción conforme");
        check(porPeriodo.stream().anyMatch(r -> r.getIdRecepcion().equals(rNoConfGuardada.getIdRecepcion())), "Consulta por período incluye recepción no conforme");
        List<Recepcion> fueraPeriodo = recepcionService.listarPorPeriodo(base.plusDays(2), base.plusDays(3));
        check(fueraPeriodo.isEmpty(), "Consulta por período sin resultados fuera de rango");

        // 15. Si falla el registro de la incidencia, no debe quedar recepción guardada ni vale cambiado
        System.out.println("15) Falla al registrar incidencia (no conforme) => sin recepción y sin cambio de estado");
        IncidenciaRepository incRepoFalla = new IncidenciaRepositoryMemory() {
            @Override
            public Incidencia save(Incidencia entity) {
                throw new RuntimeException("Fallo simulado al guardar incidencia");
            }

            @Override
            public java.util.List<Incidencia> findByVale(UUID idVale) {
                return java.util.List.of();
            }
        };
        RecepcionService recepcionServiceFalla = new RecepcionService(recepRepo, valeService, null);
        IncidenciaService incidenciaServiceFalla = new IncidenciaService(incRepoFalla, recepcionServiceFalla, valeService);
        recepcionServiceFalla.setIncidenciaService(incidenciaServiceFalla);
        try {
            recepcionServiceFalla.registrar(nuevaRecepcion(valeFalloIncidencia.getIdVale(), "3.00", "kg",
                    Recepcion.RESULTADO_NO_CONFORME, "Diferencia de cantidad", base.plusHours(11)));
            throw new IllegalStateException("Fallo: se esperaba error por registro de incidencia");
        } catch (RuntimeException ex) {
            check("Fallo simulado al guardar incidencia".equals(ex.getMessage()), "Se propaga el fallo de registro de incidencia");
        }
        ValeEntrega valeTrasFallo = valeService.buscarPorId(valeFalloIncidencia.getIdVale()).orElseThrow();
        check(ValeEntrega.ESTADO_EMITIDO.equals(valeTrasFallo.getEstado()), "Vale permanece EMITIDO si falla la incidencia");
        check(!recepcionService.buscarPorVale(valeFalloIncidencia.getIdVale()).isPresent(), "No queda recepción guardada si falla la incidencia");
        check(incRepoFalla.findByVale(valeFalloIncidencia.getIdVale()).isEmpty(), "No queda incidencia registrada si falla su guardado");

        System.out.println("\n==> Verificación automatizada de Etapa 4.7 (revisada) finalizada correctamente.");
        System.out.println("Nota: recepción NO_CONFORME genera incidencia mínima asociada (vale y recepción); operación en memoria con coherencia compensada.");
    }

    private static ValeEntrega emitirVale(ValeEntregaService valeService, UUID idUeb, UUID idDest, UUID idProd,
                                         String cantidad, String unidad, LocalDateTime fechaHora) {
        ValeEntrega v = new ValeEntrega();
        v.setIdVale(UUID.randomUUID());
        v.setIdUEB(idUeb);
        v.setIdDestino(idDest);
        v.setIdProducto(idProd);
        v.setCantidadDespachada(new BigDecimal(cantidad));
        v.setUnidadMedida(unidad);
        v.setFechaHoraEmision(fechaHora);
        return valeService.emitir(v);
    }

    private static Recepcion nuevaRecepcion(UUID idVale, String cantidad, String unidad,
                                            String resultado, String observaciones, LocalDateTime fechaHora) {
        Recepcion r = new Recepcion();
        r.setIdVale(idVale);
        r.setCantidadRecibida(cantidad == null ? null : new BigDecimal(cantidad));
        r.setUnidadMedida(unidad);
        r.setResultado(resultado);
        r.setObservaciones(observaciones);
        r.setFechaHoraRecepcion(fechaHora);
        return r;
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
