package cu.pican.controller.app;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.model.Usuario;
import cu.pican.model.persistence.NoOpTransactionManager;
import cu.pican.model.repository.AclaracionOperativaRepository;
import cu.pican.model.repository.ConsolidadoUEBRepository;
import cu.pican.model.repository.NaveRepository;
import cu.pican.model.repository.ParteNaveRepository;
import cu.pican.model.repository.ParteUnidadAsociadaRepository;
import cu.pican.model.repository.ProduccionCarneRepository;
import cu.pican.model.repository.UEBRepository;
import cu.pican.model.repository.UnidadAsociadaRepository;
import cu.pican.model.repository.UsuarioRepository;
import cu.pican.model.repository.memory.AclaracionOperativaRepositoryMemory;
import cu.pican.model.repository.memory.ConsolidadoUEBRepositoryMemory;
import cu.pican.model.repository.memory.NaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteNaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteUnidadAsociadaRepositoryMemory;
import cu.pican.model.repository.memory.ProduccionCarneRepositoryMemory;
import cu.pican.model.repository.memory.UEBRepositoryMemory;
import cu.pican.model.repository.memory.UnidadAsociadaRepositoryMemory;
import cu.pican.model.repository.memory.UsuarioRepositoryMemory;
import cu.pican.controller.service.ConsolidadoUEBService;
import cu.pican.controller.service.NaveService;
import cu.pican.controller.service.ParteNaveService;
import cu.pican.controller.service.ParteUnidadAsociadaService;
import cu.pican.controller.service.ProduccionCarneService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.UnidadAsociadaService;
import cu.pican.controller.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Demostración/pruebas de la capa de servicios (Etapa 3).
 * Ejecutar: java -cp aplicacionPican\out cu.pican.controller.app.ServiceDemo
 */
public class ServiceDemo {

    public static void main(String[] args) {
        cu.pican.controller.app.AutorizacionDemo.instalarTodosLosRoles();
        // Repositorios en memoria
        UEBRepository uebRepo = new UEBRepositoryMemory();
        NaveRepository naveRepo = new NaveRepositoryMemory();
        UnidadAsociadaRepository unidadRepo = new UnidadAsociadaRepositoryMemory();
        ParteNaveRepository parteNaveRepo = new ParteNaveRepositoryMemory();
        ParteUnidadAsociadaRepository parteAsocRepo = new ParteUnidadAsociadaRepositoryMemory();
        ProduccionCarneRepository carneRepo = new ProduccionCarneRepositoryMemory();
        ConsolidadoUEBRepository consolidadoRepo = new ConsolidadoUEBRepositoryMemory();
        AclaracionOperativaRepository aclaracionRepo = new AclaracionOperativaRepositoryMemory();
        UsuarioRepository usuarioRepo = new UsuarioRepositoryMemory();

        // Servicios con inyección de dependencias
        UEBService uebService = new UEBService(uebRepo);
        NaveService naveService = new NaveService(naveRepo, uebRepo);
        UnidadAsociadaService unidadService = new UnidadAsociadaService(unidadRepo);
        ParteNaveService parteNaveService = new ParteNaveService(parteNaveRepo, naveRepo, usuarioRepo);
        ParteUnidadAsociadaService parteAsocService = new ParteUnidadAsociadaService(parteAsocRepo, unidadRepo, uebRepo);
        ProduccionCarneService carneService = new ProduccionCarneService(carneRepo, uebRepo);
        ConsolidadoUEBService consolidadoService = new ConsolidadoUEBService(
                new NoOpTransactionManager(), consolidadoRepo, uebRepo, naveRepo, parteNaveRepo,
                parteAsocRepo, unidadRepo, carneRepo, aclaracionRepo);

        // Datos base
        UUID idUEB = UUID.randomUUID();
        UUID idNave = UUID.randomUUID();
        UUID idUnidad = UUID.randomUUID();
        UUID idUsuario = UUID.randomUUID();

        // 1. Registrar una UEB válida
        System.out.println("1) Registrar UEB válida");
        UEB ueb = new UEB(idUEB, "UEB San Luis", "UEB-SL", true);
        uebService.registrar(ueb);
        check(uebRepo.findById(idUEB).isPresent(), "UEB válida registrada");

        // 2. Rechazar una UEB sin nombre o código
        System.out.println("2) Rechazar UEB sin nombre o código");
        expectValidation(() -> uebService.registrar(new UEB(UUID.randomUUID(), "", "COD", true)), "UEB sin nombre");
        expectValidation(() -> uebService.registrar(new UEB(UUID.randomUUID(), "Nombre", " ", true)), "UEB sin código");

        // 3. Registrar una nave vinculada a una UEB existente
        System.out.println("3) Registrar nave vinculada a UEB existente");
        Nave nave = new Nave(idNave, idUEB, "Nave 1", "N-01", true);
        naveService.registrar(nave);
        check(naveRepo.findById(idNave).isPresent(), "Nave registrada con UEB existente");

        // 4. Rechazar una nave con UEB inexistente
        System.out.println("4) Rechazar nave con UEB inexistente");
        expectValidation(() -> naveService.registrar(new Nave(UUID.randomUUID(), UUID.randomUUID(), "Nave X", "N-X", true)),
                "Nave con UEB inexistente");

        // Preparar unidad asociada y usuario para pruebas de partes
        unidadService.registrar(new UnidadAsociada(idUnidad, "Unidad Asociada A", "UA-01", true));
        usuarioRepo.save(new Usuario(idUsuario, "operador1", "Operador Uno", true));

        // 5. Registrar un parte de nave válido
        System.out.println("5) Registrar parte de nave válido");
        LocalDate fecha = LocalDate.of(2026, 11, 15);
        ParteNave parteValido = nuevoParteNave(UUID.randomUUID(), idNave, fecha, idUsuario,
                500, 2, 3, 1000, 10, new BigDecimal("120.0"));
        parteNaveService.registrar(parteValido);
        check(parteNaveRepo.findById(parteValido.getIdParte()).isPresent(), "Parte de nave válido registrado");

        // 6. Rechazar cantidades negativas (fechas distintas para ejercitar cada validación sin chocar con la unicidad)
        System.out.println("6) Rechazar cantidades negativas");
        ParteNave negativoHuevos = nuevoParteNave(UUID.randomUUID(), idNave, fecha.plusDays(1), null,
                500, 0, 0, -1, 0, new BigDecimal("10"));
        expectValidation(() -> parteNaveService.registrar(negativoHuevos), "Huevos producidos negativos");
        ParteNave negativoPienso = nuevoParteNave(UUID.randomUUID(), idNave, fecha.plusDays(2), null,
                500, 0, 0, 100, 0, new BigDecimal("-0.5"));
        expectValidation(() -> parteNaveService.registrar(negativoPienso), "Pienso negativo");

        // 7. Rechazar huevos rotos superiores a huevos producidos
        System.out.println("7) Rechazar huevos rotos superiores a producidos");
        ParteNave rotosMayor = nuevoParteNave(UUID.randomUUID(), idNave, fecha.plusDays(3), null,
                500, 0, 0, 100, 101, new BigDecimal("10"));
        expectValidation(() -> parteNaveService.registrar(rotosMayor), "Huevos rotos > producidos");

        // 8. Registrar producción de carne válida
        System.out.println("8) Registrar producción de carne válida");
        ProduccionCarne carneValida = nuevaCarne(UUID.randomUUID(), idUEB, fecha, new BigDecimal("2.500"));
        carneService.registrar(carneValida);
        check(carneRepo.findById(carneValida.getIdProduccion()).isPresent(), "Producción de carne válida registrada");

        // 9. Rechazar producción de carne negativa
        System.out.println("9) Rechazar producción de carne negativa");
        ProduccionCarne carneNegativa = nuevaCarne(UUID.randomUUID(), idUEB, fecha, new BigDecimal("-1.0"));
        expectValidation(() -> carneService.registrar(carneNegativa), "Cantidad de carne negativa");

        // 10. Registrar una consolidación con fechas correctas
        System.out.println("10) Registrar consolidación con fechas correctas");
        ConsolidadoUEB consolidado = nuevoConsolidado(UUID.randomUUID(), idUEB,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15));
        consolidadoService.registrar(consolidado);
        check(consolidadoRepo.findById(consolidado.getIdConsolidado()).isPresent(), "Consolidado válido registrado");

        // 11. Rechazar un rango de fechas inválido
        System.out.println("11) Rechazar rango de fechas inválido");
        ConsolidadoUEB rangoInvalido = nuevoConsolidado(UUID.randomUUID(), idUEB,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 10));
        expectValidation(() -> consolidadoService.registrar(rangoInvalido), "Fecha fin anterior a inicio");

        // 12. Comprobar que una consolidación no modifica los registros originales
        System.out.println("12) Consolidación no modifica registros originales");
        int partesAntes = parteNaveRepo.findAll().size();
        int carneAntes = carneRepo.findAll().size();
        Integer huevosAntes = parteNaveRepo.findById(parteValido.getIdParte()).get().getHuevosProducidos();
        BigDecimal toneladasAntes = carneRepo.findById(carneValida.getIdProduccion()).get().getCantidadToneladas();

        ConsolidadoUEB otroConsolidado = nuevoConsolidado(UUID.randomUUID(), idUEB,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        consolidadoService.registrar(otroConsolidado);

        check(parteNaveRepo.findAll().size() == partesAntes, "Cantidad de partes de nave no cambió");
        check(carneRepo.findAll().size() == carneAntes, "Cantidad de registros de carne no cambió");
        check(huevosAntes.equals(parteNaveRepo.findById(parteValido.getIdParte()).get().getHuevosProducidos()),
                "Parte de nave original no fue modificado");
        check(toneladasAntes.compareTo(carneRepo.findById(carneValida.getIdProduccion()).get().getCantidadToneladas()) == 0,
                "Producción de carne original no fue modificada");

        // Comprobación adicional coherente: ParteUnidadAsociada válida sin campos productivos inventados
        System.out.println("Extra) Registrar parte de unidad asociada válido (sin campos productivos inventados)");
        ParteUnidadAsociada pa = new ParteUnidadAsociada();
        pa.setIdParteAsociado(UUID.randomUUID());
        pa.setIdUnidad(idUnidad);
        pa.setIdUEB(idUEB);
        pa.setEstado(ParteUnidadAsociada.ESTADO_REGISTRADO);
        pa.setFechaProduccion(fecha);
        pa.setReferenciaPapel("PAPEL-001");
        pa.setObservaciones("Datos tomados del formulario en papel (pendiente definición de campos productivos)");
        pa.setCreadoEn(LocalDateTime.now());
        pa.setVersion(1L);
        parteAsocService.registrar(pa);
        check(parteAsocRepo.findById(pa.getIdParteAsociado()).isPresent(), "Parte de unidad asociada registrado");

        System.out.println("\n==> Todas las comprobaciones de la Etapa 3 finalizaron correctamente.");
    }

    private static ParteNave nuevoParteNave(UUID idParte, UUID idNave, LocalDate fecha, UUID idUsuario,
                                            Integer vivas, Integer muertas, Integer enfermas,
                                            Integer producidos, Integer rotos, BigDecimal piensoKg) {
        ParteNave p = new ParteNave();
        p.setIdParte(idParte);
        p.setIdNave(idNave);
        p.setFechaProduccion(fecha);
        p.setIdUsuario(idUsuario);
        p.setGallinasVivas(vivas);
        p.setGallinasMuertas(muertas);
        p.setGallinasEnfermas(enfermas);
        p.setHuevosProducidos(producidos);
        p.setHuevosRotos(rotos);
        p.setPiensoKg(piensoKg);
        p.setCreadoEn(LocalDateTime.now());
        p.setVersion(1L);
        return p;
    }

    private static ProduccionCarne nuevaCarne(UUID id, UUID idUEB, LocalDate fecha, BigDecimal toneladas) {
        ProduccionCarne c = new ProduccionCarne();
        c.setIdProduccion(id);
        c.setIdUEB(idUEB);
        c.setFechaProduccion(fecha);
        c.setCantidadToneladas(toneladas);
        c.setCreadoEn(LocalDateTime.now());
        c.setVersion(1L);
        return c;
    }

    private static ConsolidadoUEB nuevoConsolidado(UUID id, UUID idUEB, LocalDate inicio, LocalDate fin) {
        ConsolidadoUEB c = new ConsolidadoUEB();
        c.setIdConsolidado(id);
        c.setIdUEB(idUEB);
        c.setFechaInicio(inicio);
        c.setFechaFin(fin);
        c.setEstado("BORRADOR");
        c.setCreadoEn(LocalDateTime.now());
        c.setVersion(1L);
        return c;
    }

    private static void expectValidation(Runnable action, String descripcion) {
        try {
            action.run();
            throw new IllegalStateException("Fallo: se esperaba rechazo por validación (" + descripcion + ")");
        } catch (ValidacionException ex) {
            System.out.println("  OK - Rechazado: " + descripcion + " -> " + ex.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Fallo de comprobación: " + message);
        }
        System.out.println("  OK - " + message);
    }
}
