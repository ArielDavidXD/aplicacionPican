package cu.pican.model.repository.jdbc;

import cu.pican.model.ProduccionCarne;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcRepositorySupport;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.repository.ProduccionCarneRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ProduccionCarneRepositoryPostgres extends JdbcRepositorySupport<ProduccionCarne>
        implements ProduccionCarneRepository {

    public ProduccionCarneRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "produccion_carne";
    }

    @Override
    protected String columnaId() {
        return "id_produccion";
    }

    @Override
    protected UUID idDe(ProduccionCarne entidad) {
        return entidad.getIdProduccion();
    }

    @Override
    protected ProduccionCarne mapear(ResultSet rs) throws SQLException {
        ProduccionCarne produccion = new ProduccionCarne();
        produccion.setIdProduccion(JdbcSupport.uuid(rs, "id_produccion"));
        produccion.setIdUEB(JdbcSupport.uuid(rs, "id_ueb"));
        produccion.setFechaProduccion(JdbcSupport.fecha(rs, "fecha_produccion"));
        produccion.setCantidadToneladas(JdbcSupport.decimal(rs, "cantidad_toneladas"));
        produccion.setObservaciones(rs.getString("observaciones"));
        produccion.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        produccion.setCreadoEn(JdbcSupport.fechaHora(rs, "creado_en"));
        produccion.setVersion(JdbcSupport.largo(rs, "version"));
        return produccion;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, ProduccionCarne entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdProduccion());
        escribirDatos(ps, entidad, 2);
        ps.setLong(8, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, ProduccionCarne entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, ProduccionCarne entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdUEB());
        JdbcSupport.setFecha(ps, i + 1, entidad.getFechaProduccion());
        JdbcSupport.setDecimal(ps, i + 2, entidad.getCantidadToneladas());
        JdbcSupport.setString(ps, i + 3, entidad.getObservaciones());
        JdbcSupport.setUuid(ps, i + 4, entidad.getIdUsuario());
        JdbcSupport.setFechaHora(ps, i + 5,
                entidad.getCreadoEn() == null ? LocalDateTime.now() : entidad.getCreadoEn());
    }

    @Override
    protected String columnasInsercion() {
        return "id_produccion, id_ueb, fecha_produccion, cantidad_toneladas, observaciones, "
                + "id_usuario, creado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_ueb = ?, fecha_produccion = ?, cantidad_toneladas = ?, observaciones = ?, "
                + "id_usuario = ?, creado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 6;
    }

    @Override
    public List<ProduccionCarne> findByUEB(UUID idUEB) {
        return listarConFiltro("id_ueb = ?", List.of(idUEB), "fecha_produccion, creado_en");
    }

    @Override
    public List<ProduccionCarne> findByFecha(LocalDate fechaProduccion) {
        return listarConFiltro("fecha_produccion = ?", List.of(fechaProduccion), "creado_en");
    }
}
