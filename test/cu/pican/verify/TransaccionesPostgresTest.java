package cu.pican.verify;

import cu.pican.model.Nave;
import cu.pican.model.UEB;
import cu.pican.persistence.DatabaseConfig;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcSupport;
import cu.pican.persistence.JdbcTransactionManager;
import cu.pican.persistence.MigrationRunner;
import cu.pican.persistence.PersistenciaException;
import cu.pican.persistence.TransactionManager;
import cu.pican.repository.jdbc.NaveRepositoryPostgres;
import cu.pican.repository.jdbc.UEBRepositoryPostgres;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fase 1 · Etapa 4.14 · Pruebas transaccionales sobre PostgreSQL real.
 *
 * Ejecutable independiente (sin JUnit): usa una base EXCLUSIVA de pruebas.
 * Los datos se identifican con el prefijo TXTEST- y se eliminan al inicio y al final.
 *
 * Uso:
 *   java -Dpican.test.url=jdbc:postgresql://127.0.0.1:5432/pican_test \
 *        -Dpican.test.user=... -Dpican.test.password=... \
 *        -Dpican.test.migrations=sql/migrations \
 *        cu.pican.verify.TransaccionesPostgresTest
 */
public final class TransaccionesPostgresTest {

    private static final String PREFIJO = "TXTEST-";

    private final List<Resultado> resultados = new ArrayList<>();
    private DatabaseConfig config;
    private JdbcConnectionManager conexiones;
    private TransactionManager transacciones;
    private UEBRepositoryPostgres uebs;
    private NaveRepositoryPostgres naves;

    public static void main(String[] args) {
        cu.pican.app.AutorizacionDemo.instalarTodosLosRoles();
        int codigoSalida;
        try {
            codigoSalida = new TransaccionesPostgresTest().ejecutarTodo();
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            codigoSalida = 2;
        }
        System.exit(codigoSalida);
    }

    private int ejecutarTodo() throws Exception {
        prepararConfiguracion();
        System.out.println("[PICAN] Base de pruebas: " + config.getBaseDatos()
                + " | URL: " + config.getUrl() + " | usuario: " + config.getUsuario());

        conexiones = new JdbcConnectionManager(config);
        conexiones.verificarConexion();

        String directorioMigraciones = propiedad("pican.test.migrations", "sql/migrations");
        List<String> migraciones = new MigrationRunner(conexiones, Paths.get(directorioMigraciones)).ejecutar();
        for (String migracion : migraciones) {
            System.out.println("[MIGRACION] " + migracion);
        }

        transacciones = new JdbcTransactionManager(conexiones);
        uebs = new UEBRepositoryPostgres(conexiones);
        naves = new NaveRepositoryPostgres(conexiones);

        try {
            limpiarTodo();

            verificar("TX-01 · Transacción exitosa guarda los cambios",
                    "La UEB insertada dentro de la transacción debe existir tras el commit",
                    this::tx01Commit);

            verificar("TX-02 · Error provoca rollback",
                    "La UEB insertada no debe existir tras una excepción de negocio",
                    this::tx02Rollback);

            verificar("TX-03 · Inserción seguida de excepción sin datos parciales",
                    "No deben quedar UEB ni nave de la transacción fallida",
                    this::tx03SinDatosParciales);

            verificar("TX-04 · Dos operaciones relacionadas se confirman juntas",
                    "UEB y nave deben existir tras el commit",
                    this::tx04CommitConjunto);

            verificar("TX-05 · Fallo en la segunda operación revierte la primera",
                    "La UEB no debe existir cuando la nave falla por FK inválida",
                    this::tx05RollbackSegundaOperacion);

            verificar("TX-06 · Conexiones se liberan correctamente",
                    "Sin conexión enlazada y sin crecimiento de backend tras 10 transacciones",
                    this::tx06LiberacionConexiones);

            verificar("TX-07 · Transacción fallida no deja conexiones inutilizables",
                    "SELECT 1 posterior funciona y el backend vuelve al nivel anterior",
                    this::tx07ConexionUtilTrasFallo);

            verificar("TX-08 · Operación posterior funciona tras rollback",
                    "Se puede guardar y consultar una UEB después de un rollback",
                    this::tx08OperacionPosterior);

            verificar("TX-09 · Repositorios usan la conexión transaccional",
                    "La fila es visible en la conexión activa y no en otra hasta el commit",
                    this::tx09ConexionTransaccional);

            verificar("TX-10 · Transacciones anidadas comparten conexión",
                    "La transacción interna reutiliza la conexión y participa del commit/rollback",
                    this::tx10Anidadas);
        } finally {
            limpiarTodo();
        }

        imprimirInforme();
        escribirInforme();
        long fallos = resultados.stream().filter(r -> !"APROBADA".equals(r.estado)).count();
        System.out.println("[RESUMEN] " + (resultados.size() - fallos) + "/" + resultados.size()
                + " pruebas aprobadas");
        return fallos == 0 ? 0 : 1;
    }

    // -------------------------------------------------------------------------
    // Configuración y seguridad de la base de pruebas
    // -------------------------------------------------------------------------

    private void prepararConfiguracion() {
        String url = requerir("pican.test.url",
                "Falta -Dpican.test.url=jdbc:postgresql://host:puerto/base_de_pruebas");
        String usuario = requerir("pican.test.user", "Falta -Dpican.test.user=...");
        String contrasena = propiedad("pican.test.password", "");

        String nombreBase = nombreBase(url);
        System.setProperty("pican.db.name", nombreBase);
        String nombre = nombreBase.toLowerCase(Locale.ROOT);
        if (!nombre.contains("test") && !nombre.contains("prueba")) {
            throw new PersistenciaException("La base indicada ('" + nombreBase
                    + "') no parece exclusiva de pruebas. Se exige que el nombre contenga 'test' o 'prueba'.");
        }

        // DatabaseConfig prioriza propiedades de sistema, de modo que la configuración
        // de pruebas nunca depende de un config/pican.properties con credenciales reales.
        System.setProperty("pican.persistence", DatabaseConfig.POSTGRESQL);
        System.setProperty("pican.db.url", url);
        System.setProperty("pican.db.user", usuario);
        System.setProperty("pican.db.password", contrasena);
        System.setProperty("pican.db.migrate", "true");
        config = DatabaseConfig.cargar();
    }

    private static String nombreBase(String url) {
        String limpia = url.split("\\?", 2)[0];
        return limpia.substring(limpia.lastIndexOf('/') + 1);
    }

    private static String propiedad(String clave, String defecto) {
        String valor = System.getProperty(clave);
        return valor == null || valor.trim().isEmpty() ? defecto : valor;
    }

    private static String requerir(String clave, String mensaje) {
        String valor = propiedad(clave, null);
        if (valor == null) {
            throw new PersistenciaException(mensaje);
        }
        return valor;
    }

    // -------------------------------------------------------------------------
    // Casos de prueba
    // -------------------------------------------------------------------------

    private void tx01Commit() {
        UUID id = UUID.randomUUID();
        transacciones.ejecutar(() -> {
            uebs.save(ueb(id, "TX01"));
            return null;
        });
        check(contarUeb(id) == 1, "Se esperaba 1 UEB tras el commit");
    }

    private void tx02Rollback() {
        UUID id = UUID.randomUUID();
        try {
            transacciones.ejecutar(() -> {
                uebs.save(ueb(id, "TX02"));
                throw new IllegalStateException("fallo de negocio simulado TX02");
            });
            throw new AssertionError("Se esperaba la excepción de negocio");
        } catch (IllegalStateException esperada) {
            check("fallo de negocio simulado TX02".equals(esperada.getMessage()),
                    "Se propagó una excepción distinta: " + esperada.getMessage());
        }
        check(contarUeb(id) == 0, "La UEB no debía persistir tras el rollback");
    }

    private void tx03SinDatosParciales() {
        UUID idUeb = UUID.randomUUID();
        UUID idNave = UUID.randomUUID();
        try {
            transacciones.ejecutar(() -> {
                uebs.save(ueb(idUeb, "TX03"));
                naves.save(nave(idNave, idUeb, "TX03"));
                throw new IllegalStateException("fallo tras dos inserciones TX03");
            });
            throw new AssertionError("Se esperaba la excepción de negocio");
        } catch (IllegalStateException esperada) {
            // esperado
        }
        check(contarUeb(idUeb) == 0, "Quedó una UEB parcial");
        check(contarNave(idNave) == 0, "Quedó una nave parcial");
    }

    private void tx04CommitConjunto() {
        UUID idUeb = UUID.randomUUID();
        UUID idNave = UUID.randomUUID();
        transacciones.ejecutar(() -> {
            uebs.save(ueb(idUeb, "TX04"));
            naves.save(nave(idNave, idUeb, "TX04"));
            return null;
        });
        check(contarUeb(idUeb) == 1, "Falta la UEB confirmada");
        check(contarNave(idNave) == 1, "Falta la nave confirmada");
    }

    private void tx05RollbackSegundaOperacion() {
        UUID idUeb = UUID.randomUUID();
        UUID idNave = UUID.randomUUID();
        UUID uebInexistente = UUID.randomUUID();
        try {
            transacciones.ejecutar(() -> {
                uebs.save(ueb(idUeb, "TX05"));
                naves.save(nave(idNave, uebInexistente, "TX05"));
                return null;
            });
            throw new AssertionError("Se esperaba fallo de integridad referencial");
        } catch (PersistenciaException esperada) {
            check(esperada.getMessage() != null && esperada.getMessage().toLowerCase(Locale.ROOT).contains("guardar nave"),
                    "El mensaje de error no identifica la nave: " + esperada.getMessage());
        }
        check(contarUeb(idUeb) == 0, "La UEB debía revertirse con el fallo de la nave");
        check(contarNave(idNave) == 0, "La nave no debió insertarse");
    }

    private void tx06LiberacionConexiones() throws SQLException {
        check(!JdbcSupport.hayConexionActiva(), "Quedó una conexión enlazada al iniciar TX-06");
        int antes = contarBackends();
        for (int i = 0; i < 10; i++) {
            final int indice = i;
            final UUID id = UUID.randomUUID();
            transacciones.ejecutar(() -> {
                uebs.save(ueb(id, "TX06-" + indice));
                return null;
            });
        }
        check(!JdbcSupport.hayConexionActiva(), "Quedó una conexión enlazada tras las transacciones");
        int despues = contarBackends();
        check(despues <= antes + 1,
                "Los backends crecieron de " + antes + " a " + despues + " tras 10 transacciones");
    }

    private void tx07ConexionUtilTrasFallo() throws SQLException {
        int antes = contarBackends();
        try {
            transacciones.ejecutar(() -> {
                uebs.save(ueb(UUID.randomUUID(), "TX07"));
                throw new IllegalStateException("fallo TX07");
            });
            throw new AssertionError("Se esperaba fallo TX07");
        } catch (IllegalStateException esperada) {
            // esperado
        }
        check(!JdbcSupport.hayConexionActiva(), "La conexión fallida sigue enlazada al hilo");
        try (Connection c = DriverManager.getConnection(config.getUrl(), config.getUsuario(), config.getContrasena());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            check(rs.next() && rs.getInt(1) == 1, "SELECT 1 no devolvió 1 tras el rollback");
        }
        int despues = contarBackends();
        check(despues <= antes + 1,
                "Los backends no volvieron a su nivel tras el fallo: " + antes + " -> " + despues);
    }

    private void tx08OperacionPosterior() {
        UUID idFallo = UUID.randomUUID();
        try {
            transacciones.ejecutar(() -> {
                uebs.save(ueb(idFallo, "TX08-FALLO"));
                throw new IllegalStateException("fallo TX08");
            });
            throw new AssertionError("Se esperaba fallo TX08");
        } catch (IllegalStateException esperada) {
            // esperado
        }
        UUID idPosterior = UUID.randomUUID();
        uebs.save(ueb(idPosterior, "TX08-POSTERIOR"));
        check(contarUeb(idFallo) == 0, "La UEB fallida no debe existir");
        check(contarUeb(idPosterior) == 1, "La operación posterior debió funcionar");
    }

    private void tx09ConexionTransaccional() {
        UUID id = UUID.randomUUID();
        Connection[] enlazada = new Connection[1];
        transacciones.ejecutar(() -> {
            enlazada[0] = JdbcSupport.conexionActiva();
            check(enlazada[0] != null, "No hay conexión activa dentro de la transacción");
            uebs.save(ueb(id, "TX09"));
            check(contarEn(enlazada[0], id) == 1,
                    "El repositorio no escribió en la conexión de la transacción");
            try (Connection otra = DriverManager.getConnection(
                    config.getUrl(), config.getUsuario(), config.getContrasena())) {
                check(contarEn(otra, id) == 0,
                        "Otra conexión vio los datos antes del commit");
            } catch (SQLException ex) {
                throw new PersistenciaException("No se pudo abrir conexión observadora: "
                        + JdbcConnectionManager.describirError(ex), ex);
            }
            return null;
        });
        check(contarUeb(id) == 1, "La fila no quedó persistida tras el commit");
    }

    private void tx10Anidadas() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        UUID idD = UUID.randomUUID();
        Connection[] externa = new Connection[1];
        Connection[] interna = new Connection[1];

        transacciones.ejecutar(() -> {
            externa[0] = JdbcSupport.conexionActiva();
            uebs.save(ueb(idA, "TX10-A"));
            transacciones.ejecutar(() -> {
                interna[0] = JdbcSupport.conexionActiva();
                uebs.save(ueb(idB, "TX10-B"));
                return null;
            });
            return null;
        });
        check(externa[0] == interna[0], "La transacción anidada abrió otra conexión");
        check(contarUeb(idA) == 1 && contarUeb(idB) == 1,
                "El commit externo debió confirmar ambas inserciones");

        try {
            transacciones.ejecutar(() -> {
                uebs.save(ueb(idC, "TX10-C"));
                transacciones.ejecutar(() -> {
                    uebs.save(ueb(idD, "TX10-D"));
                    throw new IllegalStateException("fallo anidado TX10");
                });
                return null;
            });
            throw new AssertionError("Se esperaba propagación del fallo anidado");
        } catch (IllegalStateException esperada) {
            // esperado: la transacción externa debe revertir
        }
        check(contarUeb(idC) == 0 && contarUeb(idD) == 0,
                "El fallo anidado debió revertir todas las inserciones");
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private static UEB ueb(UUID id, String sufijo) {
        return new UEB(id, "UEB prueba transaccional " + sufijo, PREFIJO + sufijo + "-" + id, true);
    }

    private static Nave nave(UUID id, UUID idUeb, String sufijo) {
        return new Nave(id, idUeb, "Nave prueba transaccional " + sufijo, PREFIJO + sufijo + "-" + id, true);
    }

    private int contarUeb(UUID id) {
        return contar("ueb", "id_ueb", id);
    }

    private int contarNave(UUID id) {
        return contar("nave", "id_nave", id);
    }

    private int contar(String tabla, String columnaId, UUID id) {
        try (Connection c = DriverManager.getConnection(
                config.getUrl(), config.getUsuario(), config.getContrasena());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM " + tabla + " WHERE " + columnaId + " = ?")) {
            JdbcSupport.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo contar en " + tabla + ": "
                    + JdbcConnectionManager.describirError(ex), ex);
        }
    }

    private static int contarEn(Connection c, UUID id) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM ueb WHERE id_ueb = ?")) {
            JdbcSupport.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudo contar en la conexión activa: "
                    + JdbcConnectionManager.describirError(ex), ex);
        }
    }

    private int contarBackends() throws SQLException {
        try (Connection c = DriverManager.getConnection(
                config.getUrl(), config.getUsuario(), config.getContrasena());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private void limpiarTodo() {
        try (Connection c = DriverManager.getConnection(
                config.getUrl(), config.getUsuario(), config.getContrasena());
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM nave WHERE codigo LIKE '" + PREFIJO + "%'");
            st.executeUpdate("DELETE FROM ueb WHERE codigo LIKE '" + PREFIJO + "%'");
        } catch (SQLException ex) {
            throw new PersistenciaException("No se pudieron limpiar los datos de prueba: "
                    + JdbcConnectionManager.describirError(ex), ex);
        }
    }

    private void verificar(String nombre, String esperado, Prueba prueba) {
        String obtenido;
        String estado;
        try {
            prueba.ejecutar();
            estado = "APROBADA";
            obtenido = "Se cumplió el resultado esperado";
        } catch (Throwable fallo) {
            estado = "FALLIDA";
            obtenido = fallo.getClass().getSimpleName() + ": " + fallo.getMessage();
        }
        resultados.add(new Resultado(nombre, esperado, obtenido, estado));
        System.out.println("[" + estado + "] " + nombre + " -> " + obtenido);
    }

    private static void check(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    private void imprimirInforme() {
        System.out.println("=== INFORME DE PRUEBAS TRANSACCIONALES (Fase 1) ===");
        for (Resultado r : resultados) {
            System.out.println(r.estado + " | " + r.nombre);
            System.out.println("  Esperado: " + r.esperado);
            System.out.println("  Obtenido: " + r.obtenido);
        }
    }

    private void escribirInforme() {
        StringBuilder texto = new StringBuilder();
        texto.append("INFORME DE PRUEBAS TRANSACCIONALES · ETAPA 4.14 · FASE 1\n");
        texto.append("Base: ").append(config.getBaseDatos()).append("\n\n");
        for (Resultado r : resultados) {
            texto.append(r.estado).append(" | ").append(r.nombre).append('\n')
                    .append("  Esperado: ").append(r.esperado).append('\n')
                    .append("  Obtenido: ").append(r.obtenido).append('\n');
        }
        try {
            Path ruta = Paths.get("test-results", "RESULTADOS_TRANSACCIONES.txt");
            Files.createDirectories(ruta.getParent());
            Files.write(ruta, texto.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("[INFORME] " + ruta.toAbsolutePath());
        } catch (IOException ex) {
            System.out.println("[AVISO] No se pudo escribir el informe: " + ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface Prueba {
        void ejecutar() throws Exception;
    }

    private static final class Resultado {
        private final String nombre;
        private final String esperado;
        private final String obtenido;
        private final String estado;

        private Resultado(String nombre, String esperado, String obtenido, String estado) {
            this.nombre = nombre;
            this.esperado = esperado;
            this.obtenido = obtenido;
            this.estado = estado;
        }
    }
}
