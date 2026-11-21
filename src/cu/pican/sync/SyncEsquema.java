package cu.pican.sync;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registro estático de las tablas que participan en el intercambio UEB → central.
 * No modifica V1: solo describe columnas existentes para leerlas y escribirlas por JDBC.
 */
public final class SyncEsquema {

    public enum Tipo {
        UUID, TEXTO, ENTERO, LONG, DECIMAL, FECHA, FECHA_HORA, BOOLEANO, LOTE
    }

    public static final class Fk {
        public final String columna;
        public final String tabla;
        public final boolean obligatoria;

        public Fk(String columna, String tabla, boolean obligatoria) {
            this.columna = columna;
            this.tabla = tabla;
            this.obligatoria = obligatoria;
        }
    }

    private static final LinkedHashMap<String, LinkedHashMap<String, Tipo>> TABLAS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> CLAVES = new LinkedHashMap<>();
    private static final LinkedHashMap<String, List<Fk>> DEPENDENCIAS = new LinkedHashMap<>();

    static {
        registrar("ueb", "id_ueb", columnas(
                "id_ueb", Tipo.UUID, "nombre", Tipo.TEXTO, "codigo", Tipo.TEXTO,
                "activa", Tipo.BOOLEANO), Collections.emptyList());
        registrar("nave", "id_nave", columnas(
                "id_nave", Tipo.UUID, "id_ueb", Tipo.UUID, "nombre", Tipo.TEXTO,
                "codigo", Tipo.TEXTO, "activa", Tipo.BOOLEANO),
                Arrays.asList(new Fk("id_ueb", "ueb", true)));
        registrar("unidad_asociada", "id_unidad", columnas(
                "id_unidad", Tipo.UUID, "nombre", Tipo.TEXTO, "codigo", Tipo.TEXTO,
                "activa", Tipo.BOOLEANO), Collections.emptyList());
        registrar("producto", "id_producto", columnas(
                "id_producto", Tipo.UUID, "codigo", Tipo.TEXTO, "nombre", Tipo.TEXTO,
                "activa", Tipo.BOOLEANO, "unidad_medida", Tipo.TEXTO), Collections.emptyList());
        registrar("destino", "id_destino", columnas(
                "id_destino", Tipo.UUID, "codigo", Tipo.TEXTO, "nombre", Tipo.TEXTO,
                "activa", Tipo.BOOLEANO), Collections.emptyList());
        registrar("producto_destino", "id_relacion", columnas(
                "id_relacion", Tipo.UUID, "id_producto", Tipo.UUID, "id_destino", Tipo.UUID,
                "activa", Tipo.BOOLEANO),
                Arrays.asList(new Fk("id_producto", "producto", true),
                        new Fk("id_destino", "destino", true)));
        registrar("parte_nave", "id_parte", columnas(
                "id_parte", Tipo.UUID, "id_nave", Tipo.UUID, "fecha_produccion", Tipo.FECHA,
                "gallinas_vivas", Tipo.ENTERO, "gallinas_muertas", Tipo.ENTERO,
                "gallinas_enfermas", Tipo.ENTERO, "huevos_producidos", Tipo.ENTERO,
                "huevos_rotos", Tipo.ENTERO, "pienso_kg", Tipo.DECIMAL,
                "observaciones", Tipo.TEXTO, "estado", Tipo.TEXTO, "id_usuario", Tipo.UUID),
                Arrays.asList(new Fk("id_nave", "nave", true), new Fk("id_usuario", "usuario", false)));
        registrar("parte_unidad_asociada", "id_parte_asociado", columnas(
                "id_parte_asociado", Tipo.UUID, "id_unidad", Tipo.UUID,
                "fecha_produccion", Tipo.FECHA, "referencia_papel", Tipo.TEXTO,
                "observaciones", Tipo.TEXTO, "estado", Tipo.TEXTO, "id_usuario", Tipo.UUID),
                Arrays.asList(new Fk("id_unidad", "unidad_asociada", true),
                        new Fk("id_usuario", "usuario", false)));
        registrar("produccion_carne", "id_produccion", columnas(
                "id_produccion", Tipo.UUID, "id_ueb", Tipo.UUID, "fecha_produccion", Tipo.FECHA,
                "cantidad_toneladas", Tipo.DECIMAL, "observaciones", Tipo.TEXTO, "id_usuario", Tipo.UUID),
                Arrays.asList(new Fk("id_ueb", "ueb", true), new Fk("id_usuario", "usuario", false)));
        registrar("consolidado_ueb", "id_consolidado", columnas(
                "id_consolidado", Tipo.UUID, "id_ueb", Tipo.UUID, "fecha_inicio", Tipo.FECHA,
                "fecha_fin", Tipo.FECHA, "estado", Tipo.TEXTO, "motivo_parcial", Tipo.TEXTO,
                "id_usuario", Tipo.UUID),
                Arrays.asList(new Fk("id_ueb", "ueb", true), new Fk("id_usuario", "usuario", false)));
        registrar("vale_entrega", "id_vale", columnas(
                "id_vale", Tipo.UUID, "numero_vale", Tipo.TEXTO, "id_ueb", Tipo.UUID,
                "id_destino", Tipo.UUID, "id_producto", Tipo.UUID,
                "cantidad_despachada", Tipo.DECIMAL, "unidad_medida", Tipo.TEXTO,
                "fecha_hora_emision", Tipo.FECHA_HORA, "estado", Tipo.TEXTO, "id_usuario", Tipo.UUID,
                "motivo_anulacion", Tipo.TEXTO, "fecha_hora_anulacion", Tipo.FECHA_HORA,
                "id_usuario_anulacion", Tipo.UUID),
                Arrays.asList(new Fk("id_ueb", "ueb", true), new Fk("id_destino", "destino", true),
                        new Fk("id_producto", "producto", true), new Fk("id_usuario", "usuario", false),
                        new Fk("id_usuario_anulacion", "usuario", false)));
        registrar("recepcion", "id_recepcion", columnas(
                "id_recepcion", Tipo.UUID, "id_vale", Tipo.UUID,
                "fecha_hora_recepcion", Tipo.FECHA_HORA, "cantidad_recibida", Tipo.DECIMAL,
                "unidad_medida", Tipo.TEXTO, "resultado", Tipo.TEXTO, "observaciones", Tipo.TEXTO,
                "id_usuario", Tipo.UUID),
                Arrays.asList(new Fk("id_vale", "vale_entrega", true),
                        new Fk("id_usuario", "usuario", false)));
        registrar("incidencia", "id_incidencia", columnas(
                "id_incidencia", Tipo.UUID, "id_vale", Tipo.UUID, "id_recepcion", Tipo.UUID,
                "tipo", Tipo.TEXTO, "cantidad_afectada", Tipo.DECIMAL, "descripcion", Tipo.TEXTO,
                "medida_acordada", Tipo.TEXTO, "fecha_compromiso", Tipo.FECHA_HORA,
                "estado", Tipo.TEXTO, "observaciones", Tipo.TEXTO,
                "fecha_hora_registro", Tipo.FECHA_HORA, "id_usuario", Tipo.UUID,
                "fecha_cierre", Tipo.FECHA_HORA, "resultado_solucion", Tipo.TEXTO),
                Arrays.asList(new Fk("id_vale", "vale_entrega", true),
                        new Fk("id_recepcion", "recepcion", true),
                        new Fk("id_usuario", "usuario", false)));
    }

    private SyncEsquema() {
    }

    public static List<String> tablasEnOrden() {
        return Collections.unmodifiableList(new ArrayList<>(TABLAS.keySet()));
    }

    public static boolean esExportable(String tabla) {
        return TABLAS.containsKey(tabla);
    }

    public static String clavePrimaria(String tabla) {
        String clave = CLAVES.get(tabla);
        if (clave == null) {
            throw new SyncException("Tabla no registrada para sincronización: " + tabla);
        }
        return clave;
    }

    public static Map<String, Tipo> columnas(String tabla) {
        LinkedHashMap<String, Tipo> columnas = TABLAS.get(tabla);
        if (columnas == null) {
            throw new SyncException("Tabla no registrada para sincronización: " + tabla);
        }
        return Collections.unmodifiableMap(columnas);
    }

    public static List<Fk> dependencias(String tabla) {
        return DEPENDENCIAS.getOrDefault(tabla, Collections.emptyList());
    }

    public static void asignar(PreparedStatement ps, int indice, Tipo tipo, String valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, tipoSql(tipo));
            return;
        }
        switch (tipo) {
            case UUID:
            case LOTE:
                ps.setObject(indice, valor, Types.OTHER);
                break;
            case TEXTO:
                ps.setString(indice, valor);
                break;
            case ENTERO:
                ps.setInt(indice, Integer.parseInt(valor));
                break;
            case LONG:
                ps.setLong(indice, Long.parseLong(valor));
                break;
            case DECIMAL:
                ps.setBigDecimal(indice, new BigDecimal(valor));
                break;
            case FECHA:
                ps.setDate(indice, java.sql.Date.valueOf(LocalDate.parse(valor)));
                break;
            case FECHA_HORA:
                ps.setTimestamp(indice, java.sql.Timestamp.valueOf(
                        LocalDateTime.parse(valor.trim().replace(' ', 'T'))));
                break;
            case BOOLEANO:
                ps.setBoolean(indice, Boolean.parseBoolean(valor));
                break;
            default:
                throw new SyncException("Tipo de columna no soportado: " + tipo);
        }
    }

    private static int tipoSql(Tipo tipo) {
        switch (tipo) {
            case UUID:
            case LOTE:
                return Types.OTHER;
            case ENTERO:
                return Types.INTEGER;
            case LONG:
                return Types.BIGINT;
            case DECIMAL:
                return Types.NUMERIC;
            case FECHA:
                return Types.DATE;
            case FECHA_HORA:
                return Types.TIMESTAMP;
            case BOOLEANO:
                return Types.BOOLEAN;
            case TEXTO:
            default:
                return Types.VARCHAR;
        }
    }

    public static Object valorJdbc(ResultSet rs, String columna, Tipo tipo) throws SQLException {
        Object valor = rs.getObject(columna);
        if (valor == null) {
            return null;
        }
        switch (tipo) {
            case FECHA:
                return rs.getDate(columna).toLocalDate().toString();
            case FECHA_HORA:
                return rs.getTimestamp(columna).toLocalDateTime().toString();
            case DECIMAL:
                return ((BigDecimal) valor).toPlainString();
            case BOOLEANO:
                return Boolean.toString(rs.getBoolean(columna));
            case ENTERO:
            case LONG:
                return String.valueOf(valor);
            case UUID:
            case LOTE:
            case TEXTO:
            default:
                return String.valueOf(valor);
        }
    }

    private static LinkedHashMap<String, Tipo> columnas(Object... pares) {
        LinkedHashMap<String, Tipo> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], (Tipo) pares[i + 1]);
        }
        return mapa;
    }

    private static void registrar(String tabla, String clave, LinkedHashMap<String, Tipo> columnasNegocio,
                                  List<Fk> dependencias) {
        LinkedHashMap<String, Tipo> todas = new LinkedHashMap<>(columnasNegocio);
        todas.put("id_ueb_origen", Tipo.UUID);
        todas.put("version", Tipo.LONG);
        todas.put("creado_en", Tipo.FECHA_HORA);
        todas.put("actualizado_en", Tipo.FECHA_HORA);
        todas.put("sincronizado_en", Tipo.FECHA_HORA);
        todas.put("id_lote_sincronizacion", Tipo.LOTE);
        todas.put("eliminado_logico", Tipo.BOOLEANO);
        TABLAS.put(tabla, todas);
        CLAVES.put(tabla, clave);
        DEPENDENCIAS.put(tabla, dependencias);
    }
}
