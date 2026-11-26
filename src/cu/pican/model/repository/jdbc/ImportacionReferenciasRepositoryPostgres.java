package cu.pican.model.repository.jdbc;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.persistence.PersistenciaException;
import cu.pican.model.repository.ImportacionReferenciasRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Implementación PostgreSQL de la escritura controlada de CU-18. */
public class ImportacionReferenciasRepositoryPostgres implements ImportacionReferenciasRepository {

    private static final String EXISTE_INDICADOR =
            "SELECT 1 FROM indicador_historico WHERE eliminado_logico = FALSE "
                    + "AND tabla = ? AND indicador = ? AND anio = ? LIMIT 1";
    private static final String EXISTE_META =
            "SELECT 1 FROM meta_estrategica WHERE eliminado_logico = FALSE AND indicador = ? LIMIT 1";
    private static final String INSERT_INDICADOR =
            "INSERT INTO indicador_historico (id, tabla, indicador, anio, valor, unidad_medida, fuente) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
    private static final String INSERT_META =
            "INSERT INTO meta_estrategica (id, indicador, valor_real_2019, valor_estimado_2030, "
                    + "incremento_porcentual, unidad_medida, fuente) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";

    private final JdbcConnectionManager conexiones;

    public ImportacionReferenciasRepositoryPostgres(JdbcConnectionManager conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
    }

    @Override
    public boolean existeIndicador(TablaHistorica tabla, String indicador, int anio) {
        return existe(EXISTE_INDICADOR,
                List.of(tabla == null ? "" : tabla.name(), indicador == null ? "" : indicador, anio));
    }

    @Override
    public boolean existeMeta(String indicador) {
        return existe(EXISTE_META, List.of(indicador == null ? "" : indicador));
    }

    @Override
    public boolean guardarIndicadorSiAusente(IndicadorHistorico indicador) {
        if (indicador == null || existeIndicador(indicador.getTabla(), indicador.getIndicador(), indicador.getAnio())) {
            return false;
        }
        return insertar(INSERT_INDICADOR, List.of(
                indicador.getId(), indicador.getTabla().name(), indicador.getIndicador(),
                indicador.getAnio(), indicador.getValor(), indicador.getUnidadMedida(), indicador.getFuente()));
    }

    @Override
    public boolean guardarMetaSiAusente(MetaEstrategica meta) {
        if (meta == null || existeMeta(meta.getIndicador())) {
            return false;
        }
        return insertar(INSERT_META, List.of(
                meta.getId(), meta.getIndicador(), meta.getValorReal2019(),
                meta.getValorEstimado2030(), meta.getIncrementoPorcentual(),
                meta.getUnidadMedida(), meta.getFuente()));
    }

    private boolean existe(String sql, List<Object> parametros) {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            enlazar(ps, parametros);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo comprobar el registro oficial: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c, cerrar);
        }
    }

    private boolean insertar(String sql, List<Object> parametros) {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            enlazar(ps, parametros);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo guardar el registro oficial: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c, cerrar);
        }
    }

    private static void enlazar(PreparedStatement ps, List<Object> parametros) throws SQLException {
        for (int i = 0; i < parametros.size(); i++) {
            Object valor = parametros.get(i);
            int indice = i + 1;
            if (valor instanceof Integer entero) {
                JdbcSupport.setEntero(ps, indice, entero);
            } else if (valor instanceof BigDecimal decimal) {
                JdbcSupport.setDecimal(ps, indice, decimal);
            } else {
                JdbcSupport.setString(ps, indice, valor == null ? null : valor.toString());
            }
        }
    }

    private static void cerrar(Connection c, boolean cerrar) {
        if (cerrar) {
            try {
                c.close();
            } catch (SQLException ignored) {
                // No se oculta el error original.
            }
        }
    }
}
