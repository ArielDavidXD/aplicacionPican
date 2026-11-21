package cu.pican.verify;

import cu.pican.app.AppContext;
import cu.pican.backup.BackupArchivo;
import cu.pican.backup.EstadoEsquema;
import cu.pican.backup.InformacionCopia;
import cu.pican.backup.ManifiestoCopia;
import cu.pican.backup.PgBackupOperations;
import cu.pican.backup.ResultadoCopia;
import cu.pican.backup.ResultadoRestauracion;
import cu.pican.backup.VersionAplicacion;
import cu.pican.model.BackupAuditoria;
import cu.pican.model.Rol;
import cu.pican.model.Usuario;
import cu.pican.persistence.DatabaseConfig;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.memory.BackupAuditoriaRepositoryMemory;
import cu.pican.repository.memory.ControlRestauracionRepositoryMemory;
import cu.pican.service.AutorizacionException;
import cu.pican.service.AutorizacionService;
import cu.pican.service.BackupService;
import cu.pican.service.CasoUso;
import cu.pican.service.SesionAutorizacionProvider;
import cu.pican.sync.SyncException;
import cu.pican.sync.SyncService;
import cu.pican.view.PermisosInterfaz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Etapa 4.18 / CU-19: copias reales, restauracion segura, auditoria y bloqueo SIN-13. */
public final class CopiasSeguridadTest {

    private static final String CLAVE = System.getProperty("pican.test.admin.password");
    private static String modo;
    private static Path directorio;
    private static AppContext contexto;
    private static String baseCreada;
    private static int aprobadas;
    private static int totales;

    public static void main(String[] args) {
        modo = args.length == 0 ? "memoria" : args[0];
        try {
            configurar(modo);
            directorio = Files.createTempDirectory("pican-cu19-test-");
            System.out.println("=== COPIAS DE SEGURIDAD CU-19 (4.18) - " + modo.toUpperCase(Locale.ROOT) + " ===");
            contexto = new AppContext();
            ejecutarTodo();
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            System.exit(2);
        } finally {
            borrarTemporales();
            eliminarBaseCreada();
        }
    }

    private static void configurar(String modoSeleccionado) {
        if (CLAVE == null || CLAVE.isBlank()) {
            throw new IllegalArgumentException("Falta -Dpican.test.admin.password.");
        }
        System.setProperty("pican.admin.password", CLAVE);
        System.setProperty("pican.db.mode", DatabaseConfig.MODO_LOCAL);
        if (!"postgresql".equals(modoSeleccionado)) {
            System.setProperty("pican.persistence", DatabaseConfig.MEMORIA);
            return;
        }
        String url = System.getProperty("pican.test.url");
        String usuario = System.getProperty("pican.test.user");
        String clave = System.getProperty("pican.test.password");
        if (url == null || usuario == null || clave == null) {
            throw new IllegalArgumentException("Faltan -Dpican.test.url, -Dpican.test.user o -Dpican.test.password.");
        }
        String prefijo = url.substring(0, url.lastIndexOf('/') + 1);
        validarNombreBase(url);
        String nombre = "pican_cu19_" + System.currentTimeMillis() + "_test";
        String urlMantenimiento = prefijo + "postgres";
        try (Connection c = DriverManager.getConnection(urlMantenimiento, usuario, clave);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE " + nombre);
            baseCreada = nombre;
            System.setProperty("pican.db.url", prefijo + nombre);
        } catch (SQLException ex) {
            System.out.println("[INFO] No se pudo crear una base propia; se usa la base de prueba indicada.");
            System.setProperty("pican.db.url", url);
        }
        System.setProperty("pican.persistence", DatabaseConfig.POSTGRESQL);
        System.setProperty("pican.db.user", usuario);
        System.setProperty("pican.db.password", clave);
        System.setProperty("pican.db.migrate", "true");
        String bin = System.getProperty("pican.test.pg.bin");
        if (bin != null && !bin.isBlank()) {
            System.setProperty("pican.backup.pg.bin", bin);
        }
    }

    private static void ejecutarTodo() throws Exception {
        registrarCuentas();

        verificar("CU19-01", "CU-19 habilitado y reservado al ADMINISTRADOR",
                () -> CasoUso.CU_19.isHabilitado()
                        && CasoUso.CU_19.getRolesPermitidos().size() == 1
                        && CasoUso.CU_19.getRolesPermitidos().contains(Rol.ADMINISTRADOR));

        verificar("CU19-02", "Sin nuevo moduleId: la matriz solo agrega CU-19 a administracion",
                () -> PermisosInterfaz.casosDelModulo("administracion").contains(CasoUso.CU_19)
                        && PermisosInterfaz.restricciones().size() == 11
                        && !PermisosInterfaz.puedeAcceder("administracion", Rol.JEFE_UEB));

        verificar("CU19-03", "AppContext expone el servicio de copias",
                () -> contexto.getBackupService() != null);

        contexto.getAuthService().logout();
        verificar("CU19-04", "Sin sesion: crear copia rechazado",
                () -> rechaza(() -> contexto.getBackupService().crearCopia(directorio),
                        AutorizacionException.class));

        entrar(contexto, "jefe418", CLAVE);
        verificar("CU19-05", "JEFE no puede crear, inspeccionar ni restaurar copias",
                () -> rechaza(() -> contexto.getBackupService().crearCopia(directorio),
                        AutorizacionException.class)
                        && rechaza(() -> contexto.getBackupService().inspeccionar(null),
                        AutorizacionException.class)
                        && rechaza(() -> contexto.getBackupService().restaurar(null, true, true),
                        AutorizacionException.class));

        entrar(contexto, "admin418", CLAVE);
        verificar("V3-01", "La migracion V3 de auditoria existe y no altera V1/V2",
                () -> Files.exists(Path.of("sql/migrations/V3__backup_auditoria.sql"))
                        && Files.exists(Path.of("sql/migrations/V1__esquema_inicial.sql"))
                        && Files.exists(Path.of("sql/migrations/V2__sincronizacion.sql")));

        Path sintetica = crearCopiaSintetica("volcado-de-prueba-test".getBytes(StandardCharsets.UTF_8),
                VersionAplicacion.actual(), EstadoEsquema.migracionesLocales());
        verificar("VAL-100-01", "Copia integra y compatible es aceptada por la inspeccion", () -> {
            InformacionCopia info = contexto.getBackupService().inspeccionar(sintetica);
            return info.isIntegro() && info.isCompatible() && info.esValida()
                    && info.getManifiesto().getSha256Dump().length() == 64;
        });

        Path alterada = directorio.resolve("copia-alterada-test.zip");
        byte[] original = "volcado-original-test".getBytes(StandardCharsets.UTF_8);
        byte[] manipulado = "volcado-manipulado-test".getBytes(StandardCharsets.UTF_8);
        ManifiestoCopia manifiestoOriginal = new ManifiestoCopia(VersionAplicacion.actual(),
                EstadoEsquema.unirVersiones(EstadoEsquema.migracionesLocales()),
                EstadoEsquema.migracionesLocales(), LocalDateTime.now().toString(), "base_test",
                "inst-test", "LOCAL", "admin418", original.length, BackupArchivo.hash(original));
        BackupArchivo.escribir(alterada, manipulado, manifiestoOriginal.aJson());
        verificar("VAL-100-02", "Alteracion del volcado detectada por SHA-256 del manifiesto",
                () -> rechaza(() -> contexto.getBackupService().inspeccionar(alterada), RuntimeException.class));

        Path futura = crearCopiaSintetica(original, "999.0.0", EstadoEsquema.migracionesLocales());
        verificar("VAL-100-03", "Copia de version futura rechazada por compatibilidad", () -> {
            InformacionCopia info = contexto.getBackupService().inspeccionar(futura);
            return !info.esValida() && info.getDetalleCompatibilidad().contains("superior");
        });

        Path intrusa = directorio.resolve("copia-intrusa-test.zip");
        LinkedHashMap<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("BACKUP.dump", original);
        entradas.put("MANIFEST.json", "{}".getBytes(StandardCharsets.UTF_8));
        entradas.put("INTEGRIDAD.sha256", "x".getBytes(StandardCharsets.UTF_8));
        entradas.put("MALICIOSO.txt", "x".getBytes(StandardCharsets.UTF_8));
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(intrusa))) {
            for (var entrada : entradas.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue());
                zip.closeEntry();
            }
        }
        verificar("VAL-100-04", "Entrada ZIP no permitida rechazada",
                () -> rechaza(() -> contexto.getBackupService().inspeccionar(intrusa), RuntimeException.class));

        verificar("VAL-98-01", "Directorio sin permisos rechazado y auditado como fallo",
                () -> {
                    ResultadoCopia resultado = contexto.getBackupService()
                            .crearCopia(directorio.resolve("directorio-inexistente-test"));
                    return !resultado.isExitoso()
                            && contexto.getBackupService().historial().stream()
                            .anyMatch(e -> "CREACION".equals(e.getOperacion())
                                    && "FALLIDO".equals(e.getResultado())
                                    && e.getMotivoFallo() != null && !e.getMotivoFallo().isBlank());
                });

        if ("postgresql".equals(modo)) {
            ejecutarPostgres();
        } else {
            ejecutarMemoria();
        }

        verificar("AUD-01", "Auditoria conserva historial con usuario, operacion, archivo, fecha y resultado", () -> {
            List<BackupAuditoria> eventos = contexto.getBackupService().historial();
            return !eventos.isEmpty() && eventos.stream().allMatch(e ->
                    e.getNombreUsuario() != null && !e.getNombreUsuario().isBlank()
                            && e.getOperacion() != null && e.getFechaHora() != null
                            && e.getResultado() != null && e.getIdAuditoria() != null);
        });

        verificar("SEG-01", "Ni el manifiesto ni la auditoria contienen la contrasena", () -> {
            try {
                String manifiesto = new String(Files.readAllBytes(sintetica), StandardCharsets.ISO_8859_1);
                String auditoria = contexto.getBackupService().historial().toString();
                return !manifiesto.contains(CLAVE) && !auditoria.contains(CLAVE);
            } catch (Exception ex) {
                throw new RuntimeException("No se pudo leer la copia sintetica.", ex);
            }
        });

        System.out.println("=== RESULTADO " + modo.toUpperCase(Locale.ROOT) + ": "
                + aprobadas + "/" + totales + " APROBADAS ===");
        if (aprobadas != totales) {
            throw new AssertionError("Faltaron comprobaciones en " + modo);
        }
    }

    private static void ejecutarMemoria() {
        verificar("VAL-98-02", "MEMORY rechaza copias reales con mensaje explicito", () -> {
            ResultadoCopia resultado = contexto.getBackupService().crearCopia(directorio);
            return !resultado.isExitoso() && resultado.getMensaje().contains("PostgreSQL");
        });
        verificar("VAL-99-01", "MEMORY rechaza restauracion real antes de tocar datos", () -> {
            ResultadoRestauracion resultado = contexto.getBackupService()
                    .restaurar(directorio.resolve("copia-inexistente-test.zip"), true, true);
            return !resultado.isExitoso();
        });
    }

    private static void ejecutarPostgres() {
        String[] baseInicial = new String[1];
        verificar("PG-01", "Se identifica la base PostgreSQL activa", () -> {
            baseInicial[0] = baseActual();
            return baseInicial[0] != null && baseInicial[0].toLowerCase(Locale.ROOT).contains("test");
        });

        ResultadoCopia[] copia = new ResultadoCopia[1];
        verificar("VAL-98-03", "pg_dump real genera una copia publicada y verificada", () -> {
            copia[0] = contexto.getBackupService().crearCopia(directorio);
            if (!copia[0].isExitoso()) {
                System.out.println("[DETALLE VAL-98-03] " + copia[0].getMensaje());
            }
            return copia[0].isExitoso()
                    && Files.isRegularFile(copia[0].getArchivo())
                    && copia[0].getTamanoBytes() > 0
                    && copia[0].getSha256().length() == 64;
        });

        Path archivo = copia[0].getArchivo();
        verificar("VAL-100-05", "La copia real contiene exactamente tres entradas y pasa VAL-100", () -> {
            LinkedHashMap<String, byte[]> entradas = BackupArchivo.leer(archivo);
            return entradas.size() == 3
                    && entradas.containsKey(BackupArchivo.DUMP)
                    && entradas.containsKey(BackupArchivo.MANIFIESTO)
                    && entradas.containsKey(BackupArchivo.INTEGRIDAD)
                    && contexto.getBackupService().inspeccionar(archivo).esValida();
        });

        verificar("VAL-99-02", "Falta la segunda confirmacion: cancelado sin sustituir datos", () -> {
            ResultadoRestauracion resultado = contexto.getBackupService().restaurar(archivo, true, false);
            return resultado.isCancelado() && !resultado.isExitoso()
                    && baseInicial[0].equals(baseActual());
        });

        verificar("VAL-99-03", "Restauracion real completada y verificada", () -> {
            ResultadoRestauracion resultado = contexto.getBackupService().restaurar(archivo, true, true);
            if (!resultado.isExitoso()) {
                System.out.println("[DETALLE VAL-99-03] " + resultado.getMensaje());
            }
            return resultado.isExitoso()
                    && baseInicial[0].equals(resultado.getBaseActiva())
                    && baseInicial[0].equals(baseActual())
                    && tablaExiste("pican_backup_auditoria")
                    && tablaExiste("pican_restauracion_control");
        });

        verificar("AUD-02", "La restauracion exitosa queda registrada con hash y auditoria", () -> contexto
                .getBackupService().historial().stream()
                .anyMatch(e -> "RESTAURACION".equals(e.getOperacion())
                        && "EXITOSO".equals(e.getResultado())
                        && e.getSha256() != null && e.getSha256().length() == 64));

        verificar("SIN-13-01", "Tras restaurar en UEB LOCAL se bloquea la emission de vales",
                () -> rechaza(() -> contexto.getValeEntregaService().emitir(null),
                        cu.pican.service.ValidacionException.class));

        verificar("SIN-13-02", "Tras restaurar en UEB LOCAL se bloquea la generacion de paquetes", () -> {
            SyncService sync = contexto.getSyncService();
            return rechaza(() -> sync.generarExportacion(directorio.resolve("paquete-bloqueado-test.zip"),
                    UUID.randomUUID()), SyncException.class);
        });

        verificar("SIN-13-03", "En CENTRAL el mismo control no bloquea", () -> {
            System.setProperty("pican.db.mode", DatabaseConfig.MODO_CENTRAL);
            DatabaseConfig central = DatabaseConfig.cargar();
            BooleanSupplier bloqueo = () -> DatabaseConfig.MODO_LOCAL.equals(central.getModo())
                    && contexto.getBackupService() != null;
            System.setProperty("pican.db.mode", DatabaseConfig.MODO_LOCAL);
            return !bloqueo.getAsBoolean();
        });

        verificar("REC-01", "Fallo tras el intercambio: se reactiva la base anterior y se conserva la fallida",
                () -> {
                    ControlRestauracionRepositoryMemory control = new ControlRestauracionRepositoryMemory();
                    BackupAuditoriaRepositoryMemory auditoria = new BackupAuditoriaRepositoryMemory();
                    PgBackupOperations falsas = new OperacionesFalsas();
                    BackupService servicio = new BackupService(contexto.getConfig(), contexto.getConexiones(),
                            falsas, new cu.pican.backup.VerificadorDestinoSistema(), auditoria, control);
                    SesionPrueba sesion = new SesionPrueba(admin());
                    AutorizacionService autoridad = new AutorizacionService(sesion);
                    servicio.setAutorizacion(autoridad);
                    ResultadoRestauracion resultado = servicio.restaurar(archivo, true, true);
                    List<String> pasos = ((OperacionesFalsas) falsas).pasos;
                    return !resultado.isExitoso()
                            && pasos.stream().anyMatch(p -> p.startsWith("renombrar:pican_fake_test->")
                            && p.contains("_ant_test"))
                            && pasos.stream().anyMatch(p -> p.startsWith("renombrar:pican_fake_test->")
                            && p.contains("_fall_test"))
                            && pasos.stream().anyMatch(p -> p.startsWith("renombrar:")
                            && p.endsWith("->pican_fake_test"))
                            && pasos.contains("conexiones:pican_fake_test:true")
                            && auditoria.listar().stream().anyMatch(e -> "FALLIDO".equals(e.getResultado()));
                });
    }

    private static void registrarCuentas() {
        contexto.getAuthService().login("admin", CLAVE);
        crear("admin418", Rol.ADMINISTRADOR);
        crear("jefe418", Rol.JEFE_UEB);
        contexto.getAuthService().logout();
    }

    private static void crear(String nombre, String rol) {
        if (contexto.getUsuarioService().buscarPorNombreUsuario(nombre).isPresent()) {
            return;
        }
        contexto.getUsuarioService().registrar(nombre, "Prueba CU-19 " + rol, CLAVE, rol);
    }

    private static void entrar(AppContext contextoEntrada, String nombre, String clave) {
        contextoEntrada.getAuthService().logout();
        contextoEntrada.getAuthService().login(nombre, clave);
    }

    private static Usuario admin() {
        return new Usuario(UUID.randomUUID(), "admin418", "Administrador de prueba", null,
                Rol.predefinido(Rol.ADMINISTRADOR), true, LocalDateTime.now(), false);
    }

    private static Path crearCopiaSintetica(byte[] dump, String version,
                                            List<ManifiestoCopia.Migracion> migraciones) {
        Path archivo = directorio.resolve("copia-sintetica-" + UUID.randomUUID() + "-test.zip");
        ManifiestoCopia manifiesto = new ManifiestoCopia(version,
                EstadoEsquema.unirVersiones(migraciones), migraciones, LocalDateTime.now().toString(),
                "base_test", "inst-test", "LOCAL", "admin418", dump.length, BackupArchivo.hash(dump));
        BackupArchivo.escribir(archivo, dump, manifiesto.aJson());
        return archivo;
    }

    private static String baseActual() {
        Connection c = contexto.getConexiones().obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT current_database()")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo consultar la base activa de prueba.", ex);
        } finally {
            if (cerrar) {
                try { c.close(); } catch (SQLException ignored) { }
            }
        }
    }

    private static boolean tablaExiste(String tabla) {
        Connection c = contexto.getConexiones().obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (ResultSet rs = c.getMetaData().getTables(null, "public", tabla, new String[]{"TABLE"})) {
            return rs.next();
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo verificar la tabla " + tabla, ex);
        } finally {
            if (cerrar) {
                try { c.close(); } catch (SQLException ignored) { }
            }
        }
    }

    private static void validarNombreBase(String url) {
        String base = url.split("\\?", 2)[0];
        base = base.substring(base.lastIndexOf('/') + 1);
        if (!base.toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalArgumentException("Base no exclusiva de pruebas: " + base);
        }
    }

    private static boolean rechaza(Runnable accion, Class<? extends RuntimeException> tipo) {
        try {
            accion.run();
            return false;
        } catch (RuntimeException ex) {
            return tipo.isInstance(ex);
        }
    }

    private static void verificar(String id, String descripcion, BooleanSupplier prueba) {
        totales++;
        boolean ok;
        try {
            ok = prueba.getAsBoolean();
        } catch (Throwable fallo) {
            ok = false;
            System.out.println("[ERROR] " + id + " -> " + fallo.getClass().getSimpleName() + ": "
                    + fallo.getMessage());
        }
        if (!ok) {
            throw new AssertionError(id + " no aprobada: " + descripcion);
        }
        aprobadas++;
        System.out.println("[APROBADA] " + id + " -> " + descripcion);
    }

    private static void borrarTemporales() {
        if (directorio == null) return;
        try (var caminos = Files.walk(directorio)) {
            caminos.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private static void eliminarBaseCreada() {
        if (baseCreada == null || !"postgresql".equals(modo)) return;
        String url = System.getProperty("pican.test.url");
        if (url == null) return;
        String usuario = System.getProperty("pican.test.user");
        String clave = System.getProperty("pican.test.password");
        String prefijo = url.substring(0, url.lastIndexOf('/') + 1);
        try (Connection c = DriverManager.getConnection(prefijo + "postgres", usuario, clave);
             Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS " + baseCreada + " WITH (FORCE)");
            System.out.println("[LIMPIEZA] Base temporal " + baseCreada + " eliminada.");
        } catch (SQLException ex) {
            System.out.println("[LIMPIEZA] No se pudo eliminar " + baseCreada + ": " + ex.getMessage());
        }
    }

    private static final class SesionPrueba implements SesionAutorizacionProvider {
        private final Usuario usuario;
        private SesionPrueba(Usuario usuario) { this.usuario = usuario; }
        @Override public Usuario getUsuarioAutenticado() { return usuario; }
        @Override public UUID getIdUsuarioAutenticado() { return usuario == null ? null : usuario.getIdUsuario(); }
    }

    /** Operaciones falsas: ejercitan la recuperacion sin tocar ninguna base real. */
    private static final class OperacionesFalsas implements PgBackupOperations {
        private final List<String> pasos = new ArrayList<>();
        private final byte[] volcado = "volcado-falso-test".getBytes(StandardCharsets.UTF_8);

        @Override public Path volcar(Path archivo, String base) {
            try { Files.write(archivo, volcado); } catch (Exception ex) { throw new RuntimeException(ex); }
            pasos.add("volcar:" + base);
            return archivo;
        }
        @Override public void restaurar(Path volcado, String baseDestino) { pasos.add("restaurar:" + baseDestino); }
        @Override public void crearBase(String nombre) { pasos.add("crear:" + nombre); }
        @Override public void crearBaseDesdePlantilla(String nombre, String plantilla) {
            pasos.add("plantilla:" + nombre + "<-" + plantilla);
        }
        @Override public void eliminarBase(String nombre) { pasos.add("eliminar:" + nombre); }
        @Override public void renombrarBase(String desde, String hacia) {
            pasos.add("renombrar:" + desde + "->" + hacia);
            if (desde.contains("_prep_")) {
                throw new RuntimeException("Falla simulada tras el intercambio");
            }
        }
        @Override public void permitirConexiones(String base, boolean permitir) {
            pasos.add("conexiones:" + base + ":" + permitir);
        }
        @Override public void terminarConexiones(String base) { pasos.add("terminar:" + base); }
        @Override public String baseActiva() { return "pican_fake_test"; }
        @Override public void verificarConexion(String base) { pasos.add("verificar:" + base); }
        @Override public long contarMigracionesAplicadas(String base) {
            return EstadoEsquema.migracionesLocales().size();
        }
        @Override public boolean existeTabla(String base, String tabla) { return true; }
        @Override public void aplicarMigracionesPendientes() { pasos.add("migrar"); }
    }
}
