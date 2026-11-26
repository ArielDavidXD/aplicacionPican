package cu.pican.controller.service;

import cu.pican.controller.app.ReferenciasHistoricasInitializer;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.model.repository.GestionReferenciasRepository;
import cu.pican.model.repository.IndicadorHistoricoRepository;
import cu.pican.model.repository.MetaEstrategicaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * CU-02, CU-03 y CU-18. Producción registra infraestructura y metas; el administrador
 * carga las cuatro tablas oficiales desde Excel o desde el panel administrativo.
 * La consulta de análisis sigue siendo de solo lectura para los roles autorizados.
 */
public class GestionReferenciasService extends ServicioAutorizado {

    private final GestionReferenciasRepository gestion;
    private final IndicadorHistoricoRepository indicadores;
    private final MetaEstrategicaRepository metas;

    public GestionReferenciasService(GestionReferenciasRepository gestion,
                                     IndicadorHistoricoRepository indicadores,
                                     MetaEstrategicaRepository metas) {
        this.gestion = Objects.requireNonNull(gestion, "gestion");
        this.indicadores = Objects.requireNonNull(indicadores, "indicadores");
        this.metas = Objects.requireNonNull(metas, "metas");
    }

    public List<IndicadorHistorico> listarIndicadores() {
        autorizacion().requerirLectura(CasoUso.CU_02, CasoUso.CU_03, CasoUso.CU_18);
        return indicadores.findAll();
    }

    public List<MetaEstrategica> listarMetas() {
        autorizacion().requerirLectura(CasoUso.CU_02, CasoUso.CU_03, CasoUso.CU_18);
        return metas.findAll();
    }

    public IndicadorHistorico registrarIndicador(TablaHistorica tabla, String indicador, Integer anio,
                                                 BigDecimal valor, String unidadMedida, String fuente) {
        requerirGestionIndicador(tabla);
        IndicadorHistorico oficial = validarIndicador(tabla, indicador, anio, valor, unidadMedida, null);
        if (gestion.existeIndicador(tabla, oficial.getIndicador(), anio, null)) {
            throw new ValidacionException(
                    "Ya existe un registro oficial para ese indicador y año en la tabla seleccionada.");
        }
        IndicadorHistorico registro = new IndicadorHistorico("CAT-" + UUID.randomUUID(), tabla,
                oficial.getIndicador(), anio, oficial.getValor(), oficial.getUnidadMedida(),
                fuente == null ? "" : fuente.trim());
        return gestion.guardarIndicador(registro);
    }

    public IndicadorHistorico actualizarIndicador(String id, TablaHistorica tabla, String indicador, Integer anio,
                                                  BigDecimal valor, String unidadMedida, String fuente) {
        requerirGestionIndicador(tabla);
        IndicadorHistorico existente = exigirIndicador(id);
        IndicadorHistorico oficial = validarIndicador(tabla, indicador, anio, valor, unidadMedida,
                existente.getId());
        if (gestion.existeIndicador(tabla, oficial.getIndicador(), anio, existente.getId())) {
            throw new ValidacionException("Ya existe otro registro oficial con ese indicador y año.");
        }
        IndicadorHistorico registro = new IndicadorHistorico(existente.getId(), tabla,
                oficial.getIndicador(), anio, oficial.getValor(), oficial.getUnidadMedida(),
                fuente == null ? "" : fuente.trim());
        return gestion.guardarIndicador(registro);
    }

    public MetaEstrategica registrarMeta(String indicador, BigDecimal valorReal2019, BigDecimal valorEstimado2030,
                                         BigDecimal incrementoPorcentual, String unidadMedida, String fuente) {
        autorizacion().requerir(CasoUso.CU_02, CasoUso.CU_18);
        MetaEstrategica oficial = validarMeta(indicador, valorReal2019, valorEstimado2030,
                incrementoPorcentual, unidadMedida);
        if (gestion.existeMeta(oficial.getIndicador(), null)) {
            throw new ValidacionException("Ya existe una meta estratégica con ese indicador.");
        }
        MetaEstrategica meta = new MetaEstrategica("CAT-" + UUID.randomUUID(), oficial.getIndicador(),
                oficial.getValorReal2019(), oficial.getValorEstimado2030(), oficial.getIncrementoPorcentual(),
                oficial.getUnidadMedida(), fuente == null ? "" : fuente.trim());
        return gestion.guardarMeta(meta);
    }

    public MetaEstrategica actualizarMeta(String id, String indicador, BigDecimal valorReal2019,
                                          BigDecimal valorEstimado2030, BigDecimal incrementoPorcentual,
                                          String unidadMedida, String fuente) {
        autorizacion().requerir(CasoUso.CU_02, CasoUso.CU_18);
        MetaEstrategica existente = exigirMeta(id);
        MetaEstrategica oficial = validarMeta(indicador, valorReal2019, valorEstimado2030,
                incrementoPorcentual, unidadMedida);
        if (gestion.existeMeta(oficial.getIndicador(), existente.getId())) {
            throw new ValidacionException("Ya existe otra meta estratégica con ese indicador.");
        }
        MetaEstrategica meta = new MetaEstrategica(existente.getId(), oficial.getIndicador(),
                oficial.getValorReal2019(), oficial.getValorEstimado2030(), oficial.getIncrementoPorcentual(),
                oficial.getUnidadMedida(), fuente == null ? "" : fuente.trim());
        return gestion.guardarMeta(meta);
    }

    private void requerirGestionIndicador(TablaHistorica tabla) {
        if (tabla == TablaHistorica.INFRAESTRUCTURA) {
            autorizacion().requerir(CasoUso.CU_03, CasoUso.CU_18);
        } else {
            autorizacion().requerir(CasoUso.CU_18);
        }
    }

    private IndicadorHistorico exigirIndicador(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidacionException("Seleccione el registro oficial a modificar.");
        }
        return indicadores.findById(id)
                .orElseThrow(() -> new ValidacionException("El registro oficial seleccionado ya no existe."));
    }

    private MetaEstrategica exigirMeta(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidacionException("Seleccione la meta estratégica a modificar.");
        }
        return metas.findById(id)
                .orElseThrow(() -> new ValidacionException("La meta estratégica seleccionada ya no existe."));
    }

    private IndicadorHistorico validarIndicador(TablaHistorica tabla, String indicador, Integer anio,
                                                BigDecimal valor, String unidadMedida, String idExcluido) {
        if (tabla == null || tabla == TablaHistorica.METAS_ESTRATEGICAS) {
            throw new ValidacionException("Seleccione una de las tres tablas de indicadores históricos.");
        }
        if (indicador == null || indicador.isBlank()) {
            throw new ValidacionException("El indicador es obligatorio.");
        }
        if (anio == null || anio < 1900 || anio > 2100) {
            throw new ValidacionException("El año debe estar entre 1900 y 2100.");
        }
        if (!anioOficial(tabla, anio)) {
            throw new ValidacionException("El año no pertenece al período oficial de la tabla seleccionada.");
        }
        if (valor == null) {
            throw new ValidacionException("El valor es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new ValidacionException("El valor no puede ser negativo.");
        }
        if (tabla == TablaHistorica.INFRAESTRUCTURA && valor.stripTrailingZeros().scale() > 0) {
            throw new ValidacionException("Los valores de infraestructura deben ser enteros.");
        }
        IndicadorHistorico oficial = ReferenciasHistoricasInitializer.buildIndicadores().stream()
                .filter(f -> f.getTabla() == tabla && f.getAnio() == anio)
                .filter(f -> normalizar(f.getIndicador()).equals(normalizar(indicador)))
                .findFirst()
                .orElseThrow(() -> new ValidacionException(
                        "El indicador no pertenece al catálogo oficial de la tabla y año seleccionados."));
        if (valor.compareTo(oficial.getValor()) != 0) {
            throw new ValidacionException("El valor no coincide con la referencia oficial de PICAN.");
        }
        if (unidadMedida == null || !unidadMedida.trim().equals(oficial.getUnidadMedida())) {
            throw new ValidacionException("La unidad no coincide con la referencia oficial: "
                    + oficial.getUnidadMedida() + ".");
        }
        validarNavesPonedoras(tabla, oficial.getIndicador(), anio, oficial.getValor(), idExcluido);
        return oficial;
    }

    private static boolean anioOficial(TablaHistorica tabla, int anio) {
        return switch (tabla) {
            case INFRAESTRUCTURA -> anio >= 2015 && anio <= 2019;
            case EFICIENCIA_PRODUCTIVA -> anio == 2015 || anio == 2017 || anio == 2019;
            case DESTINOS_PROYECCIONES -> anio == 2021 || anio == 2025 || anio == 2030;
            case METAS_ESTRATEGICAS -> false;
        };
    }

    private void validarNavesPonedoras(TablaHistorica tabla, String indicador, Integer anio,
                                       BigDecimal valor, String idExcluido) {
        if (tabla != TablaHistorica.INFRAESTRUCTURA) {
            return;
        }
        String nombre = normalizar(indicador);
        boolean esTotal = "cantidad de naves".equals(nombre);
        boolean esPonedoras = "naves para ponedoras".equals(nombre);
        if (!esTotal && !esPonedoras) {
            return;
        }
        for (IndicadorHistorico fila : indicadores.findByTablaAndAnio(tabla, anio)) {
            if (idExcluido != null && idExcluido.equals(fila.getId())) {
                continue;
            }
            String otro = normalizar(fila.getIndicador());
            if (esTotal && "naves para ponedoras".equals(otro) && fila.getValor().compareTo(valor) > 0) {
                throw new ValidacionException("Las naves para ponedoras no pueden superar el total de naves del mismo año.");
            }
            if (esPonedoras && "cantidad de naves".equals(otro) && valor.compareTo(fila.getValor()) > 0) {
                throw new ValidacionException("Las naves para ponedoras no pueden superar el total de naves del mismo año.");
            }
        }
    }

    private static String normalizar(String valor) {
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    private static MetaEstrategica validarMeta(String indicador, BigDecimal valorReal2019,
                                                BigDecimal valorEstimado2030,
                                                BigDecimal incrementoPorcentual, String unidadMedida) {
        if (indicador == null || indicador.isBlank()) {
            throw new ValidacionException("El indicador de la meta es obligatorio.");
        }
        MetaEstrategica oficial = ReferenciasHistoricasInitializer.buildMetas().stream()
                .filter(m -> normalizar(m.getIndicador()).equals(normalizar(indicador)))
                .findFirst()
                .orElseThrow(() -> new ValidacionException(
                        "El indicador no pertenece al catálogo oficial de metas estratégicas."));
        if (valorReal2019 == null || valorEstimado2030 == null) {
            throw new ValidacionException("El valor real 2019 y el estimado 2030 son obligatorios.");
        }
        if (valorReal2019.compareTo(oficial.getValorReal2019()) != 0
                || valorEstimado2030.compareTo(oficial.getValorEstimado2030()) != 0) {
            throw new ValidacionException("Los valores no coinciden con la meta estratégica oficial de PICAN.");
        }
        if (unidadMedida == null || !unidadMedida.trim().equals(oficial.getUnidadMedida())) {
            throw new ValidacionException("La unidad no coincide con la referencia oficial: "
                    + oficial.getUnidadMedida() + ".");
        }
        BigDecimal calculado = valorEstimado2030.subtract(valorReal2019)
                .multiply(BigDecimal.valueOf(100))
                .divide(valorReal2019, 2, RoundingMode.HALF_UP);
        if (incrementoPorcentual != null && incrementoPorcentual.compareTo(calculado) != 0) {
            throw new ValidacionException("El incremento no coincide con VAL-12: debe ser " + calculado.toPlainString()
                    + " % para los valores informados.");
        }
        return oficial;
    }
}
