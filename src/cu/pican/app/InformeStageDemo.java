package cu.pican.app;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.DecisionDirectivo;
import cu.pican.model.DestinatarioInstitucional;
import cu.pican.model.EntregaInforme;
import cu.pican.model.FuenteInforme;
import cu.pican.model.Informe;
import cu.pican.model.Nave;
import cu.pican.model.OpcionFuente;
import cu.pican.model.ParteNave;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.TipoFuenteInforme;
import cu.pican.model.UEB;
import cu.pican.repository.ConsolidadoUEBRepository;
import cu.pican.repository.DecisionDirectivoRepository;
import cu.pican.repository.EntregaInformeRepository;
import cu.pican.repository.FuenteInformeRepository;
import cu.pican.repository.InformeRepository;
import cu.pican.repository.NaveRepository;
import cu.pican.repository.ParteNaveRepository;
import cu.pican.repository.ProduccionCarneRepository;
import cu.pican.repository.UEBRepository;
import cu.pican.repository.UnidadAsociadaRepository;
import cu.pican.repository.memory.ConsolidadoUEBRepositoryMemory;
import cu.pican.repository.memory.DecisionDirectivoRepositoryMemory;
import cu.pican.repository.memory.EntregaInformeRepositoryMemory;
import cu.pican.repository.memory.FuenteInformeRepositoryMemory;
import cu.pican.repository.memory.IndicadorHistoricoRepositoryMemory;
import cu.pican.repository.memory.InformeRepositoryMemory;
import cu.pican.repository.memory.MetaEstrategicaRepositoryMemory;
import cu.pican.repository.memory.NaveRepositoryMemory;
import cu.pican.repository.memory.ParteNaveRepositoryMemory;
import cu.pican.repository.memory.ProduccionCarneRepositoryMemory;
import cu.pican.repository.memory.UEBRepositoryMemory;
import cu.pican.repository.memory.UnidadAsociadaRepositoryMemory;
import cu.pican.service.AnalisisService;
import cu.pican.service.ConsolidadoUEBService;
import cu.pican.service.InformeService;
import cu.pican.service.NaveService;
import cu.pican.service.ParteNaveService;
import cu.pican.service.ProduccionCarneService;
import cu.pican.service.UEBService;
import cu.pican.service.ValidacionException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Verificación automatizada de la Etapa 4.11 (CU-15). Repositorios aislados. */
public class InformeStageDemo {

    private static InformeService service;
    private static LocalDate desde;
    private static LocalDate hasta;

    public static void main(String[] args) throws Exception {
        cu.pican.app.AutorizacionDemo.instalarTodosLosRoles();
        UEBRepository uebRepo = new UEBRepositoryMemory();
        NaveRepository naveRepo = new NaveRepositoryMemory();
        UnidadAsociadaRepository unidadRepo = new UnidadAsociadaRepositoryMemory();
        ParteNaveRepository parteRepo = new ParteNaveRepositoryMemory();
        ProduccionCarneRepository carneRepo = new ProduccionCarneRepositoryMemory();
        ConsolidadoUEBRepository consolidadoRepo = new ConsolidadoUEBRepositoryMemory();
        IndicadorHistoricoRepositoryMemory historicoRepo = new IndicadorHistoricoRepositoryMemory();
        MetaEstrategicaRepositoryMemory metaRepo = new MetaEstrategicaRepositoryMemory();
        ReferenciasHistoricasInitializer.cargar(historicoRepo, metaRepo);

        UEBService uebService = new UEBService(uebRepo);
        NaveService naveService = new NaveService(naveRepo, uebRepo);
        ParteNaveService parteService = new ParteNaveService(parteRepo, naveRepo, null);
        ProduccionCarneService carneService = new ProduccionCarneService(carneRepo, uebRepo);
        ConsolidadoUEBService consolidadoService = new ConsolidadoUEBService(consolidadoRepo, uebRepo);

        desde = LocalDate.of(2026, 1, 1);
        hasta = LocalDate.of(2026, 1, 31);
        prepararDatosAnalisis(uebService, naveService, parteService, carneService, consolidadoService);

        AnalisisService analisisService = new AnalisisService(historicoRepo, metaRepo, parteService,
                naveService, uebService, carneService, consolidadoService, null);

        InformeRepository informeRepo = new InformeRepositoryMemory();
        FuenteInformeRepository fuenteRepo = new FuenteInformeRepositoryMemory();
        DecisionDirectivoRepository decisionRepo = new DecisionDirectivoRepositoryMemory();
        EntregaInformeRepository entregaRepo = new EntregaInformeRepositoryMemory();
        service = new InformeService(informeRepo, fuenteRepo, decisionRepo, entregaRepo, analisisService);

        System.out.println("1) Creación correcta de un informe");
        Informe i1 = crear("Informe de producción - enero 2026");
        check(Informe.ESTADO_ELABORADO.equals(i1.getEstado()), "Estado inicial ELABORADO");
        check(i1.getVersion() == 1L && i1.getNumeroRevision() == 1, "Versión 1 y revisión 1");
        check(i1.getIdInformeRaiz().equals(i1.getIdInforme()), "El informe es la raíz de su cadena");
        check(i1.getFechaCreacion() != null, "Fecha de creación asignada");

        System.out.println("2) Rechazo de datos obligatorios vacíos");
        int antes = service.listarInformes().size();
        expectValidation(() -> service.crearInforme(null, "", desde, hasta, desde, "Especialista", "Contenido"), "título vacío");
        expectValidation(() -> service.crearInforme(null, "Título", desde, hasta, desde, "  ", "Contenido"), "especialista vacío");
        expectValidation(() -> service.crearInforme(null, "Título", desde, hasta, desde, "Especialista", ""), "contenido vacío");
        expectValidation(() -> service.crearInforme(null, "Título", hasta, desde, desde, "Especialista", "Contenido"), "período invertido");
        check(service.listarInformes().size() == antes, "Ningún informe inválido fue registrado");

        System.out.println("3) Registro de fuentes desde AnalisisService");
        List<OpcionFuente> opciones = service.consultarFuentesDisponibles(desde, hasta);
        check(opciones.stream().anyMatch(o -> o.getTipo() == TipoFuenteInforme.REFERENCIA_HISTORICA), "Hay fuentes históricas oficiales");
        check(opciones.stream().anyMatch(o -> o.getTipo() == TipoFuenteInforme.PRODUCCION_HUEVOS), "Hay fuentes de huevos operativos");
        check(opciones.stream().anyMatch(o -> o.getTipo() == TipoFuenteInforme.PRODUCCION_CARNE), "Hay fuentes de carne operativa");
        check(opciones.stream().anyMatch(o -> o.getTipo() == TipoFuenteInforme.CONSOLIDACION_UEB), "Hay fuentes de consolidaciones");
        for (TipoFuenteInforme tipo : TipoFuenteInforme.values()) {
            OpcionFuente op = opciones.stream().filter(o -> o.getTipo() == tipo).findFirst().orElseThrow();
            service.agregarFuente(i1.getIdInforme(), op.getTipo(), op.getIdRegistroOrigen(),
                    op.getDescripcion(), op.getFuente(), op.getDetalleConsulta());
        }
        check(service.consultarFuentes(i1.getIdInforme()).size() == 4, "4 fuentes registradas");
        for (Field f : FuenteInforme.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            check(!n.contains("cantidad") && !n.contains("valor"), "FuenteInforme no copia valores: " + f.getName());
        }

        System.out.println("4) Decisión APROBADO");
        DecisionDirectivo d1 = service.registrarDecision(i1.getIdInforme(), "Directivo Uno",
                DecisionDirectivo.APROBADO, desde, "");
        check(DecisionDirectivo.APROBADO.equals(d1.getDecision()), "Decisión APROBADO registrada");
        check(Informe.ESTADO_APROBADO.equals(service.buscarPorId(i1.getIdInforme()).getEstado()), "Informe APROBADO");
        check(service.consultarHistorialDecisiones(i1.getIdInforme()).size() == 1, "Una decisión en el historial");

        System.out.println("5) Decisión DEVUELTO");
        Informe i2 = crear("Informe devuelto");
        service.agregarFuente(i2.getIdInforme(), TipoFuenteInforme.REFERENCIA_HISTORICA,
                "T1-2019", "Referencia T1", "Información histórica oficial PICAN", null);
        DecisionDirectivo d2 = service.registrarDecision(i2.getIdInforme(), "Directivo Dos",
                DecisionDirectivo.DEVUELTO, hasta, "Revisar el resumen de destinos.");
        check(DecisionDirectivo.DEVUELTO.equals(d2.getDecision()), "Decisión DEVUELTO registrada");
        check(Informe.ESTADO_DEVUELTO.equals(service.buscarPorId(i2.getIdInforme()).getEstado()), "Informe DEVUELTO");
        check("Revisar el resumen de destinos.".equals(d2.getObservacion()), "Observación de devolución conservada");
        check(service.consultarFuentes(i2.getIdInforme()).size() == 1, "Fuente del informe devuelto conservada");

        System.out.println("6) Rechazo de devolución sin observación");
        Informe i3 = crear("Informe sin observación");
        expectValidation(() -> service.registrarDecision(i3.getIdInforme(), "Directivo Tres",
                DecisionDirectivo.DEVUELTO, desde, "   "), "devolución sin observación");
        check(Informe.ESTADO_ELABORADO.equals(service.buscarPorId(i3.getIdInforme()).getEstado()), "Estado no cambia tras el rechazo");
        check(service.consultarHistorialDecisiones(i3.getIdInforme()).isEmpty(), "No se registró la decisión inválida");

        System.out.println("7) Imposibilidad de entregar un informe no aprobado");
        expectValidation(() -> service.registrarEntrega(i3.getIdInforme(), "GEGAN", desde, "Especialista", ""), "entrega en ELABORADO");
        expectValidation(() -> service.registrarEntrega(i2.getIdInforme(), "GEGAN", desde, "Especialista", ""), "entrega en DEVUELTO");
        check(service.consultarEntregas(i3.getIdInforme()).isEmpty(), "Sin entregas en informe no aprobado");

        System.out.println("8) Entrega a GEGAN");
        EntregaInforme e1 = service.registrarEntrega(i1.getIdInforme(), "GEGAN", hasta, "Especialista Uno", "Entrega inicial");
        check(e1.getDestinatario() == DestinatarioInstitucional.GEGAN, "Destinatario GEGAN");
        check(Informe.ESTADO_ENTREGADO.equals(service.buscarPorId(i1.getIdInforme()).getEstado()), "Informe ENTREGADO");
        check(service.buscarPorId(i1.getIdInforme()).getVersion() == 3L, "Versión incrementada a 3");
        expectValidation(() -> service.registrarEntrega(i1.getIdInforme(), "GEGAN", hasta, "Especialista Uno", ""), "entrega duplicada GEGAN");

        System.out.println("9) Entrega a MINAG");
        EntregaInforme e2 = service.registrarEntrega(i1.getIdInforme(), "MINAG", hasta, "Especialista Uno", "");
        check(e2.getDestinatario() == DestinatarioInstitucional.MINAG, "Destinatario MINAG");
        check(service.consultarEntregas(i1.getIdInforme()).size() == 2, "Dos entregas registradas");

        System.out.println("10) Rechazo de destinatarios no contemplados");
        expectValidation(() -> service.registrarEntrega(i1.getIdInforme(), "OTRO", hasta, "Especialista", ""), "destinatario OTRO");
        check(service.consultarEntregas(i1.getIdInforme()).size() == 2, "No se agregó un destinatario inventado");

        System.out.println("11) Conservación del historial de decisiones");
        check(service.consultarHistorialDecisiones(i1.getIdInforme()).size() == 1, "Historial de i1 intacto tras entregas");
        check(service.consultarHistorialDecisiones(i2.getIdInforme()).size() == 1, "Historial de i2 intacto");

        System.out.println("12) Imposibilidad de sobrescribir una decisión anterior");
        expectValidation(() -> service.registrarDecision(i1.getIdInforme(), "Otro Directivo",
                DecisionDirectivo.APROBADO, desde, ""), "segunda decisión sobre informe entregado");
        expectValidation(() -> service.registrarDecision(i2.getIdInforme(), "Otro Directivo",
                DecisionDirectivo.APROBADO, desde, ""), "decisión sobre informe devuelto");
        check(service.consultarHistorialDecisiones(i1.getIdInforme()).size() == 1, "Historial de i1 no sobrescrito");
        check(service.consultarHistorialDecisiones(i2.getIdInforme()).size() == 1, "Historial de i2 no sobrescrito");

        System.out.println("13) Consulta de informes por estado");
        check(service.filtrarPorEstado(Informe.ESTADO_ENTREGADO).stream()
                .anyMatch(i -> i.getIdInforme().equals(i1.getIdInforme())), "i1 aparece en ENTREGADO");
        check(service.filtrarPorEstado(Informe.ESTADO_DEVUELTO).stream()
                .anyMatch(i -> i.getIdInforme().equals(i2.getIdInforme())), "i2 aparece en DEVUELTO");
        check(service.filtrarPorEstado(Informe.ESTADO_ELABORADO).stream()
                .anyMatch(i -> i.getIdInforme().equals(i3.getIdInforme())), "i3 aparece en ELABORADO");

        System.out.println("14) Protección de informes entregados");
        String tituloOriginal = service.buscarPorId(i1.getIdInforme()).getTitulo();
        expectValidation(() -> service.actualizarContenido(i1.getIdInforme(), "Título modificado",
                desde, hasta, desde, "Especialista", "Contenido modificado"), "modificación directa de informe entregado");
        check(tituloOriginal.equals(service.buscarPorId(i1.getIdInforme()).getTitulo()), "Contenido original intacto");

        System.out.println("15) Conservación de versiones después de una corrección");
        Informe revision = service.crearRevision(i2.getIdInforme(), "Informe corregido",
                desde, hasta, hasta, "Especialista Dos", "Contenido corregido.");
        Informe anterior = service.buscarPorId(i2.getIdInforme());
        check(revision.getNumeroRevision() == 2, "Nueva revisión número 2");
        check(i2.getIdInforme().equals(revision.getIdInformeAnterior()), "Se registra el informe anterior");
        check(i2.getIdInformeRaiz().equals(revision.getIdInformeRaiz()), "Misma raíz de cadena");
        check(Informe.ESTADO_ELABORADO.equals(revision.getEstado()), "La revisión inicia en ELABORADO");
        check("Informe devuelto".equals(anterior.getTitulo())
                && Informe.ESTADO_DEVUELTO.equals(anterior.getEstado()), "Versión anterior intacta");
        check(service.consultarFuentes(revision.getIdInforme()).size() == 1, "Fuentes referenciadas en la revisión");
        service.registrarDecision(revision.getIdInforme(), "Directivo Cuatro",
                DecisionDirectivo.APROBADO, hasta, "");
        check(service.consultarHistorialDecisiones(anterior.getIdInforme()).size() == 1, "Decisión anterior conservada");
        check(service.consultarHistorialDecisiones(revision.getIdInforme()).size() == 1, "Nueva decisión en la revisión");
        check(service.consultarRevisiones(anterior.getIdInformeRaiz()).size() == 2, "Dos revisiones en la cadena");

        System.out.println("\n==> Verificación automatizada de Etapa 4.11 (CU-15) finalizada correctamente.");
        System.out.println("Nota: sin autenticación, sin cuentas para directivos, sin PDF/Excel, sin USB y sin PostgreSQL.");
    }

    private static Informe crear(String titulo) {
        return service.crearInforme(null, titulo, desde, hasta, desde, "Especialista de estadística",
                "Resumen del informe " + titulo);
    }

    private static void prepararDatosAnalisis(UEBService uebService, NaveService naveService,
                                              ParteNaveService parteService,
                                              ProduccionCarneService carneService,
                                              ConsolidadoUEBService consolidadoService) {
        UUID idUeb = UUID.randomUUID();
        uebService.registrar(new UEB(idUeb, "UEB Demo", "UEBD", true));
        UUID idNave = UUID.randomUUID();
        naveService.registrar(new Nave(idNave, idUeb, "Nave Demo", "ND", true));

        ParteNave p = new ParteNave();
        p.setIdParte(UUID.randomUUID());
        p.setIdNave(idNave);
        p.setFechaProduccion(LocalDate.of(2026, 1, 5));
        p.setGallinasVivas(0);
        p.setGallinasMuertas(0);
        p.setGallinasEnfermas(0);
        p.setHuevosProducidos(100);
        p.setHuevosRotos(0);
        parteService.registrar(p);

        cu.pican.model.ProduccionCarne c = new cu.pican.model.ProduccionCarne();
        c.setIdProduccion(UUID.randomUUID());
        c.setIdUEB(idUeb);
        c.setFechaProduccion(LocalDate.of(2026, 1, 10));
        c.setCantidadToneladas(new BigDecimal("12.5"));
        carneService.registrar(c);

        ConsolidadoUEB consolidado = new ConsolidadoUEB();
        consolidado.setIdConsolidado(UUID.randomUUID());
        consolidado.setIdUEB(idUeb);
        consolidado.setFechaInicio(desde);
        consolidado.setFechaFin(hasta);
        consolidado.setEstado("REGISTRADO");
        consolidadoService.registrar(consolidado);
    }

    private static void expectValidation(Runnable accion, String caso) {
        try {
            accion.run();
            throw new IllegalStateException("Se esperaba rechazo: " + caso);
        } catch (ValidacionException ex) {
            System.out.println("  OK - Rechazado (" + caso + "): " + ex.getMessage());
        }
    }

    private static void check(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new IllegalStateException("Fallo: " + mensaje);
        }
        System.out.println("  OK - " + mensaje);
    }
}