package cu.pican.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** Estado local del bloqueo posterior a restauración exigido por SIN-13. */
public final class ControlRestauracion {

    private final boolean bloqueado;
    private final String motivo;
    private final LocalDateTime fechaBloqueo;
    private final UUID idAuditoria;
    private final String idInstalacion;

    public ControlRestauracion(boolean bloqueado, String motivo, LocalDateTime fechaBloqueo,
                               UUID idAuditoria, String idInstalacion) {
        this.bloqueado = bloqueado;
        this.motivo = motivo;
        this.fechaBloqueo = fechaBloqueo;
        this.idAuditoria = idAuditoria;
        this.idInstalacion = idInstalacion;
    }

    public static ControlRestauracion desbloqueado(String idInstalacion) {
        return new ControlRestauracion(false, "Sin restauración pendiente.", null, null, idInstalacion);
    }

    public boolean isBloqueado() { return bloqueado; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFechaBloqueo() { return fechaBloqueo; }
    public UUID getIdAuditoria() { return idAuditoria; }
    public String getIdInstalacion() { return idInstalacion; }
}
