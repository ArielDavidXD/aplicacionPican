package cu.pican.controller.service;

import cu.pican.model.ComparacionMeta;
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
import cu.pican.model.repository.IndicadorHistoricoRepository;
import cu.pican.model.repository.MetaEstrategicaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CU-14: consultas de analisis. Separa estrictamente dos fuentes:
 * - HISTORICO OFICIAL: tablas de referencia y metas (solo lectura).
 * - OPERATIVO: partes de nave, produccion de carne, consolidaciones y UEB registradas en el sistema.
 *
 * No calcula porcentajes de cumplimiento (no existe base temporal y metodologica aprobada),
 * no convierte unidades, no suma consolidaciones a los partes y no interpreta datos ausentes como cero.
 */
public class AnalisisService extends ServicioAutorizado {

    private static final String FUENTE_PARTE_NAVE = "OPERATIVO: Parte de produccion de huevos por nave";
    private static final String FUENTE_CARNE = "OPERATIVO: Produccion de carne registrada";
    private static final String FUENTE_CONSOLIDADO = "OPERATIVO: Consolidado de UEB (no se suma a los partes)";
    private static final String FUENTE_ASOCIADAS = "OPERATIVO: Parte de unidad asociada (sin campos productivos definidos)";
    private static final String SIN_DATO = "Sin dato en el registro original; NO se interpreta como cero.";

    private final IndicadorHistoricoRepository indicadorRepository;
    private final MetaEstrategicaRepository metaRepository;
    private final ParteNaveService parteNaveService;
    private final NaveService naveService;
    private final UEBService uebService;
    private final ProduccionCarneService produccionCarneService;
    private final ConsolidadoUEBService consolidadoService;
    private final ParteUnidadAsociadaService parteAsociadaService;

    public AnalisisService(IndicadorHistoricoRepository indicadorRepository,
                           MetaEstrategicaRepository metaRepository,
                           ParteNaveService parteNaveService,
                           NaveService naveService,
                           UEBService uebService,
                           ProduccionCarneService produccionCarneService,
                           ConsolidadoUEBService consolidadoService,
                           ParteUnidadAsociadaService parteAsociadaService) {
        this.indicadorRepository = indicadorRepository;
        this.metaRepository = metaRepository;
        this.parteNaveService = parteNaveService;
        this.naveService = naveService;
        this.uebService = uebService;
        this.produccionCarneService = produccionCarneService;
        this.consolidadoService = consolidadoService;
        this.parteAsociadaService = parteAsociadaService;
    }

    // -------------------- Informacion historica oficial (solo lectura) --------------------

    public List<IndicadorHistorico> consultarTodasLasReferencias() {
        autorizacion().requerirLectura(CasoUso.CU_01, CasoUso.CU_02, CasoUso.CU_03, CasoUso.CU_14);
        return indicadorRepository.findAll();
    }

    public List<IndicadorHistorico> consultarReferencias(TablaHistorica tabla) {
        autorizacion().requerirLectura(CasoUso.CU_01, CasoUso.CU_02, CasoUso.CU_03, CasoUso.CU_14);
        return indicadorRepository.findByTabla(tabla);
    }

    public List<IndicadorHistorico> consultarReferenciasPorAnio(int anio) {
        autorizacion().requerirLectura(CasoUso.CU_01, CasoUso.CU_02, CasoUso.CU_03, CasoUso.CU_14);
        return indicadorRepository.findByAnio(anio);
    }

    public List<IndicadorHistorico> consultarReferencias(TablaHistorica tabla, int anio) {
        autorizacion().requerirLectura(CasoUso.CU_01, CasoUso.CU_02, CasoUso.CU_03, CasoUso.CU_14);
        return indicadorRepository.findByTablaAndAnio(tabla, anio);
    }

    public List<IndicadorHistorico> consultarReferenciasPorIndicador(String indicador) {
        autorizacion().requerirLectura(CasoUso.CU_01, CasoUso.CU_02, CasoUso.CU_03, CasoUso.CU_14);
        return indicadorRepository.findByIndicador(indicador);
    }

    public List<MetaEstrategica> consultarMetas() {
        autorizacion().requerirLectura(CasoUso.CU_01, CasoUso.CU_02, CasoUso.CU_03, CasoUso.CU_14);
        return metaRepository.findAll();
    }

    /** UEB disponibles para los filtros: lectura de catálogo compartida por varios CU. */
    public List<UEB> listarUEBs() {
        autorizacion().requerirLecturaCatalogo();
        return uebService.listarTodas();
    }

    /** Naves disponibles para los filtros: lectura de catálogo compartida por varios CU. */
    public List<Nave> listarNaves() {
        autorizacion().requerirLecturaCatalogo();
        return naveService.listarTodas();
    }

    // -------------------- Informacion operativa --------------------

    /** Consulta de huevos por periodo (todas las naves). Solo se lee el campo comparable "huevos producidos". */
    public List<RegistroAnalisis> consultarHuevosPorPeriodo(LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        validarPeriodo(desde, hasta);
        List<RegistroAnalisis> out = new ArrayList<>();
        for (ParteNave parte : parteNaveService.listarTodos()) {
            if (enPeriodo(parte.getFechaProduccion(), desde, hasta)) {
                out.add(registroHuevo(parte));
            }
        }
        return ordenar(out);
    }

    /** Consulta de huevos por UEB y periodo; las naves se resuelven desde la relacion existente. */
    public List<RegistroAnalisis> consultarHuevosPorUEB(UUID idUEB, LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        ValidationUtils.requireUuid(idUEB, "La UEB es obligatoria.");
        validarUEB(idUEB);
        validarPeriodo(desde, hasta);
        Set<UUID> navesDeLaUEB = naveService.buscarPorUEB(idUEB).stream()
                .map(Nave::getIdNave)
                .collect(Collectors.toSet());
        List<RegistroAnalisis> out = new ArrayList<>();
        for (ParteNave parte : parteNaveService.listarTodos()) {
            if (navesDeLaUEB.contains(parte.getIdNave())
                    && enPeriodo(parte.getFechaProduccion(), desde, hasta)) {
                out.add(registroHuevo(parte));
            }
        }
        return ordenar(out);
    }

    /** Consulta de huevos por nave y periodo. */
    public List<RegistroAnalisis> consultarHuevosPorNave(UUID idNave, LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        ValidationUtils.requireUuid(idNave, "La nave es obligatoria.");
        if (!naveService.buscarPorId(idNave).isPresent()) {
            throw new ValidacionException("La nave indicada no existe.");
        }
        validarPeriodo(desde, hasta);
        List<RegistroAnalisis> out = new ArrayList<>();
        for (ParteNave parte : parteNaveService.buscarPorNave(idNave)) {
            if (enPeriodo(parte.getFechaProduccion(), desde, hasta)) {
                out.add(registroHuevo(parte));
            }
        }
        return ordenar(out);
    }

    /** Consulta de carne por periodo (todas las UEB). */
    public List<RegistroAnalisis> consultarCarnePorPeriodo(LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        validarPeriodo(desde, hasta);
        List<RegistroAnalisis> out = new ArrayList<>();
        for (ProduccionCarne produccion : produccionCarneService.listarTodas()) {
            if (enPeriodo(produccion.getFechaProduccion(), desde, hasta)) {
                out.add(registroCarne(produccion));
            }
        }
        return ordenar(out);
    }

    /** Consulta de carne por UEB y periodo. */
    public List<RegistroAnalisis> consultarCarnePorUEB(UUID idUEB, LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        ValidationUtils.requireUuid(idUEB, "La UEB es obligatoria.");
        validarUEB(idUEB);
        validarPeriodo(desde, hasta);
        List<RegistroAnalisis> out = new ArrayList<>();
        for (ProduccionCarne produccion : produccionCarneService.buscarPorUEB(idUEB)) {
            if (enPeriodo(produccion.getFechaProduccion(), desde, hasta)) {
                out.add(registroCarne(produccion));
            }
        }
        return ordenar(out);
    }

    /**
     * Consulta de consolidaciones. Se listan como registros propios; no se agregan a los totales
     * de partes ni se considera que sustituyan a los originales. No hay duplicacion por nave ni por parte.
     */
    public List<RegistroAnalisis> consultarConsolidaciones(LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        validarPeriodo(desde, hasta);
        List<RegistroAnalisis> out = new ArrayList<>();
        for (ConsolidadoUEB consolidado : consolidadoService.listarTodos()) {
            if (!seSolapa(consolidado, desde, hasta)) {
                continue;
            }
            String obs = "Estado: " + valorOGuion(consolidado.getEstado())
                    + ". Motivo parcial: " + valorOGuion(consolidado.getMotivoParcial()) + ". " + SIN_DATO;
            out.add(new RegistroAnalisis(consolidado.getFechaInicio(), nombreUEB(consolidado.getIdUEB()),
                    FUENTE_CONSOLIDADO, null, "", obs));
        }
        return ordenar(out);
    }

    public List<RegistroAnalisis> consultarConsolidacionesPorUEB(UUID idUEB, LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        ValidationUtils.requireUuid(idUEB, "La UEB es obligatoria.");
        validarUEB(idUEB);
        validarPeriodo(desde, hasta);
        List<RegistroAnalisis> out = new ArrayList<>();
        for (ConsolidadoUEB consolidado : consolidadoService.buscarPorUEB(idUEB)) {
            if (seSolapa(consolidado, desde, hasta)) {
                out.add(new RegistroAnalisis(consolidado.getFechaInicio(), nombreUEB(consolidado.getIdUEB()),
                        FUENTE_CONSOLIDADO, null, "",
                        "Estado: " + valorOGuion(consolidado.getEstado()) + ". " + SIN_DATO));
            }
        }
        return ordenar(out);
    }

    /**
     * Los partes de unidades asociadas no tienen campos productivos confirmados: se listan como
     * referencia documental, sin inventar cantidades ni sumarlas a la produccion de huevos.
     */
    public List<RegistroAnalisis> consultarPartesUnidadesAsociadas(LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        validarPeriodo(desde, hasta);
        List<RegistroAnalisis> out = new ArrayList<>();
        for (ParteUnidadAsociada parte : parteAsociadaService.listarTodos()) {
            if (enPeriodo(parte.getFechaProduccion(), desde, hasta)) {
                out.add(new RegistroAnalisis(parte.getFechaProduccion(),
                        valorOGuion(parte.getReferenciaPapel()), FUENTE_ASOCIADAS, null, "",
                        "Campos productivos pendientes de definicion. " + SIN_DATO));
            }
        }
        return ordenar(out);
    }

    /**
     * Muestra la meta oficial junto a la produccion operativa registrada en el periodo solicitado.
     * NO hay calculo de cumplimiento: las unidades y la base temporal no son directamente comparables.
     * El porcentaje mostrado es unicamente el incremento aprobado de la Tabla 4.
     */
    public List<ComparacionMeta> compararMetasConProduccionOperativa(LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_14);
        validarPeriodo(desde, hasta);
        BigDecimal huevos = sumarDisponibles(consultarHuevosPorPeriodo(desde, hasta));
        BigDecimal carne = sumarDisponibles(consultarCarnePorPeriodo(desde, hasta));
        String periodo = textoPeriodo(desde, hasta);

        List<ComparacionMeta> out = new ArrayList<>();
        for (MetaEstrategica meta : consultarMetas()) {
            boolean esHuevo = meta.getIndicador().toLowerCase().contains("huevo");
            out.add(new ComparacionMeta(
                    meta.getIndicador(),
                    meta.getValorReal2019(),
                    meta.getValorEstimado2030(),
                    meta.getIncrementoPorcentual(),
                    meta.getUnidadMedida(),
                    esHuevo ? huevos : carne,
                    esHuevo ? "huevos (U)" : "t",
                    periodo,
                    "Meta 2030 oficial (no cuota anual). Produccion operativa registrada en "
                            + periodo + " con su propia unidad. Sin porcentaje de cumplimiento: "
                            + "no existe base temporal y metodologica aprobada."));
        }
        return out;
    }

    // -------------------- Auxiliares --------------------

    private RegistroAnalisis registroHuevo(ParteNave parte) {
        BigDecimal cantidad = parte.getHuevosProducidos() == null
                ? null : BigDecimal.valueOf(parte.getHuevosProducidos());
        return new RegistroAnalisis(parte.getFechaProduccion(), nombreNave(parte.getIdNave()),
                FUENTE_PARTE_NAVE, cantidad, "huevos (U)", cantidad == null ? SIN_DATO : "");
    }

    private RegistroAnalisis registroCarne(ProduccionCarne produccion) {
        return new RegistroAnalisis(produccion.getFechaProduccion(), nombreUEB(produccion.getIdUEB()),
                FUENTE_CARNE, produccion.getCantidadToneladas(), "t",
                produccion.getCantidadToneladas() == null ? SIN_DATO : "");
    }

    private String nombreNave(UUID idNave) {
        if (idNave == null) {
            return "(nave no indicada)";
        }
        return naveService.buscarPorId(idNave).map(Nave::getNombre).orElse("(nave no encontrada)");
    }

    private String nombreUEB(UUID idUEB) {
        if (idUEB == null) {
            return "(UEB no indicada)";
        }
        return uebService.buscarPorId(idUEB).map(UEB::getNombre).orElse("(UEB no encontrada)");
    }

    private void validarUEB(UUID idUEB) {
        if (!uebService.buscarPorId(idUEB).isPresent()) {
            throw new ValidacionException("La UEB indicada no existe.");
        }
    }

    private void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ValidacionException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private boolean enPeriodo(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null) {
            return false;
        }
        return (desde == null || !fecha.isBefore(desde)) && (hasta == null || !fecha.isAfter(hasta));
    }

    /** Solapamiento de intervalos cerrados [inicio, fin] con limites de filtro opcionales. */
    private boolean seSolapa(ConsolidadoUEB c, LocalDate desde, LocalDate hasta) {
        if (c.getFechaInicio() == null || c.getFechaFin() == null) {
            return false;
        }
        if (desde == null) {
            return !c.getFechaInicio().isAfter(hasta);
        }
        if (hasta == null) {
            return !c.getFechaFin().isBefore(desde);
        }
        return !c.getFechaFin().isBefore(desde) && !c.getFechaInicio().isAfter(hasta);
    }

    /** Devuelve null cuando no hay ningun dato informado: nunca se devuelve cero por ausencia. */
    private BigDecimal sumarDisponibles(List<RegistroAnalisis> registros) {
        BigDecimal total = null;
        for (RegistroAnalisis r : registros) {
            if (r.getCantidad() == null) {
                continue;
            }
            total = total == null ? r.getCantidad() : total.add(r.getCantidad());
        }
        return total;
    }

    private List<RegistroAnalisis> ordenar(List<RegistroAnalisis> registros) {
        registros.sort(Comparator.comparing(RegistroAnalisis::getFecha,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> valorOGuion(r.getElemento())));
        return registros;
    }

    private String valorOGuion(String valor) {
        return valor == null || valor.trim().isEmpty() ? "-" : valor;
    }

    private String textoPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            return "todo el periodo registrado";
        }
        if (desde == null) {
            return "hasta " + hasta;
        }
        if (hasta == null) {
            return "desde " + desde;
        }
        return desde + " a " + hasta;
    }
}