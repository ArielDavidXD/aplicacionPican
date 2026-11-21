package cu.pican.repository.jdbc;

import cu.pican.model.ParteUnidadAsociada;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.ParteUnidadAsociadaRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ParteUnidadAsociadaRepositoryPostgres extends JdbcRepositorySupport<ParteUnidadAsociada>
        implements ParteUnidadAsociadaRepository {

    public ParteUnidadAsociadaRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "parte_unidad_asociada";
    }

    @Override
    protected String columnaId() {
        return "id_parte_asociado";
    }

    @Override
    protected UUID idDe(ParteUnidadAsociada entidad) {
        return entidad.getIdParteAsociado();
    }

    @Override
    protected ParteUnidadAsociada mapear(ResultSet rs) throws SQLException {
        ParteUnidadAsociada parte = new ParteUnidadAsociada();
        parte.setIdParteAsociado(JdbcSupport.uuid(rs, "id_parte_asociado"));
        parte.setIdUnidad(JdbcSupport.uuid(rs, "id_unidad"));
        parte.setFechaProduccion(JdbcSupport.fecha(rs, "fecha_produccion"));
        parte.setReferenciaPapel(rs.getString("referencia_papel"));
        parte.setObservaciones(rs.getString("observaciones"));
        parte.setEstado(rs.getString("estado"));
        parte.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        parte.setCreadoEn(JdbcSupport.fechaHora(rs, "creado_en"));
        parte.setVersion(JdbcSupport.largo(rs, "version"));
        return parte;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, ParteUnidadAsociada entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdParteAsociado());
        escribirDatos(ps, entidad, 2);
        ps.setLong(9, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, ParteUnidadAsociada entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, ParteUnidadAsociada entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdUnidad());
        JdbcSupport.setFecha(ps, i + 1, entidad.getFechaProduccion());
        JdbcSupport.setString(ps, i + 2, entidad.getReferenciaPapel());
        JdbcSupport.setString(ps, i + 3, entidad.getObservaciones());
        JdbcSupport.setString(ps, i + 4, entidad.getEstado());
        JdbcSupport.setUuid(ps, i + 5, entidad.getIdUsuario());
        JdbcSupport.setFechaHora(ps, i + 6,
                entidad.getCreadoEn() == null ? LocalDateTime.now() : entidad.getCreadoEn());
    }

    @Override
    protected String columnasInsercion() {
        return "id_parte_asociado, id_unidad, fecha_produccion, referencia_papel, observaciones, "
                + "estado, id_usuario, creado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_unidad = ?, fecha_produccion = ?, referencia_papel = ?, observaciones = ?, "
                + "estado = ?, id_usuario = ?, creado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 7;
    }

    @Override
    public List<ParteUnidadAsociada> findByUnidad(UUID idUnidad) {
        return listarConFiltro("id_unidad = ?", List.of(idUnidad), "fecha_produccion, creado_en");
    }

    @Override
    public List<ParteUnidadAsociada> findByFecha(LocalDate fechaProduccion) {
        return listarConFiltro("fecha_produccion = ?", List.of(fechaProduccion), "creado_en");
    }
}
