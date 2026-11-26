package cu.pican.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class AclaracionOperativa {

    public static final String PARTE_NAVE = "PARTE_NAVE";
    public static final String PARTE_UNIDAD = "PARTE_UNIDAD";
    public static final String PRODUCCION_CARNE = "PRODUCCION_CARNE";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String RESUELTA = "RESUELTA";

    private UUID idAclaracion;
    private UUID idUEBOrigen;
    private String tipoOrigen;
    private UUID idOrigen;
    private String motivo;
    private String estado;
    private UUID idUsuario;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaResolucion;
    private String observacionResolucion;

    public AclaracionOperativa() {
    }

    public UUID getIdAclaracion() { return idAclaracion; }
    public void setIdAclaracion(UUID idAclaracion) { this.idAclaracion = idAclaracion; }
    public UUID getIdUEBOrigen() { return idUEBOrigen; }
    public void setIdUEBOrigen(UUID idUEBOrigen) { this.idUEBOrigen = idUEBOrigen; }
    public String getTipoOrigen() { return tipoOrigen; }
    public void setTipoOrigen(String tipoOrigen) { this.tipoOrigen = tipoOrigen; }
    public UUID getIdOrigen() { return idOrigen; }
    public void setIdOrigen(UUID idOrigen) { this.idOrigen = idOrigen; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public UUID getIdUsuario() { return idUsuario; }
    public void setIdUsuario(UUID idUsuario) { this.idUsuario = idUsuario; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(LocalDateTime fechaResolucion) { this.fechaResolucion = fechaResolucion; }
    public String getObservacionResolucion() { return observacionResolucion; }
    public void setObservacionResolucion(String observacionResolucion) { this.observacionResolucion = observacionResolucion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AclaracionOperativa that)) return false;
        return Objects.equals(idAclaracion, that.idAclaracion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idAclaracion);
    }
}
