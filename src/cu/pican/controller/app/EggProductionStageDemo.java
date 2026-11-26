package cu.pican.controller.app;

import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.UEB;
import cu.pican.model.repository.NaveRepository;
import cu.pican.model.repository.ParteNaveRepository;
import cu.pican.model.repository.UEBRepository;
import cu.pican.model.repository.UsuarioRepository;
import cu.pican.model.repository.memory.NaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteNaveRepositoryMemory;
import cu.pican.model.repository.memory.UEBRepositoryMemory;
import cu.pican.model.repository.memory.UsuarioRepositoryMemory;
import cu.pican.controller.service.NaveService;
import cu.pican.controller.service.ParteNaveService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Verificación automatizada de apoyo para la Etapa 4.3 (producción de huevos).
 * No sustituye pruebas manuales de interfaz; valida operaciones de servicio/datos usadas por el módulo.
 */
public class EggProductionStageDemo {

    public static void main(String[] args) {
        cu.pican.controller.app.AutorizacionDemo.instalarTodosLosRoles();
        UEBRepository uebRepo = new UEBRepositoryMemory();
        NaveRepository naveRepo = new NaveRepositoryMemory();
        ParteNaveRepository parteRepo = new ParteNaveRepositoryMemory();
        UsuarioRepository usuarioRepo = new UsuarioRepositoryMemory();

        UEBService uebService = new UEBService(uebRepo);
        NaveService naveService = new NaveService(naveRepo, uebRepo);
        ParteNaveService parteService = new ParteNaveService(parteRepo, naveRepo, usuarioRepo);

        UUID idUeb1 = UUID.randomUUID();
        UUID idUeb2 = UUID.randomUUID();
        UUID idNave1 = UUID.randomUUID();
        UUID idNave2 = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb1, "UEB Demo 1", "D1", true));
        uebService.registrar(new UEB(idUeb2, "UEB Demo 2", "D2", true));
        naveService.registrar(new Nave(idNave1, idUeb1, "Nave D1", "N1", true));
        naveService.registrar(new Nave(idNave2, idUeb2, "Nave D2", "N2", true));

        // 1. Registrar parte válido y listar
        System.out.println("1) Registrar parte válido y listar");
        ParteNave p1 = buildParte(UUID.randomUUID(), idNave1, LocalDate.of(2026, 11, 10), 100, 1, 2, 80, 2, "12.5");
        parteService.registrar(p1);
        check(parteService.listarTodos().size() == 1, "Parte registrado y listado");

        // 2. Rechazar parte sin nave
        System.out.println("2) Rechazar parte sin nave");
        ParteNave sinNave = buildParte(UUID.randomUUID(), null, LocalDate.of(2026, 11, 11), 100, 0, 0, 50, 0, "10");
        expectValidation(() -> parteService.registrar(sinNave), "sin nave");

        // 3. Rechazar cantidades negativas
        System.out.println("3) Rechazar cantidades negativas");
        expectValidation(() -> parteService.registrar(buildParte(UUID.randomUUID(), idNave1, LocalDate.of(2026, 11, 11), -1, 0, 0, 50, 0, "10")), "gallinas vivas negativas");
        expectValidation(() -> parteService.registrar(buildParte(UUID.randomUUID(), idNave1, LocalDate.of(2026, 11, 11), 10, 0, 0, 50, 0, "-1")), "pienso negativo");

        // 4. Rechazar huevos rotos superiores a producidos
        System.out.println("4) Rechazar huevos rotos superiores a producidos");
        expectValidation(() -> parteService.registrar(buildParte(UUID.randomUUID(), idNave1, LocalDate.of(2026, 11, 11), 10, 0, 0, 50, 51, "10")), "rotos > producidos");

        // 5. Rechazar gallinas muertas superiores a vivas
        System.out.println("5) Rechazar gallinas muertas superiores a vivas");
        expectValidation(() -> parteService.registrar(buildParte(UUID.randomUUID(), idNave1, LocalDate.of(2026, 11, 11), 10, 11, 0, 50, 0, "10")), "muertas > vivas");

        // 6. Rechazar gallinas enfermas superiores a vivas
        System.out.println("6) Rechazar gallinas enfermas superiores a vivas");
        expectValidation(() -> parteService.registrar(buildParte(UUID.randomUUID(), idNave1, LocalDate.of(2026, 11, 11), 10, 0, 11, 50, 0, "10")), "enfermas > vivas");

        // 7. Editar conservando UUID
        System.out.println("7) Editar conservando UUID");
        p1.setHuevosProducidos(90);
        p1.setObservaciones("Actualizado");
        parteService.actualizar(p1);
        ParteNave actualizado = parteService.buscarPorId(p1.getIdParte()).orElseThrow();
        check(p1.getIdParte().equals(actualizado.getIdParte()), "UUID conservado");
        check(Integer.valueOf(90).equals(actualizado.getHuevosProducidos()), "Datos actualizados");
        check(parteService.listarTodos().size() == 1, "No se creó registro nuevo");

        // 8. Filtrar por nave y fecha; simular filtro por UEB (naves de UEB)
        System.out.println("8) Filtros por nave, fecha y UEB");
        ParteNave p2 = buildParte(UUID.randomUUID(), idNave2, LocalDate.of(2026, 11, 12), 50, 0, 0, 40, 1, "8");
        parteService.registrar(p2);
        check(parteService.buscarPorNave(idNave1).size() == 1, "Filtro por nave");
        check(parteService.buscarPorFecha(LocalDate.of(2026, 11, 12)).size() == 1, "Filtro por fecha");
        Set<UUID> navesUeb1 = naveService.buscarPorUEB(idUeb1).stream().map(Nave::getIdNave).collect(Collectors.toSet());
        List<ParteNave> porUeb1 = parteService.listarTodos().stream().filter(p -> navesUeb1.contains(p.getIdNave())).collect(Collectors.toList());
        check(porUeb1.size() == 1 && idNave1.equals(porUeb1.get(0).getIdNave()), "Filtro por UEB (vía naves)");

        System.out.println("\n==> Verificación automatizada de Etapa 4.3 finalizada correctamente.");
    }

    private static ParteNave buildParte(UUID id, UUID idNave, LocalDate fecha,
                                        Integer vivas, Integer muertas, Integer enfermas,
                                        Integer producidos, Integer rotos, String pienso) {
        ParteNave p = new ParteNave();
        p.setIdParte(id);
        p.setIdNave(idNave);
        p.setFechaProduccion(fecha);
        p.setGallinasVivas(vivas);
        p.setGallinasMuertas(muertas);
        p.setGallinasEnfermas(enfermas);
        p.setHuevosProducidos(producidos);
        p.setHuevosRotos(rotos);
        p.setPiensoKg(pienso == null ? null : new BigDecimal(pienso));
        p.setObservaciones("demo");
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
