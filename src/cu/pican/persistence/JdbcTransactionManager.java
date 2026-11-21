package cu.pican.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Transacciones JDBC reales. Todos los repositorios PostgreSQL participan en la misma conexión
 * mientras la transacción está activa; si no hay transacción, cada operación usa autocommit.
 */
public final class JdbcTransactionManager implements TransactionManager {

    private final JdbcConnectionManager conexiones;

    public JdbcTransactionManager(JdbcConnectionManager conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public <T> T ejecutar(Supplier<T> operacion) {
        if (JdbcSupport.hayConexionActiva()) {
            return operacion.get();
        }

        Connection conexion = conexiones.obtenerConexion();
        boolean autocommitAnterior = true;
        try {
            autocommitAnterior = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            JdbcSupport.enlazar(conexion);
            T resultado = operacion.get();
            conexion.commit();
            return resultado;
        } catch (RuntimeException ex) {
            rollbackSilencioso(conexion, ex);
            throw ex;
        } catch (SQLException ex) {
            rollbackSilencioso(conexion, ex);
            throw new PersistenciaException("No se pudo completar la transacción: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            JdbcSupport.desenlazar();
            try {
                conexion.setAutoCommit(autocommitAnterior);
            } catch (SQLException ignored) {
                // La conexión se cierra a continuación; no se oculta el resultado de la operación.
            }
            try {
                conexion.close();
            } catch (SQLException ignored) {
                // No se oculta el resultado de la operación por un fallo al cerrar.
            }
        }
    }

    private void rollbackSilencioso(Connection conexion, Exception causa) {
        try {
            if (!conexion.isClosed()) {
                conexion.rollback();
            }
        } catch (SQLException ex) {
            causa.addSuppressed(ex);
        }
    }
}
