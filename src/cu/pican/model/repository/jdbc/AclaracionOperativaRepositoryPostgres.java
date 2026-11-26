package cu.pican.model.repository.jdbc;

import cu.pican.model.AclaracionOperativa;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcRepositorySupport;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.repository.AclaracionOperativaRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class AclaracionOperativaRepositoryPostgres extends JdbcRepositorySupport<AclaracionOperativa>
        implements AclaracionOperativaRepository {

    public AclaracionOperativaRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "aclaracion_operativa";
    }

    @Override
    protected String columnaId() {
        return "id_aclaracion";
    }

    @Override
    protected String condicionAdicional() {
        return "eliminado_logico = FALSE";
    }

    @Override
    protected UUID idDe(AclaracionOperativa entidad) {
        return entidad.getIdAclaracion();
    }

    @Override
    protected AclaracionOperativa mapear(ResultSet rs) throws SQLException {
        AclaracionOperativa a = new AclaracionOperativa();
        a.setIdAclaracion(JdbcSupport.uuid(rs, "id_aclaracion"));
        a.setIdUEBOrigen(JdbcSupport.uuid(rs, "id_ueb_origen"));
        a.setTipoOrigen(rs.getString("tipo_origen"));
        a.setIdOrigen(JdbcSupport.uuid(rs, "id_origen"));
        a.setMotivo(rs.getString("motivo"));
        a.setEstado(rs.getString("estado"));
        a.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        a.setFechaSolicitud(JdbcSupport.fechaHora(rs, "fecha_solicitud"));
        a.setFechaResolucion(JdbcSupport.fechaHora(rs, "fecha_resolucion"));
        a.setObservacionResolucion(rs.getString("observacion_resolucion"));
        return a;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, AclaracionOperativa entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdAclaracion());
        escribirDatos(ps, entidad, 2);
        ps.setLong(11, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, AclaracionOperativa entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, AclaracionOperativa entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdUEBOrigen());
        JdbcSupport.setString(ps, i + 1, entidad.getTipoOrigen());
        JdbcSupport.setUuid(ps, i + 2, entidad.getIdOrigen());
        JdbcSupport.setString(ps, i + 3, entidad.getMotivo());
        JdbcSupport.setString(ps, i + 4, entidad.getEstado());
        JdbcSupport.setUuid(ps, i + 5, entidad.getIdUsuario());
        JdbcSupport.setFechaHora(ps, i + 6,
                entidad.getFechaSolicitud() == null ? LocalDateTime.now() : entidad.getFechaSolicitud());
        JdbcSupport.setFechaHora(ps, i + 7, entidad.getFechaResolucion());
        JdbcSupport.setString(ps, i + 8, entidad.getObservacionResolucion());
    }

    @Override
    protected String columnasInsercion() {
        return "id_aclaracion, id_ueb_origen, tipo_origen, id_origen, motivo, estado, id_usuario, "
                + "fecha_solicitud, fecha_resolucion, observacion_resolucion, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_ueb_origen = ?, tipo_origen = ?, id_origen = ?, motivo = ?, estado = ?, id_usuario = ?, "
                + "fecha_solicitud = ?, fecha_resolucion = ?, observacion_resolucion = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 9;
    }

    @Override
    public List<AclaracionOperativa> findByOrigen(String tipoOrigen, UUID idOrigen) {
        return listarConFiltro("tipo_origen = ? AND id_origen = ?",
                List.of(tipoOrigen, idOrigen), "fecha_solicitud");
    }

    @Override
    public List<AclaracionOperativa> findPendientes() {
        return listarConFiltro("estado = ?", List.of(AclaracionOperativa.PENDIENTE), "fecha_solicitud");
    }
}
