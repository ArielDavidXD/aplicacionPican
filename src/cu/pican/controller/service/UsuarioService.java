package cu.pican.controller.service;

import cu.pican.model.Rol;
import cu.pican.model.Usuario;
import cu.pican.model.persistence.NoOpTransactionManager;
import cu.pican.model.persistence.TransactionManager;
import cu.pican.model.repository.UsuarioRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CU-16: gestión de cuentas de los trabajadores de PICAN.
 * No crea cuentas para el directivo externo (el especialista registra su nombre y decisión en CU-15).
 * No define una estructura compleja de permisos: los roles previstos llevan una descripción de permisos generales.
 */
public class UsuarioService extends ServicioAutorizado {

    private static final int LONGITUD_MINIMA_CONTRASENA = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;
    private final TransactionManager transacciones;
    private AuthService authService;

    /** Constructor histórico: mantiene el comportamiento en memoria de demostraciones y pruebas. */
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher) {
        this(usuarioRepository, passwordHasher, new NoOpTransactionManager());
    }

    /**
     * En PostgreSQL el repositorio escribe la fila de {@code rol} (ON CONFLICT DO NOTHING)
     * y la del usuario. Ambas escrituras deben compartir transacción.
     */
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher,
                          TransactionManager transacciones) {
        this.usuarioRepository = usuarioRepository;
        this.passwordHasher = passwordHasher;
        this.transacciones = transacciones == null ? new NoOpTransactionManager() : transacciones;
    }

    /** Permite comprobar la regla de "no desactivarse dejando el sistema sin administrador útil". */
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    // -------------------- Consultas --------------------

    public List<Usuario> listarTodos() {
        autorizacion().requerir(CasoUso.CU_16);
        return listarTodosInterno();
    }

    private List<Usuario> listarTodosInterno() {
        return usuarioRepository.findAll();
    }

    public List<Usuario> listarPorEstado(boolean activo) {
        autorizacion().requerir(CasoUso.CU_16);
        return usuarioRepository.findByActivo(activo);
    }

    public Optional<Usuario> buscarPorId(UUID idUsuario) {
        autorizacion().requerir(CasoUso.CU_16);
        if (idUsuario == null) {
            return Optional.empty();
        }
        return usuarioRepository.findById(idUsuario);
    }

    public Optional<Usuario> buscarPorNombreUsuario(String nombreUsuario) {
        autorizacion().requerir(CasoUso.CU_16);
        return usuarioRepository.findByNombreUsuario(nombreUsuario);
    }

    public List<Rol> rolesDisponibles() {
        autorizacion().requerir(CasoUso.CU_16);
        return Arrays.asList(
                Rol.predefinido(Rol.ADMINISTRADOR),
                Rol.predefinido(Rol.JEFE_UEB),
                Rol.predefinido(Rol.ESPECIALISTA_PRODUCCION),
                Rol.predefinido(Rol.ESPECIALISTA_ESTADISTICA));
    }

    public boolean existeAdministrador() {
        autorizacion().requerir(CasoUso.CU_16);
        return existeAdministradorInterno();
    }

    private boolean existeAdministradorInterno() {
        return listarTodosInterno().stream().anyMatch(this::esAdministrador);
    }

    /**
     * Consulta de arranque usada por {@code UsuarioInicialInitializer} antes de que exista sesión.
     * No expone datos de cuentas, solo si ya se creó el administrador inicial.
     */
    public boolean existeAdministradorInicial() {
        return existeAdministradorInterno();
    }

    // -------------------- Altas y modificación --------------------

    /**
     * Registra una cuenta. La contraseña se deriva con PBKDF2 y se exige cambio en el primer acceso,
     * porque ha sido entregada por el administrador y no elegida libremente por el usuario.
     */
    public Usuario registrar(String nombreUsuario, String nombreCompleto, String contrasena, String nombreRol) {
        autorizacion().requerir(CasoUso.CU_16);
        return transacciones.ejecutar(() -> {
            validarDatosCuenta(nombreUsuario, nombreCompleto, contrasena, nombreRol);
            if (usuarioRepository.existsByNombreUsuario(nombreUsuario)) {
                throw new ValidacionException("Ya existe un usuario con ese nombre de usuario.");
            }
            Usuario usuario = new Usuario(UUID.randomUUID(), nombreUsuario.trim(), nombreCompleto.trim(),
                    passwordHasher.hash(contrasena), resolverRol(nombreRol), true, LocalDateTime.now(), true);
            return usuarioRepository.save(usuario);
        });
    }

    /**
     * Crea la cuenta administrativa inicial solo cuando no existe ninguna cuenta con rol administrador.
     * Es idempotente: repetir la inicialización nunca duplica la cuenta.
     */
    public Usuario inicializarAdministrador(String nombreUsuario, String nombreCompleto,
                                            String contrasenaInicial, boolean exigirCambio) {
        autorizacion().requerirBootstrapAdministrador();
        return transacciones.ejecutar(() -> {
            if (existeAdministradorInterno()) {
                return listarTodosInterno().stream().filter(this::esAdministrador).findFirst().orElse(null);
            }
            Optional<Usuario> porNombre = usuarioRepository.findByNombreUsuario(nombreUsuario);
            if (porNombre.isPresent()) {
                throw new ValidacionException("El nombre de usuario reservado para la administración ya está asignado; no se crea una cuenta duplicada.");
            }
            validarDatosCuenta(nombreUsuario, nombreCompleto, contrasenaInicial, Rol.ADMINISTRADOR);
            Usuario admin = new Usuario(UUID.randomUUID(), nombreUsuario.trim(), nombreCompleto.trim(),
                    passwordHasher.hash(contrasenaInicial), resolverRol(Rol.ADMINISTRADOR), true,
                    LocalDateTime.now(), exigirCambio);
            return usuarioRepository.save(admin);
        });
    }

    /** Modifica los datos editable de la cuenta. El nombre de usuario es la clave de acceso y no se cambia aquí. */
    public Usuario actualizarDatos(UUID idUsuario, String nombreCompleto, String nombreRol) {
        autorizacion().requerir(CasoUso.CU_16);
        return transacciones.ejecutar(() -> {
            Usuario actual = obtener(idUsuario);
            ValidationUtils.requireNotBlank(nombreCompleto, "El nombre completo es obligatorio.");
            Rol rol = resolverRol(nombreRol);
            validarNoUltimoAdministrador(actual, rol);
            Usuario copia = copiar(actual);
            copia.setNombreCompleto(nombreCompleto.trim());
            copia.setRol(rol);
            return usuarioRepository.update(copia);
        });
    }

    public Usuario asignarRol(UUID idUsuario, String nombreRol) {
        autorizacion().requerir(CasoUso.CU_16);
        return transacciones.ejecutar(() -> {
            Usuario actual = obtener(idUsuario);
            Rol rol = resolverRol(nombreRol);
            validarNoUltimoAdministrador(actual, rol);
            Usuario copia = copiar(actual);
            copia.setRol(rol);
            return usuarioRepository.update(copia);
        });
    }

    public Usuario activar(UUID idUsuario) {
        autorizacion().requerir(CasoUso.CU_16);
        return transacciones.ejecutar(() -> {
            Usuario actual = obtener(idUsuario);
            if (actual.isActivo()) {
                return actual;
            }
            Usuario copia = copiar(actual);
            copia.setActivo(true);
            return usuarioRepository.update(copia);
        });
    }

    /**
     * Desactiva la cuenta (baja lógica). Nunca se permite dejar el sistema sin una cuenta
     * administrativa activa, en particular al desactivar la propia sesión.
     */
    public Usuario desactivar(UUID idUsuario) {
        autorizacion().requerir(CasoUso.CU_16);
        return transacciones.ejecutar(() -> {
            Usuario actual = obtener(idUsuario);
            if (!actual.isActivo()) {
                return actual;
            }
            boolean esSesionActual = authService != null
                    && idUsuario.equals(authService.getIdUsuarioAutenticado());
            boolean quedaAdministrador = listarTodos().stream()
                    .anyMatch(u -> !idUsuario.equals(u.getIdUsuario()) && u.isActivo() && esAdministrador(u));
            if (!quedaAdministrador && (esAdministrador(actual) || esSesionActual)) {
                throw new ValidacionException("No se puede desactivar esta cuenta porque dejaría el sistema sin una cuenta administrativa utilizable.");
            }
            Usuario copia = copiar(actual);
            copia.setActivo(false);
            return usuarioRepository.update(copia);
        });
    }

    /** La baja física queda bloqueada cuando la cuenta ya aparece en operaciones registradas. */
    public void eliminar(UUID idUsuario) {
        autorizacion().requerir(CasoUso.CU_16);
        transacciones.ejecutar((Runnable) () -> {
            Usuario actual = obtener(idUsuario);
            if (usuarioRepository.estaAsociadoAOperaciones(idUsuario)) {
                throw new ValidacionException("El usuario tiene operaciones asociadas; solo se permite desactivar la cuenta.");
            }
            boolean quedaAdministrador = listarTodos().stream()
                    .anyMatch(u -> !idUsuario.equals(u.getIdUsuario()) && u.isActivo() && esAdministrador(u));
            if (esAdministrador(actual) && !quedaAdministrador) {
                throw new ValidacionException("No se puede eliminar la última cuenta administrativa activa.");
            }
            try {
                usuarioRepository.deleteById(idUsuario);
            } catch (IllegalStateException ex) {
                throw new ValidacionException(ex.getMessage());
            }
        });
    }

    /** Cambia la contraseña verificando la actual; limpia el indicador de cambio obligatorio. */
    public Usuario cambiarContrasena(UUID idUsuario, String contrasenaActual, String contrasenaNueva) {
        autorizacion().requerirPropietarioOUso(idUsuario, CasoUso.CU_16);
        return transacciones.ejecutar(() -> {
            Usuario actual = obtener(idUsuario);
            ValidationUtils.requireNotBlank(contrasenaActual, "La contraseña actual es obligatoria.");
            validarContrasenaNueva(contrasenaNueva);
            if (!passwordHasher.verificar(contrasenaActual, actual.getPasswordHash())) {
                throw new ValidacionException("La contraseña actual no es correcta.");
            }
            Usuario copia = copiar(actual);
            copia.setPasswordHash(passwordHasher.hash(contrasenaNueva));
            copia.setCambioContrasenaRequerido(false);
            return usuarioRepository.update(copia);
        });
    }

    /**
     * Genera una contraseña temporal sin consultar ni modificar datos. Se usa también durante el
     * arranque, antes de que exista sesión, por lo que no exige CU-16.
     */
    public String generarContrasenaTemporal() {
        return passwordHasher.generarContrasenaTemporal();
    }

    // -------------------- Auxiliares --------------------

    private Usuario obtener(UUID idUsuario) {
        ValidationUtils.requireUuid(idUsuario, "El usuario es obligatorio.");
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ValidacionException("El usuario indicado no existe."));
    }

    private void validarDatosCuenta(String nombreUsuario, String nombreCompleto, String contrasena, String nombreRol) {
        ValidationUtils.requireNotBlank(nombreUsuario, "El nombre de usuario es obligatorio.");
        ValidationUtils.requireNotBlank(nombreCompleto, "El nombre completo es obligatorio.");
        validarContrasenaNueva(contrasena);
        resolverRol(nombreRol);
    }

    private void validarContrasenaNueva(String contrasena) {
        ValidationUtils.requireNotBlank(contrasena, "La contraseña es obligatoria.");
        if (contrasena.length() < LONGITUD_MINIMA_CONTRASENA) {
            throw new ValidacionException("La contraseña debe tener al menos " + LONGITUD_MINIMA_CONTRASENA + " caracteres.");
        }
    }

    private Rol resolverRol(String nombreRol) {
        ValidationUtils.requireNotBlank(nombreRol, "El rol es obligatorio.");
        Rol rol = Rol.predefinido(nombreRol);
        if (rol == null) {
            throw new ValidacionException("El rol indicado no es uno de los roles previstos para PICAN.");
        }
        return rol;
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() != null && Rol.ADMINISTRADOR.equals(usuario.getRol().getNombre());
    }

    /**
     * Un cambio de rol no puede dejar el sistema sin ninguna cuenta administrativa activa.
     * La misma guarda ya existía para la baja lógica y para la baja física.
     */
    private void validarNoUltimoAdministrador(Usuario actual, Rol nuevoRol) {
        if (!actual.isActivo() || !esAdministrador(actual)) {
            return;
        }
        if (nuevoRol != null && Rol.ADMINISTRADOR.equals(nuevoRol.getNombre())) {
            return;
        }
        boolean quedaAdministrador = listarTodos().stream()
                .anyMatch(u -> !actual.getIdUsuario().equals(u.getIdUsuario())
                        && u.isActivo() && esAdministrador(u));
        if (!quedaAdministrador) {
            throw new ValidacionException("No se puede cambiar el rol de esta cuenta porque dejaría el sistema sin una cuenta administrativa utilizable.");
        }
    }

    private Usuario copiar(Usuario o) {
        return new Usuario(o.getIdUsuario(), o.getNombreUsuario(), o.getNombreCompleto(),
                o.getPasswordHash(), o.getRol(), o.isActivo(), o.getFechaCreacion(),
                o.isCambioContrasenaRequerido());
    }
}
