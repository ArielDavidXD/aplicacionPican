package cu.pican.controller.app;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.RegistroAnalisis;
import cu.pican.model.TablaHistorica;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.model.persistence.NoOpTransactionManager;
import cu.pican.model.repository.AclaracionOperativaRepository;
import cu.pican.model.repository.ConsolidadoUEBRepository;
import cu.pican.model.repository.NaveRepository;
import cu.pican.model.repository.ParteNaveRepository;
import cu.pican.model.repository.ParteUnidadAsociadaRepository;
import cu.pican.model.repository.ProduccionCarneRepository;
import cu.pican.model.repository.UEBRepository;
import cu.pican.model.repository.UnidadAsociadaRepository;
import cu.pican.model.repository.memory.AclaracionOperativaRepositoryMemory;
import cu.pican.model.repository.memory.ConsolidadoUEBRepositoryMemory;
import cu.pican.model.repository.memory.IndicadorHistoricoRepositoryMemory;
import cu.pican.model.repository.memory.MetaEstrategicaRepositoryMemory;
import cu.pican.model.repository.memory.NaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteNaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteUnidadAsociadaRepositoryMemory;
import cu.pican.model.repository.memory.ProduccionCarneRepositoryMemory;
import cu.pican.model.repository.memory.UEBRepositoryMemory;
import cu.pican.model.repository.memory.UnidadAsociadaRepositoryMemory;
import cu.pican.controller.service.AnalisisService;
import cu.pican.controller.service.ConsolidadoUEBService;
import cu.pican.controller.service.NaveService;
import cu.pican.controller.service.ParteNaveService;
import cu.pican.controller.service.ParteUnidadAsociadaService;
import cu.pican.controller.service.ProduccionCarneService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.UnidadAsociadaService;
import cu.pican.controller.service.ValidacionException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Verificación automatizada de la Etapa 4.10 (CU-14). Repositorios aislados, sin afectar la aplicación. */
public class AnalysisStageDemo {

    private static AnalisisService service;
    private static UUID idUeb1;
    private static UUID idNave1;

    public static void main(String[] args) throws Exception {
        cu.pican.controller.app.AutorizacionDemo.instalarTodosLosRoles();
        UEBRepository uebRepo = new UEBRepositoryMemory();
        NaveRepository naveRepo = new NaveRepositoryMemory();
        UnidadAsociadaRepository unidadRepo = new UnidadAsociadaRepositoryMemory();
        ParteNaveRepository parteRepo = new ParteNaveRepositoryMemory();
        ParteUnidadAsociadaRepository parteAsocRepo = new ParteUnidadAsociadaRepositoryMemory();
        ProduccionCarneRepository carneRepo = new ProduccionCarneRepositoryMemory();
        ConsolidadoUEBRepository consolidadoRepo = new ConsolidadoUEBRepositoryMemory();
        AclaracionOperativaRepository aclaracionRepo = new AclaracionOperativaRepositoryMemory();
        IndicadorHistoricoRepositoryMemory historicoRepo = new IndicadorHistoricoRepositoryMemory();
        MetaEstrategicaRepositoryMemory metaRepo = new MetaEstrategicaRepositoryMemory();

        ReferenciasHistoricasInitializer.cargar(historicoRepo, metaRepo);
        ReferenciasHistoricasInitializer.cargar(historicoRepo, metaRepo);

        UEBService uebService = new UEBService(uebRepo);
        NaveService naveService = new NaveService(naveRepo, uebRepo);
        UnidadAsociadaService unidadService = new UnidadAsociadaService(unidadRepo);
        ParteNaveService parteNaveService = new ParteNaveService(parteRepo, naveRepo, null);
        ParteUnidadAsociadaService parteAsocService = new ParteUnidadAsociadaService(parteAsocRepo, unidadRepo, uebRepo);
        ProduccionCarneService carneService = new ProduccionCarneService(carneRepo, uebRepo);
        ConsolidadoUEBService consolidadoService = new ConsolidadoUEBService(
                new NoOpTransactionManager(), consolidadoRepo, uebRepo, naveRepo, parteRepo,
                parteAsocRepo, unidadRepo, carneRepo, aclaracionRepo);

        service = new AnalisisService(historicoRepo, metaRepo, parteNaveService, naveService,
                uebService, carneService, consolidadoService, parteAsocService);

        prepararDatosOperativos(uebService, naveService, unidadService, parteNaveService, parteRepo,
                parteAsocService, carneService, consolidadoService);

        System.out.println("1) Carga correcta de los indicadores historicos");
        check(service.consultarTodasLasReferencias().size() == 67, "67 indicadores de las tablas 1-3");
        check(service.consultarMetas().size() == 2, "2 metas de la tabla 4");
        check(historicoRepo.findAll().size() == 67, "La carga repetida no duplica referencias");

        System.out.println("2) Exactitud de valores de las cuatro tablas");
        check(valor(TablaHistorica.INFRAESTRUCTURA, "Cantidad Naves", 2019).equals("222"), "T1 Cantidad Naves 2019 = 222");
        check(valor(TablaHistorica.INFRAESTRUCTURA, "Camiones", 2016).equals("58"), "T1 Camiones 2016 = 58");
        check(valor(TablaHistorica.EFICIENCIA_PRODUCTIVA, "Aprovechamiento Capacidades (%)", 2017).equals("69.5"), "T2 Aprovechamiento 2017 = 69.5");
        check(valor(TablaHistorica.EFICIENCIA_PRODUCTIVA, "Productividad Huevo/Ave (U)", 2019).equals("259.62"), "T2 Productividad 2019 = 259.62");
        check(valor(TablaHistorica.EFICIENCIA_PRODUCTIVA, "Producción Total Carne (t)", 2015).equals("832.2"), "T2 Carne 2015 = 832.2");
        check(valor(TablaHistorica.DESTINOS_PROYECCIONES, "Huevos ponedoras comerciales: Total", 2030).equals("182.2"), "T3 Total 2030 = 182.2");
        check(valor(TablaHistorica.DESTINOS_PROYECCIONES, "Huevos ponedoras comerciales: Turismo/Mercado MLC", 2025).equals("21.4"), "T3 Turismo 2025 = 21.4");
        check(valor(TablaHistorica.DESTINOS_PROYECCIONES, "Autoabastecimiento Municipal (t)", 2030).equals("9862.0"), "T3 Autoabastecimiento 2030 = 9862.0");
        MetaEstrategica mHuevos = service.consultarMetas().get(0);
        MetaEstrategica mCarne = service.consultarMetas().get(1);
        check(mHuevos.getValorReal2019().toPlainString().equals("211.9"), "T4 Huevos real 2019 = 211.9");
        check(mHuevos.getValorEstimado2030().toPlainString().equals("241.4"), "T4 Huevos meta 2030 = 241.4");
        check(mCarne.getValorReal2019().toPlainString().equals("1208.8"), "T4 Carne real 2019 = 1208.8");
        check(mCarne.getValorEstimado2030().toPlainString().equals("10051.9"), "T4 Carne meta 2030 = 10051.9");

        System.out.println("3) Consulta de referencias por año");
        check(service.consultarReferenciasPorAnio(2015).size() == 12, "2015: 5 indicadores T1 + 7 de T2");
        check(service.consultarReferencias(TablaHistorica.EFICIENCIA_PRODUCTIVA, 2016).isEmpty(), "No existe 2016 en la tabla 2");
        check(service.consultarReferencias(TablaHistorica.DESTINOS_PROYECCIONES, 2030).size() == 7, "2030: 7 indicadores de la tabla 3");

        System.out.println("4) Consulta de huevos por período");
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);
        List<RegistroAnalisis> huevos = service.consultarHuevosPorPeriodo(desde, hasta);
        check(huevos.size() == 3, "3 partes en el período");
        check(huevos.stream().filter(r -> r.getCantidad() == null).count() == 1, "El parte sin dato conserva cantidad nula");
        check(huevos.stream().anyMatch(r -> r.getObservacion().isEmpty()), "Los partes con dato no se marcan como sin dato");

        System.out.println("5) Consulta por UEB y nave");
        check(service.consultarHuevosPorUEB(idUeb1, desde, hasta).size() == 2, "UEB 1 tiene 2 partes");
        check(service.consultarHuevosPorNave(idNave1, desde, hasta).size() == 2, "Nave 1 tiene 2 partes");
        check(service.consultarPartesUnidadesAsociadas(desde, hasta).size() == 1, "Parte de unidad asociada sin campos productivos");

        System.out.println("6) Consulta de carne por período y UEB");
        check(service.consultarCarnePorPeriodo(desde, hasta).size() == 2, "2 registros de carne en el período");
        check(service.consultarCarnePorUEB(idUeb1, desde, hasta).size() == 1, "UEB 1 tiene 1 registro de carne");

        System.out.println("7) Consolidaciones sin duplicación");
        List<RegistroAnalisis> consolidados = service.consultarConsolidaciones(desde, hasta);
        check(consolidados.size() == 2, "2 consolidaciones, una fila por registro");
        check(consolidados.stream().allMatch(r -> r.getCantidad() == null), "Las consolidaciones no aportan cantidades a los totales");
        check(huevos.size() == 3, "Consultar consolidaciones no altera los partes");

        System.out.println("8) Períodos sin información");
        LocalDate vacioDesde = LocalDate.of(2035, 1, 1);
        LocalDate vacioHasta = LocalDate.of(2035, 1, 31);
        check(service.consultarHuevosPorPeriodo(vacioDesde, vacioHasta).isEmpty(), "Sin huevos en 2035");
        check(service.consultarCarnePorPeriodo(vacioDesde, vacioHasta).isEmpty(), "Sin carne en 2035");
        check(service.compararMetasConProduccionOperativa(vacioDesde, vacioHasta).get(0).getProduccionOperativa() == null,
                "Producción operativa ausente = null, nunca cero");
        expectValidation(() -> service.consultarHuevosPorPeriodo(hasta, desde), "período invertido");

        System.out.println("9) Comparación con metas estratégicas");
        List<cu.pican.model.ComparacionMeta> comparaciones = service.compararMetasConProduccionOperativa(desde, hasta);
        check(comparaciones.size() == 2, "2 filas de comparación");
        cu.pican.model.ComparacionMeta ch = comparaciones.get(0);
        check(ch.getValorHistorico2019().toPlainString().equals("211.9"), "Histórico oficial 211.9");
        check(ch.getValorMeta2030().toPlainString().equals("241.4"), "Meta oficial 241.4");
        check(ch.getProduccionOperativa() != null && ch.getProduccionOperativa().compareTo(new BigDecimal("150")) == 0,
                "Producción operativa 150 huevos con unidad propia");
        check("huevos (U)".equals(ch.getUnidadOperativa()), "Unidad operativa distinta de la histórica");
        check(ch.getAclaracion().contains("Sin porcentaje de cumplimiento"), "Se explicita la ausencia de cálculo de cumplimiento");

        System.out.println("10) Incrementos estratégicos aprobados");
        check(mHuevos.getIncrementoPorcentual().toPlainString().equals("13.92"), "Incremento huevos = 13.92");
        check(mCarne.getIncrementoPorcentual().toPlainString().equals("731.56"), "Incremento carne = 731.56");
        check(comparaciones.get(1).getIncrementoPorcentualAprobado().toPlainString().equals("731.56"), "Incremento propagado a la comparación");

        System.out.println("11) Referencias de solo lectura");
        for (Method m : AnalisisService.class.getDeclaredMethods()) {
            String n = m.getName().toLowerCase(Locale.ROOT);
            check(!n.startsWith("registrar") && !n.startsWith("actualizar") && !n.startsWith("guardar")
                    && !n.startsWith("modificar") && !n.startsWith("eliminar") && !n.startsWith("borrar"),
                    "Sin método de escritura: " + m.getName());
        }
        for (Class<?> clazz : new Class<?>[]{IndicadorHistorico.class, MetaEstrategica.class}) {
            for (Method m : clazz.getDeclaredMethods()) {
                check(!m.getName().startsWith("set"), clazz.getSimpleName() + " sin setters");
            }
        }
        check(!cu.pican.model.repository.Repository.class.isAssignableFrom(cu.pican.model.repository.IndicadorHistoricoRepository.class),
                "Repositorio histórico no extiende Repository");
        check(!cu.pican.model.repository.Repository.class.isAssignableFrom(cu.pican.model.repository.MetaEstrategicaRepository.class),
                "Repositorio de metas no extiende Repository");

        System.out.println("12) Sin fórmulas de cumplimiento");
        for (Class<?> clazz : new Class<?>[]{AnalisisService.class, cu.pican.model.ComparacionMeta.class}) {
            for (Method m : clazz.getDeclaredMethods()) {
                check(!m.getName().toLowerCase(Locale.ROOT).contains("cumplimiento"),
                        clazz.getSimpleName() + "." + m.getName() + " no calcula cumplimiento");
            }
            for (Field f : clazz.getDeclaredFields()) {
                check(!f.getName().toLowerCase(Locale.ROOT).contains("cumplimiento"),
                        clazz.getSimpleName() + " sin campo de cumplimiento");
            }
        }

        System.out.println("\n==> Verificación automatizada de Etapa 4.10 (CU-14) finalizada correctamente.");
        System.out.println("Nota: metas y referencias solo lectura; sin cuotas, sin porcentajes de cumplimiento y sin PostgreSQL.");
    }

    private static void prepararDatosOperativos(UEBService uebService, NaveService naveService,
                                                UnidadAsociadaService unidadService, ParteNaveService parteService,
                                                ParteNaveRepository parteRepo,
                                                ParteUnidadAsociadaService parteAsocService,
                                                ProduccionCarneService carneService,
                                                ConsolidadoUEBService consolidadoService) {
        idUeb1 = UUID.randomUUID();
        UUID idUeb2 = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb1, "UEB Uno", "UEB1", true));
        uebService.registrar(new UEB(idUeb2, "UEB Dos", "UEB2", true));

        idNave1 = UUID.randomUUID();
        UUID idNave2 = UUID.randomUUID();
        naveService.registrar(new Nave(idNave1, idUeb1, "Nave A", "NA", true));
        naveService.registrar(new Nave(idNave2, idUeb2, "Nave B", "NB", true));

        parteService.registrar(parte(UUID.randomUUID(), idNave1, LocalDate.of(2026, 1, 5), 100));
        parteService.registrar(parte(UUID.randomUUID(), idNave1, LocalDate.of(2026, 1, 12), 50));
        // El servicio exige huevosProducidos no nulo; el caso "sin dato" se inserta
        // directamente en el repositorio para verificar el tratamiento analítico de NULL.
        parteRepo.save(parte(UUID.randomUUID(), idNave2, LocalDate.of(2026, 1, 20), null));

        UUID idUnidad = UUID.randomUUID();
        unidadService.registrar(new UnidadAsociada(idUnidad, "Unidad Asociada Uno", "UA1", true));
        ParteUnidadAsociada pa = new ParteUnidadAsociada();
        pa.setIdParteAsociado(UUID.randomUUID());
        pa.setIdUnidad(idUnidad);
        pa.setIdUEB(idUeb1);
        pa.setEstado(ParteUnidadAsociada.ESTADO_REGISTRADO);
        pa.setFechaProduccion(LocalDate.of(2026, 1, 15));
        pa.setReferenciaPapel("Ref papel 15");
        parteAsocService.registrar(pa);

        carneService.registrar(carne(UUID.randomUUID(), idUeb1, LocalDate.of(2026, 1, 10), "12.5"));
        carneService.registrar(carne(UUID.randomUUID(), idUeb2, LocalDate.of(2026, 1, 18), "8.0"));

        consolidadoService.registrar(consolidado(UUID.randomUUID(), idUeb1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15)));
        consolidadoService.registrar(consolidado(UUID.randomUUID(), idUeb2,
                LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 31)));
    }

    private static ParteNave parte(UUID id, UUID idNave, LocalDate fecha, Integer huevos) {
        ParteNave p = new ParteNave();
        p.setIdParte(id);
        p.setIdNave(idNave);
        p.setFechaProduccion(fecha);
        p.setGallinasVivas(0);
        p.setGallinasMuertas(0);
        p.setGallinasEnfermas(0);
        p.setHuevosProducidos(huevos);
        p.setHuevosRotos(0);
        p.setEstado(ParteNave.ESTADO_REGISTRADO);
        return p;
    }

    private static ProduccionCarne carne(UUID id, UUID idUEB, LocalDate fecha, String cantidad) {
        ProduccionCarne c = new ProduccionCarne();
        c.setIdProduccion(id);
        c.setIdUEB(idUEB);
        c.setFechaProduccion(fecha);
        c.setCantidadToneladas(new BigDecimal(cantidad));
        return c;
    }

    private static ConsolidadoUEB consolidado(UUID id, UUID idUEB, LocalDate inicio, LocalDate fin) {
        ConsolidadoUEB c = new ConsolidadoUEB();
        c.setIdConsolidado(id);
        c.setIdUEB(idUEB);
        c.setFechaInicio(inicio);
        c.setFechaFin(fin);
        c.setEstado("REGISTRADO");
        return c;
    }

    private static String valor(TablaHistorica tabla, String indicador, int anio) {
        for (IndicadorHistorico i : service.consultarReferenciasPorIndicador(indicador)) {
            if (i.getTabla() == tabla && i.getAnio() == anio) {
                return i.getValor().toPlainString();
            }
        }
        throw new IllegalStateException("No existe " + indicador + " " + anio);
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