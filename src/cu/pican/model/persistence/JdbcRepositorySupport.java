package cu.pican.model.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base reutilizable para repositorios JDBC. No contiene reglas de negocio: solo traduce entidades
 * a filas y participa en la transacción activa cuando existe. La conexión de una transacción
 * nunca es cerrada por un repositorio individual.
 */
public abstract class JdbcRepositorySupport<T> {

    protected final JdbcConnectionManager conexiones;

    protected JdbcRepositorySupport(JdbcConnectionManager conexiones) {
        this.conexiones = conexiones;
    }

    protected abstract String tabla();

    protected abstract UUID idDe(T entidad);

    protected abstract T mapear(ResultSet rs) throws SQLException;

    protected abstract void escribirInsercion(PreparedStatement ps, T entidad) throws SQLException;

    protected abstract void escribirActualizacion(PreparedStatement ps, T entidad) throws SQLException;

    protected String columnaId() {
        return "id";
    }

    protected String condicionAdicional() {
        return "eliminado_logico = FALSE";
    }

    protected Connection abrir() {
        return conexiones.obtenerConexion();
    }

    protected void cerrar(Connection c) {
        Connection activa = JdbcSupport.conexionActiva();
        if (c != null && activa != c) {
            try {
                c.close();
            } catch (SQLException ignored) {
                // No se oculta el resultado de la operación por un fallo al cerrar.
            }
        }
    }

    public List<T> findAll() {
        String sql = "SELECT * FROM " + tabla() + " WHERE " + condicionAdicional() + " ORDER BY creado_en, " + columnaId();
        Connection c = abrir();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<T> resultado = new ArrayList<>();
            while (rs.next()) {
                resultado.add(mapear(rs));
            }
            return resultado;
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo listar " + tabla() + ": "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c);
        }
    }

    public Optional<T> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM " + tabla() + " WHERE " + columnaId() + " = ? AND " + condicionAdicional();
        Connection c = abrir();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            JdbcSupport.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo consultar " + tabla() + " por UUID: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c);
        }
    }

    public T save(T entidad) {
        if (entidad == null) {
            throw new PersistenciaException("La entidad no puede ser nula.");
        }
        UUID id = idDe(entidad);
        if (id == null) {
            throw new PersistenciaException("La entidad debe tener identificador UUID.");
        }
        Connection c = abrir();
        try {
            if (existe(c, id)) {
                actualizar(c, entidad);
            } else {
                insertar(c, entidad);
            }
            return entidad;
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo guardar " + tabla() + ": "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c);
        }
    }

    public T update(T entidad) {
        return save(entidad);
    }

    public void deleteById(UUID id) {
        if (id == null) {
            return;
        }
        String sql = "DELETE FROM " + tabla() + " WHERE " + columnaId() + " = ?";
        Connection c = abrir();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            JdbcSupport.setUuid(ps, 1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo eliminar de " + tabla() + ": "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c);
        }
    }

    protected boolean existe(Connection c, UUID id) throws SQLException {
        String sql = "SELECT 1 FROM " + tabla() + " WHERE " + columnaId() + " = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            JdbcSupport.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertar(Connection c, T entidad) throws SQLException {
        String sql = "INSERT INTO " + tabla() + " (" + columnasInsercion() + ") VALUES (" + marcadoresInsercion() + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            escribirInsercion(ps, entidad);
            ps.executeUpdate();
        }
    }

    private void actualizar(Connection c, T entidad) throws SQLException {
        String sql = "UPDATE " + tabla() + " SET " + columnasActualizacion() + " WHERE " + columnaId() + " = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            escribirActualizacion(ps, entidad);
            JdbcSupport.setUuid(ps, marcadoresActualizacion() + 1, idDe(entidad));
            ps.executeUpdate();
        }
    }

    protected abstract String columnasInsercion();

    protected abstract String marcadoresInsercion();

    protected abstract String columnasActualizacion();

    protected abstract int marcadoresActualizacion();

    protected List<T> listarConFiltro(String where, List<Object> parametros, String orderBy) {
        String sql = "SELECT * FROM " + tabla() + " WHERE " + condicionAdicional()
                + (where == null || where.isBlank() ? "" : " AND (" + where + ")")
                + (orderBy == null || orderBy.isBlank() ? "" : " ORDER BY " + orderBy);
        Connection c = abrir();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int indice = 1;
            for (Object valor : parametros) {
                asignarParametro(ps, indice++, valor);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<T> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
                return resultado;
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo consultar " + tabla() + ": "
                    + JdbcConnectionManager.describirError(ex), ex);
        } finally {
            cerrar(c);
        }
    }

    protected Optional<T> buscarUno(String where, Object parametro) {
        List<T> resultados = listarConFiltro(where, List.of(parametro), null);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    protected static void asignarParametro(PreparedStatement ps, int indice, Object valor) throws SQLException {
        if (valor instanceof UUID uuid) {
            JdbcSupport.setUuid(ps, indice, uuid);
        } else if (valor instanceof String texto) {
            JdbcSupport.setString(ps, indice, texto);
        } else if (valor instanceof Boolean booleano) {
            JdbcSupport.setBoolean(ps, indice, booleano);
        } else if (valor instanceof Integer entero) {
            JdbcSupport.setEntero(ps, indice, entero);
        } else if (valor instanceof Long largo) {
            JdbcSupport.setLong(ps, indice, largo);
        } else if (valor instanceof java.time.LocalDate fecha) {
            JdbcSupport.setFecha(ps, indice, fecha);
        } else if (valor instanceof java.time.LocalDateTime fechaHora) {
            JdbcSupport.setFechaHora(ps, indice, fechaHora);
        } else if (valor instanceof java.math.BigDecimal decimal) {
            JdbcSupport.setDecimal(ps, indice, decimal);
        } else {
            ps.setObject(indice, valor);
        }
    }
}
