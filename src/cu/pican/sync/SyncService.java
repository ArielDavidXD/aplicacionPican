package cu.pican.sync;

import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.TransactionManager;
import cu.pican.service.AutorizacionException;
import cu.pican.service.AutorizacionService;
import cu.pican.service.AuthService;
import cu.pican.service.CasoUso;
import cu.pican.sync.SyncModelo.Conflicto;
import cu.pican.sync.SyncModelo.ItemResultado;
import cu.pican.sync.SyncModelo.Registro;
import cu.pican.sync.SyncModelo.ResultadoImportacion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Servicio de sincronización por archivos. Toda operación comprueba el rol del actor
 * antes de leer o escribir. La UI nunca es la frontera de seguridad.
 */
public final class SyncService {

    public static final String PROTOCOLO = "1.0";
    public static final String ESQUEMA = "V1+V2";
    public static final String EXPORTACION = "EXPORTACION_UEB";
    public static final String CONFIRMACION = "CONFIRMACION";
    public static final String CONFLICTOS = "CONFLICTOS";

    private static final Set<String> ENTRADAS_PERMITIDAS = new HashSet<>(Arrays.asList(
            "MANIFEST.json", "REGISTROS.json", "CONFIRMACION.json"));
    private static final Set<String> ENTRADAS_EXPORTACION = new HashSet<>(Arrays.asList(
            "MANIFEST.json", "REGISTROS.json"));
    private static final Set<String> ENTRADAS_CONFIRMACION = new HashSet<>(Arrays.asList(
            "MANIFEST.json", "CONFIRMACION.json"));

    private final TransactionManager transacciones;
    private final SyncRepositorio repositorio;
    private final AuthService authService;
    private final AutorizacionService autorizacion;
    private BooleanSupplier bloqueoPostRestauracion = () -> false;

    public SyncService(TransactionManager transacciones, JdbcConnectionManager conexiones, AuthService authService) {
        if (transacciones == null || authService == null) {
            throw new SyncException("Faltan dependencias del servicio de sincronización.");
        }
        this.transacciones = transacciones;
        this.repositorio = new SyncRepositorio(conexiones);
        this.authService = authService;
        this.autorizacion = new AutorizacionService(authService);
    }

    /** SIN-13: inyectado desde AppContext; bloquea la generacion de paquetes tras restauracion LOCAL. */
    public void setBloqueoPostRestauracion(BooleanSupplier bloqueoPostRestauracion) {
        this.bloqueoPostRestauracion = bloqueoPostRestauracion == null ? () -> false : bloqueoPostRestauracion;
    }

    private void verificarBloqueoPostRestauracion() {
        if (bloqueoPostRestauracion.getAsBoolean()) {
            throw new SyncException(
                    "SIN-13: la UEB fue restaurada; no se pueden generar paquetes hasta importar la bajada completa del central.");
        }
    }

    // -------------------------------------------------------------------------
    // CU-08 · UEB
    // -------------------------------------------------------------------------

    public UUID generarExportacion(Path destino, UUID idUebOrigen) {
        verificarBloqueoPostRestauracion();
        requerir(CasoUso.CU_08);
        if (idUebOrigen == null) {
            throw new SyncException("Debe identificarse la UEB de origen del paquete.");
        }
        UUID idPaquete = UUID.randomUUID();
        UUID idLote = UUID.randomUUID();
        LocalDateTime ahora = LocalDateTime.now();

        List<Registro> pendientes = repositorio.listarPendientes(idUebOrigen);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        List<Object> registros = new ArrayList<>();
        for (Registro registro : pendientes) {
            registros.add(registro.aMapa());
        }
        payload.put("registros", registros);
        byte[] bytesPayload = SyncArchivo.bytes(SyncJson.escribir(payload));

        LinkedHashMap<String, Object> manifestacion = manifiestoBasico(
                idPaquete, EXPORTACION, idUebOrigen, "CENTRAL", ahora, idLote,
                pendientes.size(), bytesPayload, "REGISTROS.json");

        LinkedHashMap<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("MANIFEST.json", SyncArchivo.bytes(SyncJson.escribir(manifestacion)));
        entradas.put("REGISTROS.json", bytesPayload);
        SyncArchivo.escribir(destino, entradas);

        UUID idUsuario = authService.getIdUsuarioAutenticado();
        transacciones.ejecutar(() -> {
            repositorio.registrarPaquete(manifestacion, "GENERADO", null, idUsuario);
            for (Registro registro : pendientes) {
                repositorio.registrarItem(idPaquete, new ItemResultado(
                        registro.tabla, registro.idRegistro, "GENERADO", "Incluido en el paquete"));
            }
            return null;
        });
        return idPaquete;
    }

    public ResultadoImportacion importarConfirmacion(Path origen) {
        requerir(CasoUso.CU_08);
        PaqueteLeido paquete = leerValidado(origen);
        if (!CONFIRMACION.equals(paquete.tipo)) {
            throw new SyncException("El archivo seleccionado no es una confirmación de la central.");
        }
        String destino = texto(paquete.manifestacion.get("idDestino"), "");
        if (!destino.toUpperCase(Locale.ROOT).startsWith("UEB:")) {
            throw new SyncException("La confirmación no está dirigida a una UEB: " + destino);
        }
        UUID idPaquete = id(paquete.manifestacion.get("idPaquete"));
        if (repositorio.paqueteYaProcesado(idPaquete)) {
            return resultadoYaProcesado(idPaquete);
        }
        UUID idPaqueteOrigen = id(paquete.confirmacion.get("idPaqueteOrigen"));
        Map<String, Object> original = repositorio.consultarPaquete(idPaqueteOrigen);
        if (original == null) {
            throw new SyncException("La confirmación hace referencia a un paquete que no existe en esta UEB: "
                    + idPaqueteOrigen);
        }
        UUID idLote = id(paquete.manifestacion.get("idLote"));
        UUID idUsuario = authService.getIdUsuarioAutenticado();
        String observacion = texto(paquete.confirmacion.get("mensaje"), "Confirmación procesada");
        // Un RECHAZO de la central no es una confirmación satisfactoria: no debe marcar
        // sincronizado_en ni version_confirmada en la UEB de origen (DISEÑO §2 y §3).
        String estado = texto(paquete.confirmacion.get("estado"), "IMPORTADO")
                .trim().toUpperCase(Locale.ROOT);
        boolean rechazada = "RECHAZADO".equals(estado);

        transacciones.ejecutar(() -> {
            repositorio.registrarPaquete(paquete.manifestacion,
                    rechazada ? "RECHAZADO" : "RECIBIDO", observacion, idUsuario);
            if (!rechazada) {
                repositorio.confirmarPaquete(idPaqueteOrigen, idLote, LocalDateTime.now());
            }
            repositorio.actualizarEstadoPaquete(idPaquete,
                    rechazada ? "RECHAZADO" : "CONFIRMADO", observacion);
            Object procesados = paquete.confirmacion.get("procesados");
            if (procesados instanceof List) {
                for (Object elemento : (List<?>) procesados) {
                    if (!(elemento instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> item = (Map<?, ?>) elemento;
                    repositorio.registrarItem(idPaquete, new ItemResultado(
                            texto(item.get("tabla")), texto(item.get("idRegistro")),
                            texto(item.get("estado"), "CONFIRMADO"), texto(item.get("detalle"))));
                }
            }
            return null;
        });
        return new ResultadoImportacion(idPaquete.toString(),
                rechazada ? "RECHAZADO" : "CONFIRMADO", observacion);
    }

    public void generarInformeConflictos(Path destino, UUID idUebDestino) {
        requerir(CasoUso.CU_09);
        List<Map<String, Object>> conflictos = repositorio.listarConflictos("PENDIENTE");
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        List<Object> items = new ArrayList<>();
        for (Map<String, Object> conflicto : conflictos) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("tabla", texto(conflicto.get("tabla")));
            item.put("idRegistro", texto(conflicto.get("id_registro")));
            item.put("versionRecibida", texto(conflicto.get("version_recibida")));
            item.put("versionLocal", texto(conflicto.get("version_local")));
            item.put("estado", "PENDIENTE");
            items.add(item);
        }
        payload.put("procesados", Collections.emptyList());
        payload.put("conflictos", items);
        payload.put("mensaje", "Conflictos pendientes de decisión; el sistema no resuelve automáticamente.");
        escribirConfirmacion(destino, CONFLICTOS, idUebDestino, payload);
    }

    // -------------------------------------------------------------------------
    // CU-09 · Dirección de Producción
    // -------------------------------------------------------------------------

    public ResultadoImportacion importarPaquete(Path origen, String destinoLogico) {
        requerir(CasoUso.CU_09);
        PaqueteLeido paquete = leerValidado(origen);
        if (!EXPORTACION.equals(paquete.tipo)) {
            throw new SyncException("Solo se pueden importar paquetes de exportación de UEB en la central.");
        }
        String destino = texto(paquete.manifestacion.get("idDestino"), "");
        if (destinoLogico == null || !destinoLogico.equals(destino)) {
            throw new SyncException("Destino incorrecto. El paquete está dirigido a '" + destino + "'.");
        }
        UUID idPaquete = id(paquete.manifestacion.get("idPaquete"));
        if (repositorio.paqueteYaProcesado(idPaquete)) {
            return resultadoYaProcesado(idPaquete);
        }

        UUID idLote = id(paquete.manifestacion.get("idLote"));
        UUID idUsuario = authService.getIdUsuarioAutenticado();
        ResultadoImportacion resultado = transacciones.ejecutar(() -> {
            ResultadoImportacion acumulado = new ResultadoImportacion(idPaquete.toString(), "IMPORTADO", "");
            repositorio.registrarPaquete(paquete.manifestacion, "RECIBIDO", null, idUsuario);
            repositorio.actualizarEstadoPaquete(idPaquete, "VALIDADO", "Integridad y formato correctos");

            List<String> orden = SyncEsquema.tablasEnOrden();
            List<Registro> ordenados = new ArrayList<>(paquete.registros);
            ordenados.sort((a, b) -> {
                int delta = orden.indexOf(a.tabla) - orden.indexOf(b.tabla);
                return delta != 0 ? delta : Long.compare(a.version, b.version);
            });

            for (Registro registro : ordenados) {
                ItemResultado item = aplicarUno(registro, idPaquete, idLote);
                repositorio.registrarItem(idPaquete, item);
                acumulado.items.add(item);
            }
            long conflictos = acumulado.cuantos("CONFLICTO");
            long rechazados = acumulado.cuantos("RECHAZADO");
            String estado = conflictos > 0 || rechazados > 0 ? "PARCIAL" : "IMPORTADO";
            String mensaje = "Procesados " + acumulado.items.size() + " registros; "
                    + "aplicados " + acumulado.cuantos("APLICADO") + ", "
                    + "omitidos " + acumulado.cuantos("OMITIDO") + ", "
                    + "conflictos " + conflictos + ", rechazados " + rechazados + ".";
            repositorio.actualizarEstadoPaquete(idPaquete, estado, mensaje);
            acumulado.estado = estado;
            acumulado.mensaje = mensaje;
            return acumulado;
        });
        return resultado;
    }

    private ItemResultado aplicarUno(Registro registro, UUID idPaquete, UUID idLote) {
        if (!SyncEsquema.esExportable(registro.tabla)) {
            return new ItemResultado(registro.tabla, registro.idRegistro,
                    "RECHAZADO", "Tabla no autorizada para intercambio UEB→central.");
        }
        if (!"UPSERT".equals(registro.accion)) {
            return new ItemResultado(registro.tabla, registro.idRegistro,
                    "RECHAZADO", "Acción no reconocida: " + registro.accion);
        }
        String problemaDependencia = repositorio.validarDependencias(registro);
        if (problemaDependencia != null) {
            return new ItemResultado(registro.tabla, registro.idRegistro, "RECHAZADO", problemaDependencia);
        }

        long versionLocal = repositorio.versionFila(registro.tabla, registro.idRegistro);
        if (versionLocal < 0) {
            repositorio.aplicarRegistro(registro, idLote);
            return new ItemResultado(registro.tabla, registro.idRegistro,
                    "APLICADO", "Registro nuevo insertado con su lote y origen.");
        }
        if (registro.version < versionLocal) {
            return registrarConflicto(registro, idPaquete, versionLocal, "Versión recibida anterior a la local");
        }
        if (registro.version == versionLocal) {
            if (mismoContenido(registro)) {
                return new ItemResultado(registro.tabla, registro.idRegistro,
                        "OMITIDO", "Registro repetido con la misma versión y contenido.");
            }
            return registrarConflicto(registro, idPaquete, versionLocal, "Misma versión con contenido distinto");
        }
        if (repositorio.existeConflictoPendiente(registro.tabla, registro.idRegistro)) {
            return registrarConflicto(registro, idPaquete, versionLocal, "Existe conflicto pendiente de decisión");
        }
        repositorio.aplicarRegistro(registro, idLote);
        return new ItemResultado(registro.tabla, registro.idRegistro,
                "APLICADO", "Versión " + registro.version + " aplicada sobre la local " + versionLocal + ".");
    }

    /**
     * Registra la evidencia del conflicto y devuelve el único ítem que debe persistirse.
     * El bucle de importación se encarga de escribirlo: hacerlo también aquí sobrescribía
     * el motivo con un detalle distinto por la cláusula ON CONFLICT DO UPDATE.
     */
    private ItemResultado registrarConflicto(Registro registro, UUID idPaquete, long versionLocal, String motivo) {
        Conflicto conflicto = new Conflicto(registro.tabla, registro.idRegistro,
                registro.version, versionLocal);
        Map<String, Object> filaLocal = repositorio.leerFila(registro.tabla, registro.idRegistro);
        repositorio.registrarConflicto(conflicto, idPaquete,
                SyncJson.escribir(registro.aMapa()),
                filaLocal == null ? null : SyncJson.escribir(filaLocal));
        return new ItemResultado(registro.tabla, registro.idRegistro, "CONFLICTO", motivo);
    }

    public void generarConfirmacion(Path destino, UUID idPaqueteOrigen) {
        requerir(CasoUso.CU_09);
        Map<String, Object> original = repositorio.consultarPaquete(idPaqueteOrigen);
        if (original == null) {
            throw new SyncException("No existe el paquete que se desea confirmar: " + idPaqueteOrigen);
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        List<Object> procesados = new ArrayList<>();
        for (Map<String, Object> item : repositorio.consultarItems(idPaqueteOrigen)) {
            procesados.add(new ItemResultado(
                    texto(item.get("tabla")), texto(item.get("id_registro")),
                    texto(item.get("estado")), texto(item.get("detalle"))).aMapa());
        }
        List<Object> conflictos = new ArrayList<>();
        for (Map<String, Object> conflicto
                : repositorio.listarConflictosPorPaquete("PENDIENTE", idPaqueteOrigen)) {
            Conflicto c = new Conflicto(texto(conflicto.get("tabla")),
                    texto(conflicto.get("id_registro")),
                    Long.parseLong(texto(conflicto.get("version_recibida"), "0")),
                    Long.parseLong(texto(conflicto.get("version_local"), "0")));
            conflictos.add(c.aMapa());
        }
        String estadoPaquete = texto(original.get("estado"));
        payload.put("idPaqueteOrigen", idPaqueteOrigen.toString());
        payload.put("idLote", texto(original.get("id_lote")));
        payload.put("estado", estadoPaquete);
        payload.put("recibidoEn", LocalDateTime.now().toString());
        payload.put("procesados", procesados);
        payload.put("conflictos", conflictos);
        payload.put("mensaje", texto(original.get("mensaje"), "Paquete procesado"));
        String idUeb = texto(original.get("id_ueb_origen"));
        escribirConfirmacion(destino, CONFIRMACION,
                idUeb == null ? null : UUID.fromString(idUeb), payload);
    }

    private void escribirConfirmacion(Path destino, String tipo, UUID idUebDestino,
                                      LinkedHashMap<String, Object> payload) {
        byte[] bytesPayload = SyncArchivo.bytes(SyncJson.escribir(payload));
        UUID idPaquete = UUID.randomUUID();
        UUID idLote = UUID.randomUUID();
        LinkedHashMap<String, Object> manifestacion = manifiestoBasico(
                idPaquete, tipo, null, idUebDestino == null ? "CENTRAL" : "UEB:" + idUebDestino,
                LocalDateTime.now(), idLote, 0, bytesPayload, "CONFIRMACION.json");
        LinkedHashMap<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("MANIFEST.json", SyncArchivo.bytes(SyncJson.escribir(manifestacion)));
        entradas.put("CONFIRMACION.json", bytesPayload);
        SyncArchivo.escribir(destino, entradas);
        UUID idUsuario = authService.getIdUsuarioAutenticado();
        transacciones.ejecutar(() -> {
            repositorio.registrarPaquete(manifestacion, "GENERADO", null, idUsuario);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Consultas y validación común
    // -------------------------------------------------------------------------

    public List<Map<String, Object>> consultarHistorial() {
        requerir(CasoUso.CU_08, CasoUso.CU_09);
        return repositorio.listarPaquetes(100);
    }

    public List<Map<String, Object>> consultarConflictos() {
        requerir(CasoUso.CU_08, CasoUso.CU_09);
        return repositorio.listarConflictos("PENDIENTE");
    }

    public PaqueteLeido validarPaquete(Path origen) {
        requerir(CasoUso.CU_08, CasoUso.CU_09);
        PaqueteLeido paquete = leerValidado(origen);
        if (EXPORTACION.equals(paquete.tipo)) {
            requerir(CasoUso.CU_09);
        } else {
            requerir(CasoUso.CU_08, CasoUso.CU_09);
        }
        return paquete;
    }

    private PaqueteLeido leerValidado(Path origen) {
        LinkedHashMap<String, byte[]> entradas = SyncArchivo.leer(origen, ENTRADAS_PERMITIDAS);
        byte[] bytesManifiesto = entradas.get("MANIFEST.json");
        if (bytesManifiesto == null) {
            throw new SyncException("Falta MANIFEST.json en el paquete.");
        }
        Map<String, Object> manifestacion = SyncJson.parsearObjeto(SyncArchivo.texto(bytesManifiesto));
        if (!"PICAN-SYNC".equals(texto(manifestacion.get("formato")))) {
            throw new SyncException("El archivo no es un paquete PICAN reconocido.");
        }
        if (!PROTOCOLO.equals(texto(manifestacion.get("versionProtocolo")))) {
            throw new SyncException("Versión de protocolo no compatible: "
                    + texto(manifestacion.get("versionProtocolo")));
        }
        if (!ESQUEMA.equals(texto(manifestacion.get("versionEsquema")))) {
            throw new SyncException("Versión de esquema no compatible: "
                    + texto(manifestacion.get("versionEsquema")));
        }
        String tipo = texto(manifestacion.get("tipo"), "");
        if (!Arrays.asList(EXPORTACION, CONFIRMACION, CONFLICTOS).contains(tipo)) {
            throw new SyncException("Tipo de paquete no reconocido: " + tipo);
        }
        id(manifestacion.get("idPaquete"));
        id(manifestacion.get("idLote"));
        Set<String> esperados = EXPORTACION.equals(tipo) ? ENTRADAS_EXPORTACION : ENTRADAS_CONFIRMACION;
        if (!entradas.keySet().equals(esperados)) {
            throw new SyncException("El paquete contiene archivos inesperados o incompletos para su tipo.");
        }
        String nombrePayload = EXPORTACION.equals(tipo) ? "REGISTROS.json" : "CONFIRMACION.json";
        byte[] payload = entradas.get(nombrePayload);
        if (!SyncArchivo.hashCoincide(texto(manifestacion.get("payloadSha256")), payload)) {
            throw new SyncException("Integridad verificada incorrectamente: el contenido fue modificado o está incompleto.");
        }

        PaqueteLeido leido = new PaqueteLeido();
        leido.manifestacion = manifestacion;
        leido.tipo = tipo;
        leido.payload = payload;
        if (EXPORTACION.equals(tipo)) {
            Map<String, Object> datos = SyncJson.parsearObjeto(SyncArchivo.texto(payload));
            Object lista = datos.get("registros");
            if (!(lista instanceof List)) {
                throw new SyncException("El paquete no contiene una lista de registros.");
            }
            int declarados = Integer.parseInt(texto(manifestacion.get("cantidadRegistros"), "0"));
            if (((List<?>) lista).size() != declarados) {
                throw new SyncException("La cantidad de registros declarada no coincide con el contenido.");
            }
            if (declarados > 5000) {
                throw new SyncException("El paquete supera el límite de 5000 registros.");
            }
            for (Object elemento : (List<?>) lista) {
                if (!(elemento instanceof Map)) {
                    throw new SyncException("Registro con formato inválido.");
                }
                leido.registros.add(Registro.desdeMapa(castMapa(elemento)));
            }
        } else {
            leido.confirmacion = SyncJson.parsearObjeto(SyncArchivo.texto(payload));
        }
        return leido;
    }

    private void requerir(CasoUso... casos) {
        try {
            autorizacion.requerir(casos);
        } catch (AutorizacionException ex) {
            throw new SyncException(ex.getMessage());
        }
    }

    private ResultadoImportacion resultadoYaProcesado(UUID idPaquete) {
        ResultadoImportacion resultado = new ResultadoImportacion(idPaquete.toString(), "OMITIDO",
                "El paquete ya fue procesado; no se repitieron operaciones.");
        for (Map<String, Object> fila : repositorio.consultarItems(idPaquete)) {
            resultado.items.add(new ItemResultado(
                    texto(fila.get("tabla")), texto(fila.get("id_registro")),
                    texto(fila.get("estado")), "Registro omitido por repetición del paquete."));
        }
        return resultado;
    }

    private boolean mismoContenido(Registro registro) {
        Map<String, Object> fila = repositorio.leerFila(registro.tabla, registro.idRegistro);
        if (fila == null) {
            return false;
        }
        for (Map.Entry<String, String> entrada : registro.datos.entrySet()) {
            String esperado = canonico(entrada.getValue());
            String actual = canonico(fila.get(entrada.getKey()));
            if (!esperado.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private static String canonico(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = String.valueOf(valor).replace(' ', 'T');
        if (texto.endsWith(".0")) {
            texto = texto.substring(0, texto.length() - 2);
        }
        return texto;
    }

    private LinkedHashMap<String, Object> manifiestoBasico(UUID idPaquete, String tipo, UUID idUebOrigen,
                                                           String idDestino, LocalDateTime generadoEn,
                                                           UUID idLote, int cantidad,
                                                           byte[] payload, String nombrePayload) {
        LinkedHashMap<String, Object> manifestacion = new LinkedHashMap<>();
        manifestacion.put("formato", "PICAN-SYNC");
        manifestacion.put("versionProtocolo", PROTOCOLO);
        manifestacion.put("versionEsquema", ESQUEMA);
        manifestacion.put("idPaquete", idPaquete.toString());
        manifestacion.put("tipo", tipo);
        manifestacion.put("idUebOrigen", idUebOrigen == null ? null : idUebOrigen.toString());
        manifestacion.put("idDestino", idDestino);
        manifestacion.put("generadoEn", generadoEn.toString());
        manifestacion.put("idLote", idLote.toString());
        manifestacion.put("cantidadRegistros", Integer.toString(cantidad));
        manifestacion.put("payload", nombrePayload);
        manifestacion.put("payloadSha256", SyncArchivo.sha256(payload));
        manifestacion.put("generadoPor", id(authService.getIdUsuarioAutenticado()));
        manifestacion.put("observaciones", "Intercambio manual mediante archivo transportado en USB.");
        return manifestacion;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMapa(Object valor) {
        return (Map<String, Object>) valor;
    }

    private static UUID id(Object valor) {
        String texto = texto(valor);
        if (texto == null || texto.trim().isEmpty() || "null".equals(texto)) {
            throw new SyncException("Falta un identificador UUID obligatorio en el paquete.");
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException ex) {
            throw new SyncException("UUID inválido en el paquete: " + texto, ex);
        }
    }

    private static String id(UUID valor) {
        return valor == null ? null : valor.toString();
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static String texto(Object valor, String porDefecto) {
        String resultado = texto(valor);
        return resultado == null || resultado.trim().isEmpty() || "null".equals(resultado)
                ? porDefecto : resultado;
    }

    /** Paquete leído y validado, listo para importar o mostrar al usuario. */
    public static final class PaqueteLeido {
        public Map<String, Object> manifestacion;
        public Map<String, Object> confirmacion;
        public String tipo;
        public byte[] payload;
        public final List<Registro> registros = new ArrayList<>();

        public String idPaquete() {
            return texto(manifestacion.get("idPaquete"));
        }

        public String idDestino() {
            return texto(manifestacion.get("idDestino"));
        }

        public int cantidad() {
            return registros.size();
        }
    }
}
