package cu.pican.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Usuario del sistema (CU-16).
 * La contraseña nunca se almacena en texto plano: {@code passwordHash} contiene el resultado
 * codificado de PBKDF2 (algoritmo, iteraciones, salt y derivación).
 * Se mantiene el constructor original para no romper las demostraciones de etapas anteriores.
 * El vínculo con UEB sigue pendiente de confirmación documental (no se inventa aquí).
 */
public class Usuario {

    private UUID idUsuario;
    private String nombreUsuario;
    private String nombreCompleto;
    private String passwordHash;
    private Rol rol;
    private boolean activo;
    private LocalDateTime fechaCreacion;
    private boolean cambioContrasenaRequerido;

    public Usuario() {
    }

    /** Constructor histórico usado por demostraciones sin autenticación real. */
    public Usuario(UUID idUsuario, String nombreUsuario, String nombreCompleto, boolean activo) {
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.nombreCompleto = nombreCompleto;
        this.activo = activo;
    }

    public Usuario(UUID idUsuario, String nombreUsuario, String nombreCompleto, String passwordHash,
                   Rol rol, boolean activo, LocalDateTime fechaCreacion, boolean cambioContrasenaRequerido) {
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.nombreCompleto = nombreCompleto;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.activo = activo;
        this.fechaCreacion = fechaCreacion;
        this.cambioContrasenaRequerido = cambioContrasenaRequerido;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(UUID idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    /**
     * Devuelve la representación codificada del hash (nunca la contraseña en texto plano).
     * No se muestra en tablas ni mensajes de la interfaz.
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public boolean isCambioContrasenaRequerido() {
        return cambioContrasenaRequerido;
    }

    public void setCambioContrasenaRequerido(boolean cambioContrasenaRequerido) {
        this.cambioContrasenaRequerido = cambioContrasenaRequerido;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Usuario)) return false;
        Usuario usuario = (Usuario) o;
        return Objects.equals(idUsuario, usuario.idUsuario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idUsuario);
    }
}
