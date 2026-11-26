package cu.pican.verify;

import cu.pican.model.Destino;
import cu.pican.model.Incidencia;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.Recepcion;
import cu.pican.model.UEB;
import cu.pican.model.Usuario;
import cu.pican.model.ValeEntrega;
import cu.pican.model.persistence.DatabaseConfig;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcTransactionManager;
import cu.pican.model.persistence.MigrationRunner;
import cu.pican.model.persistence.TransactionManager;
import cu.pican.model.repository.RecepcionRepository;
import cu.pican.model.repository.jdbc.DestinoRepositoryPostgres;
import cu.pican.model.repository.jdbc.IncidenciaRepositoryPostgres;
import cu.pican.model.repository.jdbc.ProductoDestinoRepositoryPostgres;
import cu.pican.model.repository.jdbc.ProductoRepositoryPostgres;
import cu.pican.model.repository.jdbc.RecepcionRepositoryPostgres;
import cu.pican.model.repository.jdbc.UEBRepositoryPostgres;
import cu.pican.model.repository.jdbc.UsuarioRepositoryPostgres;
import cu.pican.model.repository.jdbc.ValeEntregaRepositoryPostgres;
import cu.pican.controller.service.DestinoService;
import cu.pican.controller.service.IncidenciaService;
import cu.pican.controller.service.PasswordHasher;
import cu.pican.controller.service.ProductoDestinoService;
import cu.pican.controller.service.ProductoService;
import cu.pican.controller.service.RecepcionService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.UsuarioService;
import cu.pican.controller.service.ValeEntregaService;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fase 3 · Etapa 4.14 · Transacciones en servicios.
 *
 * Usa PostgreSQL real y una base exclusiva de pruebas. Los datos se identifican con
 * SRVTEST- y se eliminan al inicio y al final. Incluye un fallo forzado controlado
 * en el segundo write de RecepcionService para demostrar rollback de la operación completa.
 */
public final class ServiciosTransaccionalesTest {

    private static final String PREFIJO = "SRVTEST-";

    private final List<Resultado> resultados = new ArrayList<>();
    private DatabaseConfig config;
    private TransactionManager transacciones;
    private UEBService uebService;
    private ValeEntregaService valeService;
    private RecepcionService recepcionService;
    private IncidenciaService incidenciaService;
    private UsuarioService usuarioService;

    public static void main(String[] args) {
        cu.pican.controller.app.AutorizacionDemo.instalarTodosLosRoles();
        int salida;
        try {
            salida = new ServiciosTransaccionalesTest().ejecutarTodo();
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            salida = 2;
        }
        System.exit(salida);
    }

    private int ejecutarTodo() throws Exception {
        preparar();
        limpiarTodo();
        try {
            verificar("SRV-01", "Recepción NO_CONFORME confirma vale, recepción e incidencia juntos",
                    this::srv01RecepcionExitosa);
            verificar("SRV-02", "Fallo en el segundo write revierte el cambio de estado del vale",
                    this::srv02RollbackServicio);
            verificar("SRV-03", "Cerrar la última incidencia cierra el vale (coherencia de estados)",
                    this::srv03CierreIncidencia);
            verificar("SRV-04", "Regla de negocio: una incidencia cerrada no se puede cerrar dos veces",
                    this::srv04ReglaCierre);
            verificar("SRV-05", "UsuarioService participa en la transacción activa (rol + usuario)",
                    this::srv05UsuarioTransaccional);
        } finally {
            limpiarTodo();
        }

        long fallos = resultados.stream().filter(r -> !"APROBADA".equals(r.estado)).count();
        imprimirInforme();
        escribirInforme();
        System.out.println("[RESUMEN] " + (resultados.size() - fallos) + "/" + resultados.size()
                + " pruebas aprobadas");
        return fallos == 0 ? 0 : 1;
    }

    // -------------------------------------------------------------------------
    // Montaje del grafo real de servicios
    // -------------------------------------------------------------------------

    private void preparar() {
        String url = requerir("pican.test.url", "Falta -Dpican.test.url");
        String usuario = requerir("pican.test.user", "Falta -Dpican.test.user");
        String base = url.split("\\?", 2)[0];
        base = base.substring(base.lastIndexOf('/') + 1);
        if (!base.toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalArgumentException("Base no exclusiva de pruebas: " + base);
        }

        System.setProperty("pican.persistence", DatabaseConfig.POSTGRESQL);
        System.setProperty("pican.db.url", url);
        System.setProperty("pican.db.user", usuario);
        System.setProperty("pican.db.password", System.getProperty("pican.test.password", "trust_local"));
        System.setProperty("pican.db.name", base);
        System.setProperty("pican.db.migrate", "true");
        config = DatabaseConfig.cargar();

        JdbcConnectionManager conexiones = new JdbcConnectionManager(config);
        conexiones.verificarConexion();
        new MigrationRunner(conexiones, Paths.get(System.getProperty("pican.test.migrations", "sql/migrations")))
                .ejecutar();
        transacciones = new JdbcTransactionManager(conexiones);

        UEBRepositoryPostgres uebRepo = new UEBRepositoryPostgres(conexiones);
        ProductoRepositoryPostgres productoRepo = new ProductoRepositoryPostgres(conexiones);
        DestinoRepositoryPostgres destinoRepo = new DestinoRepositoryPostgres(conexiones);
        ProductoDestinoRepositoryPostgres relacionRepo = new ProductoDestinoRepositoryPostgres(conexiones);
        ValeEntregaRepositoryPostgres valeRepo = new ValeEntregaRepositoryPostgres(conexiones);
        RecepcionRepositoryPostgres recepcionRepo = new RecepcionRepositoryPostgres(conexiones);
        IncidenciaRepositoryPostgres incidenciaRepo = new IncidenciaRepositoryPostgres(conexiones);

        uebService = new UEBService(uebRepo);
        ProductoService productoService = new ProductoService(productoRepo);
        DestinoService destinoService = new DestinoService(destinoRepo);
        ProductoDestinoService relacionService =
                new ProductoDestinoService(relacionRepo, productoRepo, destinoRepo);
        valeService = new ValeEntregaService(
                valeRepo, uebService, productoService, destinoService, relacionService, transacciones);
        incidenciaService = new IncidenciaService(incidenciaRepo, null, valeService, transacciones);
        recepcionService = new RecepcionService(recepcionRepo, valeService, incidenciaService, transacciones);
        recepcionService.setIncidenciaService(incidenciaService);
        valeService.setRecepcionRepository(recepcionRepo);

        usuarioService = new UsuarioService(
                new UsuarioRepositoryPostgres(conexiones), new PasswordHasher(), transacciones);
    }

    // -------------------------------------------------------------------------
    // Casos
    // -------------------------------------------------------------------------

    private void srv01RecepcionExitosa() {
        Datos d = prepararDatos();
        Recepcion recepcion = nuevaRecepcion(d.idVale(), d.unidad(), Recepcion.RESULTADO_NO_CONFORME);
        recepcionService.registrar(recepcion);

        check(estadoVale(d.idVale()).equals(ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA),
                "El vale debió quedar RECIBIDO_CON_INCIDENCIA");
        check(contar("recepcion", "id_recepcion", recepcion.getIdRecepcion()) == 1,
                "No se guardó la recepción");
        check(contarIncidenciasDeVale(d.idVale()) == 1,
                "No se creó la incidencia automática");
    }

    private void srv02RollbackServicio() {
        Datos d = prepararDatos();
       RecepcionRepository real = new RecepcionRepositoryPostgres(conexionesDe(config));
        RecepcionRepository fallido = (RecepcionRepository) Proxy.newProxyInstance(
                RecepcionRepository.class.getClassLoader(),
                new Class<?>[]{RecepcionRepository.class},
                (InvocationHandler) (proxy, metodo, argumentos) -> {
                    if ("save".equals(metodo.getName())) {
                        throw new IllegalStateException("fallo forzado en el segundo write");
                    }
                    try {
                        return metodo.invoke(real, argumentos);
                    } catch (java.lang.reflect.InvocationTargetException ex) {
                        Throwable causa = ex.getCause();
                        if (causa instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        throw new IllegalStateException(causa);
                    }
                });
        RecepcionService servicioFallido = new RecepcionService(fallido, valeService, incidenciaService, transacciones);
        Recepcion recepcion = nuevaRecepcion(d.idVale(), d.unidad(), Recepcion.RESULTADO_CONFORME);

        try {
            servicioFallido.registrar(recepcion);
            throw new AssertionError("Se esperaba el fallo forzado");
        } catch (IllegalStateException esperada) {
            check("fallo forzado en el segundo write".equals(esperada.getMessage()),
                    "Se propagó otra excepción: " + esperada.getMessage());
        }

        check(estadoVale(d.idVale()).equals(ValeEntrega.ESTADO_EMITIDO),
                "El vale debió permanecer EMITIDO tras el rollback");
        check(contar("recepcion", "id_recepcion", recepcion.getIdRecepcion()) == 0,
                "No debió quedar recepción parcial");
        check(contarIncidenciasDeVale(d.idVale()) == 0,
                "No debió quedar incidencia parcial");
    }

    private void srv03CierreIncidencia() {
        Datos d = prepararDatos();
        recepcionService.registrar(nuevaRecepcion(d.idVale(), d.unidad(), Recepcion.RESULTADO_NO_CONFORME));
        UUID idIncidencia = primerIdIncidencia(d.idVale());

        incidenciaService.cerrar(idIncidencia, LocalDateTime.now(), "Solucionado y verificado en prueba SRV-03");

        String estado = estadoIncidencia(idIncidencia);
        check(Incidencia.ESTADO_CERRADA.equals(estado), "La incidencia debió quedar CERRADA; está " + estado);
        check(estadoVale(d.idVale()).equals(ValeEntrega.ESTADO_CERRADO),
                "El vale debió quedar CERRADO al cerrar la última incidencia");
    }

    private void srv04ReglaCierre() {
        Datos d = prepararDatos();
        recepcionService.registrar(nuevaRecepcion(d.idVale(), d.unidad(), Recepcion.RESULTADO_NO_CONFORME));
        UUID idIncidencia = primerIdIncidencia(d.idVale());
        incidenciaService.cerrar(idIncidencia, LocalDateTime.now(), "Solucionado y verificado en prueba SRV-04");

        try {
            incidenciaService.cerrar(idIncidencia, LocalDateTime.now(), "Segundo cierre no permitido");
            throw new AssertionError("Se esperaba rechazo de la regla de negocio");
        } catch (cu.pican.controller.service.ValidacionException esperada) {
            check(esperada.getMessage().contains("no se pueden reabrir"),
                    "Mensaje inesperado: " + esperada.getMessage());
        }
        check(Incidencia.ESTADO_CERRADA.equals(estadoIncidencia(idIncidencia)),
                "La incidencia debió permanecer CERRADA");
    }

    private void srv05UsuarioTransaccional() {
        String nombre = "srvtest_" + UUID.randomUUID().toString().substring(0, 8);
        UUID[] id = new UUID[1];
        try {
            transacciones.ejecutar(() -> {
                Usuario creado = usuarioService.registrar(nombre, "Prueba transaccional",
                        "ClaveSegura123", cu.pican.model.Rol.JEFE_UEB);
                id[0] = creado.getIdUsuario();
                throw new IllegalStateException("rollback forzado SRV-05");
            });
            throw new AssertionError("Se esperaba el rollback forzado");
        } catch (IllegalStateException esperada) {
            check("rollback forzado SRV-05".equals(esperada.getMessage()),
                    "Se propagó otra excepción: " + esperada.getMessage());
        }
        check(contar("usuario", "id_usuario", id[0]) == 0,
                "El usuario no debió persistir; UsuarioService no participó en la transacción");
    }

    // -------------------------------------------------------------------------
    // Datos y consultas de verificación
    // -------------------------------------------------------------------------

    private Datos prepararDatos() {
        UUID idUeb = UUID.randomUUID();
        UUID idProducto = UUID.randomUUID();
        UUID idDestino = UUID.randomUUID();
        UUID idRelacion = UUID.randomUUID();
        UUID idVale = UUID.randomUUID();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String unidad = "docena";

        uebService.registrar(new UEB(idUeb, "UEB servicio transaccional", PREFIJO + "UEB-" + sufijo, true));
        new ProductoService(new ProductoRepositoryPostgres(conexionesDe(config)))
                .registrar(new Producto(idProducto, PREFIJO + "PRO-" + sufijo,
                        "Producto prueba servicio", true, unidad));
        new DestinoService(new DestinoRepositoryPostgres(conexionesDe(config)))
                .registrar(new Destino(idDestino, PREFIJO + "DES-" + sufijo, "Destino prueba servicio", true));
        new ProductoDestinoService(
                new ProductoDestinoRepositoryPostgres(conexionesDe(config)),
                new ProductoRepositoryPostgres(conexionesDe(config)),
                new DestinoRepositoryPostgres(conexionesDe(config)))
                .registrar(new ProductoDestino(idRelacion, idProducto, idDestino, true));

        ValeEntrega vale = new ValeEntrega();
        vale.setIdVale(idVale);
        vale.setIdUEB(idUeb);
        vale.setIdProducto(idProducto);
        vale.setIdDestino(idDestino);
        vale.setCantidadDespachada(new BigDecimal("100"));
        vale.setUnidadMedida(unidad);
        vale.setFechaHoraEmision(LocalDateTime.now());
        valeService.emitir(vale);
        return new Datos(idVale, unidad);
    }

    private static JdbcConnectionManager conexionesDe(DatabaseConfig config) {
        return new JdbcConnectionManager(config);
    }

    private static Recepcion nuevaRecepcion(UUID idVale, String unidad, String resultado) {
        Recepcion r = new Recepcion();
        r.setIdRecepcion(UUID.randomUUID());
        r.setIdVale(idVale);
        r.setFechaHoraRecepcion(LocalDateTime.now());
        r.setCantidadRecibida(new BigDecimal(resultado.equals(Recepcion.RESULTADO_CONFORME) ? "100" : "95"));
        r.setUnidadMedida(unidad);
        r.setResultado(resultado);
        r.setObservaciones(resultado.equals(Recepcion.RESULTADO_CONFORME) ? null : "Faltan 5 unidades en la prueba");
        return r;
    }

    private String estadoVale(UUID id) {
        return consultarTexto("SELECT estado FROM vale_entrega WHERE id_vale = ?", id);
    }

    private String estadoIncidencia(UUID id) {
        return consultarTexto("SELECT estado FROM incidencia WHERE id_incidencia = ?", id);
    }

    private int contarIncidenciasDeVale(UUID idVale) {
        try (Connection c = DriverManager.getConnection(config.getUrl(), config.getUsuario(), config.getContrasena());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM incidencia WHERE id_vale = ?")) {
            JdbcSupportUuid(ps, 1, idVale);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private UUID primerIdIncidencia(UUID idVale) {
        try (Connection c = DriverManager.getConnection(config.getUrl(), config.getUsuario(), config.getContrasena());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_incidencia FROM incidencia WHERE id_vale = ? ORDER BY creado_en LIMIT 1")) {
            JdbcSupportUuid(ps, 1, idVale);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No hay incidencia para el vale " + idVale);
                }
                return UUID.fromString(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String consultarTexto(String sql, UUID id) {
        try (Connection c = DriverManager.getConnection(config.getUrl(), config.getUsuario(), config.getContrasena());
             PreparedStatement ps = c.prepareStatement(sql)) {
            JdbcSupportUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "(sin fila)";
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private int contar(String tabla, String columna, UUID id) {
        try (Connection c = DriverManager.getConnection(config.getUrl(), config.getUsuario(), config.getContrasena());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM " + tabla + " WHERE " + columna + " = ?")) {
            JdbcSupportUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void JdbcSupportUuid(PreparedStatement ps, int indice, UUID valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, java.sql.Types.OTHER);
        } else {
            ps.setObject(indice, valor.toString(), java.sql.Types.OTHER);
        }
    }

    private void limpiarTodo() {
        String[] sentencias = {
                "DELETE FROM incidencia WHERE id_vale IN (SELECT id_vale FROM vale_entrega WHERE id_ueb IN (SELECT id_ueb FROM ueb WHERE codigo LIKE '" + PREFIJO + "%'))",
                "DELETE FROM recepcion WHERE id_vale IN (SELECT id_vale FROM vale_entrega WHERE id_ueb IN (SELECT id_ueb FROM ueb WHERE codigo LIKE '" + PREFIJO + "%'))",
                "DELETE FROM vale_secuencia WHERE id_ueb IN (SELECT id_ueb FROM ueb WHERE codigo LIKE '" + PREFIJO + "%')",
                "DELETE FROM vale_entrega WHERE id_ueb IN (SELECT id_ueb FROM ueb WHERE codigo LIKE '" + PREFIJO + "%')",
                "DELETE FROM producto_destino WHERE id_producto IN (SELECT id_producto FROM producto WHERE codigo LIKE '" + PREFIJO + "%')",
                "DELETE FROM producto WHERE codigo LIKE '" + PREFIJO + "%'",
                "DELETE FROM destino WHERE codigo LIKE '" + PREFIJO + "%'",
                "DELETE FROM ueb WHERE codigo LIKE '" + PREFIJO + "%'",
                "DELETE FROM usuario WHERE nombre_usuario LIKE 'srvtest_%'"
        };
        try (Connection c = DriverManager.getConnection(config.getUrl(), config.getUsuario(), config.getContrasena());
             Statement st = c.createStatement()) {
            for (String sql : sentencias) {
                st.executeUpdate(sql);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo limpiar: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Infraestructura de pruebas
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
        System.out.println("=== INFORME DE SERVICIOS TRANSACCIONALES (Fase 3) ===");
        for (Resultado r : resultados) {
            System.out.println(r.estado + " | " + r.id + " | " + r.esperado + " | " + r.obtenido);
        }
    }

    private void escribirInforme() {
        StringBuilder texto = new StringBuilder();
        texto.append("INFORME DE SERVICIOS TRANSACCIONALES · ETAPA 4.14 · FASE 3\n\n");
        for (Resultado r : resultados) {
            texto.append(r.estado).append(" | ").append(r.id).append('\n')
                    .append("  Esperado: ").append(r.esperado).append('\n')
                    .append("  Obtenido: ").append(r.obtenido).append('\n');
        }
        try {
            Path ruta = Paths.get("test-results", "RESULTADOS_SERVICIOS.txt");
            Files.createDirectories(ruta.getParent());
            Files.write(ruta, texto.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("[INFORME] " + ruta.toAbsolutePath());
        } catch (IOException ex) {
            System.out.println("[AVISO] No se pudo escribir el informe: " + ex.getMessage());
        }
    }

    private record Datos(UUID idVale, String unidad) {}

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
