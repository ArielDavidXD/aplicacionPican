package cu.pican.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Modelo interno de paquetes, registros y resultados de sincronización. */
public final class SyncModelo {

    private SyncModelo() {
    }

    public static final class Registro {
        public String tabla;
        public String accion;
        public String idRegistro;
        public long version;
        public String idUebOrigen;
        public String creadoEn;
        public String actualizadoEn;
        public boolean eliminadoLogico;
        public LinkedHashMap<String, String> datos = new LinkedHashMap<>();

        public Map<String, Object> aMapa() {
            LinkedHashMap<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("tabla", tabla);
            mapa.put("accion", accion);
            mapa.put("idRegistro", idRegistro);
            mapa.put("version", Long.toString(version));
            mapa.put("idUebOrigen", idUebOrigen);
            mapa.put("creadoEn", creadoEn);
            mapa.put("actualizadoEn", actualizadoEn);
            mapa.put("eliminadoLogico", eliminadoLogico);
            mapa.put("datos", datos);
            return mapa;
        }

        @SuppressWarnings("unchecked")
        public static Registro desdeMapa(Map<String, Object> mapa) {
            Registro registro = new Registro();
            registro.tabla = texto(mapa.get("tabla"));
            registro.accion = texto(mapa.get("accion"));
            registro.idRegistro = texto(mapa.get("idRegistro"));
            registro.version = Long.parseLong(texto(mapa.get("version")));
            registro.idUebOrigen = texto(mapa.get("idUebOrigen"));
            registro.creadoEn = texto(mapa.get("creadoEn"));
            registro.actualizadoEn = texto(mapa.get("actualizadoEn"));
            registro.eliminadoLogico = Boolean.TRUE.equals(mapa.get("eliminadoLogico"));
            Object datos = mapa.get("datos");
            if (!(datos instanceof Map)) {
                throw new SyncException("Registro sin bloque de datos: " + registro.idRegistro);
            }
            for (Map.Entry<?, ?> entrada : ((Map<?, ?>) datos).entrySet()) {
                Object valor = entrada.getValue();
                if (valor != null && !(valor instanceof String)) {
                    throw new SyncException("Valor no textual en " + registro.tabla + "."
                            + entrada.getKey() + ": se exige representación como cadena.");
                }
                registro.datos.put(String.valueOf(entrada.getKey()), (String) valor);
            }
            if (registro.tabla == null || registro.idRegistro == null || registro.accion == null) {
                throw new SyncException("Registro incompleto: faltan tabla, idRegistro o accion.");
            }
            return registro;
        }

        private static String texto(Object valor) {
            return valor == null ? null : String.valueOf(valor);
        }
    }

    public static final class ItemResultado {
        public String tabla;
        public String idRegistro;
        public String estado;
        public String detalle;

        public ItemResultado(String tabla, String idRegistro, String estado, String detalle) {
            this.tabla = tabla;
            this.idRegistro = idRegistro;
            this.estado = estado;
            this.detalle = detalle;
        }

        public Map<String, Object> aMapa() {
            LinkedHashMap<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("tabla", tabla);
            mapa.put("idRegistro", idRegistro);
            mapa.put("estado", estado);
            mapa.put("detalle", detalle);
            return mapa;
        }
    }

    public static final class ResultadoImportacion {
        public String idPaquete;
        public String estado;
        public String mensaje;
        public final List<ItemResultado> items = new ArrayList<>();

        public ResultadoImportacion(String idPaquete, String estado, String mensaje) {
            this.idPaquete = idPaquete;
            this.estado = estado;
            this.mensaje = mensaje;
        }

        public long cuantos(String estadoItem) {
            return items.stream().filter(i -> estadoItem.equals(i.estado)).count();
        }
    }

    public static final class Conflicto {
        public String tabla;
        public String idRegistro;
        public long versionRecibida;
        public long versionLocal;

        public Conflicto(String tabla, String idRegistro, long versionRecibida, long versionLocal) {
            this.tabla = tabla;
            this.idRegistro = idRegistro;
            this.versionRecibida = versionRecibida;
            this.versionLocal = versionLocal;
        }

        public Map<String, Object> aMapa() {
            LinkedHashMap<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("tabla", tabla);
            mapa.put("idRegistro", idRegistro);
            mapa.put("versionRecibida", Long.toString(versionRecibida));
            mapa.put("versionLocal", Long.toString(versionLocal));
            return mapa;
        }
    }
}
