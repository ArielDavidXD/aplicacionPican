package cu.pican.app;

import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.repository.ProduccionCarneRepository;
import cu.pican.repository.UEBRepository;
import cu.pican.repository.memory.ProduccionCarneRepositoryMemory;
import cu.pican.repository.memory.UEBRepositoryMemory;
import cu.pican.service.ProduccionCarneService;
import cu.pican.service.UEBService;
import cu.pican.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Verificación automatizada de apoyo para la Etapa 4.4 (producción de carne).
 * No sustituye pruebas manuales de interfaz; valida operaciones de servicio/datos usadas por el módulo.
 */
public class MeatProductionStageDemo {

    public static void main(String[] args) {
        cu.pican.app.AutorizacionDemo.instalarTodosLosRoles();
        UEBRepository uebRepo = new UEBRepositoryMemory();
        ProduccionCarneRepository carneRepo = new ProduccionCarneRepositoryMemory();
        UEBService uebService = new UEBService(uebRepo);
        ProduccionCarneService carneService = new ProduccionCarneService(carneRepo, uebRepo);

        UUID idUeb1 = UUID.randomUUID();
        UUID idUeb2 = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb1, "UEB Demo 1", "D1", true));
        uebService.registrar(new UEB(idUeb2, "UEB Demo 2", "D2", true));

        // 1. Registrar producción válida y listar
        System.out.println("1) Registrar producción válida y listar");
        ProduccionCarne p1 = build(UUID.randomUUID(), idUeb1, LocalDate.of(2026, 11, 10), new BigDecimal("1.500"), "demo");
        carneService.registrar(p1);
        check(carneService.listarTodas().size() == 1, "Producción registrada y listada");

        // 2. Rechazar sin UEB
        System.out.println("2) Rechazar producción sin UEB");
        expectValidation(() -> carneService.registrar(build(UUID.randomUUID(), null, LocalDate.of(2026, 11, 11), new BigDecimal("1.0"), "x")),
                "sin UEB");

        // 3. Rechazar fecha vacía (null) - en UI también se rechaza formato inválido
        System.out.println("3) Rechazar fecha vacía/nula");
        expectValidation(() -> carneService.registrar(build(UUID.randomUUID(), idUeb1, null, new BigDecimal("1.0"), "x")),
                "fecha vacía");

        // 4. Rechazar cantidades negativas
        System.out.println("4) Rechazar cantidades negativas");
        expectValidation(() -> carneService.registrar(build(UUID.randomUUID(), idUeb1, LocalDate.of(2026, 11, 11), new BigDecimal("-0.1"), "x")),
                "cantidad negativa");

        // 5. Permitir cantidad cero explícita
        System.out.println("5) Permitir cantidad cero explícita");
        ProduccionCarne p0 = build(UUID.randomUUID(), idUeb1, LocalDate.of(2026, 11, 11), BigDecimal.ZERO, "cero");
        carneService.registrar(p0);
        check(BigDecimal.ZERO.compareTo(carneService.buscarPorId(p0.getIdProduccion()).orElseThrow().getCantidadToneladas()) == 0,
                "Cantidad cero aceptada");

        // 6. Editar conservando UUID
        System.out.println("6) Editar conservando UUID");
        p1.setCantidadToneladas(new BigDecimal("2.000"));
        p1.setObservaciones("actualizado");
        carneService.actualizar(p1);
        ProduccionCarne act = carneService.buscarPorId(p1.getIdProduccion()).orElseThrow();
        check(p1.getIdProduccion().equals(act.getIdProduccion()), "UUID conservado");
        check(new BigDecimal("2.000").compareTo(act.getCantidadToneladas()) == 0, "Cantidad actualizada");
        long countP1 = carneService.listarTodas().stream().filter(p -> p.getIdProduccion().equals(p1.getIdProduccion())).count();
        check(countP1 == 1, "No se creó registro nuevo");

        // 7. Filtros: por UEB, por fecha, combinados
        System.out.println("7) Filtros por UEB, fecha y combinados");
        ProduccionCarne p2 = build(UUID.randomUUID(), idUeb2, LocalDate.of(2026, 11, 12), new BigDecimal("0.750"), "otra");
        carneService.registrar(p2);
        check(carneService.buscarPorUEB(idUeb1).size() == 2, "Filtro por UEB");
        check(carneService.buscarPorFecha(LocalDate.of(2026, 11, 12)).size() == 1, "Filtro por fecha");
        List<ProduccionCarne> combinado = carneService.buscarPorUEB(idUeb1);
        combinado.removeIf(p -> !LocalDate.of(2026, 11, 11).equals(p.getFechaProduccion()));
        check(combinado.size() == 1 && p0.getIdProduccion().equals(combinado.get(0).getIdProduccion()), "Filtros combinados UEB+fecha");

        System.out.println("\n==> Verificación automatizada de Etapa 4.4 finalizada correctamente.");
    }

    private static ProduccionCarne build(UUID id, UUID idUeb, LocalDate fecha, BigDecimal cantidad, String obs) {
        ProduccionCarne p = new ProduccionCarne();
        p.setIdProduccion(id);
        p.setIdUEB(idUeb);
        p.setFechaProduccion(fecha);
        p.setCantidadToneladas(cantidad);
        p.setObservaciones(obs);
        p.setCreadoEn(LocalDateTime.now());
        p.setVersion(1L);
        return p;
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
