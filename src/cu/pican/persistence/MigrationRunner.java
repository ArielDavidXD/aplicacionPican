package cu.pican.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Ejecuta migraciones SQL una sola vez y deja constancia clara de éxitos y fallos. */
public final class MigrationRunner {

    private static final String REGISTRO =
            "CREATE TABLE IF NOT EXISTS pican_schema_migrations ("
                    + "version VARCHAR(64) PRIMARY KEY, "
                    + "descripcion VARCHAR(255) NOT NULL, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "ejecutada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "exito BOOLEAN NOT NULL DEFAULT TRUE, "
                    + "error TEXT)";

    private final JdbcConnectionManager conexiones;
    private final Path directorio;

    public MigrationRunner(JdbcConnectionManager conexiones, Path directorio) {
        this.conexiones = conexiones;
        this.directorio = directorio;
    }

    public static Path directorioPorDefecto() {
        String configurado = System.getProperty("pican.migrations.dir");
        if (configurado != null && !configurado.trim().isEmpty()) {
            return Paths.get(configurado.trim());
        }
        String[] candidatos = {
                "aplicacionPican/sql/migrations",
                "sql/migrations",
                "../aplicacionPican/sql/migrations"
        };
        for (String candidato : candidatos) {
            Path ruta = Paths.get(candidato);
            if (Files.isDirectory(ruta)) {
                return ruta;
            }
        }
        return Paths.get(candidatos[0]);
    }

    public List<String> ejecutar() {
        if (!Files.isDirectory(directorio)) {
            throw new PersistenciaException("No existe el directorio de migraciones: " + directorio);
        }
        asegurarRegistro();
        List<Path> archivos = new ArrayList<>();
        try (var flujo = Files.list(directorio)) {
            flujo.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                    .forEach(archivos::add);
        } catch (IOException ex) {
            throw new PersistenciaException("No se pudieron enumerar las migraciones en " + directorio, ex);
        }
        archivos.sort(Comparator.comparing(p -> p.getFileName().toString()));
        if (archivos.isEmpty()) {
            throw new PersistenciaException("No se encontraron migraciones SQL en " + directorio);
        }

        List<String> ejecutadas = new ArrayList<>();
        for (Path archivo : archivos) {
            String nombre = archivo.getFileName().toString();
            String sql = leer(archivo);
            String checksum = checksum(sql);
            String version = version(nombre);
            String descripcion = descripcion(nombre);
            EstadoMigracion estado = consultarEstado(version);
            if (estado != null && estado.exito && estado.checksum.equals(checksum)) {
                ejecutadas.add(nombre + " (omitida: ya ejecutada)");
                continue;
            }
            if (estado != null && estado.exito) {
                throw new PersistenciaException("La migración " + nombre
                        + " ya está registrada con un checksum distinto. No se ha vuelto a ejecutar.");
            }
            ejecutarMigracion(nombre, version, descripcion, checksum, sql);
            ejecutadas.add(nombre + " (ejecutada)");
        }
        return ejecutadas;
    }

    private void asegurarRegistro() {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (Statement st = c.createStatement()) {
            st.execute(REGISTRO);
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo crear el registro de migraciones: "
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

    private EstadoMigracion consultarEstado(String version) {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        String sql = "SELECT checksum, exito FROM pican_schema_migrations WHERE version = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new EstadoMigracion(rs.getString("checksum"), rs.getBoolean("exito"));
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo consultar el estado de la migración " + version + ": "
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

    private void ejecutarMigracion(String nombre, String version, String descripcion,
                                   String checksum, String sql) {
        Connection c = conexiones.obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        boolean autocommitAnterior = true;
        boolean transaccionLocal = false;
        try {
            autocommitAnterior = c.getAutoCommit();
            if (autocommitAnterior) {
                c.setAutoCommit(false);
                transaccionLocal = true;
            }
            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
            registrar(version, descripcion, checksum, true, null, c);
            if (transaccionLocal) {
                c.commit();
            }
        } catch (SQLException ex) {
            revertir(c, transaccionLocal, ex);
            registrarFueraDeTransaccion(version, descripcion, checksum, ex);
            throw new PersistenciaException("La migración " + nombre + " falló: "
                    + JdbcConnectionManager.describirError(ex), ex);
        } catch (RuntimeException ex) {
            revertir(c, transaccionLocal, ex);
            registrarFueraDeTransaccion(version, descripcion, checksum, ex);
            throw ex;
        } finally {
            try {
                c.setAutoCommit(autocommitAnterior);
            } catch (SQLException ignored) {
                // La conexión se cierra si no es transaccional.
            }
            if (cerrar) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // No se oculta el error original.
                }
            }
        }
    }

    private void revertir(Connection c, boolean transaccionLocal, Exception causa) {
        if (!transaccionLocal) {
            return;
        }
        try {
            c.rollback();
            c.setAutoCommit(true);
        } catch (SQLException ex) {
            causa.addSuppressed(ex);
        }
    }

    private void registrar(String version, String descripcion, String checksum, boolean exito,
                           String error, Connection c) throws SQLException {
        String sql = "INSERT INTO pican_schema_migrations (version, descripcion, checksum, exito, error) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT (version) DO UPDATE SET descripcion = EXCLUDED.descripcion, "
                + "checksum = EXCLUDED.checksum, ejecutada_en = CURRENT_TIMESTAMP, "
                + "exito = EXCLUDED.exito, error = EXCLUDED.error";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, version);
            ps.setString(2, descripcion);
            ps.setString(3, checksum);
            ps.setBoolean(4, exito);
            JdbcSupport.setString(ps, 5, error);
            ps.executeUpdate();
        }
    }

    private void registrarFueraDeTransaccion(String version, String descripcion, String checksum, Exception causa) {
        String mensaje = causa.getMessage() == null ? causa.getClass().getSimpleName() : causa.getMessage();
        Connection conexion = JdbcSupport.conexionActiva();
        boolean cerrar = false;
        try {
            if (conexion == null) {
                conexion = conexiones.obtenerConexion();
                cerrar = conexion != JdbcSupport.conexionActiva();
            }
            if (!conexionUtilizable(conexion, causa)) {
                return;
            }
            registrar(version, descripcion, checksum, false, mensaje, conexion);
        } catch (SQLException ex) {
            causa.addSuppressed(new PersistenciaException(
                    "No se pudo registrar el fallo de la migración: "
                            + JdbcConnectionManager.describirError(ex), ex));
        } catch (RuntimeException ex) {
            causa.addSuppressed(ex);
        } finally {
            if (cerrar && conexion != null) {
                try {
                    conexion.close();
                } catch (SQLException ex) {
                    causa.addSuppressed(new PersistenciaException(
                            "No se pudo cerrar la conexión usada para registrar el fallo de la migración: "
                                    + JdbcConnectionManager.describirError(ex), ex));
                }
            }
        }
    }

    /** Comprueba que la conexión no pertenezca a una transacción abortada antes de reutilizarla. */
    private boolean conexionUtilizable(Connection conexion, Exception causa) {
        try (Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (SQLException ex) {
            causa.addSuppressed(new PersistenciaException(
                    "La conexión no estaba utilizable para registrar el fallo de la migración: "
                            + JdbcConnectionManager.describirError(ex), ex));
            return false;
        }
    }

    private String leer(Path archivo) {
        try {
            return new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new PersistenciaException("No se pudo leer la migración " + archivo, ex);
        }
    }

    private String checksum(String contenido) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(contenido.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new PersistenciaException("No se pudo calcular el checksum de la migración", ex);
        }
    }

    private String version(String nombre) {
        int guion = nombre.indexOf('_');
        return guion < 0 ? nombre : nombre.substring(0, guion);
    }

    private String descripcion(String nombre) {
        int dobleGuion = nombre.indexOf("__");
        String texto = dobleGuion < 0 ? nombre : nombre.substring(dobleGuion + 2);
        return texto.endsWith(".sql") ? texto.substring(0, texto.length() - 4) : texto;
    }

    private static final class EstadoMigracion {
        private final String checksum;
        private final boolean exito;

        private EstadoMigracion(String checksum, boolean exito) {
            this.checksum = checksum;
            this.exito = exito;
        }
    }
}
