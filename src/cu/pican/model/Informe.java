package cu.pican.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Informe estadístico del CU-15. El estado y la versión del registro se gestionan en InformeService.
 * numeroRevision identifica la revisión del contenido dentro de una cadena de correcciones;
 * el registro anterior se conserva íntegramente.
 */
public class Informe {

    public static final String ESTADO_BORRADOR = "BORRADOR";
    public static final String ESTADO_EN_REVISION = "EN_REVISION";
    public static final String ESTADO_DEVUELTO = "DEVUELTO";
    public static final String ESTADO_APROBADO = "APROBADO";
    public static final String ESTADO_ENTREGADO = "ENTREGADO";

    private UUID idInforme;
    private UUID idInformeRaiz;
    private UUID idInformeAnterior;
    private String numeroInforme;
    private String titulo;
    private LocalDate periodoDesde;
    private LocalDate periodoHasta;
    private LocalDate fechaElaboracion;
    private String especialistaResponsable;
    /** Usuario autenticado que elabora el informe (opcional; no sustituye el nombre del especialista). */
    private UUID idUsuario;
    private String contenidoResumen;
    /** Secciones 2 a 7 del informe (CU-15): la 1 es contenidoResumen y la 8 se compone al exportar. */
    private String seccionProduccionMetas;
    private String seccionEntregas;
    private String seccionInfraestructura;
    private String seccionCalidadDatos;
    private String seccionAlertas;
    private String seccionCausasMedidas;
    private String estado;
    private LocalDateTime fechaCreacion;
    private Long version;
    private Integer numeroRevision;

    public UUID getIdInforme() { return idInforme; }
    public void setIdInforme(UUID idInforme) { this.idInforme = idInforme; }

    public UUID getIdInformeRaiz() { return idInformeRaiz; }
    public void setIdInformeRaiz(UUID idInformeRaiz) { this.idInformeRaiz = idInformeRaiz; }

    public UUID getIdInformeAnterior() { return idInformeAnterior; }
    public void setIdInformeAnterior(UUID idInformeAnterior) { this.idInformeAnterior = idInformeAnterior; }

    public String getNumeroInforme() { return numeroInforme; }
    public void setNumeroInforme(String numeroInforme) { this.numeroInforme = numeroInforme; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public void setPeriodoDesde(LocalDate periodoDesde) { this.periodoDesde = periodoDesde; }

    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public void setPeriodoHasta(LocalDate periodoHasta) { this.periodoHasta = periodoHasta; }

    public LocalDate getFechaElaboracion() { return fechaElaboracion; }
    public void setFechaElaboracion(LocalDate fechaElaboracion) { this.fechaElaboracion = fechaElaboracion; }

    public String getEspecialistaResponsable() { return especialistaResponsable; }
    public void setEspecialistaResponsable(String especialistaResponsable) { this.especialistaResponsable = especialistaResponsable; }

    public UUID getIdUsuario() { return idUsuario; }
    public void setIdUsuario(UUID idUsuario) { this.idUsuario = idUsuario; }

    public String getContenidoResumen() { return contenidoResumen; }
    public void setContenidoResumen(String contenidoResumen) { this.contenidoResumen = contenidoResumen; }

    public String getSeccionProduccionMetas() { return seccionProduccionMetas; }
    public void setSeccionProduccionMetas(String seccionProduccionMetas) { this.seccionProduccionMetas = seccionProduccionMetas; }

    public String getSeccionEntregas() { return seccionEntregas; }
    public void setSeccionEntregas(String seccionEntregas) { this.seccionEntregas = seccionEntregas; }

    public String getSeccionInfraestructura() { return seccionInfraestructura; }
    public void setSeccionInfraestructura(String seccionInfraestructura) { this.seccionInfraestructura = seccionInfraestructura; }

    public String getSeccionCalidadDatos() { return seccionCalidadDatos; }
    public void setSeccionCalidadDatos(String seccionCalidadDatos) { this.seccionCalidadDatos = seccionCalidadDatos; }

    public String getSeccionAlertas() { return seccionAlertas; }
    public void setSeccionAlertas(String seccionAlertas) { this.seccionAlertas = seccionAlertas; }

    public String getSeccionCausasMedidas() { return seccionCausasMedidas; }
    public void setSeccionCausasMedidas(String seccionCausasMedidas) { this.seccionCausasMedidas = seccionCausasMedidas; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Integer getNumeroRevision() { return numeroRevision; }
    public void setNumeroRevision(Integer numeroRevision) { this.numeroRevision = numeroRevision; }

    public String getPeriodoAnalizado() {
        if (periodoDesde == null && periodoHasta == null) {
            return "-";
        }
        if (periodoDesde == null) {
            return "hasta " + periodoHasta;
        }
        if (periodoHasta == null) {
            return "desde " + periodoDesde;
        }
        return periodoDesde + " a " + periodoHasta;
    }
}