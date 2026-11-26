package cu.pican.controller.app;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.persistence.PersistenciaException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Carga idempotente de las referencias históricas oficiales en PostgreSQL.
 * Reutiliza exactamente los datos de {@link ReferenciasHistoricasInitializer}; no calcula ni inventa valores.
 * Los registros existentes nunca se modifican: {@code ON CONFLICT DO NOTHING}.
 */
public final class ReferenciasHistoricasJdbcInitializer {

    private static final String INSERT_INDICADOR =
            "INSERT INTO indicador_historico (id, tabla, indicador, anio, valor, unidad_medida, fuente) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";

    private static final String INSERT_META =
            "INSERT INTO meta_estrategica (id, indicador, valor_real_2019, valor_estimado_2030, "
            + "incremento_porcentual, unidad_medida, fuente) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";

    private ReferenciasHistoricasJdbcInitializer() {
    }

    public static void cargar(JdbcConnectionManager conexiones) {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        boolean transaccionLocal = false;
        boolean anterior = true;
        try {
            anterior = c.getAutoCommit();
            if (anterior) {
                c.setAutoCommit(false);
                transaccionLocal = true;
            }
            insertarIndicadores(c);
            insertarMetas(c);
            if (transaccionLocal) {
                c.commit();
            }
        } catch (SQLException ex) {
            if (transaccionLocal) {
                try {
                    c.rollback();
                } catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }
            }
            throw new PersistenciaException("No se pudieron inicializar las referencias históricas oficiales: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            if (transaccionLocal) {
                try {
                    c.setAutoCommit(anterior);
                } catch (SQLException ignored) {
                    // La conexión se cierra a continuación.
                }
            }
            if (cerrar) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // No se oculta el resultado de la inicialización.
                }
            }
        }
    }

    private static void insertarIndicadores(Connection c) throws SQLException {
        List<IndicadorHistorico> datos = ReferenciasHistoricasInitializer.buildIndicadores();
        try (PreparedStatement ps = c.prepareStatement(INSERT_INDICADOR)) {
            for (IndicadorHistorico i : datos) {
                ps.setString(1, i.getId());
                ps.setString(2, i.getTabla().name());
                ps.setString(3, i.getIndicador());
                ps.setInt(4, i.getAnio());
                ps.setBigDecimal(5, i.getValor());
                ps.setString(6, i.getUnidadMedida());
                ps.setString(7, i.getFuente());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertarMetas(Connection c) throws SQLException {
        List<MetaEstrategica> datos = ReferenciasHistoricasInitializer.buildMetas();
        try (PreparedStatement ps = c.prepareStatement(INSERT_META)) {
            for (MetaEstrategica m : datos) {
                ps.setString(1, m.getId());
                ps.setString(2, m.getIndicador());
                ps.setBigDecimal(3, m.getValorReal2019());
                ps.setBigDecimal(4, m.getValorEstimado2030());
                ps.setBigDecimal(5, m.getIncrementoPorcentual());
                ps.setString(6, m.getUnidadMedida());
                ps.setString(7, m.getFuente());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
