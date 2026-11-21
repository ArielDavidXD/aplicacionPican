package cu.pican.importacion;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lector mínimo y seguro de .xlsx usando solo el JDK.
 *
 * No usa Apache POI ni dependencias nuevas. Lee la primera hoja, celdas de texto,
 * cadenas compartidas y valores numéricos. Las celdas con fórmula se leen como
 * texto literal; nunca se ejecutan macros ni fórmulas. Toda entrada ZIP y todo
 * documento XML se validan antes de interpretarse.
 */
public final class LectorXlsx {

    public static final long TAMANO_MAXIMO = 10L * 1024 * 1024;
    public static final int ENTRADAS_MAXIMAS = 200;
    public static final int FILAS_MAXIMAS = 5000;
    public static final int CELDAS_MAXIMAS = 100000;

    private LectorXlsx() {
    }

    public static ArchivoExcel leer(Path ruta) {
        if (ruta == null || ruta.toString().isBlank()) {
            throw new ImportacionException("Debe seleccionar un archivo Excel.");
        }
        if (!Files.isRegularFile(ruta)) {
            throw new ImportacionException("No existe el archivo seleccionado.");
        }
        String nombre = ruta.getFileName().toString();
        if (!nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ImportacionException("El archivo debe tener extensión .xlsx.");
        }
        try {
            if (Files.size(ruta) > TAMANO_MAXIMO) {
                throw new ImportacionException("El archivo supera el tamaño máximo permitido.");
            }
        } catch (IOException ex) {
            throw new ImportacionException("No se pudo comprobar el tamaño del archivo.", ex);
        }

        try (ZipFile zip = new ZipFile(ruta.toFile(), StandardCharsets.UTF_8)) {
            validarEntradas(zip);
            String hoja = resolverPrimeraHoja(zip);
            List<String> compartidas = leerCompartidas(zip);
            List<FilaExcel> filas = leerHoja(zip, hoja, compartidas);
            return new ArchivoExcel(nombre, nombreDeHoja(zip), filas);
        } catch (IOException ex) {
            throw new ImportacionException("No se pudo abrir el archivo .xlsx; puede estar dañado.", ex);
        }
    }

    private static void validarEntradas(ZipFile zip) {
        int cantidad = 0;
        boolean workbook = false;
        boolean algunaHoja = false;
        Enumeration<? extends ZipEntry> entradas = zip.entries();
        while (entradas.hasMoreElements()) {
            String nombre = entradas.nextElement().getName();
            if (nombre.startsWith("/") || nombre.startsWith("\\") || nombre.contains("..")
                    || nombre.contains("\\") || nombre.contains("externalLinks")) {
                throw new ImportacionException("El archivo contiene una ruta no permitida.");
            }
            if (++cantidad > ENTRADAS_MAXIMAS) {
                throw new ImportacionException("El archivo contiene demasiadas entradas internas.");
            }
            if ("xl/workbook.xml".equals(nombre)) {
                workbook = true;
            }
            if (nombre.startsWith("xl/worksheets/sheet") && nombre.endsWith(".xml")) {
                algunaHoja = true;
            }
        }
        if (!workbook || !algunaHoja) {
            throw new ImportacionException("El archivo no tiene la estructura de un libro Excel .xlsx.");
        }
    }

    private static String resolverPrimeraHoja(ZipFile zip) {
        String id = primerIdentificadorHoja(zip);
        if (id != null) {
            String destino = destinoRelacion(zip, id);
            if (destino != null) {
                return destino;
            }
        }
        List<String> hojas = new ArrayList<>();
        Enumeration<? extends ZipEntry> entradas = zip.entries();
        while (entradas.hasMoreElements()) {
            String nombre = entradas.nextElement().getName();
            if (nombre.startsWith("xl/worksheets/sheet") && nombre.endsWith(".xml")) {
                hojas.add(nombre);
            }
        }
        hojas.sort(String::compareTo);
        if (hojas.isEmpty()) {
            throw new ImportacionException("El archivo no contiene hojas de cálculo.");
        }
        return hojas.get(0);
    }

    private static String primerIdentificadorHoja(ZipFile zip) {
        ZipEntry entrada = zip.getEntry("xl/workbook.xml");
        if (entrada == null) {
            return null;
        }
        try (InputStream flujo = zip.getInputStream(entrada)) {
            XMLStreamReader lector = factory().createXMLStreamReader(flujo);
            while (lector.hasNext()) {
                if (lector.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(lector.getLocalName())) {
                    String id = lector.getAttributeValue(null, "id");
                    if (id == null) {
                        id = lector.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                    }
                    lector.close();
                    return id;
                }
            }
            lector.close();
        } catch (IOException | XMLStreamException ex) {
            throw new ImportacionException("No se pudo leer la definición de hojas del archivo.", ex);
        }
        return null;
    }

    private static String destinoRelacion(ZipFile zip, String id) {
        ZipEntry entrada = zip.getEntry("xl/_rels/workbook.xml.rels");
        if (entrada == null) {
            return null;
        }
        try (InputStream flujo = zip.getInputStream(entrada)) {
            XMLStreamReader lector = factory().createXMLStreamReader(flujo);
            while (lector.hasNext()) {
                if (lector.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(lector.getLocalName())) {
                    if (id.equals(lector.getAttributeValue(null, "Id"))) {
                        String destino = lector.getAttributeValue(null, "Target");
                        lector.close();
                        return normalizarDestino(destino);
                    }
                }
            }
            lector.close();
        } catch (IOException | XMLStreamException ex) {
            throw new ImportacionException("No se pudo leer la estructura interna del archivo.", ex);
        }
        return null;
    }

    private static String normalizarDestino(String destino) {
        if (destino == null || destino.isBlank()) {
            return null;
        }
        String limpio = destino.replace('\\', '/');
        if (limpio.startsWith("/")) {
            limpio = limpio.substring(1);
        } else if (!limpio.startsWith("xl/")) {
            limpio = "xl/" + limpio;
        }
        if (limpio.contains("..") || !limpio.startsWith("xl/worksheets/")) {
            throw new ImportacionException("La hoja indicada por el libro no es válida.");
        }
        return limpio;
    }

    private static String nombreDeHoja(ZipFile zip) {
        ZipEntry entrada = zip.getEntry("xl/workbook.xml");
        if (entrada == null) {
            return "";
        }
        try (InputStream flujo = zip.getInputStream(entrada)) {
            XMLStreamReader lector = factory().createXMLStreamReader(flujo);
            while (lector.hasNext()) {
                if (lector.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(lector.getLocalName())) {
                    String nombre = lector.getAttributeValue(null, "name");
                    lector.close();
                    return nombre == null ? "" : nombre;
                }
            }
            lector.close();
        } catch (IOException | XMLStreamException ex) {
            return "";
        }
        return "";
    }

    private static List<String> leerCompartidas(ZipFile zip) {
        ZipEntry entrada = zip.getEntry("xl/sharedStrings.xml");
        List<String> resultado = new ArrayList<>();
        if (entrada == null) {
            return resultado;
        }
        try (InputStream flujo = zip.getInputStream(entrada)) {
            XMLStreamReader lector = factory().createXMLStreamReader(flujo);
            StringBuilder actual = null;
            while (lector.hasNext()) {
                int evento = lector.next();
                if (evento == XMLStreamConstants.START_ELEMENT && "si".equals(lector.getLocalName())) {
                    actual = new StringBuilder();
                } else if (evento == XMLStreamConstants.START_ELEMENT && "t".equals(lector.getLocalName()) && actual != null) {
                    actual.append(lector.getElementText());
                } else if (evento == XMLStreamConstants.END_ELEMENT && "si".equals(lector.getLocalName()) && actual != null) {
                    resultado.add(actual.toString());
                    actual = null;
                }
            }
            lector.close();
        } catch (IOException | XMLStreamException ex) {
            throw new ImportacionException("No se pudieron leer las cadenas del archivo.", ex);
        }
        return resultado;
    }

    private static List<FilaExcel> leerHoja(ZipFile zip, String rutaHoja, List<String> compartidas) {
        ZipEntry entrada = zip.getEntry(rutaHoja);
        if (entrada == null) {
            throw new ImportacionException("No se encontró la hoja de cálculo indicada.");
        }
        List<FilaExcel> filas = new ArrayList<>();
        int numeroEsperado = 1;
        int celdasLeidas = 0;
        try (InputStream flujo = zip.getInputStream(entrada)) {
            XMLStreamReader lector = factory().createXMLStreamReader(flujo);
            while (lector.hasNext()) {
                if (lector.next() != XMLStreamConstants.START_ELEMENT || !"row".equals(lector.getLocalName())) {
                    continue;
                }
                if (filas.size() >= FILAS_MAXIMAS) {
                    throw new ImportacionException("El archivo contiene demasiadas filas.");
                }
                String referencia = lector.getAttributeValue(null, "r");
                int numero = numeroEsperado;
                if (referencia != null) {
                    try {
                        numero = Integer.parseInt(referencia);
                    } catch (NumberFormatException ignored) {
                        numero = numeroEsperado;
                    }
                }
                numeroEsperado = numero + 1;
                List<String> celdas = new ArrayList<>();
                while (lector.hasNext()) {
                    int evento = lector.next();
                    if (evento == XMLStreamConstants.START_ELEMENT && "c".equals(lector.getLocalName())) {
                        if (++celdasLeidas > CELDAS_MAXIMAS) {
                            throw new ImportacionException("El archivo contiene demasiadas celdas.");
                        }
                        String referenciaCelda = lector.getAttributeValue(null, "r");
                        String celda = leerCelda(lector, compartidas);
                        int columna = columnaDe(referenciaCelda);
                        while (celdas.size() < columna) {
                            celdas.add("");
                        }
                        celdas.set(columna - 1, celda);
                    } else if (evento == XMLStreamConstants.END_ELEMENT && "row".equals(lector.getLocalName())) {
                        break;
                    }
                }
                filas.add(new FilaExcel(numero, celdas));
            }
            lector.close();
        } catch (IOException | XMLStreamException ex) {
            throw new ImportacionException("No se pudo leer la hoja de cálculo.", ex);
        }
        return filas;
    }

    private static String leerCelda(XMLStreamReader lector, List<String> compartidas)
            throws XMLStreamException {
        String tipo = lector.getAttributeValue(null, "t");
        String valor = "";
        while (lector.hasNext()) {
            int evento = lector.next();
            if (evento == XMLStreamConstants.START_ELEMENT && "v".equals(lector.getLocalName())) {
                valor = lector.getElementText();
            } else if (evento == XMLStreamConstants.START_ELEMENT && "is".equals(lector.getLocalName())) {
                StringBuilder texto = new StringBuilder();
                while (lector.hasNext()) {
                    int interno = lector.next();
                    if (interno == XMLStreamConstants.START_ELEMENT && "t".equals(lector.getLocalName())) {
                        texto.append(lector.getElementText());
                    } else if (interno == XMLStreamConstants.END_ELEMENT && "is".equals(lector.getLocalName())) {
                        break;
                    }
                }
                valor = texto.toString();
            } else if (evento == XMLStreamConstants.END_ELEMENT && "c".equals(lector.getLocalName())) {
                break;
            }
        }
        if ("s".equals(tipo)) {
            try {
                int indice = Integer.parseInt(valor.trim());
                if (indice < 0 || indice >= compartidas.size()) {
                    throw new ImportacionException("El archivo hace referencia a una cadena compartida inexistente.");
                }
                return compartidas.get(indice);
            } catch (NumberFormatException ex) {
                throw new ImportacionException("El archivo contiene una referencia de texto inválida.", ex);
            }
        }
        return valor;
    }

    private static int columnaDe(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return 1;
        }
        int columna = 0;
        for (int i = 0; i < referencia.length(); i++) {
            char c = Character.toUpperCase(referencia.charAt(i));
            if (c < 'A' || c > 'Z') {
                break;
            }
            columna = columna * 26 + (c - 'A' + 1);
        }
        return Math.max(1, columna);
    }

    private static XMLInputFactory factory() {
        XMLInputFactory factoria = XMLInputFactory.newInstance();
        establecer(factoria, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        establecer(factoria, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        establecer(factoria, XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return factoria;
    }

    private static void establecer(XMLInputFactory factoria, String propiedad, Object valor) {
        try {
            factoria.setProperty(propiedad, valor);
        } catch (IllegalArgumentException ignorado) {
            // Implementaciones que no exponen la propiedad; el parser sigue sin resolver entidades externas.
        }
    }
}
