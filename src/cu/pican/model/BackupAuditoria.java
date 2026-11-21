package cu.pican.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** Registro inmutable de una operación CU-19. El historial nunca se elimina. */
public final class BackupAuditoria {
    private final UUID idAuditoria;
    private final UUID idUsuario;
    private final String nombreUsuario;
    private final String operacion;
    private final String archivo;
    private final LocalDateTime fechaHora;
    private final String resultado;
    private final String motivoFallo;
    private final Long tamanoBytes;
    private final String sha256;
    private final String versionAplicacion;
    private final String versionEsquema;
    private final String idInstalacion;

    public BackupAuditoria(UUID idAuditoria, UUID idUsuario, String nombreUsuario, String operacion,
                           String archivo, LocalDateTime fechaHora, String resultado, String motivoFallo,
                           Long tamanoBytes, String sha256, String versionAplicacion,
                           String versionEsquema, String idInstalacion) {
        this.idAuditoria = idAuditoria;
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.operacion = operacion;
        this.archivo = archivo;
        this.fechaHora = fechaHora;
        this.resultado = resultado;
        this.motivoFallo = motivoFallo;
        this.tamanoBytes = tamanoBytes;
        this.sha256 = sha256;
        this.versionAplicacion = versionAplicacion;
        this.versionEsquema = versionEsquema;
        this.idInstalacion = idInstalacion;
    }

    public UUID getIdAuditoria() { return idAuditoria; }
    public UUID getIdUsuario() { return idUsuario; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getOperacion() { return operacion; }
    public String getArchivo() { return archivo; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public String getResultado() { return resultado; }
    public String getMotivoFallo() { return motivoFallo; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public String getSha256() { return sha256; }
    public String getVersionAplicacion() { return versionAplicacion; }
    public String getVersionEsquema() { return versionEsquema; }
    public String getIdInstalacion() { return idInstalacion; }
}
