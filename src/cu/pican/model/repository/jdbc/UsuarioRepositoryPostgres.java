package cu.pican.model.repository.jdbc;

import cu.pican.model.Rol;
import cu.pican.model.Usuario;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcRepositorySupport;
import cu.pican.model.persistence.JdbcSupport;
import cu.pican.model.persistence.PersistenciaException;
import cu.pican.model.repository.UsuarioRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UsuarioRepositoryPostgres extends JdbcRepositorySupport<Usuario> implements UsuarioRepository {

    private static final String[] ROLES = {
            Rol.ADMINISTRADOR, Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA
    };

    public UsuarioRepositoryPostgres(JdbcConnectionManager conexiones) {
        super(conexiones);
    }

    @Override
    protected String tabla() {
        return "usuario";
    }

    @Override
    protected String columnaId() {
        return "id_usuario";
    }

    @Override
    protected UUID idDe(Usuario entidad) {
        return entidad.getIdUsuario();
    }

    @Override
    protected Usuario mapear(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(JdbcSupport.uuid(rs, "id_usuario"));
        usuario.setNombreUsuario(rs.getString("nombre_usuario"));
        usuario.setNombreCompleto(rs.getString("nombre_completo"));
        usuario.setPasswordHash(rs.getString("password_hash"));
        usuario.setRol(rolDesdeId(JdbcSupport.uuid(rs, "id_rol")));
        usuario.setActivo(rs.getBoolean("activo"));
        usuario.setCambioContrasenaRequerido(rs.getBoolean("cambio_contrasena_requerido"));
        usuario.setFechaCreacion(JdbcSupport.fechaHora(rs, "creado_en"));
        return usuario;
    }

    @Override
    protected void escribirInsercion(PreparedStatement ps, Usuario entidad) throws SQLException {
        JdbcSupport.setUuid(ps, 1, entidad.getIdUsuario());
        escribirDatos(ps, entidad, 2);
        ps.setLong(10, 1L);
    }

    @Override
    protected void escribirActualizacion(PreparedStatement ps, Usuario entidad) throws SQLException {
        escribirDatos(ps, entidad, 1);
    }

    private void escribirDatos(PreparedStatement ps, Usuario entidad, int inicio) throws SQLException {
        if (entidad.getPasswordHash() == null || entidad.getRol() == null) {
            throw new PersistenciaException(
                    "El usuario " + entidad.getNombreUsuario()
                            + " no tiene hash de contraseña o rol; no se puede persistir.");
        }
        JdbcSupport.setString(ps, inicio, entidad.getNombreUsuario());
        JdbcSupport.setString(ps, inicio + 1, entidad.getNombreCompleto());
        JdbcSupport.setString(ps, inicio + 2, entidad.getPasswordHash());
        JdbcSupport.setUuid(ps, inicio + 3, entidad.getRol().getIdRol());
        JdbcSupport.setBoolean(ps, inicio + 4, entidad.isActivo());
        JdbcSupport.setBoolean(ps, inicio + 5, entidad.isCambioContrasenaRequerido());
        JdbcSupport.setFechaHora(ps, inicio + 6,
                entidad.getFechaCreacion() == null ? java.time.LocalDateTime.now() : entidad.getFechaCreacion());
        JdbcSupport.setFechaHora(ps, inicio + 7, java.time.LocalDateTime.now());
    }

    @Override
    protected String columnasInsercion() {
        return "id_usuario, nombre_usuario, nombre_completo, password_hash, id_rol, activo, "
                + "cambio_contrasena_requerido, creado_en, actualizado_en, version";
    }

    @Override
    protected String marcadoresInsercion() {
        return "?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
    }

    @Override
    protected String columnasActualizacion() {
        return "nombre_usuario = ?, nombre_completo = ?, password_hash = ?, id_rol = ?, activo = ?, "
                + "cambio_contrasena_requerido = ?, creado_en = ?, actualizado_en = ?, version = version + 1";
    }

    @Override
    protected int marcadoresActualizacion() {
        return 8;
    }

    @Override
    public Usuario save(Usuario entidad) {
        asegurarRol(entidad);
        return super.save(entidad);
    }

    @Override
    public Usuario update(Usuario entidad) {
        asegurarRol(entidad);
        return super.update(entidad);
    }

    private void asegurarRol(Usuario entidad) {
        if (entidad == null || entidad.getRol() == null) {
            throw new PersistenciaException("El usuario debe tener un rol previsto antes de guardarse.");
        }
        Rol rol = entidad.getRol();
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        String sql = "INSERT INTO rol (id_rol, nombre, descripcion) VALUES (?, ?, ?) "
                + "ON CONFLICT (id_rol) DO NOTHING";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            JdbcSupport.setUuid(ps, 1, rol.getIdRol());
            JdbcSupport.setString(ps, 2, rol.getNombre());
            JdbcSupport.setString(ps, 3, rol.getDescripcion());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo asegurar el rol del usuario: "
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

    @Override
    public Optional<Usuario> findByNombreUsuario(String nombreUsuario) {
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
            return Optional.empty();
        }
        return buscarUno("lower(btrim(nombre_usuario)) = lower(btrim(?))", nombreUsuario);
    }

    @Override
    public List<Usuario> findByActivo(boolean activo) {
        return listarConFiltro("activo = ?", List.of(activo), "creado_en");
    }

    @Override
    public boolean existsByNombreUsuario(String nombreUsuario) {
        return findByNombreUsuario(nombreUsuario).isPresent();
    }

    @Override
    public boolean estaAsociadoAOperaciones(UUID idUsuario) {
        if (idUsuario == null) {
            return false;
        }
        String sql = "SELECT EXISTS (SELECT 1 FROM parte_nave WHERE id_usuario = ?) OR "
                + "EXISTS (SELECT 1 FROM parte_unidad_asociada WHERE id_usuario = ?) OR "
                + "EXISTS (SELECT 1 FROM produccion_carne WHERE id_usuario = ?) OR "
                + "EXISTS (SELECT 1 FROM consolidado_ueb WHERE id_usuario = ?) OR "
                + "EXISTS (SELECT 1 FROM vale_entrega WHERE id_usuario = ? OR id_usuario_anulacion = ?) OR "
                + "EXISTS (SELECT 1 FROM recepcion WHERE id_usuario = ?) OR "
                + "EXISTS (SELECT 1 FROM incidencia WHERE id_usuario = ?) OR "
                + "EXISTS (SELECT 1 FROM informe WHERE id_usuario = ?)";
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= 9; i++) {
                JdbcSupport.setUuid(ps, i, idUsuario);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo comprobar las operaciones del usuario: "
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

    private Rol rolDesdeId(UUID idRol) {
        for (String nombre : ROLES) {
            Rol rol = Rol.predefinido(nombre);
            if (rol != null && rol.getIdRol().equals(idRol)) {
                return rol;
            }
        }
        throw new PersistenciaException("El usuario tiene un rol no previsto en CU-16: " + idRol);
    }
}
