package cu.pican.repository.jdbc;

import cu.pican.model.Nave;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.NaveRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NaveRepositoryPostgres extends JdbcRepositorySupport<Nave> implements NaveRepository {

    public NaveRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "nave";
    }

    @Override
    protected String columnaId() {
        return "id_nave";
    }

    @Override
    protected UUID idDe(Nave entidad) {
        return entidad.getIdNave();
    }

    @Override
    protected Nave mapear(ResultSet rs) throws SQLException {
        Nave nave = new Nave();
        nave.setIdNave(JdbcSupport.uuid(rs, "id_nave"));
        nave.setIdUEB(JdbcSupport.uuid(rs, "id_ueb"));
        nave.setNombre(rs.getString("nombre"));
        nave.setCodigo(rs.getString("codigo"));
        nave.setActiva(rs.getBoolean("activa"));
        return nave;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, Nave entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdNave());
        JdbcSupport.setUuid(ps, 2, entidad.getIdUEB());
        JdbcSupport.setString(ps, 3, entidad.getNombre());
        JdbcSupport.setString(ps, 4, entidad.getCodigo());
        JdbcSupport.setBoolean(ps, 5, entidad.isActiva());
        ps.setLong(6, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, Nave entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdUEB());
        JdbcSupport.setString(ps, 2, entidad.getNombre());
        JdbcSupport.setString(ps, 3, entidad.getCodigo());
        JdbcSupport.setBoolean(ps, 4, entidad.isActiva());
    }

    @Override
    protected String columnasInsercion() {
        return "id_nave, id_ueb, nombre, codigo, activa, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_ueb = ?, nombre = ?, codigo = ?, activa = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 4;
    }

    @Override
    public List<Nave> findByUEB(UUID idUEB) {
        return listarConFiltro("id_ueb = ?", List.of(idUEB), "creado_en");
    }

    @Override
    public Optional<Nave> findByCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Optional.empty();
        }
        return buscarUno("lower(btrim(codigo)) = lower(btrim(?))", codigo);
    }
}
