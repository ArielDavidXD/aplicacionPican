package cu.pican.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Parte de producción registrado para una unidad asociada.
 * La información proviene de documentos en papel registrados por la UEB.
 *
 * IMPORTANTE: no se copian automáticamente los campos productivos de {@link ParteNave}.
 * Los campos productivos específicos deben definirse a partir del formulario de papel real
 * (información pendiente de confirmación).
 */
public class ParteUnidadAsociada {

    private UUID idParteAsociado;
    private UUID idUnidad;
    private LocalDate fechaProduccion;
    private String referenciaPapel;
    private String observaciones;
    private String estado; // Valores/flujo pendientes de definición.
    private UUID idUsuario;
    private LocalDateTime creadoEn;
    private Long version;

    public ParteUnidadAsociada() {
    }

    public UUID getIdParteAsociado() {
        return idParteAsociado;
    }

    public void setIdParteAsociado(UUID idParteAsociado) {
        this.idParteAsociado = idParteAsociado;
    }

    public UUID getIdUnidad() {
        return idUnidad;
    }

    public void setIdUnidad(UUID idUnidad) {
        this.idUnidad = idUnidad;
    }

    public LocalDate getFechaProduccion() {
        return fechaProduccion;
    }

    public void setFechaProduccion(LocalDate fechaProduccion) {
        this.fechaProduccion = fechaProduccion;
    }

    public String getReferenciaPapel() {
        return referenciaPapel;
    }

    public void setReferenciaPapel(String referenciaPapel) {
        this.referenciaPapel = referenciaPapel;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
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
        if (!(o instanceof ParteUnidadAsociada)) return false;
        ParteUnidadAsociada that = (ParteUnidadAsociada) o;
        return Objects.equals(idParteAsociado, that.idParteAsociado);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idParteAsociado);
    }
}
