package cu.pican.model.repository.jdbc;

import cu.pican.model.DestinatarioInstitucional;
import cu.pican.model.EntregaInforme;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcRepositorySupport;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.repository.EntregaInformeRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class EntregaInformeRepositoryPostgres extends JdbcRepositorySupport<EntregaInforme>
        implements EntregaInformeRepository {

    public EntregaInformeRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "entrega_informe";
    }

    @Override
    protected String columnaId() {
        return "id_entrega";
    }

    @Override
    protected UUID idDe(EntregaInforme entidad) {
        return entidad.getIdEntrega();
    }

    @Override
    protected EntregaInforme mapear(ResultSet rs) throws SQLException {
        return new EntregaInforme(
                JdbcSupport.uuid(rs, "id_entrega"),
                JdbcSupport.uuid(rs, "id_informe"),
                DestinatarioInstitucional.valueOf(rs.getString("destinatario")),
                JdbcSupport.fecha(rs, "fecha_entrega"),
                rs.getString("responsable_registro"),
                rs.getString("observaciones"),
                JdbcSupport.fechaHora(rs, "fecha_registro"));
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, EntregaInforme entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdEntrega());
        escribirDatos(ps, entidad, 2);
        ps.setLong(8, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, EntregaInforme entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, EntregaInforme entidad, int i) throws SQLException {
        JdbcSupport.setUuid(ps, i, entidad.getIdInforme());
        JdbcSupport.setString(ps, i + 1, entidad.getDestinatario().name());
        JdbcSupport.setFecha(ps, i + 2, entidad.getFechaEntrega());
        JdbcSupport.setString(ps, i + 3, entidad.getResponsableRegistro());
        JdbcSupport.setString(ps, i + 4, entidad.getObservaciones());
        JdbcSupport.setFechaHora(ps, i + 5, entidad.getFechaRegistro());
    }

    @Override
    protected String columnasInsercion() {
        return "id_entrega, id_informe, destinatario, fecha_entrega, responsable_registro, "
                + "observaciones, fecha_registro, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "id_informe = ?, destinatario = ?, fecha_entrega = ?, responsable_registro = ?, "
                + "observaciones = ?, fecha_registro = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 6;
    }

    @Override
    public List<EntregaInforme> findByIdInforme(UUID idInforme) {
        if (idInforme == null) {
            return List.of();
        }
        return listarConFiltro("id_informe = ?", List.of(idInforme), "fecha_entrega, creado_en");
    }

    @Override
    public boolean existsByIdInformeAndDestinatario(UUID idInforme, DestinatarioInstitucional destinatario) {
        if (idInforme == null || destinatario == null) {
            return false;
        }
        String sql = "SELECT 1 FROM entrega_informe WHERE eliminado_logico = FALSE "
                + "AND id_informe = ? AND destinatario = ? LIMIT 1";
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            JdbcSupport.setUuid(ps, 1, idInforme);
            ps.setString(2, destinatario.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new cu.pican.model.persistence.PersistenciaException(
                    "No se pudo comprobar la entrega del informe: "
                            + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            if (cerrar) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // No se oculta el error original.
                }
            }
        }
    }
}
