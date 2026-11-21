package cu.pican.app;

import cu.pican.model.Usuario;
import cu.pican.service.UsuarioService;

/**
 * Inicialización controlada de la primera cuenta administrativa (CU-16).
 *
 * Primer acceso:
 * 1) Antes de ejecutar la aplicación se puede definir la contraseña inicial con la variable de entorno
 *    {@code PICAN_ADMIN_PASSWORD} o la propiedad de sistema {@code -Dpican.admin.password=...}.
 * 2) En PostgreSQL, si no se configura nada, el arranque falla con un mensaje claro: no se genera
 *    ni se imprime una credencial. En MEMORY (solo demostración) se mantiene la generación temporal
 *    de una sola vez. No hay contraseña predeterminada escrita en el código.
 * 3) El usuario es {@code admin}. La cuenta se crea con "cambio de contraseña obligatorio":
 *    el inicio de sesión exige reemplazar la contraseña temporal antes de entrar a los módulos.
 * 4) Repetir la inicialización no crea duplicados: solo actúa si no existe ningún administrador.
 */
public final class UsuarioInicialInitializer {

    public static final String USUARIO_INICIAL = "admin";
    private static final String NOMBRE_COMPLETO = "Administrador del sistema";

    private UsuarioInicialInitializer() {}

    public static void inicializar(UsuarioService usuarios) {
        inicializar(usuarios, true);
    }

    /**
     * @param permitirGeneracionSinConfiguracion solo para demostraciones en memoria.
     *        En PostgreSQL la cuenta inicial exige contraseña configurada por
     *        {@code -Dpican.admin.password} o {@code PICAN_ADMIN_PASSWORD}; nunca se
     *        genera una credencial que tendría que imprimirse en texto plano.
     */
    public static void inicializar(UsuarioService usuarios, boolean permitirGeneracionSinConfiguracion) {
        if (usuarios.existeAdministradorInicial()) {
            return;
        }
        String configurada = leerConfiguracion();
        boolean generada = configurada == null;
        if (generada && !permitirGeneracionSinConfiguracion) {
            throw new IllegalStateException("No existe la cuenta administrativa inicial. "
                    + "Defina PICAN_ADMIN_PASSWORD o -Dpican.admin.password antes de iniciar; "
                    + "no se genera ni se muestra una contraseña en texto plano.");
        }
        String contrasena = generada ? usuarios.generarContrasenaTemporal() : configurada;
        Usuario creado = usuarios.inicializarAdministrador(
                USUARIO_INICIAL, NOMBRE_COMPLETO, contrasena, true);
        if (generada && creado != null) {
            System.out.println("[PICAN] Cuenta administrativa inicial creada. Usuario: " + creado.getNombreUsuario());
            System.out.println("[PICAN] Contraseña temporal (se muestra solo en esta creación): " + contrasena);
            System.out.println("[PICAN] Debe cambiarla en el primer acceso. No se almacena en texto plano.");
        }
    }

    private static String leerConfiguracion() {
        String valor = System.getProperty("pican.admin.password");
        if (esVacio(valor)) {
            valor = System.getenv("PICAN_ADMIN_PASSWORD");
        }
        return esVacio(valor) ? null : valor;
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
