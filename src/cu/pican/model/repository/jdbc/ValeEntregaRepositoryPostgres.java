package cu.pican.model.repository.jdbc;

import cu.pican.model.ValeEntrega;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcRepositorySupport;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.persistence.PersistenciaException;
import cu.pican.model.repository.ValeEntregaRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ValeEntregaRepositoryPostgres extends JdbcRepositorySupport<ValeEntrega>
        implements ValeEntregaRepository {

    public ValeEntregaRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "vale_entrega";
    }

    @Override
    protected String columnaId() {
        return "id_vale";
    }

    @Override
    protected UUID idDe(ValeEntrega entidad) {
        return entidad.getIdVale();
    }

    @Override
    protected ValeEntrega mapear(ResultSet rs) throws SQLException {
        ValeEntrega vale = new ValeEntrega();
        vale.setIdVale(JdbcSupport.uuid(rs, "id_vale"));
        vale.setNumeroVale(rs.getString("numero_vale"));
        vale.setIdUEB(JdbcSupport.uuid(rs, "id_ueb"));
        vale.setIdDestino(JdbcSupport.uuid(rs, "id_destino"));
        vale.setIdProducto(JdbcSupport.uuid(rs, "id_producto"));
        vale.setCantidadDespachada(JdbcSupport.decimal(rs, "cantidad_despachada"));
        vale.setUnidadMedida(rs.getString("unidad_medida"));
        vale.setFechaHoraEmision(JdbcSupport.fechaHora(rs, "fecha_hora_emision"));
        vale.setEstado(rs.getString("estado"));
        vale.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        vale.setFechaCreacion(JdbcSupport.fechaHora(rs, "creado_en"));
        vale.setMotivoAnulacion(rs.getString("motivo_anulacion"));
        vale.setFechaHoraAnulacion(JdbcSupport.fechaHora(rs, "fecha_hora_anulacion"));
        vale.setIdUsuarioAnulacion(JdbcSupport.uuid(rs, "id_usuario_anulacion"));
        vale.setVersion(JdbcSupport.largo(rs, "version"));
        return vale;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, ValeEntrega entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdVale());
        escribirDatos(ps, entidad, 2);
        ps.setLong(15, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, ValeEntrega entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, ValeEntrega entidad, int i) throws SQLException {
        JdbcSupport.setString(ps, i, entidad.getNumeroVale());
        JdbcSupport.setUuid(ps, i + 1, entidad.getIdUEB());
        JdbcSupport.setUuid(ps, i + 2, entidad.getIdDestino());
        JdbcSupport.setUuid(ps, i + 3, entidad.getIdProducto());
        JdbcSupport.setDecimal(ps, i + 4, entidad.getCantidadDespachada());
        JdbcSupport.setString(ps, i + 5, entidad.getUnidadMedida());
        JdbcSupport.setFechaHora(ps, i + 6, entidad.getFechaHoraEmision());
        JdbcSupport.setString(ps, i + 7, entidad.getEstado());
        JdbcSupport.setUuid(ps, i + 8, entidad.getIdUsuario());
        JdbcSupport.setString(ps, i + 9, entidad.getMotivoAnulacion());
        JdbcSupport.setFechaHora(ps, i + 10, entidad.getFechaHoraAnulacion());
        JdbcSupport.setUuid(ps, i + 11, entidad.getIdUsuarioAnulacion());
        JdbcSupport.setFechaHora(ps, i + 12,
                entidad.getFechaCreacion() == null ? LocalDateTime.now() : entidad.getFechaCreacion());
    }

    @Override
    protected String columnasInsercion() {
        return "id_vale, numero_vale, id_ueb, id_destino, id_producto, cantidad_despachada, "
                + "unidad_medida, fecha_hora_emision, estado, id_usuario, motivo_anulacion, "
                + "fecha_hora_anulacion, id_usuario_anulacion, creado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "numero_vale = ?, id_ueb = ?, id_destino = ?, id_producto = ?, cantidad_despachada = ?, "
                + "unidad_medida = ?, fecha_hora_emision = ?, estado = ?, id_usuario = ?, "
                + "motivo_anulacion = ?, fecha_hora_anulacion = ?, id_usuario_anulacion = ?, "
                + "creado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 13;
    }

    @Override
    public Optional<ValeEntrega> findByNumeroVale(String numeroVale) {
        if (numeroVale == null) {
            return Optional.empty();
        }
        return buscarUno("numero_vale = ?", numeroVale);
    }

    @Override
    public List<ValeEntrega> findByUEB(UUID idUEB) {
        return listarConFiltro("id_ueb = ?", List.of(idUEB), "creado_en");
    }

    @Override
    public List<ValeEntrega> findByDestino(UUID idDestino) {
        return listarConFiltro("id_destino = ?", List.of(idDestino), "creado_en");
    }

    @Override
    public List<ValeEntrega> findByProducto(UUID idProducto) {
        return listarConFiltro("id_producto = ?", List.of(idProducto), "creado_en");
    }

    @Override
    public List<ValeEntrega> findByPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        List<Object> parametros = new ArrayList<>();
        StringBuilder where = new StringBuilder("1 = 1");
        if (desde != null) {
            where.append(" AND fecha_hora_emision >= ?");
            parametros.add(desde);
        }
        if (hasta != null) {
            where.append(" AND fecha_hora_emision <= ?");
            parametros.add(hasta);
        }
        return listarConFiltro(where.toString(), parametros, "fecha_hora_emision, creado_en");
    }

    @Override
    public List<ValeEntrega> findByUEBOrdenadosPorFecha(UUID idUEB) {
        return listarConFiltro("id_ueb = ?", List.of(idUEB),
                "fecha_hora_emision ASC NULLS LAST, creado_en");
    }

    @Override
    public List<ValeEntrega> findByEstado(String estado) {
        if (estado == null) {
            return List.of();
        }
        return listarConFiltro("estado = ?", List.of(estado), "creado_en");
    }

    @Override
    public int siguienteSecuencia(UUID idUEB) {
        String sql = "INSERT INTO vale_secuencia (id_ueb, secuencia) VALUES (?, 1) "
                + "ON CONFLICT (id_ueb) DO UPDATE SET secuencia = vale_secuencia.secuencia + 1 "
                + "RETURNING secuencia";
        Connection c = abrir();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            JdbcSupport.setUuid(ps, 1, idUEB);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            throw new PersistenciaException("No se pudo obtener la secuencia de vales para la UEB.");
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo obtener la secuencia de vales: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c);
        }
    }
}
