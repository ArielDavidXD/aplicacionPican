package cu.pican.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Producción de carne.
 * La cantidad se registra en toneladas.
 * No se separa carne especializada y alternativa en la captura operativa.
 * Pendiente de confirmación: posibilidad de varios registros por UEB y fecha
 * (no se establece restricción única definitiva).
 */
public class ProduccionCarne {

    private UUID idProduccion;
    private UUID idUEB;
    private LocalDate fechaProduccion;
    private BigDecimal cantidadToneladas;
    private String observaciones;
    private UUID idUsuario;
    private LocalDateTime creadoEn;
    private Long version;

    public ProduccionCarne() {
    }

    public UUID getIdProduccion() {
        return idProduccion;
    }

    public void setIdProduccion(UUID idProduccion) {
        this.idProduccion = idProduccion;
    }

    public UUID getIdUEB() {
        return idUEB;
    }

    public void setIdUEB(UUID idUEB) {
        this.idUEB = idUEB;
    }

    public LocalDate getFechaProduccion() {
        return fechaProduccion;
    }

    public void setFechaProduccion(LocalDate fechaProduccion) {
        this.fechaProduccion = fechaProduccion;
    }

    public BigDecimal getCantidadToneladas() {
        return cantidadToneladas;
    }

    public void setCantidadToneladas(BigDecimal cantidadToneladas) {
        this.cantidadToneladas = cantidadToneladas;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(UUID idUsuario) {
        this.idUsuario = idUsuario;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProduccionCarne)) return false;
        ProduccionCarne that = (ProduccionCarne) o;
        return Objects.equals(idProduccion, that.idProduccion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idProduccion);
    }
}
