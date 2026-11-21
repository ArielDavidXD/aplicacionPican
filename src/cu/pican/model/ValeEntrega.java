package cu.pican.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Vale de entrega (un producto por vale).
 * La cantidad y la unidad de medida pertenecen directamente al vale.
 * Estado inicial: EMITIDO. La recepción registra el resultado y puede transitar el vale a CERRADO o RECIBIDO_CON_INCIDENCIA (CU-11).
 * Un vale EMITIDO puede anularse lógicamente (CU-13), conservando el registro y sus datos originales.
 * No se almacenan copias de UEB/Producto/Destino, solo sus identificadores UUID.
 */
public class ValeEntrega {

    public static final String ESTADO_EMITIDO = "EMITIDO";
    public static final String ESTADO_CERRADO = "CERRADO";
    public static final String ESTADO_RECIBIDO_CON_INCIDENCIA = "RECIBIDO_CON_INCIDENCIA";
    public static final String ESTADO_ANULADO = "ANULADO";

    private UUID idVale;
    private String numeroVale;
    private UUID idUEB;
    private UUID idDestino;
    private UUID idProducto;
    private BigDecimal cantidadDespachada;
    private String unidadMedida;
    private LocalDateTime fechaHoraEmision;
    private String estado;
    private UUID idUsuario;
    private LocalDateTime fechaCreacion;
    private String motivoAnulacion;
    private LocalDateTime fechaHoraAnulacion;
    private UUID idUsuarioAnulacion; // opcional mientras no exista sesión definitiva
    private Long version;

    public ValeEntrega() {
    }

    public UUID getIdVale() {
        return idVale;
    }

    public void setIdVale(UUID idVale) {
        this.idVale = idVale;
    }

    public String getNumeroVale() {
        return numeroVale;
    }

    public void setNumeroVale(String numeroVale) {
        this.numeroVale = numeroVale;
    }

    public UUID getIdUEB() {
        return idUEB;
    }

    public void setIdUEB(UUID idUEB) {
        this.idUEB = idUEB;
    }

    public UUID getIdDestino() {
        return idDestino;
    }

    public void setIdDestino(UUID idDestino) {
        this.idDestino = idDestino;
    }

    public UUID getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(UUID idProducto) {
        this.idProducto = idProducto;
    }

    public BigDecimal getCantidadDespachada() {
        return cantidadDespachada;
    }

    public void setCantidadDespachada(BigDecimal cantidadDespachada) {
        this.cantidadDespachada = cantidadDespachada;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public LocalDateTime getFechaHoraEmision() {
        return fechaHoraEmision;
    }

    public void setFechaHoraEmision(LocalDateTime fechaHoraEmision) {
        this.fechaHoraEmision = fechaHoraEmision;
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

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public void setMotivoAnulacion(String motivoAnulacion) {
        this.motivoAnulacion = motivoAnulacion;
    }

    public LocalDateTime getFechaHoraAnulacion() {
        return fechaHoraAnulacion;
    }

    public void setFechaHoraAnulacion(LocalDateTime fechaHoraAnulacion) {
        this.fechaHoraAnulacion = fechaHoraAnulacion;
    }

    public UUID getIdUsuarioAnulacion() {
        return idUsuarioAnulacion;
    }

    public void setIdUsuarioAnulacion(UUID idUsuarioAnulacion) {
        this.idUsuarioAnulacion = idUsuarioAnulacion;
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
        if (!(o instanceof ValeEntrega)) return false;
        ValeEntrega that = (ValeEntrega) o;
        return Objects.equals(idVale, that.idVale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idVale);
    }
}
