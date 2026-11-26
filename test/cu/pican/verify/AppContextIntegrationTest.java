package cu.pican.verify;

import cu.pican.controller.app.AppContext;
import cu.pican.controller.app.DemoDataInitializer;
import cu.pican.model.UEB;
import cu.pican.model.persistence.NoOpTransactionManager;
import cu.pican.model.persistence.JdbcTransactionManager;
import cu.pican.model.persistence.PersistenciaException;
import cu.pican.model.repository.IndicadorHistoricoRepository;
import cu.pican.model.repository.UEBRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fases 2, 4 y 5 · Etapa 4.14 · Verificación de la integración real de AppContext.
 *
 * Comprueba MEMORY, POSTGRES, tipos de los 20 repositorios, ausencia de datos de
 * demostración en PostgreSQL, referencias históricas oficiales y persistencia
 * tras reconstruir el contexto (reinicio simulado de la aplicación).
 */
public final class AppContextIntegrationTest {

    private static final String PREFIJO = "CTXTEST-";
    private static final String CLAVE_ADMIN_PRUEBA = "ClaveCtx416!";
    private final List<Resultado> resultados = new ArrayList<>();

    public static void main(String[] args) {
        int salida;
        try {
            salida = new AppContextIntegrationTest().ejecutarTodo();
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            salida = 2;
        }
        System.exit(salida);
    }

    private int ejecutarTodo() throws Exception {
        // Etapa 4.16: las operaciones de catálogo exigen sesión; se define antes de construir contextos.
        System.setProperty("pican.admin.password", CLAVE_ADMIN_PRUEBA);
        verificarMemoria();
        verificarPostgres();

        long fallos = resultados.stream().filter(r -> !"APROBADA".equals(r.estado)).count();
        imprimirInforme();
        escribirInforme();
        System.out.println("[RESUMEN] " + (resultados.size() - fallos) + "/" + resultados.size()
                + " comprobaciones aprobadas");
        return fallos == 0 ? 0 : 1;
    }

    // -------------------------------------------------------------------------
    // Fase 5 · MEMORY
    // -------------------------------------------------------------------------

    private void verificarMemoria() {
        System.setProperty("pican.persistence", "memory");
        AppContext contexto;
        try {
            contexto = new AppContext();
            contexto.getAuthService().login("admin", CLAVE_ADMIN_PRUEBA);
        } catch (Throwable fallo) {
            resultados.add(new Resultado("CTX-MEM-01", "Construir AppContext en modo MEMORY",
                    fallo.getClass().getSimpleName() + ": " + fallo.getMessage(), "FALLIDA"));
            return;
        }

        verificar("CTX-MEM-01", "AppContext en modo memoria sin gestor JDBC", () -> {
            check(contexto.esMemoria(), "esMemoria() devolvió false");
            check(contexto.getConexiones() == null, "No debería existir JdbcConnectionManager en memoria");
            check(contexto.getTransactionManager() instanceof NoOpTransactionManager,
                    "El gestor transaccional debería ser NoOpTransactionManager");
        });

        verificar("CTX-MEM-02", "Los 20 repositorios son implementaciones Memory",
                () -> checkRepositorios(contexto, "Memory"));

        verificar("CTX-MEM-03", "DemoDataInitializer sigue funcionando en memoria", () -> {
            DemoDataInitializer.initialize(contexto);
            boolean hayDemo = contexto.getUebService().listarTodas().stream()
                    .anyMatch(u -> u.getCodigo() != null && u.getCodigo().startsWith("DEMO-"));
            check(hayDemo, "No se cargaron las UEB de demostración");
        });

        verificar("CTX-MEM-04", "CRUD de catálogo y servicios en memoria", () -> {
            UUID id = UUID.randomUUID();
            contexto.getUebService().registrar(new UEB(id, "UEB prueba memoria", PREFIJO + id, true));
            check(contexto.getUebService().buscarPorId(id).isPresent(), "La UEB no se pudo consultar");
            contexto.getUebService().desactivar(id);
            check(!contexto.getUebService().buscarPorId(id).orElseThrow().isActiva(),
                    "La baja lógica no se aplicó");
        });
    }

    // -------------------------------------------------------------------------
    // Fases 2 y 4 · POSTGRES
    // -------------------------------------------------------------------------

    private void verificarPostgres() throws Exception {
        String url = requerir("pican.test.url", "Falta -Dpican.test.url");
        String usuario = requerir("pican.test.user", "Falta -Dpican.test.user");
        String nombreBase = url.split("\\?", 2)[0];
        nombreBase = nombreBase.substring(nombreBase.lastIndexOf('/') + 1);
        if (!nombreBase.toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalArgumentException("La base debe ser exclusiva de pruebas: " + nombreBase);
        }

        System.setProperty("pican.persistence", "postgresql");
        System.setProperty("pican.db.url", url);
        System.setProperty("pican.db.user", usuario);
        System.setProperty("pican.db.password", System.getProperty("pican.test.password", "trust_local"));
        System.setProperty("pican.db.name", nombreBase);
        System.setProperty("pican.db.migrate", "true");
        System.setProperty("pican.migrations.dir", System.getProperty("pican.test.migrations", "sql/migrations"));

        AppContext contexto;
        try {
            contexto = new AppContext();
            contexto.getAuthService().login("admin", CLAVE_ADMIN_PRUEBA);
        } catch (Throwable fallo) {
            resultados.add(new Resultado("CTX-PG-01", "Construir AppContext en modo POSTGRES",
                    fallo.getClass().getSimpleName() + ": " + fallo.getMessage(), "FALLIDA"));
            return;
        }

        verificar("CTX-PG-01", "AppContext en modo PostgreSQL con gestor JDBC y transaccional", () -> {
            check(contexto.esPostgresql(), "esPostgresql() devolvió false");
            check(contexto.getConexiones() != null, "No se creó JdbcConnectionManager");
            check(contexto.getTransactionManager() instanceof JdbcTransactionManager,
                    "El gestor transaccional debería ser JdbcTransactionManager");
        });

        verificar("CTX-PG-02", "Los 20 repositorios son implementaciones Postgres",
                () -> checkRepositorios(contexto, "Postgres"));

        verificar("CTX-PG-03", "No se insertan datos de demostración en PostgreSQL", () -> {
            boolean hayDemo = contexto.getUebService().listarTodas().stream()
                    .anyMatch(u -> u.getCodigo() != null && u.getCodigo().startsWith("DEMO-"));
            check(!hayDemo, "Aparecieron UEB DEMO- en PostgreSQL");
        });

        verificar("CTX-PG-04", "Referencias históricas arrancan vacías (las registra el administrador)", () -> {
            IndicadorHistoricoRepository historical =
                    (IndicadorHistoricoRepository) contexto.getRepositorios().get("IndicadorHistoricoRepository");
            check(historical != null && historical.findAll().isEmpty(),
                    "Las referencias históricas debieron arrancar vacías");
        });

        verificar("CTX-PG-05", "Cuenta administrativa inicial disponible", () -> {
            check(contexto.getUsuarioService().existeAdministrador(),
                    "No existe la cuenta administrativa inicial");
        });

        UUID id = UUID.randomUUID();
        String codigo = PREFIJO + id;
        verificar("CTX-PG-06", "Alta y consulta de UEB mediante servicios + repositorio JDBC", () -> {
            contexto.getUebService().registrar(new UEB(id, "UEB integración PostgreSQL", codigo, true));
            check(contexto.getUebService().buscarPorCodigo(codigo).isPresent(), "La UEB no se pudo consultar");
        });

        verificar("CTX-PG-07", "Persistencia tras reconstruir AppContext (reinicio simulado)", () -> {
            AppContext reiniciado = new AppContext();
            reiniciado.getAuthService().login("admin", CLAVE_ADMIN_PRUEBA);
            try {
                check(reiniciado.esPostgresql(), "El contexto reiniciado no es PostgreSQL");
                check(reiniciado.getUebService().buscarPorId(id).isPresent(),
                        "La UEB no persistió tras reconstruir el contexto");
            } finally {
                UEBRepository repositorio =
                        (UEBRepository) reiniciado.getRepositorios().get("UEBRepository");
                repositorio.deleteById(id);
            }
        });

        verificar("CTX-PG-08", "Sin fallback silencioso: PostgreSQL inaccesible produce error", () -> {
            String urlOriginal = System.getProperty("pican.db.url");
            try {
                System.setProperty("pican.db.url",
                        "jdbc:postgresql://127.0.0.1:59999/pican_inexistente_test");
                try {
                    new AppContext();
                    throw new AssertionError("AppContext cayó en memoria o no detectó la falta de PostgreSQL");
                } catch (PersistenciaException esperada) {
                    check(esperada.getMessage() != null, "El error no aporta diagnóstico");
                }
            } finally {
                System.setProperty("pican.db.url", urlOriginal);
            }
        });
    }

    private static void checkRepositorios(AppContext contexto, String esperado) {
        Map<String, Object> repositorios = contexto.getRepositorios();
        check(repositorios.size() == 20, "Se esperaban 20 repositorios; hay " + repositorios.size());
        for (Map.Entry<String, Object> entrada : repositorios.entrySet()) {
            String clase = entrada.getValue().getClass().getSimpleName();
            check(clase.contains(esperado),
                    entrada.getKey() + " es " + clase + " (se esperaba contener '" + esperado + "')");
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private void verificar(String id, String esperado, Prueba prueba) {
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
        resultados.add(new Resultado(id, esperado, obtenido, estado));
        System.out.println("[" + estado + "] " + id + " -> " + obtenido);
    }

    private static void check(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    private static String requerir(String clave, String mensaje) {
        String valor = System.getProperty(clave);
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor;
    }

    private void imprimirInforme() {
        System.out.println("=== INFORME DE INTEGRACION APPCONTEXT (Fases 2/4/5) ===");
        for (Resultado r : resultados) {
            System.out.println(r.estado + " | " + r.id + " | " + r.esperado + " | " + r.obtenido);
        }
    }

    private void escribirInforme() {
        StringBuilder texto = new StringBuilder();
        texto.append("INFORME DE INTEGRACION DE APPCONTEXT · ETAPA 4.14\n\n");
        for (Resultado r : resultados) {
            texto.append(r.estado).append(" | ").append(r.id).append('\n')
                    .append("  Esperado: ").append(r.esperado).append('\n')
                    .append("  Obtenido: ").append(r.obtenido).append('\n');
        }
        try {
            Path ruta = Paths.get("test-results", "RESULTADOS_APPCONTEXT.txt");
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
        private final String id;
        private final String esperado;
        private final String obtenido;
        private final String estado;

        private Resultado(String id, String esperado, String obtenido, String estado) {
            this.id = id;
            this.esperado = esperado;
            this.obtenido = obtenido;
            this.estado = estado;
        }
    }
}
