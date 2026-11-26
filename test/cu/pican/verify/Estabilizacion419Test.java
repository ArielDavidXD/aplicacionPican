package cu.pican.verify;

import cu.pican.controller.app.AppContext;
import cu.pican.controller.app.UsuarioInicialInitializer;
import cu.pican.model.Rol;
import cu.pican.model.Usuario;
import cu.pican.model.repository.memory.UsuarioRepositoryMemory;
import cu.pican.controller.service.AutorizacionService;
import cu.pican.controller.service.PasswordHasher;
import cu.pican.controller.service.SesionAutorizacionProvider;
import cu.pican.controller.service.UsuarioService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.controller.sync.SyncArchivo;
import cu.pican.controller.sync.SyncJson;
import cu.pican.controller.sync.SyncModelo;
import cu.pican.controller.sync.SyncService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Etapa 4.19: comprobaciones nuevas y acotadas. No repite las baterías 4.14 a 4.18. */
public final class Estabilizacion419Test {

    private static final String CLAVE_ADMIN = System.getProperty("pican.test.admin.password");
    private static final String CLAVE_APP = System.getProperty("pican.test.app.password");

    private static int aprobadas;
    private static int totales;
    private static final List<Path> TEMPORALES = new ArrayList<>();

    public static void main(String[] args) {
        if (CLAVE_ADMIN == null || CLAVE_ADMIN.isBlank() || CLAVE_APP == null || CLAVE_APP.isBlank()) {
            throw new IllegalArgumentException(
                    "Defina -Dpican.test.admin.password y -Dpican.test.app.password antes de ejecutar.");
        }
        try {
            pruebaUltimaCuentaAdministrador();
            pruebaSinSecretoEnConsola();
            pruebaPostgres();
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.out.println("[RESUMEN] " + aprobadas + "/" + totales
                    + " comprobaciones aprobadas; ejecución interrumpida.");
            System.exit(1);
        }
        borrarTemporales();
        System.out.println("=== ESTABILIZACIÓN ETAPA 4.19 ===");
        System.out.println("[RESUMEN] " + aprobadas + "/" + totales + " comprobaciones aprobadas");
        System.exit(0);
    }

    private static void pruebaUltimaCuentaAdministrador() {
        System.setProperty("pican.persistence", "memory");
        System.setProperty("pican.admin.password", CLAVE_ADMIN);
        AppContext contexto = new AppContext();
        UsuarioService usuarios = contexto.getUsuarioService();
        contexto.getAuthService().login(UsuarioInicialInitializer.USUARIO_INICIAL, CLAVE_ADMIN);
        Usuario admin = usuarios.buscarPorNombreUsuario(UsuarioInicialInitializer.USUARIO_INICIAL).orElseThrow();

        esperarValidacion("D-02.a", () -> usuarios.asignarRol(admin.getIdUsuario(), Rol.JEFE_UEB),
                "asignarRol rechaza quitar el rol al único administrador");
        check("D-02.b", Rol.ADMINISTRADOR.equals(
                        usuarios.buscarPorNombreUsuario(UsuarioInicialInitializer.USUARIO_INICIAL)
                                .orElseThrow().getRol().getNombre()),
                "la cuenta administrativa conserva su rol tras el rechazo");
        esperarValidacion("D-02.c",
                () -> usuarios.actualizarDatos(admin.getIdUsuario(), "Nombre cambiado", Rol.JEFE_UEB),
                "actualizarDatos rechaza el mismo cambio por la misma guarda");

        usuarios.registrar("admin_segundo_419", "Segundo administrador", CLAVE_ADMIN, Rol.ADMINISTRADOR);
        Usuario cambiado = usuarios.asignarRol(admin.getIdUsuario(), Rol.JEFE_UEB);
        check("D-02.d", Rol.JEFE_UEB.equals(cambiado.getRol().getNombre()),
                "con otro administrador activo el cambio de rol sí se permite");
        contexto.getAuthService().logout();
    }

    private static void pruebaSinSecretoEnConsola() {
        String anterior = System.getProperty("pican.admin.password");
        System.clearProperty("pican.admin.password");
        AutorizacionService.instalar(new AutorizacionService(new SesionNula()));
        UsuarioService usuarios = new UsuarioService(new UsuarioRepositoryMemory(), new PasswordHasher());

        PrintStream original = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        boolean fallo = false;
        try {
            System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
            UsuarioInicialInitializer.inicializar(usuarios, false);
        } catch (IllegalStateException ex) {
            fallo = true;
        } finally {
            System.setOut(original);
        }
        check("D-03.a", fallo, "sin contraseña configurada, PostgreSQL falla cerrado");
        check("D-03.b", !usuarios.existeAdministradorInicial(),
                "no se crea cuenta administrativa sin credencial configurada");
        check("D-03.c", !captura.toString(StandardCharsets.UTF_8).contains("Contraseña temporal"),
                "no se imprime ninguna contraseña generada");

        System.setProperty("pican.admin.password", CLAVE_ADMIN);
        captura.reset();
        try {
            System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
            UsuarioInicialInitializer.inicializar(usuarios, false);
        } finally {
            System.setOut(original);
        }
        check("D-03.d", usuarios.existeAdministradorInicial(),
                "con credencial configurada se crea la cuenta inicial");
        check("D-03.e", !captura.toString(StandardCharsets.UTF_8).contains(CLAVE_ADMIN),
                "la credencial configurada jamás aparece en consola");

        if (anterior == null) {
            System.clearProperty("pican.admin.password");
        } else {
            System.setProperty("pican.admin.password", anterior);
        }
    }

    private static void pruebaPostgres() throws Exception {
        String url = System.getProperty("pican.test.url");
        String usuario = System.getProperty("pican.test.user");
        String clavePg = System.getProperty("pican.test.password");
        if (url == null || usuario == null || clavePg == null) {
            System.out.println("[OMITIDA] Fase PostgreSQL: faltan -Dpican.test.url/user/password.");
            return;
        }
        if (!url.matches("jdbc:postgresql://[A-Za-z0-9._:-]+/[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("La URL de prueba no es segura para crear una base temporal.");
        }
        String prefijo = url.substring(0, url.lastIndexOf('/') + 1);
        String nombreBase = "pican_estabilidad_419_" + System.currentTimeMillis() + "_test";
        if (!nombreBase.contains("test")) {
            throw new IllegalStateException("La base temporal debe contener 'test' en el nombre.");
        }

        try (Connection c = DriverManager.getConnection(prefijo + "postgres", usuario, clavePg);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE " + nombreBase);
        }
        System.out.println("[TEMPORAL] Base creada: " + nombreBase);

        try {
            System.setProperty("pican.persistence", "postgresql");
            System.setProperty("pican.db.url", prefijo + nombreBase);
            System.setProperty("pican.db.user", usuario);
            System.setProperty("pican.db.password", clavePg);
            System.setProperty("pican.db.migrate", "true");
            System.setProperty("pican.admin.password", CLAVE_ADMIN);

            AppContext contexto = new AppContext();
            contexto.getAuthService().login("admin", CLAVE_ADMIN);
            contexto.getUsuarioService().registrar("jefe_419", "Jefe de UEB 419", CLAVE_APP, Rol.JEFE_UEB);
            contexto.getUsuarioService().registrar("esta_419", "Estadística 419", CLAVE_APP,
                    Rol.ESPECIALISTA_ESTADISTICA);
            contexto.getAuthService().logout();

            UUID uebRechazo = UUID.randomUUID();
            UUID uebConfirmada = UUID.randomUUID();
            String urlBase = prefijo + nombreBase;
            try (Connection c = DriverManager.getConnection(urlBase, usuario, clavePg)) {
                insertarUeb(c, uebRechazo, "URECH419");
                insertarUeb(c, uebConfirmada, "UCONF419");
            }
            UUID paqueteRechazado = insertarPaquete(urlBase, usuario, clavePg, uebRechazo, "URECH419");
            UUID paqueteConfirmado = insertarPaquete(urlBase, usuario, clavePg, uebConfirmada, "UCONF419");
            SyncService sync = contexto.getSyncService();

            // D-01: un RECHAZADO no confirma; una confirmación válida sí marca sincronizado_en.
            Path rechazo = crearConfirmacion(uebRechazo, paqueteRechazado, "RECHAZADO");
            contexto.getAuthService().login("jefe_419", CLAVE_APP);
            SyncModelo.ResultadoImportacion resultadoRechazo = sync.importarConfirmacion(rechazo);
            check("D-01.a", "RECHAZADO".equals(resultadoRechazo.estado),
                    "la importación devuelve estado RECHAZADO");
            check("D-01.b", esNuloEnBase(urlBase, usuario, clavePg,
                            "ueb", "sincronizado_en", "id_ueb", uebRechazo),
                    "un rechazo no escribe sincronizado_en en la UEB de origen");
            check("D-01.c", contarEnBase(urlBase, usuario, clavePg,
                            "sync_exportacion", "tabla", "ueb", "id_registro", uebRechazo.toString()) == 0,
                    "un rechazo no registra version_confirmada");
            check("D-01.d", contarEnBase(urlBase, usuario, clavePg,
                            "sync_paquete", "id_destino", "UEB:" + uebRechazo,
                            "estado", "RECHAZADO") == 1,
                    "el paquete de confirmación queda registrado como RECHAZADO");
            contexto.getAuthService().logout();

            Path confirmado = crearConfirmacion(uebConfirmada, paqueteConfirmado, "IMPORTADO");
            contexto.getAuthService().login("jefe_419", CLAVE_APP);
            SyncModelo.ResultadoImportacion resultadoOk = sync.importarConfirmacion(confirmado);
            check("D-01.e", "CONFIRMADO".equals(resultadoOk.estado),
                    "una confirmación IMPORTADO se procesa como CONFIRMADO");
            check("D-01.f", !esNuloEnBase(urlBase, usuario, clavePg,
                            "ueb", "sincronizado_en", "id_ueb", uebConfirmada),
                    "la confirmación satisfactoria sí escribe sincronizado_en");
            contexto.getAuthService().logout();

            // D-07: el motivo del conflicto no se sobrescribe con una segunda escritura.
            Path antiguo = crearExportacionAntigua(uebRechazo);
            contexto.getAuthService().login("esta_419", CLAVE_APP);
            SyncModelo.ResultadoImportacion importado = sync.importarPaquete(antiguo, "CENTRAL");
            check("D-07.a", "PARCIAL".equals(importado.estado) && importado.cuantos("CONFLICTO") == 1,
                    "el paquete con versión anterior genera un conflicto");
            String detalle = detalleItem(urlBase, usuario, clavePg,
                    UUID.fromString(importado.idPaquete), uebRechazo);
            check("D-07.b", "Versión recibida anterior a la local".equals(detalle),
                    "el motivo original del conflicto se conserva como detalle único");
            contexto.getAuthService().logout();

            // D-06: la confirmación solo incluye conflictos de su paquete de origen.
            UUID idPaqueteImportado = UUID.fromString(importado.idPaquete);
            insertarConflicto(urlBase, usuario, clavePg, paqueteConfirmado, uebConfirmada);
            Path destinoConfirmacion = Files.createTempFile("pican-419-confirmacion-generada-", ".zip");
            TEMPORALES.add(destinoConfirmacion);
            contexto.getAuthService().login("esta_419", CLAVE_APP);
            sync.generarConfirmacion(destinoConfirmacion, idPaqueteImportado);
            contexto.getAuthService().logout();
            Map<String, Object> cargamento = SyncJson.parsearObjeto(SyncArchivo.texto(
                    SyncArchivo.leer(destinoConfirmacion, Set.of("MANIFEST.json", "CONFIRMACION.json"))
                            .get("CONFIRMACION.json")));
            Object conflictos = cargamento.get("conflictos");
            check("D-06.a", conflictos instanceof List && ((List<?>) conflictos).size() == 1,
                    "la confirmación incluye únicamente el conflicto del paquete indicado");
            if (conflictos instanceof List && !((List<?>) conflictos).isEmpty()) {
                Object primero = ((List<?>) conflictos).get(0);
                check("D-06.b", primero instanceof Map
                                && uebRechazo.toString().equals(String.valueOf(((Map<?, ?>) primero).get("idRegistro"))),
                        "el conflicto incluido corresponde al paquete confirmado");
            }
        } finally {
            try (Connection c = DriverManager.getConnection(prefijo + "postgres", usuario, clavePg);
                 Statement st = c.createStatement()) {
                st.executeUpdate("DROP DATABASE IF EXISTS " + nombreBase + " WITH (FORCE)");
                System.out.println("[LIMPIEZA] Base temporal " + nombreBase + " eliminada.");
            } catch (Exception ex) {
                System.out.println("[LIMPIEZA] No se pudo eliminar " + nombreBase + ": " + ex.getMessage());
            }
        }
    }

    private static void insertarUeb(Connection c, UUID id, String codigo) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ueb "
                + "(id_ueb, nombre, codigo, activa, version) VALUES (?,?,?,TRUE,2)")) {
            ps.setObject(1, id, Types.OTHER);
            ps.setString(2, "UEB temporal 4.19 " + codigo);
            ps.setString(3, codigo);
            ps.executeUpdate();
        }
    }

    private static UUID insertarPaquete(String url, String usuario, String clave,
                                        UUID idUeb, String codigo) throws Exception {
        UUID idPaquete = UUID.randomUUID();
        UUID idLote = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(url, usuario, clave);
             PreparedStatement psPaquete = c.prepareStatement("INSERT INTO sync_paquete "
                     + "(id_paquete, tipo, id_ueb_origen, id_destino, version_protocolo, version_esquema, "
                     + "id_lote, cantidad_registros, estado) VALUES (?,?,?,?,?,?,?,?,?)");
             PreparedStatement psItem = c.prepareStatement("INSERT INTO sync_item "
                     + "(id_item, id_paquete, tabla, id_registro, accion, estado, version, detalle) "
                     + "VALUES (?,?,?,?,?,?,?,?)")) {
            psPaquete.setObject(1, idPaquete, Types.OTHER);
            psPaquete.setString(2, SyncService.EXPORTACION);
            psPaquete.setObject(3, idUeb, Types.OTHER);
            psPaquete.setString(4, "CENTRAL");
            psPaquete.setString(5, SyncService.PROTOCOLO);
            psPaquete.setString(6, SyncService.ESQUEMA);
            psPaquete.setObject(7, idLote, Types.OTHER);
            psPaquete.setInt(8, 1);
            psPaquete.setString(9, "GENERADO");
            psPaquete.executeUpdate();

            psItem.setObject(1, UUID.randomUUID(), Types.OTHER);
            psItem.setObject(2, idPaquete, Types.OTHER);
            psItem.setString(3, "ueb");
            psItem.setString(4, idUeb.toString());
            psItem.setString(5, "UPSERT");
            psItem.setString(6, "GENERADO");
            psItem.setLong(7, 2L);
            psItem.setString(8, "Incluido en el paquete " + codigo);
            psItem.executeUpdate();
        }
        return idPaquete;
    }

    private static Path crearConfirmacion(UUID idUeb, UUID idPaqueteOrigen, String estado) throws Exception {
        LinkedHashMap<String, Object> carga = new LinkedHashMap<>();
        carga.put("idPaqueteOrigen", idPaqueteOrigen.toString());
        carga.put("idLote", UUID.randomUUID().toString());
        carga.put("estado", estado);
        carga.put("mensaje", "Prueba 4.19: " + estado);
        carga.put("recibidoEn", LocalDateTime.now().toString());
        carga.put("procesados", List.of());
        carga.put("conflictos", List.of());
        byte[] bytesCarga = SyncArchivo.bytes(SyncJson.escribir(carga));
        LinkedHashMap<String, Object> manifiesto = manifiesto(UUID.randomUUID(), SyncService.CONFIRMACION,
                idUeb, "UEB:" + idUeb, bytesCarga, "CONFIRMACION.json");
        return escribirZip("pican-419-confirmacion-", manifiesto, bytesCarga, "CONFIRMACION.json");
    }

    private static Path crearExportacionAntigua(UUID idUeb) throws Exception {
        SyncModelo.Registro registro = new SyncModelo.Registro();
        registro.tabla = "ueb";
        registro.accion = "UPSERT";
        registro.idRegistro = idUeb.toString();
        registro.version = 1L;
        registro.idUebOrigen = UUID.randomUUID().toString();
        registro.creadoEn = LocalDateTime.now().toString();
        registro.actualizadoEn = registro.creadoEn;
        registro.eliminadoLogico = false;
        registro.datos.put("id_ueb", idUeb.toString());
        registro.datos.put("nombre", "Versión anterior 4.19");
        registro.datos.put("codigo", "UANT419");
        registro.datos.put("activa", "true");
        LinkedHashMap<String, Object> carga = new LinkedHashMap<>();
        carga.put("registros", List.of(registro.aMapa()));
        byte[] bytesCarga = SyncArchivo.bytes(SyncJson.escribir(carga));
        LinkedHashMap<String, Object> manifiesto = manifiesto(UUID.randomUUID(), SyncService.EXPORTACION,
                idUeb, "CENTRAL", bytesCarga, "REGISTROS.json");
        return escribirZip("pican-419-exportacion-", manifiesto, bytesCarga, "REGISTROS.json");
    }

    private static LinkedHashMap<String, Object> manifiesto(UUID idPaquete, String tipo,
                                                             UUID idUebOrigen, String idDestino,
                                                             byte[] bytesCarga, String nombreCarga) {
        LinkedHashMap<String, Object> manifiesto = new LinkedHashMap<>();
        manifiesto.put("formato", "PICAN-SYNC");
        manifiesto.put("versionProtocolo", SyncService.PROTOCOLO);
        manifiesto.put("versionEsquema", SyncService.ESQUEMA);
        manifiesto.put("idPaquete", idPaquete.toString());
        manifiesto.put("tipo", tipo);
        manifiesto.put("idUebOrigen", idUebOrigen.toString());
        manifiesto.put("idDestino", idDestino);
        manifiesto.put("generadoEn", LocalDateTime.now().toString());
        manifiesto.put("idLote", UUID.randomUUID().toString());
        manifiesto.put("cantidadRegistros", "1");
        manifiesto.put("payload", nombreCarga);
        manifiesto.put("payloadSha256", SyncArchivo.sha256(bytesCarga));
        manifiesto.put("generadoPor", UUID.randomUUID().toString());
        manifiesto.put("observaciones", "Prueba aislada de la Etapa 4.19.");
        return manifiesto;
    }

    private static Path escribirZip(String prefijo, LinkedHashMap<String, Object> manifiesto,
                                    byte[] bytesCarga, String nombreCarga) throws Exception {
        LinkedHashMap<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("MANIFEST.json", SyncArchivo.bytes(SyncJson.escribir(manifiesto)));
        entradas.put(nombreCarga, bytesCarga);
        Path ruta = Files.createTempFile(prefijo, ".zip");
        SyncArchivo.escribir(ruta, entradas);
        TEMPORALES.add(ruta);
        return ruta;
    }

    private static void insertarConflicto(String url, String usuario, String clave,
                                          UUID idPaquete, UUID idRegistro) throws Exception {
        try (Connection c = DriverManager.getConnection(url, usuario, clave);
             PreparedStatement ps = c.prepareStatement("INSERT INTO sync_conflicto "
                     + "(id_conflicto, tabla, id_registro, id_paquete, version_recibida, version_local, estado) "
                     + "VALUES (?,?,?,?,?,?,'PENDIENTE')")) {
            ps.setObject(1, UUID.randomUUID(), Types.OTHER);
            ps.setString(2, "ueb");
            ps.setString(3, idRegistro.toString());
            ps.setObject(4, idPaquete, Types.OTHER);
            ps.setLong(5, 1L);
            ps.setLong(6, 2L);
            ps.executeUpdate();
        }
    }

    private static boolean esNuloEnBase(String url, String usuario, String clave,
                                        String tabla, String columna, String columnaWhere,
                                        UUID valor) throws Exception {
        try (Connection c = DriverManager.getConnection(url, usuario, clave);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + columna + " FROM " + tabla + " WHERE " + columnaWhere + " = ?")) {
            ps.setObject(1, valor, Types.OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getObject(1) == null;
            }
        }
    }

    private static String detalleItem(String url, String usuario, String clave,
                                      UUID idPaquete, UUID idRegistro) throws Exception {
        try (Connection c = DriverManager.getConnection(url, usuario, clave);
             PreparedStatement ps = c.prepareStatement("SELECT detalle FROM sync_item "
                     + "WHERE id_paquete = ? AND id_registro = ?")) {
            ps.setObject(1, idPaquete, Types.OTHER);
            ps.setString(2, idRegistro.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static int contarEnBase(String url, String usuario, String clave,
                                    String tabla, String columnaFiltro, String valorFiltro,
                                    String columnaId, String valorId) throws Exception {
        try (Connection c = DriverManager.getConnection(url, usuario, clave);
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + tabla
                     + " WHERE " + columnaFiltro + " = ? AND " + columnaId + " = ?")) {
            ps.setString(1, valorFiltro);
            ps.setString(2, valorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void borrarTemporales() {
        for (Path ruta : TEMPORALES) {
            try {
                Files.deleteIfExists(ruta);
            } catch (Exception ignored) {
                // Limpieza best-effort de archivos temporales de prueba.
            }
        }
    }

    private static void esperarValidacion(String id, Runnable accion, String descripcion) {
        totales++;
        try {
            accion.run();
        } catch (ValidacionException ex) {
            aprobadas++;
            System.out.println("[APROBADA] " + id + " -> " + descripcion);
            return;
        } catch (RuntimeException ex) {
            throw new AssertionError(id + ": se esperaba ValidacionException, se obtuvo " + ex.getMessage(), ex);
        }
        throw new AssertionError(id + ": la operación no fue rechazada");
    }

    private static void check(String id, boolean condicion, String descripcion) {
        totales++;
        if (!condicion) {
            throw new AssertionError("[FALLIDA] " + id + " -> " + descripcion);
        }
        aprobadas++;
        System.out.println("[APROBADA] " + id + " -> " + descripcion);
    }

    private static final class SesionNula implements SesionAutorizacionProvider {
        @Override public Usuario getUsuarioAutenticado() { return null; }
        @Override public UUID getIdUsuarioAutenticado() { return null; }
    }
}
