package cu.pican.repository.jdbc;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.TablaHistorica;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcSupport;
import cu.pican.persistence.PersistenciaException;
import cu.pican.repository.IndicadorHistoricoRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Repositorio de solo lectura de las tablas históricas oficiales. */
public class IndicadorHistoricoRepositoryPostgres implements IndicadorHistoricoRepository {

    private final JdbcConnectionManager conexiones;

    public IndicadorHistoricoRepositoryPostgres(JdbcConnectionManager conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public List<IndicadorHistorico> findAll() {
        return consultar("1 = 1", List.of(), "tabla, anio, id");
    }

    @Override
    public Optional<IndicadorHistorico> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        List<IndicadorHistorico> datos = consultar("id = ?", List.of(id), null);
        return datos.isEmpty() ? Optional.empty() : Optional.of(datos.get(0));
    }

    @Override
    public List<IndicadorHistorico> findByTabla(TablaHistorica tabla) {
        if (tabla == null) {
            return List.of();
        }
        return consultar("tabla = ?", List.of(tabla.name()), "anio, id");
    }

    @Override
    public List<IndicadorHistorico> findByAnio(int anio) {
        return consultar("anio = ?", List.of(anio), "tabla, id");
    }

    @Override
    public List<IndicadorHistorico> findByTablaAndAnio(TablaHistorica tabla, int anio) {
        if (tabla == null) {
            return List.of();
        }
        return consultar("tabla = ? AND anio = ?", List.of(tabla.name(), anio), "id");
    }

    @Override
    public List<IndicadorHistorico> findByIndicador(String indicador) {
        if (indicador == null) {
            return List.of();
        }
        return consultar("indicador = ?", List.of(indicador), "anio, id");
    }

    private List<IndicadorHistorico> consultar(String where, List<Object> parametros, String order) {
        String sql = "SELECT * FROM indicador_historico WHERE eliminado_logico = FALSE AND (" + where + ")"
                + (order == null || order.isBlank() ? "" : " ORDER BY " + order);
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object valor : parametros) {
                if (valor instanceof Integer entero) {
                    ps.setInt(i++, entero);
                } else {
                    ps.setString(i++, valor.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<IndicadorHistorico> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(new IndicadorHistorico(
                            rs.getString("id"),
                            TablaHistorica.valueOf(rs.getString("tabla")),
                            rs.getString("indicador"),
                            rs.getInt("anio"),
                            rs.getBigDecimal("valor"),
                            rs.getString("unidad_medida"),
                            rs.getString("fuente")));
                }
                return resultado;
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo consultar indicadores históricos: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            if (cerrar) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // No se oculta el error original.
                }
            }
        }
    }
}
