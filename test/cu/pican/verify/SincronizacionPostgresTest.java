package cu.pican.verify;

import cu.pican.controller.app.AppContext;
import cu.pican.model.Rol;
import cu.pican.controller.sync.SyncArchivo;
import cu.pican.controller.sync.SyncException;
import cu.pican.controller.sync.SyncJson;
import cu.pican.controller.sync.SyncModelo.ResultadoImportacion;
import cu.pican.controller.sync.SyncService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cu.pican.verify.SyncTestSupport.check;
import static cu.pican.verify.SyncTestSupport.contar;
import static cu.pican.verify.SyncTestSupport.contarDonde;
import static cu.pican.verify.SyncTestSupport.crearDatos;
import static cu.pican.verify.SyncTestSupport.contexto;
import static cu.pican.verify.SyncTestSupport.estadoPaquete;
import static cu.pican.verify.SyncTestSupport.ejecutarSql;
import static cu.pican.verify.SyncTestSupport.esNulo;
import static cu.pican.verify.SyncTestSupport.existeFila;
import static cu.pican.verify.SyncTestSupport.registrar;
import static cu.pican.verify.SyncTestSupport.sufijo;
import static cu.pican.verify.SyncTestSupport.valorColumna;

/** Etapa 4.15 · Fases 4 y 5 · Sincronización real por archivos entre dos UEB y la central. */
public final class SincronizacionPostgresTest {

    private static final Path TEMPORAL = Paths.get("test-results", "paquetes-4.15");
    private static final HashSet<String> EXPORT = new HashSet<>(Arrays.asList(
            "MANIFEST.json", "REGISTROS.json"));

    private final List<Resultado> resultados = new ArrayList<>();
    private AppContext uebA;
    private AppContext uebB;
    private AppContext central;
    private UUID idUebA;
    private UUID idParteA;
    private UUID idUebB;
    private UUID idParteB;
    private Path paqueteA;
    private Path paqueteB;
    private UUID idPaqueteA;
    private String nombreJefeA;
    private String nombreJefeB;
    private String nombreEstadistica;

    public static void main(String[] args) {
        int salida;
        try {
            salida = new SincronizacionPostgresTest().ejecutarTodo();
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            salida = 2;
        }
        System.exit(salida);
    }

    private int ejecutarTodo() throws Exception {
        Files.createDirectories(TEMPORAL);
        limpiarPaquetes();
        uebA = contexto("pican_sync_ueb_a_test");
        uebB = contexto("pican_sync_ueb_b_test");
        central = contexto("pican_sync_central_test");

        verificar("SIN-01", "Tres contextos aislados con autenticación por rol", () -> {
            nombreJefeA = registrar(uebA, "sync_jefe_a", Rol.JEFE_UEB);
            nombreJefeB = registrar(uebB, "sync_jefe_b", Rol.JEFE_UEB);
            nombreEstadistica = registrar(central, "sync_est", Rol.ESPECIALISTA_ESTADISTICA);
            uebA.getAuthService().login(nombreJefeA, "ClaveSync415!");
            uebB.getAuthService().login(nombreJefeB, "ClaveSync415!");
            central.getAuthService().login(nombreEstadistica, "ClaveSync415!");
            check(Rol.JEFE_UEB.equals(uebA.getAuthService().getUsuarioAutenticado().getRol().getNombre()),
                    "UEB A no autenticó a un JEFE_DE_UEB");
            check(Rol.ESPECIALISTA_ESTADISTICA.equals(
                    central.getAuthService().getUsuarioAutenticado().getRol().getNombre()),
                    "La central no autenticó a ESPECIALISTA_DE_ESTADISTICA");
            // Los datos operativos se crean sin sesión: no existe relación usuario↔UEB.
            uebA.getAuthService().logout();
            uebB.getAuthService().logout();
            central.getAuthService().logout();
        });

        verificar("SIN-02", "Datos operativos sin vínculo usuario↔UEB", () -> {
            idUebA = UUID.randomUUID();
            idParteA = crearDatos(uebA, idUebA, "UEB A", "SYNCA", LocalDate.of(2026, 5, 10), 111);
            idUebB = UUID.randomUUID();
            idParteB = crearDatos(uebB, idUebB, "UEB B", "SYNCB", LocalDate.of(2026, 5, 11), 222);
            check(existeFila(uebA, "parte_nave", idParteA), "No se creó el parte de la UEB A");
            check(existeFila(uebB, "parte_nave", idParteB), "No se creó el parte de la UEB B");
            check(esNulo(uebA, "parte_nave", idParteA, "id_usuario"),
                    "El parte quedó ligado a un usuario no sincronizable");
        });

        verificar("SIN-03", "Generación de ZIP con manifiesto e integridad", () -> {
            uebA.getAuthService().login(nombreJefeA, "ClaveSync415!");
            uebB.getAuthService().login(nombreJefeB, "ClaveSync415!");
            central.getAuthService().login(nombreEstadistica, "ClaveSync415!");
            paqueteA = TEMPORAL.resolve("ueb-a.zip");
            paqueteB = TEMPORAL.resolve("ueb-b.zip");
            idPaqueteA = uebA.getSyncService().generarExportacion(paqueteA, idUebA);
            uebB.getSyncService().generarExportacion(paqueteB, idUebB);
            check(Files.size(paqueteA) > 0 && Files.size(paqueteB) > 0, "Archivos de paquete vacíos");
            LinkedHashMap<String, byte[]> entradas = SyncArchivo.leer(paqueteA, EXPORT);
            Map<String, Object> manifiesto = SyncJson.parsearObjeto(SyncArchivo.texto(entradas.get("MANIFEST.json")));
            check("PICAN-SYNC".equals(manifiesto.get("formato")), "Formato de manifiesto incorrecto");
            check(SyncArchivo.hashCoincide(String.valueOf(manifiesto.get("payloadSha256")),
                    entradas.get("REGISTROS.json")), "SHA-256 del manifiesto no coincide");
            check(esNulo(uebA, "parte_nave", idParteA, "sincronizado_en"),
                    "Crear el archivo marcó el registro como sincronizado");
            check("GENERADO".equals(estadoPaquete(uebA, idPaqueteA)), "El lote no quedó registrado como GENERADO");
        });

        verificar("SIN-04", "Validación del paquete en la central", () -> {
            SyncService.PaqueteLeido leido = central.getSyncService().validarPaquete(paqueteA);
            check(idPaqueteA.toString().equals(leido.idPaquete()), "El paquete leído tiene otro identificador");
            check("CENTRAL".equals(leido.idDestino()), "Destino del paquete incorrecto");
            check(leido.cantidad() >= 3, "El paquete no incluye catálogo y operación");
            check(leido.registros.stream().noneMatch(r ->
                    "usuario".equals(r.tabla) || "rol".equals(r.tabla)), "El paquete contiene datos de seguridad");
        });

        verificar("SIN-05", "Importación transaccional en la central", () -> {
            ResultadoImportacion resultado = central.getSyncService().importarPaquete(paqueteA, "CENTRAL");
            check("IMPORTADO".equals(resultado.estado), "La importación no quedó IMPORTADO: " + resultado.mensaje);
            check(existeFila(central, "parte_nave", idParteA), "La operación de UEB A no llegó a la central");
            check(idUebA.toString().equals(valorColumna(central, "parte_nave", idParteA, "id_ueb_origen")),
                    "No se conservó la UEB de origen");
            check(valorColumna(central, "parte_nave", idParteA, "id_lote_sincronizacion") != null,
                    "No se registró el lote de sincronización en destino");
            check("IMPORTADO".equals(estadoPaquete(central, idPaqueteA)), "Estado de paquete incorrecto en central");
        });

        verificar("SIN-06", "Idempotencia ante reimportación del mismo paquete", () -> {
            int antes = (int) contar(central, "parte_nave");
            ResultadoImportacion resultado = central.getSyncService().importarPaquete(paqueteA, "CENTRAL");
            check("OMITIDO".equals(resultado.estado), "El paquete repetido no se omitió");
            check(contar(central, "parte_nave") == antes, "La repetición insertó filas duplicadas");
        });

        verificar("SIN-07", "Autorización real en servicios, no solo en menús", () -> {
            expectSync(() -> uebA.getSyncService().importarPaquete(paqueteA, "CENTRAL"),
                    "JEFE_DE_UEB no debería importar en la central");
            expectSync(() -> central.getSyncService().generarExportacion(TEMPORAL.resolve("no-export.zip"), idUebA),
                    "ESPECIALISTA_DE_ESTADISTICA no debería generar paquetes de UEB");
            String admin = registrar(central, "sync_admin", Rol.ADMINISTRADOR);
            central.getAuthService().logout();
            central.getAuthService().login(admin, "ClaveSync415!");
            expectSync(() -> central.getSyncService().consultarHistorial(),
                    "ADMINISTRADOR no debería consultar sincronización");
            central.getAuthService().logout();
            central.getAuthService().login(nombreEstadistica, "ClaveSync415!");
        });

        verificar("SIN-08", "Rechazo de corrupción del contenido", () -> {
            Path corrupto = TEMPORAL.resolve("corrupto.zip");
            LinkedHashMap<String, byte[]> entradas = SyncArchivo.leer(paqueteA, EXPORT);
            byte[] payload = entradas.get("REGISTROS.json").clone();
            payload[10] = (byte) (payload[10] == 'x' ? 'y' : 'x');
            entradas.put("REGISTROS.json", payload);
            SyncArchivo.escribir(corrupto, entradas);
            expectSync(() -> central.getSyncService().importarPaquete(corrupto, "CENTRAL"),
                    "Un paquete modificado debería rechazarse por integridad");
        });

        verificar("SIN-09", "Rechazo de versión incompatible y destino incorrecto", () -> {
            expectSync(() -> central.getSyncService().importarPaquete(
                    editarManifiesto(paqueteA, "versionProtocolo", "9.9", "version-incompatible.zip"), "CENTRAL"),
                    "Debería rechazarse una versión de protocolo desconocida");
            expectSync(() -> central.getSyncService().importarPaquete(
                    editarManifiesto(paqueteA, "idDestino", "UEB:/otra", "destino-incorrecto.zip"), "CENTRAL"),
                    "Debería rechazarse un paquete dirigido a otra instancia");
        });

        verificar("SIN-10", "Rechazo de Zip Slip y archivos incompletos", () -> {
            Path conRuta = TEMPORAL.resolve("zip-slip.zip");
            LinkedHashMap<String, byte[]> entradas = SyncArchivo.leer(paqueteA, EXPORT);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(conRuta))) {
                for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entrada.getKey()));
                    zip.write(entrada.getValue());
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("../evil.txt"));
                zip.write("intento".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            expectSync(() -> central.getSyncService().importarPaquete(conRuta, "CENTRAL"),
                    "Debería rechazarse una entrada con ruta relativa");
            Path incompleto = TEMPORAL.resolve("incompleto.zip");
            LinkedHashMap<String, byte[]> soloManifiesto = new LinkedHashMap<>();
            soloManifiesto.put("MANIFEST.json", entradas.get("MANIFEST.json"));
            SyncArchivo.escribir(incompleto, soloManifiesto);
            expectSync(() -> central.getSyncService().importarPaquete(incompleto, "CENTRAL"),
                    "Debería rechazarse un paquete sin REGISTROS.json");
        });

        verificar("SIN-11", "Conflictos detectados sin resolución automática", () -> {
            ejecutarSql(central, "UPDATE parte_nave SET version = 2 WHERE id_parte = ?", idParteA);
            check("2".equals(valorColumna(central, "parte_nave", idParteA, "version")),
                    "No se pudo elevar la versión local de prueba");
            Path antiguo = TEMPORAL.resolve("paquete-antiguo.zip");
            LinkedHashMap<String, byte[]> entradas = SyncArchivo.leer(paqueteA, EXPORT);
            String manifiesto = SyncArchivo.texto(entradas.get("MANIFEST.json"))
                    .replaceFirst("\"idPaquete\":\"[^\"]+\"", "\"idPaquete\":\"" + UUID.randomUUID() + "\"");
            entradas.put("MANIFEST.json", SyncArchivo.bytes(manifiesto));
            SyncArchivo.escribir(antiguo, entradas);
            ResultadoImportacion resultado = central.getSyncService().importarPaquete(antiguo, "CENTRAL");
            check(resultado.cuantos("CONFLICTO") >= 1, "No se detectó el conflicto de versión");
            check("2".equals(valorColumna(central, "parte_nave", idParteA, "version")),
                    "El motor sobrescribió la versión local más reciente");
            check(contar(central, "sync_conflicto") >= 1, "No se conservó evidencia del conflicto");
            check(central.getSyncService().consultarConflictos().stream()
                    .allMatch(c -> "PENDIENTE".equals(String.valueOf(c.get("estado")))),
                    "Los conflictos no quedaron pendientes de decisión humana");
        });

        verificar("SIN-12", "Segunda UEB sin mezclar orígenes", () -> {
            ResultadoImportacion resultado = central.getSyncService().importarPaquete(paqueteB, "CENTRAL");
            check("IMPORTADO".equals(resultado.estado), "El paquete de UEB B no se importó: " + resultado.mensaje);
            check(existeFila(central, "parte_nave", idParteB), "Falta la operación de UEB B en la central");
            check(idUebA.toString().equals(valorColumna(central, "parte_nave", idParteA, "id_ueb_origen")),
                    "La operación de UEB A cambió de origen");
            check(idUebB.toString().equals(valorColumna(central, "parte_nave", idParteB, "id_ueb_origen")),
                    "La operación de UEB B no conservó su origen");
        });

        verificar("SIN-13", "Confirmación de ida y vuelta y cierre del lote", () -> {
            Path confirmacion = TEMPORAL.resolve("confirmacion.zip");
            central.getSyncService().generarConfirmacion(confirmacion, idPaqueteA);
            check(Files.size(confirmacion) > 0, "No se generó el archivo de confirmación");
            ResultadoImportacion resultado = uebA.getSyncService().importarConfirmacion(confirmacion);
            check("CONFIRMADO".equals(resultado.estado), "La UEB A no confirmó: " + resultado.mensaje);
            check(valorColumna(uebA, "parte_nave", idParteA, "sincronizado_en") != null,
                    "sincronizado_en no se actualizó tras la confirmación");
            check(valorColumna(uebA, "parte_nave", idParteA, "id_lote_sincronizacion") != null,
                    "id_lote_sincronizacion no se actualizó tras la confirmación");
            check(contarDonde(uebA, "sync_exportacion", "id_registro", idParteA.toString()) == 1,
                    "No se registró el control de exportación confirmada");
            Path siguiente = TEMPORAL.resolve("siguiente.zip");
            uebA.getSyncService().generarExportacion(siguiente, idUebA);
            LinkedHashMap<String, byte[]> entradas = SyncArchivo.leer(siguiente, EXPORT);
            Map<String, Object> payload = SyncJson.parsearObjeto(SyncArchivo.texto(entradas.get("REGISTROS.json")));
            Object registros = payload.get("registros");
            check(registros instanceof List && ((List<?>) registros).stream().noneMatch(
                    r -> idParteA.toString().equals(String.valueOf(((Map<?, ?>) r).get("idRegistro")))),
                    "El registro confirmado volvió a exportarse");
        });

        verificar("SIN-14", "MEMORY rechaza la sincronización sin simularla", () -> {
            System.setProperty("pican.persistence", "memory");
            try {
                AppContext memoria = new AppContext();
                expectSync(memoria::getSyncService,
                        "El modo MEMORY no debería ofrecer sincronización persistente");
                check(!memoria.esPostgresql(), "El contexto no quedó en MEMORY");
            } finally {
                System.setProperty("pican.persistence", "postgresql");
            }
        });

        long fallos = resultados.stream().filter(r -> !"APROBADA".equals(r.estado)).count();
        imprimirInforme();
        escribirInforme();
        System.out.println("[RESUMEN] " + (resultados.size() - fallos) + "/" + resultados.size()
                + " comprobaciones aprobadas");
        return fallos == 0 ? 0 : 1;
    }

    private void limpiarPaquetes() throws Exception {
        if (Files.isDirectory(TEMPORAL)) {
            try (java.util.stream.Stream<Path> archivos = Files.list(TEMPORAL)) {
                for (Path archivo : (Iterable<Path>) archivos::iterator) {
                    Files.deleteIfExists(archivo);
                }
            }
        }
    }

    private static Path editarManifiesto(Path origen, String clave, String valor, String nombreDestino) {
        try {
            LinkedHashMap<String, byte[]> entradas = SyncArchivo.leer(origen, EXPORT);
            String manifiesto = SyncArchivo.texto(entradas.get("MANIFEST.json"))
                    .replaceFirst("\"" + clave + "\":\"[^\"]*\"", "\"" + clave + "\":\"" + valor + "\"");
            entradas.put("MANIFEST.json", SyncArchivo.bytes(manifiesto));
            Path destino = TEMPORAL.resolve(nombreDestino);
            SyncArchivo.escribir(destino, entradas);
            return destino;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo editar el manifiesto de prueba", ex);
        }
    }

    private static void expectSync(Runnable operacion, String motivo) {
        try {
            operacion.run();
            throw new AssertionError("Se esperaba rechazo: " + motivo);
        } catch (SyncException ex) {
            // Resultado esperado.
        }
    }

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

    private void imprimirInforme() {
        System.out.println("=== SINCRONIZACIÓN UEB ↔ CENTRAL · ETAPA 4.15 · FASES 4-5 ===");
        for (Resultado r : resultados) {
            System.out.println(r.estado + " | " + r.id + " | " + r.esperado + " | " + r.obtenido);
        }
    }

    private void escribirInforme() {
        StringBuilder texto = new StringBuilder("SINCRONIZACIÓN UEB ↔ CENTRAL · ETAPA 4.15\n\n");
        for (Resultado r : resultados) {
            texto.append(r.estado).append(" | ").append(r.id).append(" | ")
                    .append(r.esperado).append(" | ").append(r.obtenido).append('\n');
        }
        try {
            Files.write(Paths.get("test-results", "RESULTADOS_SINCRONIZACION_4.15.txt"),
                    texto.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
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
