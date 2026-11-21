package cu.pican.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Decisión registrada por el especialista sobre un informe; nunca crea una cuenta para el directivo. */
public final class DecisionDirectivo {

    public static final String APROBADO = "APROBADO";
    public static final String DEVUELTO = "DEVUELTO";

    private final UUID idDecision;
    private final UUID idInforme;
    private final String nombreDirectivo;
    private final String decision;
    private final LocalDate fechaDecision;
    private final String observacion;
    private final LocalDateTime fechaRegistro;
    private final Integer numeroDecision;

    public DecisionDirectivo(UUID idDecision, UUID idInforme, String nombreDirectivo, String decision,
                             LocalDate fechaDecision, String observacion, LocalDateTime fechaRegistro,
                             Integer numeroDecision) {
        this.idDecision = idDecision;
        this.idInforme = idInforme;
        this.nombreDirectivo = nombreDirectivo;
        this.decision = decision;
        this.fechaDecision = fechaDecision;
        this.observacion = observacion;
        this.fechaRegistro = fechaRegistro;
        this.numeroDecision = numeroDecision;
    }

    public UUID getIdDecision() { return idDecision; }
    public UUID getIdInforme() { return idInforme; }
    public String getNombreDirectivo() { return nombreDirectivo; }
    public String getDecision() { return decision; }
    public LocalDate getFechaDecision() { return fechaDecision; }
    public String getObservacion() { return observacion; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public Integer getNumeroDecision() { return numeroDecision; }
}