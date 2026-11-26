package cu.pican.model.repository.jdbc;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.persistence.PersistenciaException;
import cu.pican.model.repository.GestionReferenciasRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Implementación PostgreSQL de la escritura CU-17 sobre las referencias oficiales. */
public class GestionReferenciasRepositoryPostgres implements GestionReferenciasRepository {

    private static final String EXISTE_INDICADOR =
            "SELECT 1 FROM indicador_historico WHERE eliminado_logico = FALSE "
                    + "AND tabla = ? AND indicador = ? AND anio = ?";
    private static final String EXISTE_META =
            "SELECT 1 FROM meta_estrategica WHERE eliminado_logico = FALSE AND indicador = ?";
    private static final String UPSERT_INDICADOR =
            "INSERT INTO indicador_historico (id, tabla, indicador, anio, valor, unidad_medida, fuente) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (id) DO UPDATE SET tabla = EXCLUDED.tabla, indicador = EXCLUDED.indicador, "
                    + "anio = EXCLUDED.anio, valor = EXCLUDED.valor, "
                    + "unidad_medida = EXCLUDED.unidad_medida, fuente = EXCLUDED.fuente";
    private static final String UPSERT_META =
            "INSERT INTO meta_estrategica (id, indicador, valor_real_2019, valor_estimado_2030, "
                    + "incremento_porcentual, unidad_medida, fuente) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (id) DO UPDATE SET indicador = EXCLUDED.indicador, "
                    + "valor_real_2019 = EXCLUDED.valor_real_2019, "
                    + "valor_estimado_2030 = EXCLUDED.valor_estimado_2030, "
                    + "incremento_porcentual = EXCLUDED.incremento_porcentual, "
                    + "unidad_medida = EXCLUDED.unidad_medida, fuente = EXCLUDED.fuente";

    private final JdbcConnectionManager conexiones;

    public GestionReferenciasRepositoryPostgres(JdbcConnectionManager conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
    }

    @Override
    public boolean existeIndicador(TablaHistorica tabla, String indicador, int anio, String idExcluido) {
        StringBuilder sql = new StringBuilder(EXISTE_INDICADOR);
        List<Object> parametros = new java.util.ArrayList<>(List.of(
                tabla == null ? "" : tabla.name(), indicador == null ? "" : indicador, anio));
        if (idExcluido != null && !idExcluido.isBlank()) {
            sql.append(" AND id <> ?");
            parametros.add(idExcluido);
        }
        return existe(sql.toString(), parametros);
    }

    @Override
    public boolean existeMeta(String indicador, String idExcluido) {
        StringBuilder sql = new StringBuilder(EXISTE_META);
        List<Object> parametros = new java.util.ArrayList<>(List.of(indicador == null ? "" : indicador));
        if (idExcluido != null && !idExcluido.isBlank()) {
            sql.append(" AND id <> ?");
            parametros.add(idExcluido);
        }
        return existe(sql.toString(), parametros);
    }

    @Override
    public IndicadorHistorico guardarIndicador(IndicadorHistorico indicador) {
        Objects.requireNonNull(indicador, "indicador");
        ejecutar(UPSERT_INDICADOR, List.of(
                indicador.getId(), indicador.getTabla().name(), indicador.getIndicador(),
                indicador.getAnio(), indicador.getValor(), indicador.getUnidadMedida(), indicador.getFuente()));
        return indicador;
    }

    @Override
    public MetaEstrategica guardarMeta(MetaEstrategica meta) {
        Objects.requireNonNull(meta, "meta");
        ejecutar(UPSERT_META, List.of(
                meta.getId(), meta.getIndicador(), meta.getValorReal2019(),
                meta.getValorEstimado2030(), meta.getIncrementoPorcentual(),
                meta.getUnidadMedida(), meta.getFuente()));
        return meta;
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

    private void ejecutar(String sql, List<Object> parametros) {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            enlazar(ps, parametros);
            ps.executeUpdate();
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
