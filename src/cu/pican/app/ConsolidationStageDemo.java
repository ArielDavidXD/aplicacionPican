package cu.pican.app;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.repository.ConsolidadoUEBRepository;
import cu.pican.repository.NaveRepository;
import cu.pican.repository.ParteNaveRepository;
import cu.pican.repository.ProduccionCarneRepository;
import cu.pican.repository.UEBRepository;
import cu.pican.repository.UsuarioRepository;
import cu.pican.repository.memory.ConsolidadoUEBRepositoryMemory;
import cu.pican.repository.memory.NaveRepositoryMemory;
import cu.pican.repository.memory.ParteNaveRepositoryMemory;
import cu.pican.repository.memory.ProduccionCarneRepositoryMemory;
import cu.pican.repository.memory.UEBRepositoryMemory;
import cu.pican.repository.memory.UsuarioRepositoryMemory;
import cu.pican.service.ConsolidadoUEBService;
import cu.pican.service.NaveService;
import cu.pican.service.ParteNaveService;
import cu.pican.service.ProduccionCarneService;
import cu.pican.service.UEBService;
import cu.pican.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Verificación automatizada de apoyo para la Etapa 4.5 (consolidación de producción).
 * No sustituye pruebas manuales de interfaz; valida operaciones de servicio/datos usadas por el módulo.
 */
public class ConsolidationStageDemo {

    public static void main(String[] args) {
        cu.pican.app.AutorizacionDemo.instalarTodosLosRoles();
        UEBRepository uebRepo = new UEBRepositoryMemory();
        NaveRepository naveRepo = new NaveRepositoryMemory();
        ParteNaveRepository parteRepo = new ParteNaveRepositoryMemory();
        ProduccionCarneRepository carneRepo = new ProduccionCarneRepositoryMemory();
        ConsolidadoUEBRepository consRepo = new ConsolidadoUEBRepositoryMemory();
        UsuarioRepository usuarioRepo = new UsuarioRepositoryMemory();

        UEBService uebService = new UEBService(uebRepo);
        NaveService naveService = new NaveService(naveRepo, uebRepo);
        ParteNaveService parteService = new ParteNaveService(parteRepo, naveRepo, usuarioRepo);
        ProduccionCarneService carneService = new ProduccionCarneService(carneRepo, uebRepo);
        ConsolidadoUEBService consService = new ConsolidadoUEBService(consRepo, uebRepo);

        UUID idUeb1 = UUID.randomUUID();
        UUID idUeb2 = UUID.randomUUID();
        UUID idNave1 = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb1, "UEB Demo 1", "D1", true));
        uebService.registrar(new UEB(idUeb2, "UEB Demo 2", "D2", true));
        naveService.registrar(new Nave(idNave1, idUeb1, "Nave D1", "N1", true));

        // Datos originales de producción (para verificar que no se modifican)
        ParteNave parte = new ParteNave();
        parte.setIdParte(UUID.randomUUID());
        parte.setIdNave(idNave1);
        parte.setFechaProduccion(LocalDate.of(2026, 11, 10));
        parte.setHuevosProducidos(100);
        parte.setHuevosRotos(5);
        parte.setGallinasVivas(50);
        parte.setGallinasMuertas(1);
        parte.setGallinasEnfermas(0);
        parte.setPiensoKg(new BigDecimal("10.0"));
        parte.setObservaciones("original");
        parte.setCreadoEn(LocalDateTime.now());
        parte.setVersion(1L);
        parteService.registrar(parte);

        ProduccionCarne carne = new ProduccionCarne();
        carne.setIdProduccion(UUID.randomUUID());
        carne.setIdUEB(idUeb1);
        carne.setFechaProduccion(LocalDate.of(2026, 11, 11));
        carne.setCantidadToneladas(new BigDecimal("2.0"));
        carne.setObservaciones("original carne");
        carne.setCreadoEn(LocalDateTime.now());
        carne.setVersion(1L);
        carneService.registrar(carne);

        // 1. Registrar consolidación válida
        System.out.println("1) Registrar consolidación válida");
        ConsolidadoUEB c1 = buildCons(UUID.randomUUID(), idUeb1, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "Demo", null);
        consService.registrar(c1);
        check(consService.listarTodos().size() == 1, "Consolidación válida registrada");

        // 2. Rechazar UEB inexistente
        System.out.println("2) Rechazar UEB inexistente");
        expectValidation(() -> consService.registrar(buildCons(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "X", null)), "UEB inexistente");

        // 3. Rechazar fecha de inicio vacía
        System.out.println("3) Rechazar fecha de inicio vacía");
        expectValidation(() -> consService.registrar(buildCons(UUID.randomUUID(), idUeb1,
                null, LocalDate.of(2026, 11, 30), "X", null)), "fecha inicio vacía");

        // 4. Rechazar fecha de fin vacía
        System.out.println("4) Rechazar fecha de fin vacía");
        expectValidation(() -> consService.registrar(buildCons(UUID.randomUUID(), idUeb1,
                LocalDate.of(2026, 11, 1), null, "X", null)), "fecha fin vacía");

        // 5. Rechazar fecha final anterior a la inicial
        System.out.println("5) Rechazar fecha final anterior a la inicial");
        expectValidation(() -> consService.registrar(buildCons(UUID.randomUUID(), idUeb1,
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), "X", null)), "fecha fin < inicio");

        // 6. Consultar consolidaciones
        System.out.println("6) Consultar consolidaciones");
        List<ConsolidadoUEB> todas = consService.listarTodos();
        check(todas.size() == 1 && c1.getIdConsolidado().equals(todas.get(0).getIdConsolidado()), "Consulta de consolidaciones");

        // 7. Consultar partes de huevos del período (UEB/período del consolidado)
        System.out.println("7) Consultar partes de huevos del período");
        List<ParteNave> huevosPeriodo = consultarHuevosPeriodo(c1, idUeb1, naveService, parteService);
        check(huevosPeriodo.size() == 1 && parte.getIdParte().equals(huevosPeriodo.get(0).getIdParte()),
                "Parte de huevos comprendido en el período");

        // 8. Consultar producciones de carne del período
        System.out.println("8) Consultar producciones de carne del período");
        List<ProduccionCarne> carnePeriodo = consultarCarnePeriodo(c1, idUeb1, carneService);
        check(carnePeriodo.size() == 1 && carne.getIdProduccion().equals(carnePeriodo.get(0).getIdProduccion()),
                "Producción de carne comprendida en el período");

        // 9. Verificar que los registros originales no se modifican
        System.out.println("9) Verificar que los registros originales no se modifican");
        ParteNave parteDespues = parteService.buscarPorId(parte.getIdParte()).orElseThrow();
        ProduccionCarne carneDespues = carneService.buscarPorId(carne.getIdProduccion()).orElseThrow();
        check(Integer.valueOf(100).equals(parteDespues.getHuevosProducidos()), "Huevos producidos originales intactos");
        check("original".equals(parteDespues.getObservaciones()), "Observación parte original intacta");
        check(new BigDecimal("2.0").compareTo(carneDespues.getCantidadToneladas()) == 0, "Cantidad carne original intacta");
        check("original carne".equals(carneDespues.getObservaciones()), "Observación carne original intacta");

        // 10. Editar consolidación conservando UUID
        System.out.println("10) Editar consolidación conservando UUID");
        c1.setEstado("Actualizado (demo)");
        c1.setMotivoParcial("Motivo de demostración");
        consService.actualizar(c1);
        ConsolidadoUEB c1Act = consService.buscarPorId(c1.getIdConsolidado()).orElseThrow();
        check(c1.getIdConsolidado().equals(c1Act.getIdConsolidado()), "UUID de consolidación conservado");
        check("Actualizado (demo)".equals(c1Act.getEstado()), "Estado actualizado");
        check(consService.listarTodos().size() == 1, "No se creó consolidación duplicada");

        // 11. Filtrar por UEB y período
        System.out.println("11) Filtrar por UEB y período");
        ConsolidadoUEB c2 = buildCons(UUID.randomUUID(), idUeb2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "Otro", null);
        consService.registrar(c2);
        List<ConsolidadoUEB> porUeb1 = consService.buscarPorUEB(idUeb1);
        check(porUeb1.size() == 1 && idUeb1.equals(porUeb1.get(0).getIdUEB()), "Filtro por UEB (servicio)");
        // Filtro por período (solapamiento) sobre resultados, reutilizando servicios
        List<ConsolidadoUEB> periodoNov = filtrarPorPeriodo(consService.listarTodos(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        check(periodoNov.size() == 1 && c1.getIdConsolidado().equals(periodoNov.get(0).getIdConsolidado()), "Filtro por período");
        List<ConsolidadoUEB> combinado = filtrarPorPeriodo(consService.buscarPorUEB(idUeb1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        check(combinado.size() == 1 && c1.getIdConsolidado().equals(combinado.get(0).getIdConsolidado()), "Filtro combinado UEB + período");

        // Verificación extra: originales siguen intactos tras editar/consultar
        check(Integer.valueOf(100).equals(parteService.buscarPorId(parte.getIdParte()).orElseThrow().getHuevosProducidos()),
                "Originales intactos tras operaciones de consolidación");
        check(new BigDecimal("2.0").compareTo(carneService.buscarPorId(carne.getIdProduccion()).orElseThrow().getCantidadToneladas()) == 0,
                "Carne original intacta tras operaciones de consolidación");

        System.out.println("\n==> Verificación automatizada de Etapa 4.5 finalizada correctamente.");
    }

    private static List<ParteNave> consultarHuevosPeriodo(ConsolidadoUEB c, UUID idUeb, NaveService naveService, ParteNaveService parteService) {
        List<ParteNave> res = new ArrayList<>();
        for (Nave n : naveService.buscarPorUEB(idUeb)) {
            for (ParteNave p : parteService.buscarPorNave(n.getIdNave())) {
                if (enPeriodo(p.getFechaProduccion(), c.getFechaInicio(), c.getFechaFin())) {
                    res.add(p);
                }
            }
        }
        return res;
    }

    private static List<ProduccionCarne> consultarCarnePeriodo(ConsolidadoUEB c, UUID idUeb, ProduccionCarneService carneService) {
        List<ProduccionCarne> res = new ArrayList<>();
        for (ProduccionCarne p : carneService.buscarPorUEB(idUeb)) {
            if (enPeriodo(p.getFechaProduccion(), c.getFechaInicio(), c.getFechaFin())) {
                res.add(p);
            }
        }
        return res;
    }

    private static List<ConsolidadoUEB> filtrarPorPeriodo(List<ConsolidadoUEB> base, LocalDate inicio, LocalDate fin) {
        List<ConsolidadoUEB> res = new ArrayList<>();
        for (ConsolidadoUEB c : base) {
            boolean ok = true;
            if (inicio != null && c.getFechaFin() != null && c.getFechaFin().isBefore(inicio)) ok = false;
            if (fin != null && c.getFechaInicio() != null && c.getFechaInicio().isAfter(fin)) ok = false;
            if (ok) res.add(c);
        }
        return res;
    }

    private static boolean enPeriodo(LocalDate fecha, LocalDate inicio, LocalDate fin) {
        if (fecha == null) return false;
        return (fecha.isEqual(inicio) || fecha.isAfter(inicio)) && (fecha.isEqual(fin) || fecha.isBefore(fin));
    }

    private static ConsolidadoUEB buildCons(UUID id, UUID idUeb, LocalDate inicio, LocalDate fin, String estado, String motivo) {
        ConsolidadoUEB c = new ConsolidadoUEB();
        c.setIdConsolidado(id);
        c.setIdUEB(idUeb);
        c.setFechaInicio(inicio);
        c.setFechaFin(fin);
        c.setEstado(estado);
        c.setMotivoParcial(motivo);
        c.setCreadoEn(LocalDateTime.now());
        c.setVersion(1L);
        return c;
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
