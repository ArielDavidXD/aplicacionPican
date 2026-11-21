package cu.pican.repository.jdbc;

import cu.pican.model.Incidencia;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.IncidenciaRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class IncidenciaRepositoryPostgres extends JdbcRepositorySupport<Incidencia>
        implements IncidenciaRepository {

    public IncidenciaRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "incidencia";
    }

    @Override
    protected String columnaId() {
        return "id_incidencia";
    }

    @Override
    protected UUID idDe(Incidencia entidad) {
        return entidad.getIdIncidencia();
    }

    @Override
    protected Incidencia mapear(ResultSet rs) throws SQLException {
        Incidencia incidencia = new Incidencia();
        incidencia.setIdIncidencia(JdbcSupport.uuid(rs, "id_incidencia"));
        incidencia.setIdVale(JdbcSupport.uuid(rs, "id_vale"));
        incidencia.setIdRecepcion(JdbcSupport.uuid(rs, "id_recepcion"));
        incidencia.setTipo(rs.getString("tipo"));
        incidencia.setCantidadAfectada(JdbcSupport.decimal(rs, "cantidad_afectada"));
        incidencia.setDescripcion(rs.getString("descripcion"));
        incidencia.setMedidaAcordada(rs.getString("medida_acordada"));
        incidencia.setFechaCompromiso(JdbcSupport.fechaHora(rs, "fecha_compromiso"));
        incidencia.setEstado(rs.getString("estado"));
        incidencia.setObservaciones(rs.getString("observaciones"));
        incidencia.setFechaHoraRegistro(JdbcSupport.fechaHora(rs, "fecha_hora_registro"));
        incidencia.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        incidencia.setFechaCreacion(JdbcSupport.fechaHora(rs, "creado_en"));
        incidencia.setFechaCierre(JdbcSupport.fechaHora(rs, "fecha_cierre"));
        incidencia.setResultadoSolucion(rs.getString("resultado_solucion"));
        incidencia.setVersion(JdbcSupport.largo(rs, "version"));
        return incidencia;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, Incidencia entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdIncidencia());
        escribirDatos(ps, entidad, 2);
        ps.setLong(16, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, Incidencia entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, Incidencia entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdVale());
        JdbcSupport.setUuid(ps, i + 1, entidad.getIdRecepcion());
        JdbcSupport.setString(ps, i + 2, entidad.getTipo());
        JdbcSupport.setDecimal(ps, i + 3, entidad.getCantidadAfectada());
        JdbcSupport.setString(ps, i + 4, entidad.getDescripcion());
        JdbcSupport.setString(ps, i + 5, entidad.getMedidaAcordada());
        JdbcSupport.setFechaHora(ps, i + 6, entidad.getFechaCompromiso());
        JdbcSupport.setString(ps, i + 7, entidad.getEstado());
        JdbcSupport.setString(ps, i + 8, entidad.getObservaciones());
        JdbcSupport.setFechaHora(ps, i + 9, entidad.getFechaHoraRegistro());
        JdbcSupport.setUuid(ps, i + 10, entidad.getIdUsuario());
        JdbcSupport.setFechaHora(ps, i + 11, entidad.getFechaCierre());
        JdbcSupport.setString(ps, i + 12, entidad.getResultadoSolucion());
        JdbcSupport.setFechaHora(ps, i + 13,
                entidad.getFechaCreacion() == null ? LocalDateTime.now() : entidad.getFechaCreacion());
    }

    @Override
    protected String columnasInsercion() {
        return "id_incidencia, id_vale, id_recepcion, tipo, cantidad_afectada, descripcion, "
                + "medida_acordada, fecha_compromiso, estado, observaciones, fecha_hora_registro, "
                + "id_usuario, fecha_cierre, resultado_solucion, creado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_vale = ?, id_recepcion = ?, tipo = ?, cantidad_afectada = ?, descripcion = ?, "
                + "medida_acordada = ?, fecha_compromiso = ?, estado = ?, observaciones = ?, "
                + "fecha_hora_registro = ?, id_usuario = ?, fecha_cierre = ?, resultado_solucion = ?, "
                + "creado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 14;
    }

    @Override
    public List<Incidencia> findByRecepcion(UUID idRecepcion) {
        if (idRecepcion == null) {
            return List.of();
        }
        return listarConFiltro("id_recepcion = ?", List.of(idRecepcion), "creado_en");
    }

    @Override
    public List<Incidencia> findByVale(UUID idVale) {
        if (idVale == null) {
            return List.of();
        }
        return listarConFiltro("id_vale = ?", List.of(idVale), "creado_en");
    }

    @Override
    public List<Incidencia> findByEstado(String estado) {
        if (estado == null) {
            return List.of();
        }
        return listarConFiltro("estado = ?", List.of(estado), "creado_en");
    }
}
