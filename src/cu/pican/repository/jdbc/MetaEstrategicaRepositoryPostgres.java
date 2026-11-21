package cu.pican.repository.jdbc;

import cu.pican.model.MetaEstrategica;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcSupport;
import cu.pican.persistence.PersistenciaException;
import cu.pican.repository.MetaEstrategicaRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Repositorio de solo lectura de las metas estratégicas oficiales. */
public class MetaEstrategicaRepositoryPostgres implements MetaEstrategicaRepository {

    private final JdbcConnectionManager conexiones;

    public MetaEstrategicaRepositoryPostgres(JdbcConnectionManager conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public List<MetaEstrategica> findAll() {
        return consultar("1 = 1", List.of(), "id");
    }

    @Override
    public Optional<MetaEstrategica> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        List<MetaEstrategica> datos = consultar("id = ?", List.of(id), null);
        return datos.isEmpty() ? Optional.empty() : Optional.of(datos.get(0));
    }

    @Override
    public List<MetaEstrategica> findByIndicador(String indicador) {
        if (indicador == null) {
            return List.of();
        }
        return consultar("indicador = ?", List.of(indicador), "id");
    }

    private List<MetaEstrategica> consultar(String where, List<Object> parametros, String order) {
        String sql = "SELECT * FROM meta_estrategica WHERE eliminado_logico = FALSE AND (" + where + ")"
                + (order == null || order.isBlank() ? "" : " ORDER BY " + order);
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setString(i + 1, parametros.get(i).toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<MetaEstrategica> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(new MetaEstrategica(
                            rs.getString("id"),
                            rs.getString("indicador"),
                            rs.getBigDecimal("valor_real_2019"),
                            rs.getBigDecimal("valor_estimado_2030"),
                            rs.getBigDecimal("incremento_porcentual"),
                            rs.getString("unidad_medida"),
                            rs.getString("fuente")));
                }
                return resultado;
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo consultar metas estratégicas: "
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
