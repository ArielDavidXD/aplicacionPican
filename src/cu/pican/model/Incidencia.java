package cu.pican.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Incidencia de entrega (CU-11/CU-12).
 * Mantiene su identidad y asociaciones originales (vale/recepción) y amplía los datos
 * necesarios para completar, dar seguimiento y cerrar la diferencia detectada.
 */
public class Incidencia {

    public static final String ESTADO_ABIERTA = "ABIERTA";
    public static final String ESTADO_CERRADA = "CERRADA";

    // Tipos admitidos por CU-12 (sin valores arbitrarios)
    public static final String TIPO_FALTANTE = "FALTANTE";
    public static final String TIPO_SOBRANTE = "SOBRANTE";
    public static final String TIPO_CALIDAD = "CALIDAD";
    public static final String TIPO_ROTURA = "ROTURA";
    public static final String TIPO_OTRO = "OTRO";

    private UUID idIncidencia;
    private UUID idVale;
    private UUID idRecepcion;
    private String tipo;
    private BigDecimal cantidadAfectada;
    private String descripcion;
    private String medidaAcordada;
    private LocalDateTime fechaCompromiso;
    private String estado;
    private String observaciones; // observaciones originales de la recepción (no se borran)
    private LocalDateTime fechaHoraRegistro;
    private UUID idUsuario;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaCierre;
    private String resultadoSolucion;
    private Long version;

    public Incidencia() {
    }

    public UUID getIdIncidencia() {
        return idIncidencia;
    }

    public void setIdIncidencia(UUID idIncidencia) {
        this.idIncidencia = idIncidencia;
    }

    public UUID getIdVale() {
        return idVale;
    }

    public void setIdVale(UUID idVale) {
        this.idVale = idVale;
    }

    public UUID getIdRecepcion() {
        return idRecepcion;
    }

    public void setIdRecepcion(UUID idRecepcion) {
        this.idRecepcion = idRecepcion;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getCantidadAfectada() {
        return cantidadAfectada;
    }

    public void setCantidadAfectada(BigDecimal cantidadAfectada) {
        this.cantidadAfectada = cantidadAfectada;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getMedidaAcordada() {
        return medidaAcordada;
    }

    public void setMedidaAcordada(String medidaAcordada) {
        this.medidaAcordada = medidaAcordada;
    }

    public LocalDateTime getFechaCompromiso() {
        return fechaCompromiso;
    }

    public void setFechaCompromiso(LocalDateTime fechaCompromiso) {
        this.fechaCompromiso = fechaCompromiso;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public LocalDateTime getFechaHoraRegistro() {
        return fechaHoraRegistro;
    }

    public void setFechaHoraRegistro(LocalDateTime fechaHoraRegistro) {
        this.fechaHoraRegistro = fechaHoraRegistro;
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

    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDateTime fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public String getResultadoSolucion() {
        return resultadoSolucion;
    }

    public void setResultadoSolucion(String resultadoSolucion) {
        this.resultadoSolucion = resultadoSolucion;
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
        if (!(o instanceof Incidencia)) return false;
        Incidencia that = (Incidencia) o;
        return Objects.equals(idIncidencia, that.idIncidencia);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idIncidencia);
    }
}
