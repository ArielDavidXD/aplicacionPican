package cu.pican.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Indicador de una tabla histórica oficial de PICAN.
 * Entidad de solo lectura: no expone modificadores y no puede grabarse desde los servicios de consulta.
 */
public final class IndicadorHistorico {

    private final String id;
    private final TablaHistorica tabla;
    private final String indicador;
    private final Integer anio;
    private final BigDecimal valor;
    private final String unidadMedida;
    private final String fuente;

    public IndicadorHistorico(String id, TablaHistorica tabla, String indicador, Integer anio,
                              BigDecimal valor, String unidadMedida, String fuente) {
        this.id = Objects.requireNonNull(id, "id");
        this.tabla = Objects.requireNonNull(tabla, "tabla");
        this.indicador = Objects.requireNonNull(indicador, "indicador");
        this.anio = Objects.requireNonNull(anio, "anio");
        this.valor = Objects.requireNonNull(valor, "valor");
        this.unidadMedida = unidadMedida == null ? "" : unidadMedida;
        this.fuente = fuente == null ? "" : fuente;
    }

    public String getId() {
        return id;
    }

    public TablaHistorica getTabla() {
        return tabla;
    }

    public String getIndicador() {
        return indicador;
    }

    public Integer getAnio() {
        return anio;
    }

    public BigDecimal getValor() {
        return valor;
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
        if (!(o instanceof IndicadorHistorico)) return false;
        return Objects.equals(id, ((IndicadorHistorico) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
