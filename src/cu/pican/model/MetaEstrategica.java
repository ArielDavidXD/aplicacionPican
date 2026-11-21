package cu.pican.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Meta estratégica oficial (Tabla 4). El porcentaje de incremento es un valor aprobado,
 * recalculado previamente con dos decimales; NO se calcula aquí.
 * No constituye cuota anual ni por UEB.
 */
public final class MetaEstrategica {

    private final String id;
    private final String indicador;
    private final BigDecimal valorReal2019;
    private final BigDecimal valorEstimado2030;
    private final BigDecimal incrementoPorcentual;
    private final String unidadMedida;
    private final String fuente;

    public MetaEstrategica(String id, String indicador, BigDecimal valorReal2019,
                           BigDecimal valorEstimado2030, BigDecimal incrementoPorcentual,
                           String unidadMedida, String fuente) {
        this.id = Objects.requireNonNull(id, "id");
        this.indicador = Objects.requireNonNull(indicador, "indicador");
        this.valorReal2019 = Objects.requireNonNull(valorReal2019, "valorReal2019");
        this.valorEstimado2030 = Objects.requireNonNull(valorEstimado2030, "valorEstimado2030");
        this.incrementoPorcentual = Objects.requireNonNull(incrementoPorcentual, "incrementoPorcentual");
        this.unidadMedida = unidadMedida == null ? "" : unidadMedida;
        this.fuente = fuente == null ? "" : fuente;
    }

    public String getId() {
        return id;
    }

    public String getIndicador() {
        return indicador;
    }

    public BigDecimal getValorReal2019() {
        return valorReal2019;
    }

    public BigDecimal getValorEstimado2030() {
        return valorEstimado2030;
    }

    public BigDecimal getIncrementoPorcentual() {
        return incrementoPorcentual;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public String getFuente() {
        return fuente;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetaEstrategica)) return false;
        return Objects.equals(id, ((MetaEstrategica) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
