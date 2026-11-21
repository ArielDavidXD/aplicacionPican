package cu.pican.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Recepción de un vale de entrega (CU-11).
 * Registra separadamente lo realmente recibido sin copiar la información completa del vale.
 */
public class Recepcion {

    public static final String RESULTADO_CONFORME = "CONFORME";
    public static final String RESULTADO_NO_CONFORME = "NO_CONFORME";

    private UUID idRecepcion;
    private UUID idVale;
    private LocalDateTime fechaHoraRecepcion;
    private BigDecimal cantidadRecibida;
    private String unidadMedida;
    private String resultado;
    private String observaciones;
    private UUID idUsuario; // opcional mientras no exista sesión definitiva
    private LocalDateTime fechaCreacion;
    private Long version;

    public Recepcion() {
    }

    public UUID getIdRecepcion() {
        return idRecepcion;
    }

    public void setIdRecepcion(UUID idRecepcion) {
        this.idRecepcion = idRecepcion;
    }

    public UUID getIdVale() {
        return idVale;
    }

    public void setIdVale(UUID idVale) {
        this.idVale = idVale;
    }

    public LocalDateTime getFechaHoraRecepcion() {
        return fechaHoraRecepcion;
    }

    public void setFechaHoraRecepcion(LocalDateTime fechaHoraRecepcion) {
        this.fechaHoraRecepcion = fechaHoraRecepcion;
    }

    public BigDecimal getCantidadRecibida() {
        return cantidadRecibida;
    }

    public void setCantidadRecibida(BigDecimal cantidadRecibida) {
        this.cantidadRecibida = cantidadRecibida;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
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

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
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
        if (!(o instanceof Recepcion)) return false;
        Recepcion that = (Recepcion) o;
        return Objects.equals(idRecepcion, that.idRecepcion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idRecepcion);
    }
}
