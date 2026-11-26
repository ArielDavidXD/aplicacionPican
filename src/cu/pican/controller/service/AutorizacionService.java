package cu.pican.controller.service;

import cu.pican.model.Rol;
import cu.pican.model.Usuario;

import java.util.Objects;
import java.util.UUID;

/**
 * Autoridad central de autorización de PICAN.
 *
 * Toda decisión de acceso se resuelve aquí a partir de la sesión existente y del
 * catálogo {@link CasoUso}. Los servicios no consultan roles directamente y la
 * interfaz reutiliza la misma matriz a través de {@code PermisosInterfaz}.
 *
 * La instancia activa se instala desde {@code AppContext}. No hay política permisiva
 * por defecto: si no se instaló, las operaciones protegidas se rechazan con un
 * mensaje controlado. Los arneses históricos instalan explícitamente una sesión
 * de demostración mediante {@code AutorizacionDemo}.
 */
public class AutorizacionService {

    private static volatile AutorizacionService activa;

    private final SesionAutorizacionProvider sesion;

    public AutorizacionService(SesionAutorizacionProvider sesion) {
        this.sesion = Objects.requireNonNull(sesion, "La sesión de autorización es obligatoria.");
    }

    /** Instala la política usada por los servicios. Solo AppContext la instala en producción. */
    public static void instalar(AutorizacionService servicio) {
        activa = Objects.requireNonNull(servicio, "La autorización a instalar es obligatoria.");
    }

    /** Política activa. Fallo cerrado si todavía no se instaló. */
    public static AutorizacionService activa() {
        AutorizacionService servicio = activa;
        if (servicio == null) {
            throw new AutorizacionException("No hay una política de autorización instalada en la aplicación.");
        }
        return servicio;
    }

    public Usuario usuarioAutenticado() {
        return sesion.getUsuarioAutenticado();
    }

    public boolean estaAutenticado() {
        Usuario usuario = usuarioAutenticado();
        return usuario != null && usuario.isActivo() && usuario.getRol() != null;
    }

    /**
     * Devuelve {@code true} cuando la sesión pertenece a alguno de los roles
     * responsables de cualquiera de los casos de uso indicados.
     */
    public boolean puedeAcceder(CasoUso... casos) {
        Usuario usuario = usuarioAutenticado();
        if (!estaAutenticado() || casos == null || casos.length == 0) {
            return false;
        }
        String nombreRol = usuario.getRol().getNombre();
        for (CasoUso caso : casos) {
            if (caso != null && caso.isHabilitado() && caso.getRolesPermitidos().contains(nombreRol)) {
                return true;
            }
        }
        return false;
    }

    /** Comprueba la autorización antes de ejecutar una operación protegida. */
    public void requerir(CasoUso... casos) {
        if (!puedeAcceder(casos)) {
            throw new AutorizacionException(mensajeRechazo(casos));
        }
    }

    /** Autoriza consultas al actor del caso de uso o a sus lectores autorizados. */
    public boolean puedeLeer(CasoUso... casos) {
        Usuario usuario = usuarioAutenticado();
        if (!estaAutenticado() || casos == null || casos.length == 0) {
            return false;
        }
        String nombreRol = usuario.getRol().getNombre();
        for (CasoUso caso : casos) {
            if (caso != null && caso.isHabilitado()
                    && (caso.getRolesPermitidos().contains(nombreRol)
                        || caso.getRolesLectura().contains(nombreRol))) {
                return true;
            }
        }
        return false;
    }

    /** Comprueba autorización de lectura sin conceder permiso de escritura. */
    public void requerirLectura(CasoUso... casos) {
        if (!puedeLeer(casos)) {
            throw new AutorizacionException(mensajeRechazoLectura(casos));
        }
    }

    /**
     * Autoriza una operación técnica de lectura de catálogos compartida por varios CU.
     * No crea un permiso empresarial nuevo: solo permite leer catálogos a cualquier
     * cuenta activa con rol previsto. Las escrituras siguen exigiendo CU-17.
     */
    public void requerirLecturaCatalogo() {
        if (!estaAutenticado()) {
            throw new AutorizacionException("Debe iniciar sesión para consultar catálogos.");
        }
    }

    /**
     * Permite el cambio de contraseña propia de cualquier cuenta activa.
     * El administrador además puede cambiar contraseñas por CU-16.
     */
    public void requerirPropietarioOUso(UUID idUsuario, CasoUso... casos) {
        if (!estaAutenticado()) {
            throw new AutorizacionException("Debe iniciar sesión para realizar esta operación.");
        }
        Usuario usuario = usuarioAutenticado();
        if (idUsuario != null && idUsuario.equals(usuario.getIdUsuario())) {
            return;
        }
        if (!puedeAcceder(casos)) {
            throw new AutorizacionException(mensajeRechazo(casos));
        }
    }

    /**
     * Arranque controlado de la cuenta administrativa inicial. Solo se admite sin
     * sesión y cuando todavía no existe administrador; el servicio comprueba la
     * segunda condición.
     */
    public void requerirBootstrapAdministrador() {
        if (estaAutenticado() && !puedeAcceder(CasoUso.CU_16)) {
            throw new AutorizacionException(mensajeRechazo(CasoUso.CU_16));
        }
    }

    private String mensajeRechazoLectura(CasoUso... casos) {
        if (casos == null || casos.length == 0) {
            return "Operación de consulta no autorizada.";
        }
        StringBuilder nombres = new StringBuilder();
        for (int i = 0; i < casos.length; i++) {
            if (i > 0) {
                nombres.append(", ");
            }
            nombres.append(casos[i] == null ? "?" : casos[i].getCodigo());
        }
        Usuario usuario = usuarioAutenticado();
        String rol = usuario == null || usuario.getRol() == null
                ? "sin sesión" : "rol " + usuario.getRol().getNombre();
        return "El " + rol + " no está autorizado para consultar " + nombres + ".";
    }

    private String mensajeRechazo(CasoUso... casos) {
        if (casos == null || casos.length == 0) {
            return "Operación no autorizada.";
        }
        StringBuilder nombres = new StringBuilder();
        for (int i = 0; i < casos.length; i++) {
            if (i > 0) {
                nombres.append(", ");
            }
            nombres.append(casos[i] == null ? "?" : casos[i].getCodigo());
        }
        Usuario usuario = usuarioAutenticado();
        String rol = usuario == null || usuario.getRol() == null
                ? "sin sesión" : "rol " + usuario.getRol().getNombre();
        return "El " + rol + " no está autorizado para " + nombres + ".";
    }
}
