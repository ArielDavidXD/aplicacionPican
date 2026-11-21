package cu.pican.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Entrega de un informe aprobado a una institución contemplada (GEGAN o MINAG). */
public final class EntregaInforme {

    private final UUID idEntrega;
    private final UUID idInforme;
    private final DestinatarioInstitucional destinatario;
    private final LocalDate fechaEntrega;
    private final String responsableRegistro;
    private final String observaciones;
    private final LocalDateTime fechaRegistro;

    public EntregaInforme(UUID idEntrega, UUID idInforme, DestinatarioInstitucional destinatario,
                          LocalDate fechaEntrega, String responsableRegistro, String observaciones,
                          LocalDateTime fechaRegistro) {
        this.idEntrega = idEntrega;
        this.idInforme = idInforme;
        this.destinatario = destinatario;
        this.fechaEntrega = fechaEntrega;
        this.responsableRegistro = responsableRegistro;
        this.observaciones = observaciones;
        this.fechaRegistro = fechaRegistro;
    }

    public UUID getIdEntrega() { return idEntrega; }
    public UUID getIdInforme() { return idInforme; }
    public DestinatarioInstitucional getDestinatario() { return destinatario; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public String getResponsableRegistro() { return responsableRegistro; }
    public String getObservaciones() { return observaciones; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}