package cu.pican.repository.jdbc;

import cu.pican.model.Recepcion;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcRepositorySupport;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.RecepcionRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RecepcionRepositoryPostgres extends JdbcRepositorySupport<Recepcion>
        implements RecepcionRepository {

    public RecepcionRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "recepcion";
    }

    @Override
    protected String columnaId() {
        return "id_recepcion";
    }

    @Override
    protected UUID idDe(Recepcion entidad) {
        return entidad.getIdRecepcion();
    }

    @Override
    protected Recepcion mapear(ResultSet rs) throws SQLException {
        Recepcion recepcion = new Recepcion();
        recepcion.setIdRecepcion(JdbcSupport.uuid(rs, "id_recepcion"));
        recepcion.setIdVale(JdbcSupport.uuid(rs, "id_vale"));
        recepcion.setFechaHoraRecepcion(JdbcSupport.fechaHora(rs, "fecha_hora_recepcion"));
        recepcion.setCantidadRecibida(JdbcSupport.decimal(rs, "cantidad_recibida"));
        recepcion.setUnidadMedida(rs.getString("unidad_medida"));
        recepcion.setResultado(rs.getString("resultado"));
        recepcion.setObservaciones(rs.getString("observaciones"));
        recepcion.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        recepcion.setFechaCreacion(JdbcSupport.fechaHora(rs, "creado_en"));
        recepcion.setVersion(JdbcSupport.largo(rs, "version"));
        return recepcion;
    }

    @Override
    public Recepcion save(Recepcion entidad) {
        if (entidad != null && entidad.getIdVale() != null) {
            Optional<Recepcion> existente = findByVale(entidad.getIdVale());
            if (existente.isPresent() && !Objects.equals(existente.get().getIdRecepcion(), entidad.getIdRecepcion())) {
                throw new IllegalArgumentException("El vale ya tiene una recepción registrada.");
            }
        }
        return super.save(entidad);
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, Recepcion entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdRecepcion());
        escribirDatos(ps, entidad, 2);
        ps.setLong(10, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, Recepcion entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, Recepcion entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdVale());
        JdbcSupport.setFechaHora(ps, i + 1, entidad.getFechaHoraRecepcion());
        JdbcSupport.setDecimal(ps, i + 2, entidad.getCantidadRecibida());
        JdbcSupport.setString(ps, i + 3, entidad.getUnidadMedida());
        JdbcSupport.setString(ps, i + 4, entidad.getResultado());
        JdbcSupport.setString(ps, i + 5, entidad.getObservaciones());
        JdbcSupport.setUuid(ps, i + 6, entidad.getIdUsuario());
        JdbcSupport.setFechaHora(ps, i + 7,
                entidad.getFechaCreacion() == null ? LocalDateTime.now() : entidad.getFechaCreacion());
    }

    @Override
    protected String columnasInsercion() {
        return "id_recepcion, id_vale, fecha_hora_recepcion, cantidad_recibida, unidad_medida, "
                + "resultado, observaciones, id_usuario, creado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_vale = ?, fecha_hora_recepcion = ?, cantidad_recibida = ?, unidad_medida = ?, "
                + "resultado = ?, observaciones = ?, id_usuario = ?, creado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 8;
    }

    @Override
    public Optional<Recepcion> findByVale(UUID idVale) {
        if (idVale == null) {
            return Optional.empty();
        }
        return buscarUno("id_vale = ?", idVale);
    }

    @Override
    public List<Recepcion> findByPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        List<Object> parametros = new ArrayList<>();
        StringBuilder where = new StringBuilder("1 = 1");
        if (desde != null) {
            where.append(" AND fecha_hora_recepcion >= ?");
            parametros.add(desde);
        }
        if (hasta != null) {
            where.append(" AND fecha_hora_recepcion <= ?");
            parametros.add(hasta);
        }
        return listarConFiltro(where.toString(), parametros, "fecha_hora_recepcion, creado_en");
    }
}
