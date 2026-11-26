package cu.pican.controller.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializador/analizador JSON mínimo y seguro, sin dependencias externas.
 * Soporta objetos, arreglos, cadenas, booleanos y null. Los números se transportan
 * como cadenas para conservar la precisión decimal de los datos operativos.
 */
public final class SyncJson {

    private SyncJson() {
    }

    public static String escribir(Object valor) {
        StringBuilder salida = new StringBuilder();
        escribirValor(valor, salida);
        return salida.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parsearObjeto(String texto) {
        Object valor = new Parser(texto).parsear();
        if (!(valor instanceof Map)) {
            throw new SyncException("Se esperaba un objeto JSON en la raíz del archivo.");
        }
        return (Map<String, Object>) valor;
    }

    private static void escribirValor(Object valor, StringBuilder salida) {
        if (valor == null) {
            salida.append("null");
        } else if (valor instanceof String) {
            escribirCadena((String) valor, salida);
        } else if (valor instanceof Boolean) {
            salida.append(valor.toString());
        } else if (valor instanceof Number) {
            escribirCadena(valor.toString(), salida);
        } else if (valor instanceof Map) {
            salida.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> entrada : ((Map<?, ?>) valor).entrySet()) {
                if (!primero) {
                    salida.append(',');
                }
                primero = false;
                escribirCadena(String.valueOf(entrada.getKey()), salida);
                salida.append(':');
                escribirValor(entrada.getValue(), salida);
            }
            salida.append('}');
        } else if (valor instanceof Iterable) {
            salida.append('[');
            boolean primero = true;
            for (Object elemento : (Iterable<?>) valor) {
                if (!primero) {
                    salida.append(',');
                }
                primero = false;
                escribirValor(elemento, salida);
            }
            salida.append(']');
        } else {
            throw new SyncException("Tipo no soportado por el formato de paquete: "
                    + valor.getClass().getSimpleName());
        }
    }

    private static void escribirCadena(String texto, StringBuilder salida) {
        salida.append('"');
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"': salida.append("\\\""); break;
                case '\\': salida.append("\\\\"); break;
                case '\b': salida.append("\\b"); break;
                case '\f': salida.append("\\f"); break;
                case '\n': salida.append("\\n"); break;
                case '\r': salida.append("\\r"); break;
                case '\t': salida.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        salida.append(String.format("\\u%04x", (int) c));
                    } else {
                        salida.append(c);
                    }
            }
        }
        salida.append('"');
    }

    private static final class Parser {
        private final String texto;
        private int posicion;

        private Parser(String texto) {
            this.texto = texto;
        }

        private Object parsear() {
            Object valor = valor();
            espacio();
            if (posicion != texto.length()) {
                throw error("sobran caracteres después del JSON");
            }
            return valor;
        }

        private Object valor() {
            espacio();
            if (posicion >= texto.length()) {
                throw error("fin inesperado del documento");
            }
            char c = texto.charAt(posicion);
            switch (c) {
                case '{': return objeto();
                case '[': return arreglo();
                case '"': return cadena();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default: throw error("carácter inesperado '" + c + "'");
            }
        }

        private Map<String, Object> objeto() {
            posicion++;
            Map<String, Object> mapa = new LinkedHashMap<>();
            espacio();
            if (consumir('}')) {
                return mapa;
            }
            while (true) {
                espacio();
                if (posicion >= texto.length() || texto.charAt(posicion) != '"') {
                    throw error("se esperaba una clave entre comillas");
                }
                String clave = cadena();
                espacio();
                if (!consumir(':')) {
                    throw error("se esperaba ':' después de la clave");
                }
                mapa.put(clave, valor());
                espacio();
                if (consumir('}')) {
                    return mapa;
                }
                if (!consumir(',')) {
                    throw error("se esperaba ',' o '}' en el objeto");
                }
            }
        }

        private List<Object> arreglo() {
            posicion++;
            List<Object> lista = new ArrayList<>();
            espacio();
            if (consumir(']')) {
                return lista;
            }
            while (true) {
                lista.add(valor());
                espacio();
                if (consumir(']')) {
                    return lista;
                }
                if (!consumir(',')) {
                    throw error("se esperaba ',' o ']' en el arreglo");
                }
            }
        }

        private String cadena() {
            if (posicion >= texto.length() || texto.charAt(posicion) != '"') {
                throw error("se esperaba una cadena");
            }
            posicion++;
            StringBuilder resultado = new StringBuilder();
            while (posicion < texto.length()) {
                char c = texto.charAt(posicion++);
                if (c == '"') {
                    return resultado.toString();
                }
                if (c == '\\') {
                    if (posicion >= texto.length()) {
                        throw error("escape incompleto");
                    }
                    char escape = texto.charAt(posicion++);
                    switch (escape) {
                        case '"': resultado.append('"'); break;
                        case '\\': resultado.append('\\'); break;
                        case '/': resultado.append('/'); break;
                        case 'b': resultado.append('\b'); break;
                        case 'f': resultado.append('\f'); break;
                        case 'n': resultado.append('\n'); break;
                        case 'r': resultado.append('\r'); break;
                        case 't': resultado.append('\t'); break;
                        case 'u':
                            if (posicion + 4 > texto.length()) {
                                throw error("escape Unicode incompleto");
                            }
                            try {
                                resultado.append((char) Integer.parseInt(
                                        texto.substring(posicion, posicion + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("escape Unicode inválido");
                            }
                            posicion += 4;
                            break;
                        default: throw error("escape no reconocido");
                    }
                } else if (c < 0x20) {
                    throw error("carácter de control no escapado");
                } else {
                    resultado.append(c);
                }
            }
            throw error("cadena sin cierre");
        }

        private void literal(String esperado) {
            if (!texto.startsWith(esperado, posicion)) {
                throw error("literal inválido");
            }
            posicion += esperado.length();
        }

        private void espacio() {
            while (posicion < texto.length() && Character.isWhitespace(texto.charAt(posicion))) {
                posicion++;
            }
        }

        private boolean consumir(char c) {
            if (posicion < texto.length() && texto.charAt(posicion) == c) {
                posicion++;
                return true;
            }
            return false;
        }

        private SyncException error(String motivo) {
            return new SyncException("JSON inválido en la posición " + posicion + ": " + motivo);
        }
    }
}
