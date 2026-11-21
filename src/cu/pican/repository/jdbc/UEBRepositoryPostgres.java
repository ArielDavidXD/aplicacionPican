package cu.pican.repository.jdbc;

import cu.pican.model.UEB;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.UEBRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class UEBRepositoryPostgres extends JdbcRepositorySupport<UEB> implements UEBRepository {

    public UEBRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "ueb";
    }

    @Override
    protected String columnaId() {
        return "id_ueb";
    }

    @Override
    protected UUID idDe(UEB entidad) {
        return entidad.getIdUEB();
    }

    @Override
    protected UEB mapear(ResultSet rs) throws SQLException {
        UEB ueb = new UEB();
        ueb.setIdUEB(JdbcSupport.uuid(rs, "id_ueb"));
        ueb.setNombre(rs.getString("nombre"));
        ueb.setCodigo(rs.getString("codigo"));
        ueb.setActiva(rs.getBoolean("activa"));
        return ueb;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, UEB entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdUEB());
        JdbcSupport.setString(ps, 2, entidad.getNombre());
        JdbcSupport.setString(ps, 3, entidad.getCodigo());
        JdbcSupport.setBoolean(ps, 4, entidad.isActiva());
        ps.setLong(5, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, UEB entidad) throws SQLException {
        JdbcSupport.setString(ps, 1, entidad.getNombre());
        JdbcSupport.setString(ps, 2, entidad.getCodigo());
        JdbcSupport.setBoolean(ps, 3, entidad.isActiva());
        ps.setLong(4, 1L);
    }

    @Override
    protected String columnasInsercion() {
        return "id_ueb, nombre, codigo, activa, version";
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
    public Optional<UEB> findByCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Optional.empty();
        }
        return buscarUno("lower(btrim(codigo)) = lower(btrim(?))", codigo);
    }
}
