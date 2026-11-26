package cu.pican.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Parte de producción de huevos por nave.
 * Clave de negocio: nave + fecha de producción (unicidad validada en el servicio).
 * Reglas aplicadas en el servicio:
 * - Cantidades no negativas.
 * - Gallinas muertas y enfermas no pueden superar las gallinas vivas.
 * - Huevos rotos no pueden superar los huevos producidos.
 * - Huevos buenos = huevos producidos - huevos rotos (cálculo derivado).
 * - Estados: BORRADOR (guardado sin registrar) y REGISTRADO (parte oficial).
 */
public class ParteNave {

    private UUID idParte;
    private UUID idNave;
    private LocalDate fechaProduccion;
    private Integer gallinasVivas;
    private Integer gallinasMuertas;
    private Integer gallinasEnfermas;
    private Integer huevosProducidos;
    private Integer huevosRotos;
    public static final String ESTADO_BORRADOR = "BORRADOR";
    public static final String ESTADO_REGISTRADO = "REGISTRADO";

    private BigDecimal piensoKg;
    private String observaciones;
    private boolean advertenciaRevisada;
    private String estado;
    private UUID idUsuario;
    private LocalDateTime creadoEn;
    private Long version;

    public ParteNave() {
    }

    public UUID getIdParte() {
        return idParte;
    }

    public void setIdParte(UUID idParte) {
        this.idParte = idParte;
    }

    public UUID getIdNave() {
        return idNave;
    }

    public void setIdNave(UUID idNave) {
        this.idNave = idNave;
    }

    public LocalDate getFechaProduccion() {
        return fechaProduccion;
    }

    public void setFechaProduccion(LocalDate fechaProduccion) {
        this.fechaProduccion = fechaProduccion;
    }

    public Integer getGallinasVivas() {
        return gallinasVivas;
    }

    public void setGallinasVivas(Integer gallinasVivas) {
        this.gallinasVivas = gallinasVivas;
    }

    public Integer getGallinasMuertas() {
        return gallinasMuertas;
    }

    public void setGallinasMuertas(Integer gallinasMuertas) {
        this.gallinasMuertas = gallinasMuertas;
    }

    public Integer getGallinasEnfermas() {
        return gallinasEnfermas;
    }

    public void setGallinasEnfermas(Integer gallinasEnfermas) {
        this.gallinasEnfermas = gallinasEnfermas;
    }

    public Integer getHuevosProducidos() {
        return huevosProducidos;
    }

    public void setHuevosProducidos(Integer huevosProducidos) {
        this.huevosProducidos = huevosProducidos;
    }

    public Integer getHuevosRotos() {
        return huevosRotos;
    }

    public void setHuevosRotos(Integer huevosRotos) {
        this.huevosRotos = huevosRotos;
    }

    /** Derivado (no persistido): huevos buenos = producidos - rotos. */
    public Integer getHuevosBuenos() {
        if (huevosProducidos == null || huevosRotos == null) {
            return null;
        }
        return huevosProducidos - huevosRotos;
    }

    public BigDecimal getPiensoKg() {
        return piensoKg;
    }

    public void setPiensoKg(BigDecimal piensoKg) {
        this.piensoKg = piensoKg;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public boolean isAdvertenciaRevisada() {
        return advertenciaRevisada;
    }

    public void setAdvertenciaRevisada(boolean advertenciaRevisada) {
        this.advertenciaRevisada = advertenciaRevisada;
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
        if (!(o instanceof ParteNave)) return false;
        ParteNave parteNave = (ParteNave) o;
        return Objects.equals(idParte, parteNave.idParte);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idParte);
    }
}
