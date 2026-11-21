package cu.pican.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Consolidado por UEB.
 * Debe reunir registros de producción de huevos (naves y unidades asociadas) y de producción de carne
 * sin borrar ni modificar los registros originales.
 * Pendiente: consolidaciones parciales y condiciones de autorización (deben quedar configurables
 * o documentadas; no se imponen reglas definitivas aquí).
 */
public class ConsolidadoUEB {

    private UUID idConsolidado;
    private UUID idUEB;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado; // Valores/flujo pendientes de definición.
    private String motivoParcial;
    private UUID idUsuario;
    private LocalDateTime creadoEn;
    private Long version;

    public ConsolidadoUEB() {
    }

    public UUID getIdConsolidado() {
        return idConsolidado;
    }

    public void setIdConsolidado(UUID idConsolidado) {
        this.idConsolidado = idConsolidado;
    }

    public UUID getIdUEB() {
        return idUEB;
    }

    public void setIdUEB(UUID idUEB) {
        this.idUEB = idUEB;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMotivoParcial() {
        return motivoParcial;
    }

    public void setMotivoParcial(String motivoParcial) {
        this.motivoParcial = motivoParcial;
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
        if (!(o instanceof ConsolidadoUEB)) return false;
        ConsolidadoUEB that = (ConsolidadoUEB) o;
        return Objects.equals(idConsolidado, that.idConsolidado);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idConsolidado);
    }
}
