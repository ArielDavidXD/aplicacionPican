package cu.pican.model.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Utilidades JDBC compartidas y ámbito de conexión transaccional por hilo. */
public final class JdbcSupport {

    private static final ThreadLocal<java.sql.Connection> CONEXION_ACTIVA = new ThreadLocal<>();

    private JdbcSupport() {
    }

    public static boolean hayConexionActiva() {
        return CONEXION_ACTIVA.get() != null;
    }

    public static java.sql.Connection conexionActiva() {
        return CONEXION_ACTIVA.get();
    }

    public static void enlazar(java.sql.Connection conexion) {
        CONEXION_ACTIVA.set(conexion);
    }

    public static void desenlazar() {
        CONEXION_ACTIVA.remove();
    }

    public static void setUuid(PreparedStatement ps, int indice, UUID valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.OTHER);
        } else {
            ps.setObject(indice, valor.toString(), Types.OTHER);
        }
    }

    public static void setString(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.VARCHAR);
        } else {
            ps.setString(indice, valor);
        }
    }

    public static void setTexto(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.VARCHAR);
        } else {
            ps.setString(indice, valor);
        }
    }

    public static void setBoolean(PreparedStatement ps, int indice, boolean valor) throws SQLException {
        ps.setBoolean(indice, valor);
    }

    public static void setEntero(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.INTEGER);
        } else {
            ps.setInt(indice, valor);
        }
    }

    public static void setLong(PreparedStatement ps, int indice, Long valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.BIGINT);
        } else {
            ps.setLong(indice, valor);
        }
    }

    public static void setDecimal(PreparedStatement ps, int indice, BigDecimal valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.NUMERIC);
        } else {
            ps.setBigDecimal(indice, valor);
        }
    }

    public static void setFecha(PreparedStatement ps, int indice, LocalDate valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.DATE);
        } else {
            ps.setDate(indice, java.sql.Date.valueOf(valor));
        }
    }

    public static void setFechaHora(PreparedStatement ps, int indice, LocalDateTime valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(indice, Timestamp.valueOf(valor));
        }
    }

    public static UUID uuid(ResultSet rs, String columna) throws SQLException {
        String valor = rs.getString(columna);
        return valor == null ? null : UUID.fromString(valor);
    }

    public static String texto(ResultSet rs, String columna) throws SQLException {
        return rs.getString(columna);
    }

    public static Boolean booleano(ResultSet rs, String columna) throws SQLException {
        boolean valor = rs.getBoolean(columna);
        return rs.wasNull() ? null : valor;
    }

    public static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    public static Long largo(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    public static BigDecimal decimal(ResultSet rs, String columna) throws SQLException {
        return rs.getBigDecimal(columna);
    }

    public static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        java.sql.Date valor = rs.getDate(columna);
        return valor == null ? null : valor.toLocalDate();
    }

    public static LocalDateTime fechaHora(ResultSet rs, String columna) throws SQLException {
        Timestamp valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
