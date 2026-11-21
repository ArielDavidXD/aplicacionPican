package cu.pican.repository.jdbc;

import cu.pican.model.UnidadAsociada;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.UnidadAsociadaRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class UnidadAsociadaRepositoryPostgres extends JdbcRepositorySupport<UnidadAsociada>
        implements UnidadAsociadaRepository {

    public UnidadAsociadaRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "unidad_asociada";
    }

    @Override
    protected String columnaId() {
        return "id_unidad";
    }

    @Override
    protected UUID idDe(UnidadAsociada entidad) {
        return entidad.getIdUnidad();
    }

    @Override
    protected UnidadAsociada mapear(ResultSet rs) throws SQLException {
        UnidadAsociada unidad = new UnidadAsociada();
        unidad.setIdUnidad(JdbcSupport.uuid(rs, "id_unidad"));
        unidad.setNombre(rs.getString("nombre"));
        unidad.setCodigo(rs.getString("codigo"));
        unidad.setActiva(rs.getBoolean("activa"));
        return unidad;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, UnidadAsociada entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdUnidad());
        JdbcSupport.setString(ps, 2, entidad.getNombre());
        JdbcSupport.setString(ps, 3, entidad.getCodigo());
        JdbcSupport.setBoolean(ps, 4, entidad.isActiva());
        ps.setLong(5, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, UnidadAsociada entidad) throws SQLException {
        JdbcSupport.setString(ps, 1, entidad.getNombre());
        JdbcSupport.setString(ps, 2, entidad.getCodigo());
        JdbcSupport.setBoolean(ps, 3, entidad.isActiva());
    }

    @Override
    protected String columnasInsercion() {
        return "id_unidad, nombre, codigo, activa, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "nombre = ?, codigo = ?, activa = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 3;
    }

    @Override
    public Optional<UnidadAsociada> findByCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Optional.empty();
        }
        return buscarUno("lower(btrim(codigo)) = lower(btrim(?))", codigo);
    }
}
