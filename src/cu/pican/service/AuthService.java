package cu.pican.service;

import cu.pican.model.Rol;
import cu.pican.model.User;
import cu.pican.model.Usuario;
import cu.pican.repository.UsuarioRepository;

import java.util.UUID;

/**
 * CU-16: autenticación y sesión única de la aplicación.
 * La sesión vive en esta instancia compartida desde AppContext: los paneles y servicios
 * consultan aquí al usuario autenticado en lugar de crear sesiones independientes.
 */
public class AuthService implements SesionAutorizacionProvider {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;
    private UsuarioService usuarioService;
    private volatile Usuario usuarioAutenticado;

    public AuthService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher) {
        this.usuarioRepository = usuarioRepository;
        this.passwordHasher = passwordHasher;
    }

    public void setUsuarioService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Inicia sesión verificando credenciales y estado activo. */
    public void login(String nombreUsuario, String contrasena) {
        ValidationUtils.requireNotBlank(nombreUsuario, "El nombre de usuario es obligatorio.");
        ValidationUtils.requireNotBlank(contrasena, "La contraseña es obligatoria.");

        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ValidacionException("Usuario o contraseña incorrectos."));
        if (!usuario.isActivo()) {
            throw new ValidacionException("El usuario está desactivado. Contacte con el administrador del sistema.");
        }
        if (!passwordHasher.verificar(contrasena, usuario.getPasswordHash())) {
            throw new ValidacionException("Usuario o contraseña incorrectos.");
        }
        this.usuarioAutenticado = usuario;
    }

    public void logout() {
        this.usuarioAutenticado = null;
    }

    public boolean estaAutenticado() {
        return usuarioAutenticado != null;
    }

    @Override
    public Usuario getUsuarioAutenticado() {
        return usuarioAutenticado;
    }

    /** Modelo provisional que consume el encabezado de la interfaz. */
    public User getUsuarioSesion() {
        Usuario usuario = usuarioAutenticado;
        if (usuario == null) {
            return null;
        }
        Rol rol = usuario.getRol();
        return new User(usuario.getNombreUsuario(), usuario.getNombreCompleto(),
                rol == null ? "Sin rol" : rol.getNombre());
    }

    @Override
    public UUID getIdUsuarioAutenticado() {
        return usuarioAutenticado == null ? null : usuarioAutenticado.getIdUsuario();
    }

    public boolean requiereCambioContrasena() {
        return usuarioAutenticado != null && usuarioAutenticado.isCambioContrasenaRequerido();
    }

    /** Cambia la contraseña de la sesión actual y refresca la referencia almacenada. */
    public void cambiarContrasenaSesion(String contrasenaActual, String contrasenaNueva) {
        if (usuarioAutenticado == null) {
            throw new ValidacionException("No hay una sesión activa.");
        }
        if (usuarioService == null) {
            throw new ValidacionException("La gestión de contraseñas no está disponible.");
        }
        this.usuarioAutenticado = usuarioService.cambiarContrasena(
                usuarioAutenticado.getIdUsuario(), contrasenaActual, contrasenaNueva);
    }
}
