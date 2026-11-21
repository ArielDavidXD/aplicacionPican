package cu.pican.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Único punto que abre conexiones JDBC y traduce errores de conexión a mensajes claros. */
public final class JdbcConnectionManager {

    private final DatabaseConfig config;

    public JdbcConnectionManager(DatabaseConfig config) {
        if (config == null || !config.esPostgresql()) {
            throw new PersistenciaException("No se puede crear el gestor JDBC sin configuración PostgreSQL.");
        }
        this.config = config;
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    /**
     * Devuelve la conexión de la transacción actual si existe; si no, abre una conexión nueva.
     * Los repositorios nunca crean conexiones propias fuera de este método.
     */
    public Connection obtenerConexion() {
        Connection activa = JdbcSupport.conexionActiva();
        if (activa != null) {
            return activa;
        }
        try {
            Connection conexion = DriverManager.getConnection(
                    config.getUrl(), config.getUsuario(), config.getContrasena());
            conexion.setAutoCommit(true);
            return conexion;
        } catch (SQLException ex) {
            throw new PersistenciaException(describirError(ex), ex);
        }
    }

    /** Comprobación explícita usada por las pruebas y por el arranque. */
    public void verificarConexion() {
        try (Connection conexion = DriverManager.getConnection(
                config.getUrl(), config.getUsuario(), config.getContrasena());
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SELECT 1");
        } catch (SQLException ex) {
            throw new PersistenciaException(describirError(ex), ex);
        }
    }

    public static String describirError(SQLException ex) {
        String estado = ex.getSQLState();
        String base = ex.getMessage() == null ? "sin mensaje del controlador" : ex.getMessage();
        if ("08001".equals(estado) || "08006".equals(estado) || "08004".equals(estado)) {
            return "PostgreSQL no está disponible o no se pudo conectar: " + base;
        }
        if ("28P01".equals(estado)) {
            return "Credenciales de PostgreSQL incorrectas: " + base;
        }
        if ("3D000".equals(estado)) {
            return "La base de datos indicada no existe: " + base;
        }
        if ("42501".equals(estado)) {
            return "El usuario de PostgreSQL no tiene permisos suficientes: " + base;
        }
        return "Error de PostgreSQL [" + estado + "]: " + base;
    }
}
