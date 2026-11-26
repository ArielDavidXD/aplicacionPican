package cu.pican.model.repository.jdbc;

import cu.pican.model.ControlRestauracion;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.persistence.PersistenciaException;
import cu.pican.model.repository.ControlRestauracionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Objects;

/** Persistencia del bloqueo SIN-13 en la tabla creada por V3. */
public final class ControlRestauracionRepositoryPostgres implements ControlRestauracionRepository {

    private static final String SELECT =
            "SELECT bloqueado, motivo, fecha_bloqueo, id_auditoria, id_instalacion "
                    + "FROM pican_restauracion_control WHERE id_control = 1";
    private static final String UPSERT =
            "INSERT INTO pican_restauracion_control (id_control, bloqueado, motivo, fecha_bloqueo, "
                    + "id_auditoria, id_instalacion) VALUES (1, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (id_control) DO UPDATE SET bloqueado = EXCLUDED.bloqueado, "
                    + "motivo = EXCLUDED.motivo, fecha_bloqueo = EXCLUDED.fecha_bloqueo, "
                    + "id_auditoria = EXCLUDED.id_auditoria, id_instalacion = EXCLUDED.id_instalacion";

    private final JdbcConnectionManager conexiones;

    public ControlRestauracionRepositoryPostgres(JdbcConnectionManager conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
    }

    @Override
    public ControlRestauracion obtener(String idInstalacion) {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(SELECT); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return ControlRestauracion.desbloqueado(idInstalacion);
            }
            Timestamp fecha = rs.getTimestamp(3);
            LocalDateTime fechaBloqueo = fecha == null ? null : fecha.toLocalDateTime();
            return new ControlRestauracion(rs.getBoolean(1), rs.getString(2), fechaBloqueo,
                    (java.util.UUID) rs.getObject(4), rs.getString(5));
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo consultar el control SIN-13: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c, cerrar);
        }
    }

    @Override
    public void guardar(ControlRestauracion control) {
        if (control == null) {
            return;
        }
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(UPSERT)) {
            ps.setBoolean(1, control.isBloqueado());
            ps.setString(2, control.getMotivo());
            if (control.getFechaBloqueo() == null) {
                ps.setNull(3, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(3, Timestamp.valueOf(control.getFechaBloqueo()));
            }
            if (control.getIdAuditoria() == null) {
                ps.setNull(4, Types.OTHER);
            } else {
                ps.setObject(4, control.getIdAuditoria(), Types.OTHER);
            }
            ps.setString(5, control.getIdInstalacion());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo guardar el control SIN-13: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c, cerrar);
        }
    }

    private void cerrar(Connection c, boolean cerrar) {
        if (cerrar) {
            try {
                c.close();
            } catch (SQLException ignored) {
                // No se oculta el error original.
            }
        }
    }
}
