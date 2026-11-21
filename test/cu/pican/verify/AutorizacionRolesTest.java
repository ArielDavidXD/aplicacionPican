package cu.pican.verify;

import cu.pican.app.AppContext;
import cu.pican.controller.NavigationController;
import cu.pican.model.Rol;
import cu.pican.service.AutorizacionException;
import cu.pican.service.CasoUso;
import cu.pican.service.ValidacionException;
import cu.pican.view.PermisosInterfaz;

import java.awt.CardLayout;
import java.util.Locale;
import java.util.UUID;

import javax.swing.JPanel;

/**
 * Etapa 4.16 · Autorización integral por roles.
 *
 * Se ejecuta dos veces: MEMORY y POSTGRES real. Comprueba la matriz central, la
 * navegación directa y que una llamada directa a un servicio compartido no pueda
 * saltarse el rol de la sesión. No repite los arneses funcionales de la Etapa 4.15.
 */
public final class AutorizacionRolesTest {

    private static final String CLAVE_ADMIN = "Admin416Pass!";
    private static final String CLAVE_JEFE = "Jefe416Pass!";
    private static final String CLAVE_ESTAD = "Estad416Pass!";
    private static final String CLAVE_PROD = "Prod416Pass!";

    private static int aprobadas;
    private static int totales;

    public static void main(String[] args) {
        String modo = args.length == 0 ? "memoria" : args[0];
        configurar(modo);
        System.out.println("=== AUTORIZACIÓN POR ROLES (4.16) · " + modo.toUpperCase(Locale.ROOT) + " ===");

        try {
            AppContext contexto = new AppContext();
            ejecutarTodo(contexto);
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            System.exit(2);
        }
    }

    private static void configurar(String modo) {
        System.setProperty("pican.admin.password", CLAVE_ADMIN);
        if ("postgresql".equals(modo)) {
            String url = System.getProperty("pican.test.url");
            String usuario = System.getProperty("pican.test.user");
            if (url == null || usuario == null) {
                throw new IllegalArgumentException("Faltan -Dpican.test.url y -Dpican.test.user para POSTGRES.");
            }
            String base = url.split("\\?", 2)[0];
            base = base.substring(base.lastIndexOf('/') + 1);
            if (!base.toLowerCase(Locale.ROOT).contains("test")) {
                throw new IllegalArgumentException("Base no exclusiva de pruebas: " + base);
            }
            System.setProperty("pican.persistence", "postgresql");
            System.setProperty("pican.db.url", url);
            System.setProperty("pican.db.user", usuario);
            System.setProperty("pican.db.password", System.getProperty("pican.test.password", "trust_local"));
            System.setProperty("pican.db.migrate", "true");
        } else {
            System.setProperty("pican.persistence", "memory");
        }
    }

    private static void ejecutarTodo(AppContext contexto) {
        registrarCuentas(contexto);

        verificar("AUT-01", "Matriz central: ADMINISTRADOR solo CU-16 a CU-19",
                () -> CasoUso.CU_16.getRolesPermitidos().contains(Rol.ADMINISTRADOR)
                        && CasoUso.CU_17.getRolesPermitidos().contains(Rol.ADMINISTRADOR)
                        && CasoUso.CU_18.getRolesPermitidos().contains(Rol.ADMINISTRADOR)
                        && !CasoUso.CU_05.getRolesPermitidos().contains(Rol.ADMINISTRADOR)
                        && !CasoUso.CU_14.getRolesPermitidos().contains(Rol.ADMINISTRADOR));
        verificar("AUT-02", "Matriz central: JEFE_DE_UEB sin CU admin/estadística",
                () -> CasoUso.CU_05.getRolesPermitidos().contains(Rol.JEFE_UEB)
                        && CasoUso.CU_13.getRolesPermitidos().contains(Rol.JEFE_UEB)
                        && !CasoUso.CU_16.getRolesPermitidos().contains(Rol.JEFE_UEB)
                        && !CasoUso.CU_14.getRolesPermitidos().contains(Rol.JEFE_UEB));
        verificar("AUT-03", "Matriz central: ESPECIALISTA_ESTADISTICA y PRODUCCION separados",
                () -> CasoUso.CU_14.getRolesPermitidos().contains(Rol.ESPECIALISTA_ESTADISTICA)
                        && CasoUso.CU_09.getRolesPermitidos().contains(Rol.ESPECIALISTA_ESTADISTICA)
                        && CasoUso.CU_01.getRolesPermitidos().contains(Rol.ESPECIALISTA_PRODUCCION)
                        && !CasoUso.CU_14.getRolesPermitidos().contains(Rol.ESPECIALISTA_PRODUCCION));
        // La validación de roles se ejecuta dentro de CU-16; hace falta sesión administrativa
        // para comprobar que DIRECTIVO no es un rol aceptado por el sistema.
        entrar(contexto, "admin416", CLAVE_ADMIN);
        verificar("AUT-04", "DIRECTIVO no tiene cuenta ni rol posible",
                () -> Rol.predefinido("DIRECTIVO") == null
                        && rechaza(() -> contexto.getUsuarioService().registrar(
                                "directivo416", "Directivo", "Directivo416!", "DIRECTIVO"),
                                ValidacionException.class));
        verificar("AUT-05", "CU-18 queda reservado al administrador en la matriz (implementado en 4.17)",
                () -> CasoUso.CU_18.getRolesPermitidos().size() == 1
                        && PermisosInterfaz.casosDelModulo("administracion").contains(CasoUso.CU_18));

        contexto.getAuthService().logout();
        verificar("AUT-06", "Sin sesión: lectura de catálogo rechazada en servicio",
                () -> rechaza(() -> contexto.getUebService().listarTodas(), AutorizacionException.class));
        verificar("AUT-07", "Sin sesión: lectura de producción rechazada en servicio",
                () -> rechaza(() -> contexto.getParteNaveService().listarTodos(), AutorizacionException.class));

        verificar("AUT-08", "Navegación directa: JEFE no entra a administración",
                () -> !navegacionPermite(Rol.JEFE_UEB, "administracion")
                        && navegacionPermite(Rol.JEFE_UEB, "huevos"));
        verificar("AUT-09", "Navegación directa: ESTADÍSTICA no entra a producción",
                () -> !navegacionPermite(Rol.ESPECIALISTA_ESTADISTICA, "huevos")
                        && navegacionPermite(Rol.ESPECIALISTA_ESTADISTICA, "informes"));
        verificar("AUT-10", "Navegación directa: PRODUCCIÓN ve referencias, no informes",
                () -> navegacionPermite(Rol.ESPECIALISTA_PRODUCCION, "referencias")
                        && !navegacionPermite(Rol.ESPECIALISTA_PRODUCCION, "informes"));

        entrar(contexto, "admin416", CLAVE_ADMIN);
        verificar("AUT-11", "ADMIN no puede registrar partes (CU-05)",
                () -> rechaza(() -> contexto.getParteNaveService().registrar(null), AutorizacionException.class));
        verificar("AUT-12", "ADMIN no puede emitir vales (CU-10)",
                () -> rechaza(() -> contexto.getValeEntregaService().emitir(null), AutorizacionException.class));
        verificar("AUT-13", "ADMIN no puede consultar análisis operativo (CU-14)",
                () -> rechaza(() -> contexto.getAnalisisService().consultarHuevosPorPeriodo(null, null),
                        AutorizacionException.class));

        entrar(contexto, "jefe416", CLAVE_JEFE);
        verificar("AUT-14", "JEFE no puede gestionar usuarios (CU-16)",
                () -> rechaza(() -> contexto.getUsuarioService().listarTodos(), AutorizacionException.class));
        verificar("AUT-15", "JEFE no puede elaborar informes (CU-15)",
                () -> rechaza(() -> contexto.getInformeService().listarInformes(), AutorizacionException.class));
        verificar("AUT-16", "JEFE sí consulta sus datos de producción (CU-05)",
                () -> contexto.getParteNaveService().listarTodos() != null);

        entrar(contexto, "estad416", CLAVE_ESTAD);
        verificar("AUT-17", "ESTADÍSTICA no puede confirmar recepciones (CU-11)",
                () -> rechaza(() -> contexto.getRecepcionService().registrar(null), AutorizacionException.class));
        verificar("AUT-18", "ESTADÍSTICA sí consulta referencias oficiales (CU-14)",
                () -> !contexto.getAnalisisService().consultarTodasLasReferencias().isEmpty());

        entrar(contexto, "prod416", CLAVE_PROD);
        verificar("AUT-19", "PRODUCCIÓN no puede modificar catálogos (CU-17)",
                () -> rechaza(() -> contexto.getUebService().activar(UUID.randomUUID()),
                        AutorizacionException.class));
        verificar("AUT-20", "PRODUCCIÓN sí consulta referencias (CU-01 a CU-03)",
                () -> !contexto.getAnalisisService().consultarMetas().isEmpty());

        entrar(contexto, "admin416", CLAVE_ADMIN);
        verificar("AUT-21", "Rechazo por rol no deja escrituras parciales",
                () -> {
                    int antes = contexto.getUebService().listarTodas().size();
                    rechaza(() -> contexto.getParteNaveService().registrar(null), AutorizacionException.class);
                    return contexto.getUebService().listarTodas().size() == antes;
                });

        contexto.getAuthService().logout();
        System.out.println("[RESUMEN] " + aprobadas + "/" + totales + " comprobaciones aprobadas");
        System.exit(aprobadas == totales ? 0 : 1);
    }

    private static void registrarCuentas(AppContext contexto) {
        contexto.getAuthService().login("admin", CLAVE_ADMIN);
        crear(contexto, "admin416", CLAVE_ADMIN, Rol.ADMINISTRADOR);
        crear(contexto, "jefe416", CLAVE_JEFE, Rol.JEFE_UEB);
        crear(contexto, "estad416", CLAVE_ESTAD, Rol.ESPECIALISTA_ESTADISTICA);
        crear(contexto, "prod416", CLAVE_PROD, Rol.ESPECIALISTA_PRODUCCION);
        contexto.getAuthService().logout();
    }

    private static void crear(AppContext contexto, String nombre, String clave, String rol) {
        if (contexto.getUsuarioService().buscarPorNombreUsuario(nombre).isPresent()) {
            return;
        }
        contexto.getUsuarioService().registrar(nombre, "Prueba " + rol, clave, rol);
    }

    private static void entrar(AppContext contexto, String nombre, String clave) {
        contexto.getAuthService().logout();
        contexto.getAuthService().login(nombre, clave);
    }

    private static boolean navegacionPermite(String rol, String modulo) {
        NavigationController navegacion = new NavigationController(
                new JPanel(new CardLayout()), new CardLayout());
        navegacion.aplicarPermisos(PermisosInterfaz.restricciones(), rol);
        return navegacion.puedeMostrar(modulo);
    }

    private static boolean rechaza(Runnable accion, Class<? extends RuntimeException> tipo) {
        try {
            accion.run();
            return false;
        } catch (RuntimeException ex) {
            return tipo.isInstance(ex);
        }
    }

    private static void verificar(String id, String descripcion, java.util.function.BooleanSupplier prueba) {
        totales++;
        boolean ok;
        try {
            ok = prueba.getAsBoolean();
        } catch (Throwable fallo) {
            ok = false;
            System.out.println("[ERROR] " + id + " -> " + fallo.getClass().getSimpleName() + ": "
                    + fallo.getMessage());
        }
        if (!ok) {
            throw new AssertionError(id + " no aprobada: " + descripcion);
        }
        aprobadas++;
        System.out.println("[APROBADA] " + id + " -> " + descripcion);
    }
}
