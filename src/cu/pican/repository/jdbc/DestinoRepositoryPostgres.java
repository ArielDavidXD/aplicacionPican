package cu.pican.repository.jdbc;

import cu.pican.model.Destino;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.DestinoRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class DestinoRepositoryPostgres extends JdbcRepositorySupport<Destino> implements DestinoRepository {

    public DestinoRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "destino";
    }

    @Override
    protected String columnaId() {
        return "id_destino";
    }

    @Override
    protected UUID idDe(Destino entidad) {
        return entidad.getIdDestino();
    }

    @Override
    protected Destino mapear(ResultSet rs) throws SQLException {
        Destino destino = new Destino();
        destino.setIdDestino(JdbcSupport.uuid(rs, "id_destino"));
        destino.setCodigo(rs.getString("codigo"));
        destino.setNombre(rs.getString("nombre"));
        destino.setActiva(rs.getBoolean("activa"));
        return destino;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, Destino entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdDestino());
        JdbcSupport.setString(ps, 2, entidad.getCodigo());
        JdbcSupport.setString(ps, 3, entidad.getNombre());
        JdbcSupport.setBoolean(ps, 4, entidad.isActiva());
        ps.setLong(5, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, Destino entidad) throws SQLException {
        JdbcSupport.setString(ps, 1, entidad.getCodigo());
        JdbcSupport.setString(ps, 2, entidad.getNombre());
        JdbcSupport.setBoolean(ps, 3, entidad.isActiva());
    }

    @Override
    protected String columnasInsercion() {
        return "id_destino, codigo, nombre, activa, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "codigo = ?, nombre = ?, activa = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 3;
    }

    @Override
    public Optional<Destino> findByCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Optional.empty();
        }
        return buscarUno("lower(btrim(codigo)) = lower(btrim(?))", codigo);
    }
}
