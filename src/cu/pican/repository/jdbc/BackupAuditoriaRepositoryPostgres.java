package cu.pican.repository.jdbc;

import cu.pican.model.BackupAuditoria;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcSupport;
import cu.pican.persistence.PersistenciaException;
import cu.pican.repository.BackupAuditoriaRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Historial CU-19 persistido en la tabla creada por V3. */
public final class BackupAuditoriaRepositoryPostgres implements BackupAuditoriaRepository {
    private static final String INSERT =
            "INSERT INTO pican_backup_auditoria (id_auditoria, id_usuario, nombre_usuario, operacion, "
                    + "archivo, fecha_hora, resultado, motivo_fallo, tamano_bytes, sha256, "
                    + "version_aplicacion, version_esquema, id_instalacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String LISTAR =
            "SELECT id_auditoria, id_usuario, nombre_usuario, operacion, archivo, fecha_hora, "
                    + "resultado, motivo_fallo, tamano_bytes, sha256, version_aplicacion, "
                    + "version_esquema, id_instalacion FROM pican_backup_auditoria "
                    + "ORDER BY fecha_hora DESC, id_auditoria DESC";

    private final JdbcConnectionManager conexiones;

    public BackupAuditoriaRepositoryPostgres(JdbcConnectionManager conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
    }

    @Override
    public void guardar(BackupAuditoria evento) {
        if (evento == null) return;
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(INSERT)) {
            ps.setObject(1, evento.getIdAuditoria(), Types.OTHER);
            if (evento.getIdUsuario() == null) ps.setNull(2, Types.OTHER);
            else ps.setObject(2, evento.getIdUsuario(), Types.OTHER);
            ps.setString(3, evento.getNombreUsuario());
            ps.setString(4, evento.getOperacion());
            ps.setString(5, evento.getArchivo());
            ps.setTimestamp(6, Timestamp.valueOf(evento.getFechaHora()));
            ps.setString(7, evento.getResultado());
            ps.setString(8, evento.getMotivoFallo());
            if (evento.getTamanoBytes() == null) ps.setNull(9, Types.BIGINT);
            else ps.setLong(9, evento.getTamanoBytes());
            ps.setString(10, evento.getSha256());
            ps.setString(11, evento.getVersionAplicacion());
            ps.setString(12, evento.getVersionEsquema());
            ps.setString(13, evento.getIdInstalacion());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo registrar la auditoría CU-19: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally { cerrar(c, cerrar); }
    }

    @Override
    public List<BackupAuditoria> listar() {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        List<BackupAuditoria> resultado = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(LISTAR); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                resultado.add(new BackupAuditoria(
                        (UUID) rs.getObject(1), (UUID) rs.getObject(2),
                        rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6).toLocalDateTime(), rs.getString(7), rs.getString(8),
                        (Long) rs.getObject(9), rs.getString(10), rs.getString(11),
                        rs.getString(12), rs.getString(13)));
            }
            return resultado;
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo consultar la auditoría CU-19: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally { cerrar(c, cerrar); }
    }

    private void cerrar(Connection c, boolean cerrar) {
        if (cerrar) { try { c.close(); } catch (SQLException ignored) {} }
    }
}
