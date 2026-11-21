package cu.pican.repository.jdbc;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.ConsolidadoUEBRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ConsolidadoUEBRepositoryPostgres extends JdbcRepositorySupport<ConsolidadoUEB>
        implements ConsolidadoUEBRepository {

    public ConsolidadoUEBRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "consolidado_ueb";
    }

    @Override
    protected String columnaId() {
        return "id_consolidado";
    }

    @Override
    protected UUID idDe(ConsolidadoUEB entidad) {
        return entidad.getIdConsolidado();
    }

    @Override
    protected ConsolidadoUEB mapear(ResultSet rs) throws SQLException {
        ConsolidadoUEB consolidado = new ConsolidadoUEB();
        consolidado.setIdConsolidado(JdbcSupport.uuid(rs, "id_consolidado"));
        consolidado.setIdUEB(JdbcSupport.uuid(rs, "id_ueb"));
        consolidado.setFechaInicio(JdbcSupport.fecha(rs, "fecha_inicio"));
        consolidado.setFechaFin(JdbcSupport.fecha(rs, "fecha_fin"));
        consolidado.setEstado(rs.getString("estado"));
        consolidado.setMotivoParcial(rs.getString("motivo_parcial"));
        consolidado.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        consolidado.setCreadoEn(JdbcSupport.fechaHora(rs, "creado_en"));
        consolidado.setVersion(JdbcSupport.largo(rs, "version"));
        return consolidado;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, ConsolidadoUEB entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdConsolidado());
        escribirDatos(ps, entidad, 2);
        ps.setLong(9, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, ConsolidadoUEB entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, ConsolidadoUEB entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdUEB());
        JdbcSupport.setFecha(ps, i + 1, entidad.getFechaInicio());
        JdbcSupport.setFecha(ps, i + 2, entidad.getFechaFin());
        JdbcSupport.setString(ps, i + 3, entidad.getEstado());
        JdbcSupport.setString(ps, i + 4, entidad.getMotivoParcial());
        JdbcSupport.setUuid(ps, i + 5, entidad.getIdUsuario());
        JdbcSupport.setFechaHora(ps, i + 6,
                entidad.getCreadoEn() == null ? LocalDateTime.now() : entidad.getCreadoEn());
    }

    @Override
    protected String columnasInsercion() {
        return "id_consolidado, id_ueb, fecha_inicio, fecha_fin, estado, motivo_parcial, "
                + "id_usuario, creado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_ueb = ?, fecha_inicio = ?, fecha_fin = ?, estado = ?, motivo_parcial = ?, "
                + "id_usuario = ?, creado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 7;
    }

    @Override
    public List<ConsolidadoUEB> findByUEB(UUID idUEB) {
        return listarConFiltro("id_ueb = ?", List.of(idUEB), "fecha_inicio, creado_en");
    }
}
