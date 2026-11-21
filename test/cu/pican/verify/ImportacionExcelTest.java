package cu.pican.verify;

import cu.pican.app.AppContext;
import cu.pican.importacion.ImportacionException;
import cu.pican.importacion.ResultadoImportacion;
import cu.pican.importacion.TipoImportacion;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.Rol;
import cu.pican.model.TablaHistorica;
import cu.pican.model.Usuario;
import cu.pican.persistence.JdbcSupport;
import cu.pican.persistence.NoOpTransactionManager;
import cu.pican.repository.ImportacionReferenciasRepository;
import cu.pican.repository.jdbc.ImportacionReferenciasRepositoryPostgres;
import cu.pican.repository.memory.ImportacionReferenciasRepositoryMemory;
import cu.pican.repository.memory.IndicadorHistoricoRepositoryMemory;
import cu.pican.repository.memory.MetaEstrategicaRepositoryMemory;
import cu.pican.service.AutorizacionException;
import cu.pican.service.AutorizacionService;
import cu.pican.service.CasoUso;
import cu.pican.service.ImportacionService;
import cu.pican.service.SesionAutorizacionProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Etapa 4.17 · CU-18 con archivos .xlsx generados en la prueba (sin Apache POI). */
public final class ImportacionExcelTest {

    private static final String CLAVE = System.getProperty("pican.test.admin.password");

    private static Path directorio;
    private static int aprobadas;
    private static int totales;

    public static void main(String[] args) {
        String modo = args.length == 0 ? "memoria" : args[0];
        try {
            configurar(modo);
            directorio = Files.createTempDirectory("pican-importacion-test-");
            System.out.println("=== IMPORTACIÓN CU-18 (4.17) · " + modo.toUpperCase(Locale.ROOT) + " ===");
            AppContext contexto = new AppContext();
            ejecutarTodo(contexto, modo);
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            System.exit(2);
        } finally {
            borrarTemporales();
        }
    }

    private static void configurar(String modo) {
        if (CLAVE == null || CLAVE.isBlank()) {
            throw new IllegalArgumentException("Falta -Dpican.test.admin.password.");
        }
        System.setProperty("pican.admin.password", CLAVE);
        if ("postgresql".equals(modo)) {
            String url = System.getProperty("pican.test.url");
            String usuario = System.getProperty("pican.test.user");
            String clave = System.getProperty("pican.test.password");
            if (url == null || usuario == null || clave == null) {
                throw new IllegalArgumentException("Faltan -Dpican.test.url, -Dpican.test.user o -Dpican.test.password.");
            }
            String base = url.split("\\?", 2)[0];
            base = base.substring(base.lastIndexOf('/') + 1);
            if (!base.toLowerCase(Locale.ROOT).contains("test")) {
                throw new IllegalArgumentException("Base no exclusiva de pruebas: " + base);
            }
            System.setProperty("pican.persistence", "postgresql");
            System.setProperty("pican.db.url", url);
            System.setProperty("pican.db.user", usuario);
            System.setProperty("pican.db.password", clave);
            System.setProperty("pican.db.migrate", "true");
        } else {
            System.setProperty("pican.persistence", "memory");
        }
    }

    private static void ejecutarTodo(AppContext contexto, String modo) throws Exception {
        registrarCuentas(contexto);

        verificar("IMP-01", "CU-18 implementado y reservado al ADMINISTRADOR en la matriz",
                () -> contexto.getImportacionService() != null
                        && CasoUso.CU_18.getRolesPermitidos().size() == 1
                        && CasoUso.CU_18.getRolesPermitidos().contains(Rol.ADMINISTRADOR));

        contexto.getAuthService().logout();
        verificar("IMP-02", "Sin sesión: preparar importación rechazado",
                () -> rechaza(() -> contexto.getImportacionService().preparar(TipoImportacion.TABLA_1, null),
                        AutorizacionException.class));

        entrar(contexto, "jefe417", CLAVE);
        verificar("IMP-03", "JEFE no puede preparar ni confirmar CU-18",
                () -> rechaza(() -> contexto.getImportacionService().preparar(TipoImportacion.TABLA_1, null),
                        AutorizacionException.class)
                        && rechaza(() -> contexto.getImportacionService().confirmar(null),
                        AutorizacionException.class));

        entrar(contexto, "admin417", CLAVE);
        verificar("IMP-04", "ADMIN ve el servicio en AppContext; otros roles no",
                () -> contexto.getImportacionService() != null
                        && !CasoUso.CU_05.getRolesPermitidos().contains(Rol.ADMINISTRADOR));

        Path cabeceraMala = crearXlsx("cabecera-mala-test.xlsx",
                List.of("indicador", "anio", "valor"), List.of(List.of("Cantidad Naves", "2019", "222")), Set.of());
        PathsServicio local = crearServicioLocal(contexto, modo);
        local.sesion.usuario = usuario(Rol.ADMINISTRADOR);

        verificar("IMP-05", "Error estructural de cabeceras detiene sin escribir",
                () -> rechaza(() -> local.servicio.preparar(TipoImportacion.TABLA_1, cabeceraMala),
                        ImportacionException.class)
                        && !local.destino.existeIndicador(TablaHistorica.INFRAESTRUCTURA, "Cantidad Naves", 2019));

        Path extension = directorio.resolve("extension-test.xls");
        Files.writeString(extension, "no excel", StandardCharsets.UTF_8);
        verificar("IMP-06", "Extensión distinta de .xlsx rechazada",
                () -> rechaza(() -> local.servicio.preparar(TipoImportacion.TABLA_1, extension),
                        ImportacionException.class));

        Path roto = directorio.resolve("roto-test.xlsx");
        Files.writeString(roto, "esto no es un zip", StandardCharsets.UTF_8);
        verificar("IMP-07", "Archivo ZIP inválido rechazado",
                () -> rechaza(() -> local.servicio.preparar(TipoImportacion.TABLA_1, roto),
                        ImportacionException.class));

        Path trayectoria = directorio.resolve("traversal-test.xlsx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(trayectoria), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("xl/../mal.xml"));
            zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        verificar("IMP-08", "Entrada ZIP con ../ rechazada",
                () -> rechaza(() -> local.servicio.preparar(TipoImportacion.TABLA_1, trayectoria),
                        ImportacionException.class));

        Path tabla1 = crearXlsx("tabla1-test.xlsx",
                List.of("indicador", "anio", "valor", "unidad_medida"),
                List.of(
                        List.of("Cantidad Naves", "2019", "222", "naves"),
                        List.of("Cantidad Naves", "2019", "999", "naves"),
                        List.of("Cantidad Naves", "2019", "222", "naves"),
                        List.of("Tractores", "2019", "44", "tractores")),
                Set.of(1, 2));

        ResultadoImportacion[] vista = new ResultadoImportacion[1];
        verificar("IMP-09", "Válidas, rechazadas y repetidas clasificadas por fila", () -> {
            vista[0] = local.servicio.preparar(TipoImportacion.TABLA_1, tabla1);
            return vista[0].getValidas() == 2
                    && vista[0].getRechazadas() == 1
                    && vista[0].getRepetidas() == 1
                    && vista[0].getFilasLeidas() == 4;
        });

        verificar("IMP-10", "La vista previa no escribió nada",
                () -> !local.destino.existeIndicador(TablaHistorica.INFRAESTRUCTURA, "Cantidad Naves", 2019));

        verificar("IMP-11", "Confirmación carga solo filas válidas en " + modo.toUpperCase(Locale.ROOT), () -> {
            ResultadoImportacion confirmada = local.servicio.confirmar(vista[0]);
            return confirmada.isConfirmada()
                    && confirmada.getEscritas() == 2
                    && local.destino.existeIndicador(TablaHistorica.INFRAESTRUCTURA, "Cantidad Naves", 2019)
                    && local.destino.existeIndicador(TablaHistorica.INFRAESTRUCTURA, "Tractores", 2019);
        });

        Path tabla4 = crearXlsx("tabla4-test.xlsx",
                List.of("indicador", "valor_real_2019", "valor_estimado_2030",
                        "incremento_porcentual", "unidad_medida"),
                List.of(List.of("Huevos (millones)", "211,9", "241,4", "13,92", "millones")), Set.of());
        verificar("IMP-12", "Coma decimal de la Tabla 4 aceptada sin transformar el valor", () -> {
            ResultadoImportacion preparada = local.servicio.preparar(TipoImportacion.TABLA_4, tabla4);
            ResultadoImportacion confirmada = local.servicio.confirmar(preparada);
            return confirmada.getEscritas() == 1 && local.destino.existeMeta("Huevos (millones)");
        });

        Path otra = crearXlsx("sesion-cerrada-test.xlsx",
                List.of("indicador", "anio", "valor", "unidad_medida"),
                List.of(List.of("Naves Ponedoras", "2019", "105", "naves")), Set.of(1, 2));
        verificar("IMP-13", "Sesión cerrada tras la vista previa impide confirmar", () -> {
            ResultadoImportacion pendiente = local.servicio.preparar(TipoImportacion.TABLA_1, otra);
            local.sesion.usuario = null;
            boolean rechazada = rechaza(() -> local.servicio.confirmar(pendiente), AutorizacionException.class);
            local.sesion.usuario = usuario(Rol.ADMINISTRADOR);
            return rechazada
                    && !local.destino.existeIndicador(TablaHistorica.INFRAESTRUCTURA, "Naves Ponedoras", 2019);
        });

        local.sesion.usuario = usuario(Rol.JEFE_UEB);
        verificar("IMP-14", "Servicio directo rechaza a JEFE en preparar y confirmar",
                () -> rechaza(() -> local.servicio.preparar(TipoImportacion.TABLA_1, tabla1),
                        AutorizacionException.class)
                        && rechaza(() -> local.servicio.confirmar(vista[0]), AutorizacionException.class));

        local.sesion.usuario = null;
        verificar("IMP-15", "Sin sesión el servicio directo falla cerrado",
                () -> rechaza(() -> local.servicio.preparar(TipoImportacion.TABLA_1, tabla1),
                        AutorizacionException.class));

        System.out.println("=== RESULTADO " + modo.toUpperCase(Locale.ROOT) + ": "
                + aprobadas + "/" + totales + " APROBADAS ===");
        if (aprobadas != totales) {
            throw new AssertionError("Faltaron comprobaciones en " + modo);
        }
    }

    private static PathsServicio crearServicioLocal(AppContext contexto, String modo) {
        SesionPrueba sesion = new SesionPrueba();
        AutorizacionService autoridad = new AutorizacionService(sesion);
        AutorizacionService.instalar(autoridad);

        ImportacionReferenciasRepository destino;
        if ("postgresql".equals(modo)) {
            limpiarReferenciasPostgres(contexto);
            destino = new ImportacionReferenciasRepositoryPostgres(contexto.getConexiones());
        } else {
            IndicadorHistoricoRepositoryMemory indicadores = new IndicadorHistoricoRepositoryMemory();
            MetaEstrategicaRepositoryMemory metas = new MetaEstrategicaRepositoryMemory();
            destino = new ImportacionReferenciasRepositoryMemory(indicadores, metas);
        }
        var transacciones = "postgresql".equals(modo)
                ? contexto.getTransactionManager() : new NoOpTransactionManager();
        ImportacionService servicio = new ImportacionService(destino, transacciones);
        servicio.setAutorizacion(autoridad);
        return new PathsServicio(servicio, destino, sesion);
    }

    private static void limpiarReferenciasPostgres(AppContext contexto) {
        Connection c = contexto.getConexiones().obtenerConexion();
        boolean cerrar = c != JdbcSupport.conexionActiva();
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM indicador_historico");
            st.executeUpdate("DELETE FROM meta_estrategica");
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudieron vaciar las tablas oficiales de prueba.", ex);
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

    private static void registrarCuentas(AppContext contexto) {
        contexto.getAuthService().login("admin", CLAVE);
        crear(contexto, "admin417", Rol.ADMINISTRADOR);
        crear(contexto, "jefe417", Rol.JEFE_UEB);
        contexto.getAuthService().logout();
    }

    private static void crear(AppContext contexto, String nombre, String rol) {
        if (contexto.getUsuarioService().buscarPorNombreUsuario(nombre).isPresent()) {
            return;
        }
        contexto.getUsuarioService().registrar(nombre, "Prueba " + rol, CLAVE, rol);
    }

    private static void entrar(AppContext contexto, String nombre, String clave) {
        contexto.getAuthService().logout();
        contexto.getAuthService().login(nombre, clave);
    }

    private static Usuario usuario(String rol) {
        return new Usuario(UUID.randomUUID(), "importador417", "Importador de prueba", null,
                Rol.predefinido(rol), true, LocalDateTime.now(), false);
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

    private static Path crearXlsx(String nombre, List<String> cabeceras, List<List<String>> filas,
                                  Set<Integer> numericas) throws IOException {
        Path archivo = directorio.resolve(nombre);
        Map<String, Integer> compartidas = new LinkedHashMap<>();
        registrarCadenas(cabeceras, numericas, compartidas);
        for (List<String> fila : filas) {
            registrarCadenas(fila, numericas, compartidas);
        }

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archivo), StandardCharsets.UTF_8)) {
            escribir(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                            + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
                            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                            + "</Types>");
            escribir(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");
            escribir(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<sheets><sheet name=\"Datos\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            escribir(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                            + "</Relationships>");

            StringBuilder sst = new StringBuilder("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"")
                    .append(compartidas.size()).append("\" uniqueCount=\"").append(compartidas.size()).append("\">");
            for (String texto : compartidas.keySet()) {
                sst.append("<si><t>").append(escapar(texto)).append("</t></si>");
            }
            sst.append("</sst>");
            escribir(zip, "xl/sharedStrings.xml", sst.toString());

            StringBuilder hoja = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            hoja.append(filaXml(1, cabeceras, numericas, compartidas));
            for (int i = 0; i < filas.size(); i++) {
                hoja.append(filaXml(i + 2, filas.get(i), numericas, compartidas));
            }
            hoja.append("</sheetData></worksheet>");
            escribir(zip, "xl/worksheets/sheet1.xml", hoja.toString());
        }
        return archivo;
    }

    private static void registrarCadenas(List<String> celdas, Set<Integer> numericas, Map<String, Integer> destino) {
        for (int i = 0; i < celdas.size(); i++) {
            if (!numericas.contains(i)) {
                destino.computeIfAbsent(celdas.get(i), clave -> destino.size());
            }
        }
    }

    private static String filaXml(int numero, List<String> celdas, Set<Integer> numericas,
                                  Map<String, Integer> compartidas) {
        StringBuilder fila = new StringBuilder("<row r=\"").append(numero).append("\">");
        for (int i = 0; i < celdas.size(); i++) {
            String referencia = letra(i) + numero;
            String valor = celdas.get(i);
            if (numericas.contains(i)) {
                fila.append("<c r=\"").append(referencia).append("\"><v>")
                        .append(escapar(valor)).append("</v></c>");
            } else {
                Integer indice = compartidas.get(valor);
                if (indice == null) {
                    throw new IllegalStateException("Celda sin cadena compartida: " + valor);
                }
                fila.append("<c r=\"").append(referencia).append("\" t=\"s\"><v>")
                        .append(indice).append("</v></c>");
            }
        }
        return fila.append("</row>").toString();
    }

    private static String letra(int indice) {
        return String.valueOf((char) ('A' + indice));
    }

    private static void escribir(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void borrarTemporales() {
        if (directorio == null) {
            return;
        }
        try (var caminos = Files.walk(directorio)) {
            caminos.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Limpieza best-effort de archivos de prueba.
                }
            });
        } catch (IOException ignored) {
            // Limpieza best-effort de archivos de prueba.
        }
    }

    private static final class SesionPrueba implements SesionAutorizacionProvider {
        private Usuario usuario;

        @Override
        public Usuario getUsuarioAutenticado() {
            return usuario;
        }

        @Override
        public UUID getIdUsuarioAutenticado() {
            return usuario == null ? null : usuario.getIdUsuario();
        }
    }

    private static final class PathsServicio {
        private final ImportacionService servicio;
        private final ImportacionReferenciasRepository destino;
        private final SesionPrueba sesion;

        private PathsServicio(ImportacionService servicio, ImportacionReferenciasRepository destino,
                              SesionPrueba sesion) {
            this.servicio = servicio;
            this.destino = destino;
            this.sesion = sesion;
        }
    }
}
