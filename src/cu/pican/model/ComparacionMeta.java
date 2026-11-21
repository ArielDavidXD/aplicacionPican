package cu.pican.model;

import java.math.BigDecimal;

/**
 * Comparación entre una meta estratégica oficial y la producción operativa disponible.
 * NO incluye porcentajes de cumplimiento: no existe base temporal y metodológica aprobada para calcularlos.
 */
public final class ComparacionMeta {

    private final String indicador;
    private final BigDecimal valorHistorico2019;
    private final BigDecimal valorMeta2030;
    private final BigDecimal incrementoPorcentualAprobado;
    private final String unidadHistorica;
    private final BigDecimal produccionOperativa;
    private final String unidadOperativa;
    private final String periodoOperativo;
    private final String aclaracion;

    public ComparacionMeta(String indicador, BigDecimal valorHistorico2019, BigDecimal valorMeta2030,
                           BigDecimal incrementoPorcentualAprobado, String unidadHistorica,
                           BigDecimal produccionOperativa, String unidadOperativa,
                           String periodoOperativo, String aclaracion) {
        this.indicador = indicador;
        this.valorHistorico2019 = valorHistorico2019;
        this.valorMeta2030 = valorMeta2030;
        this.incrementoPorcentualAprobado = incrementoPorcentualAprobado;
        this.unidadHistorica = unidadHistorica;
        this.produccionOperativa = produccionOperativa;
        this.unidadOperativa = unidadOperativa;
        this.periodoOperativo = periodoOperativo;
        this.aclaracion = aclaracion;
    }

    public String getIndicador() {
        return indicador;
    }

    public BigDecimal getValorHistorico2019() {
        return valorHistorico2019;
    }

    public BigDecimal getValorMeta2030() {
        return valorMeta2030;
    }

    public BigDecimal getIncrementoPorcentualAprobado() {
        return incrementoPorcentualAprobado;
    }

    public String getUnidadHistorica() {
        return unidadHistorica;
    }

    public BigDecimal getProduccionOperativa() {
        return produccionOperativa;
    }

    public String getUnidadOperativa() {
        return unidadOperativa;
    }

    public String getPeriodoOperativo() {
        return periodoOperativo;
    }

    public String getAclaracion() {
        return aclaracion;
    }
}
