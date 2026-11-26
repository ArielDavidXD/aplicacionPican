package cu.pican.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Consolidado de una UEB con los totales y la cobertura del período al consolidarlo. */
public class ConsolidadoUEB {

    public static final String ESTADO_COMPLETO = "COMPLETO";
    public static final String ESTADO_PARCIAL = "PARCIAL";

    private UUID idConsolidado;
    private UUID idUEB;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
    private String motivoParcial;
    private UUID idUsuario;
    private LocalDateTime creadoEn;
    private Long version;

    private BigDecimal totalHuevos = BigDecimal.ZERO;
    private BigDecimal totalHuevosRotos = BigDecimal.ZERO;
    private long totalGallinasVivas;
    private long totalGallinasMuertas;
    private long totalGallinasEnfermas;
    private BigDecimal totalPiensoKg = BigDecimal.ZERO;
    private BigDecimal totalCarne = BigDecimal.ZERO;
    private int partesNaveIncluidos;
    private int partesNaveExcluidos;
    private int partesUnidadIncluidos;
    private int partesUnidadExcluidos;
    private int registrosCarneIncluidos;
    private int navesSinParte;
    private int unidadesSinParte;
    private int aclaracionesPendientes;

    public ConsolidadoUEB() {
    }

    public UUID getIdConsolidado() { return idConsolidado; }
    public void setIdConsolidado(UUID idConsolidado) { this.idConsolidado = idConsolidado; }
    public UUID getIdUEB() { return idUEB; }
    public void setIdUEB(UUID idUEB) { this.idUEB = idUEB; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getMotivoParcial() { return motivoParcial; }
    public void setMotivoParcial(String motivoParcial) { this.motivoParcial = motivoParcial; }
    public UUID getIdUsuario() { return idUsuario; }
    public void setIdUsuario(UUID idUsuario) { this.idUsuario = idUsuario; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public BigDecimal getTotalHuevos() { return totalHuevos; }
    public void setTotalHuevos(BigDecimal totalHuevos) { this.totalHuevos = totalHuevos; }
    public BigDecimal getTotalHuevosRotos() { return totalHuevosRotos; }
    public void setTotalHuevosRotos(BigDecimal totalHuevosRotos) { this.totalHuevosRotos = totalHuevosRotos; }
    public long getTotalGallinasVivas() { return totalGallinasVivas; }
    public void setTotalGallinasVivas(long totalGallinasVivas) { this.totalGallinasVivas = totalGallinasVivas; }
    public long getTotalGallinasMuertas() { return totalGallinasMuertas; }
    public void setTotalGallinasMuertas(long totalGallinasMuertas) { this.totalGallinasMuertas = totalGallinasMuertas; }
    public long getTotalGallinasEnfermas() { return totalGallinasEnfermas; }
    public void setTotalGallinasEnfermas(long totalGallinasEnfermas) { this.totalGallinasEnfermas = totalGallinasEnfermas; }
    public BigDecimal getTotalPiensoKg() { return totalPiensoKg; }
    public void setTotalPiensoKg(BigDecimal totalPiensoKg) { this.totalPiensoKg = totalPiensoKg; }
    public BigDecimal getTotalCarne() { return totalCarne; }
    public void setTotalCarne(BigDecimal totalCarne) { this.totalCarne = totalCarne; }
    public int getPartesNaveIncluidos() { return partesNaveIncluidos; }
    public void setPartesNaveIncluidos(int partesNaveIncluidos) { this.partesNaveIncluidos = partesNaveIncluidos; }
    public int getPartesNaveExcluidos() { return partesNaveExcluidos; }
    public void setPartesNaveExcluidos(int partesNaveExcluidos) { this.partesNaveExcluidos = partesNaveExcluidos; }
    public int getPartesUnidadIncluidos() { return partesUnidadIncluidos; }
    public void setPartesUnidadIncluidos(int partesUnidadIncluidos) { this.partesUnidadIncluidos = partesUnidadIncluidos; }
    public int getPartesUnidadExcluidos() { return partesUnidadExcluidos; }
    public void setPartesUnidadExcluidos(int partesUnidadExcluidos) { this.partesUnidadExcluidos = partesUnidadExcluidos; }
    public int getRegistrosCarneIncluidos() { return registrosCarneIncluidos; }
    public void setRegistrosCarneIncluidos(int registrosCarneIncluidos) { this.registrosCarneIncluidos = registrosCarneIncluidos; }
    public int getNavesSinParte() { return navesSinParte; }
    public void setNavesSinParte(int navesSinParte) { this.navesSinParte = navesSinParte; }
    public int getUnidadesSinParte() { return unidadesSinParte; }
    public void setUnidadesSinParte(int unidadesSinParte) { this.unidadesSinParte = unidadesSinParte; }
    public int getAclaracionesPendientes() { return aclaracionesPendientes; }
    public void setAclaracionesPendientes(int aclaracionesPendientes) { this.aclaracionesPendientes = aclaracionesPendientes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsolidadoUEB that)) return false;
        return Objects.equals(idConsolidado, that.idConsolidado);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idConsolidado);
    }
}
