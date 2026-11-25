package cu.pican.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escritor mínimo de libros .xlsx (ZIP + XML, sin librerías externas), en la misma
 * línea que LectorXlsx. Una hoja por sección, cadenas inline y celdas numéricas nativas.
 */
public final class ExportadorXlsx {

    private ExportadorXlsx() {
    }

    /** Sección exportable: título de hoja, cabeceras y filas de celdas (String o Number). */
    public static final class Hoja {
        public final String nombre;
        public final String[] columnas;
        public final List<Object[]> filas;

        public Hoja(String nombre, String[] columnas, List<Object[]> filas) {
            this.nombre = nombre;
            this.columnas = columnas;
            this.filas = filas;
        }
    }

    public static void escribir(Path destino, List<Hoja> hojas) {
        if (destino == null) {
            throw new ExportException("Debe indicar el archivo de destino del Excel.");
        }
        if (hojas == null || hojas.isEmpty()) {
            throw new ExportException("No hay contenido para exportar a Excel.");
        }
        try {
            Path padre = destino.toAbsolutePath().getParent();
            if (padre != null && !Files.isDirectory(padre)) {
                throw new ExportException("El directorio de destino no existe: " + padre);
            }
            Map<String, byte[]> entradas = new LinkedHashMap<>();
            entradas.put("[Content_Types].xml", bytes(contentTypes(hojas.size())));
            entradas.put("_rels/.rels", bytes(relsRaiz()));
            entradas.put("xl/workbook.xml", bytes(workbook(hojas)));
            entradas.put("xl/_rels/workbook.xml.rels", bytes(workbookRels(hojas.size())));
            for (int i = 0; i < hojas.size(); i++) {
                entradas.put("xl/worksheets/sheet" + (i + 1) + ".xml", bytes(hoja(hojas.get(i))));
            }
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destino))) {
                for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entrada.getKey()));
                    zip.write(entrada.getValue());
                    zip.closeEntry();
                }
            }
        } catch (IOException ex) {
            throw new ExportException("No se pudo escribir el archivo Excel en " + destino, ex);
        }
    }

    private static String contentTypes(int cantidadHojas) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        xml.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        xml.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        xml.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= cantidadHojas; i++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        xml.append("</Types>");
        return xml.toString();
    }

    private static String relsRaiz() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook(List<Hoja> hojas) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ");
        xml.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < hojas.size(); i++) {
            xml.append("<sheet name=\"").append(escapar(nombreHoja(hojas.get(i).nombre)))
                    .append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        xml.append("</sheets></workbook>");
        return xml.toString();
    }

    private static String workbookRels(int cantidadHojas) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= cantidadHojas; i++) {
            xml.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        xml.append("</Relationships>");
        return xml.toString();
    }

    private static String hoja(Hoja hoja) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<cols>");
        for (int c = 0; c < hoja.columnas.length; c++) {
            double ancho = anchoColumna(hoja, c);
            xml.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                    .append("\" width=\"").append(ancho).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        int fila = 1;
        xml.append(filaXml(fila++, hoja.columnas, true));
        for (Object[] datos : hoja.filas) {
            xml.append(filaXml(fila++, datos, false));
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static String filaXml(int numero, Object[] celdas, boolean cabecera) {
        StringBuilder xml = new StringBuilder();
        xml.append("<row r=\"").append(numero).append("\">");
        for (int c = 0; c < celdas.length; c++) {
            String referencia = letraColumna(c) + numero;
            Object valor = celdas[c];
            if (valor instanceof Integer || valor instanceof Long
                    || valor instanceof Double || valor instanceof Float
                    || valor instanceof BigDecimal) {
                xml.append("<c r=\"").append(referencia).append("\"><v>")
                        .append(valor instanceof BigDecimal
                                ? ((BigDecimal) valor).toPlainString() : String.valueOf(valor))
                        .append("</v></c>");
            } else {
                String texto = valor == null ? "" : String.valueOf(valor);
                xml.append("<c r=\"").append(referencia).append("\" t=\"inlineStr\"><is><t")
                        .append(texto.isEmpty() ? "" : " xml:space=\"preserve\"")
                        .append(">").append(escapar(texto)).append("</t></is></c>");
            }
        }
        xml.append("</row>");
        return xml.toString();
    }

    private static double anchoColumna(Hoja hoja, int columna) {
        double maximo = Math.min(40, Math.max(8, hoja.columnas[columna].length() + 2));
        for (Object[] fila : hoja.filas) {
            if (columna < fila.length && fila[columna] != null) {
                maximo = Math.min(60, Math.max(maximo, String.valueOf(fila[columna]).length() + 2));
            }
        }
        return Math.round(maximo * 10.0) / 10.0;
    }

    private static String letraColumna(int indice) {
        StringBuilder letra = new StringBuilder();
        int n = indice;
        while (n >= 0) {
            letra.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return letra.toString();
    }

    private static String nombreHoja(String nombre) {
        String limpio = nombre == null || nombre.trim().isEmpty() ? "Hoja" : nombre.trim();
        limpio = limpio.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        return limpio.length() > 31 ? limpio.substring(0, 31) : limpio;
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    /** Clase de utilidad auxiliar: lista mutable de hojas para componer un libro. */
    public static final class Libro {
        public final List<Hoja> hojas = new ArrayList<>();

        public Libro hoja(String nombre, String[] columnas, List<Object[]> filas) {
            hojas.add(new Hoja(nombre, columnas, filas));
            return this;
        }

        public void escribir(Path destino) {
            ExportadorXlsx.escribir(destino, hojas);
        }
    }
}
