package cu.pican.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Fila de resultado de una consulta operativa de análisis.
 * La cantidad puede ser nula cuando el registro original no informa ese dato;
 * en ese caso NO se interpreta como cero.
 */
public final class RegistroAnalisis {

    private final LocalDate fecha;
    private final String elemento;
    private final String fuente;
    private final BigDecimal cantidad;
    private final String unidadMedida;
    private final String observacion;

    public RegistroAnalisis(LocalDate fecha, String elemento, String fuente, BigDecimal cantidad,
                            String unidadMedida, String observacion) {
        this.fecha = fecha;
        this.elemento = elemento;
        this.fuente = Objects.requireNonNull(fuente, "fuente");
        this.cantidad = cantidad;
        this.unidadMedida = unidadMedida == null ? "" : unidadMedida;
        this.observacion = observacion == null ? "" : observacion;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getElemento() {
        return elemento;
    }

    public String getFuente() {
        return fuente;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public String getObservacion() {
        return observacion;
    }
}
