package cu.pican.verify;

import cu.pican.controller.app.AppContext;
import cu.pican.controller.app.AutorizacionDemo;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.UEB;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.controller.service.AutorizacionService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** Utilidades del arnés de sincronización de la Etapa 4.15 (solo pruebas). */
final class SyncTestSupport {

    static final String HOST = prefijoUrl();
    static final String USUARIO = System.getProperty("pican.test.user", "postgres");
    static final String CLAVE = System.getProperty("pican.test.password", System.getenv("PGPASSWORD"));
    static final String CLAVE_ADMIN = "ClaveSync416Admin!";
    static final String CLAVE_JEFE = "ClaveSync415!";

    private SyncTestSupport() {
    }

    static AppContext contexto(String base) {
        if (CLAVE == null || CLAVE.isBlank()) {
            throw new IllegalStateException(
                    "Defina -Dpican.test.password o PGPASSWORD para la sincronización PostgreSQL.");
        }
        System.setProperty("pican.persistence", "postgresql");
        System.setProperty("pican.db.url", HOST + base);
        System.setProperty("pican.db.user", USUARIO);
        System.setProperty("pican.db.password", CLAVE);
        System.setProperty("pican.db.migrate", "true");
        System.setProperty("pican.migrations.dir", "sql/migrations");
        System.setProperty("pican.admin.password", CLAVE_ADMIN);
        return new AppContext();
    }

    private static String prefijoUrl() {
        String url = System.getProperty("pican.test.url", "jdbc:postgresql://127.0.0.1:5432/pican_sync_test");
        int separador = url.lastIndexOf('/');
        if (separador < "jdbc:postgresql://".length()) {
            throw new IllegalStateException("La URL de prueba PostgreSQL no es válida: " + url);
        }
        return url.substring(0, separador + 1);
    }

    /** Alta de cuentas bajo CU-16; deja la sesión cerrada para no alterar al llamador. */
    static String registrar(AppContext contexto, String prefijo, String rol) {
        String nombre = prefijo + "_" + UUID.randomUUID().toString().substring(0, 8);
        ejecutarComoAdmin(contexto, () -> {
            contexto.getUsuarioService().registrar(nombre, "Usuario de prueba " + rol, CLAVE_JEFE, rol);
            check(contexto.getUsuarioService().buscarPorNombreUsuario(nombre).isPresent(),
                    "No se registró " + nombre);
        });
        return nombre;
    }

    static void ejecutarComoAdmin(AppContext contexto, Runnable accion) {
        contexto.getAuthService().logout();
        contexto.getAuthService().login("admin", CLAVE_ADMIN);
        try {
            accion.run();
        } finally {
            contexto.getAuthService().logout();
        }
    }

    /**
     * Siembra operativa histórica: se autoriza explícitamente con roles de demostración
     * pero sin iniciar sesión, para conservar la ausencia de vínculo usuario↔UEB que
     * comprueba la Etapa 4.15. La autoridad real se restaura siempre.
     */
    static UUID crearDatos(AppContext contexto, UUID idUeb, String nombre, String prefijo,
                           LocalDate fecha, int huevos) {
        UUID idNave = UUID.randomUUID();
        UUID idParte = UUID.randomUUID();
        AutorizacionService original = contexto.getAutorizacionService();
        contexto.usarAutorizacion(AutorizacionDemo.politicaTodosLosRoles());
        try {
            contexto.getUebService().registrar(new UEB(idUeb, nombre, prefijo + "-" + sufijo(), true));
            contexto.getNaveService().registrar(new Nave(idNave, idUeb, "Nave " + nombre,
                    prefijo + "-NAV-" + sufijo(), true));
            ParteNave parte = new ParteNave();
            parte.setIdParte(idParte);
            parte.setIdNave(idNave);
            parte.setFechaProduccion(fecha);
            parte.setGallinasVivas(100);
            parte.setGallinasMuertas(1);
            parte.setGallinasEnfermas(2);
            parte.setHuevosProducidos(huevos);
            parte.setHuevosRotos(3);
            parte.setPiensoKg(new BigDecimal("12.50"));
            contexto.getParteNaveService().registrar(parte);
        } finally {
            contexto.usarAutorizacion(original);
        }
        return idParte;
    }

    static boolean existeFila(AppContext contexto, String tabla, UUID id) {
        return contarDonde(contexto, tabla, clave(tabla), id.toString()) > 0;
    }

    static long contarDonde(AppContext contexto, String tabla, String columna, String valor) {
        Connection conexion = contexto.getConexiones().obtenerConexion();
        boolean cerrar = conexion != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT count(*) FROM " + tabla + " WHERE " + columna + " = ?")) {
            ps.setObject(1, valor, Types.OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo consultar " + tabla + ": " + ex.getMessage(), ex);
        } finally {
            cerrar(conexion, cerrar);
        }
    }

    static long contar(AppContext contexto, String tabla) {
        Connection conexion = contexto.getConexiones().obtenerConexion();
        boolean cerrar = conexion != JdbcSupport.conexionActiva();
        try (Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM " + tabla)) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo contar " + tabla + ": " + ex.getMessage(), ex);
        } finally {
            cerrar(conexion, cerrar);
        }
    }

    static String valorColumna(AppContext contexto, String tabla, UUID id, String columna) {
        Connection conexion = contexto.getConexiones().obtenerConexion();
        boolean cerrar = conexion != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT " + columna + " FROM " + tabla + " WHERE " + clave(tabla) + " = ?")) {
            ps.setObject(1, id.toString(), Types.OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo leer " + columna + ": " + ex.getMessage(), ex);
        } finally {
            cerrar(conexion, cerrar);
        }
    }

    static boolean esNulo(AppContext contexto, String tabla, UUID id, String columna) {
        return valorColumna(contexto, tabla, id, columna) == null;
    }

    static String estadoPaquete(AppContext contexto, UUID idPaquete) {
        Connection conexion = contexto.getConexiones().obtenerConexion();
        boolean cerrar = conexion != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT estado FROM sync_paquete WHERE id_paquete = ?")) {
            ps.setObject(1, idPaquete.toString(), Types.OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo leer el estado del paquete: " + ex.getMessage(), ex);
        } finally {
            cerrar(conexion, cerrar);
        }
    }

    static void ejecutarSql(AppContext contexto, String sql, Object... parametros) {
        Connection conexion = contexto.getConexiones().obtenerConexion();
        boolean cerrar = conexion != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                Object valor = parametros[i];
                if (valor instanceof UUID) {
                    ps.setObject(i + 1, valor.toString(), Types.OTHER);
                } else {
                    ps.setObject(i + 1, valor);
                }
            }
            ps.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Fallo SQL de prueba: " + ex.getMessage(), ex);
        } finally {
            cerrar(conexion, cerrar);
        }
    }

    private static String clave(String tabla) {
        switch (tabla) {
            case "parte_nave": return "id_parte";
            case "ueb": return "id_ueb";
            case "nave": return "id_nave";
            default: throw new IllegalArgumentException("Tabla de prueba no soportada: " + tabla);
        }
    }

    private static void cerrar(Connection conexion, boolean cerrar) {
        if (cerrar) {
            try { conexion.close(); } catch (Exception ignored) { }
        }
    }

    static void check(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
