package cu.pican.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Escritor mínimo de documentos PDF (formato 1.4) sin librerías externas.
 * Usa las fuentes estándar Helvetica/Helvetica-Bold con codificación WinAnsi,
 * que cubre el alfabeto español. El contenido se pagina automáticamente y cada
 * sección se dibuja como una tabla con cabecera repetida al saltar de página.
 */
public final class ExportadorPdf {

    private static final double ANCHO = 595;
    private static final double ALTO = 842;
    private static final double MARGEN_X = 50;
    private static final double MARGEN_ALTO = 60;
    private static final double MARGEN_BAJO = 50;
    private static final int TAM_TITULO = 15;
    private static final int TAM_SUBTITULO = 9;
    private static final int TAM_SECCION = 11;
    private static final int TAM_TABLA = 9;

    private static final String F1 = "F1";
    private static final String F2 = "F2";

    private ExportadorPdf() {
    }

    /** Sección exportable: título y tabla de cabeceras + filas. */
    public static final class Seccion {
        public final String titulo;
        public final String[] columnas;
        public final List<Object[]> filas;

        public Seccion(String titulo, String[] columnas, List<Object[]> filas) {
            this.titulo = titulo;
            this.columnas = columnas;
            this.filas = filas;
        }
    }

    public static void escribir(Path destino, String tituloDocumento, List<Seccion> secciones) {
        if (destino == null) {
            throw new ExportException("Debe indicar el archivo de destino del PDF.");
        }
        if (secciones == null || secciones.isEmpty()) {
            throw new ExportException("No hay contenido para exportar a PDF.");
        }
        String titulo = tituloDocumento == null || tituloDocumento.trim().isEmpty()
                ? "Informe PICAN" : tituloDocumento.trim();
        try {
            Path padre = destino.toAbsolutePath().getParent();
            if (padre != null && !Files.isDirectory(padre)) {
                throw new ExportException("El directorio de destino no existe: " + padre);
            }
            Files.write(destino, construir(titulo, secciones));
        } catch (IOException ex) {
            throw new ExportException("No se pudo escribir el archivo PDF en " + destino, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Construcción del documento
    // -------------------------------------------------------------------------

    private static byte[] construir(String titulo, List<Seccion> secciones) {
        Constructor constructor = new Constructor(titulo);
        for (Seccion seccion : secciones) {
            constructor.seccion(seccion);
        }
        return constructor.cerrar();
    }

    private static final class Constructor {
        private final String titulo;
        private final List<byte[]> paginas = new ArrayList<>();
        private final List<Trazo> trazos = new ArrayList<>();
        private final List<Regla> reglas = new ArrayList<>();
        private List<List<String>> ultimaCabecera = new ArrayList<>();
        private double[] ultimosAnchos = new double[0];
        private double y;
        private int numeroPagina = 1;

        Constructor(String titulo) {
            this.titulo = titulo;
            encabezado();
        }

        private void encabezado() {
            trazos.add(new Trazo(MARGEN_X, ALTO - MARGEN_ALTO + 12, F2, TAM_TITULO, titulo));
            trazos.add(new Trazo(MARGEN_X, ALTO - MARGEN_ALTO - 4, F1, TAM_SUBTITULO,
                    "Generado el " + LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "  ·  Página " + numeroPagina));
            y = ALTO - MARGEN_ALTO - 24;
        }

        void seccion(Seccion seccion) {
            salto(10);
            trazos.add(new Trazo(MARGEN_X, y, F2, TAM_SECCION, seccion.titulo));
            y -= TAM_SECCION * 1.6 + 4;
            double[] anchos = anchos(seccion.columnas, seccion.filas);
            List<List<String>> cabecera = new ArrayList<>();
            for (String columna : seccion.columnas) {
                cabecera.add(List.of(columna));
            }
            ultimaCabecera = cabecera;
            ultimosAnchos = anchos;
            fila(cabecera, anchos, true);
            reglas.add(new Regla(MARGEN_X, y, MARGEN_X + suma(anchos), y));
            salto(4);
            for (Object[] filaDatos : seccion.filas) {
                List<List<String>> celdas = new ArrayList<>();
                for (int c = 0; c < seccion.columnas.length; c++) {
                    String texto = filaDatos.length > c && filaDatos[c] != null
                            ? String.valueOf(filaDatos[c]) : "";
                    celdas.add(quebrar(texto, anchos[c] - 4, TAM_TABLA));
                }
                fila(celdas, anchos, false);
            }
        }

        private void fila(List<List<String>> celdas, double[] anchos, boolean cabecera) {
            double altoLinea = TAM_TABLA * 1.5;
            int lineas = 1;
            for (List<String> celda : celdas) {
                lineas = Math.max(lineas, celda.size());
            }
            for (int linea = 0; linea < lineas; linea++) {
                if (y - altoLinea < MARGEN_BAJO) {
                    nuevaPagina();
                    if (!cabecera) {
                        repeticionDeCabecera(celdas.size(), anchos);
                    }
                }
                for (int c = 0; c < celdas.size(); c++) {
                    List<String> celda = celdas.get(c);
                    if (linea >= celda.size()) {
                        continue;
                    }
                    double x = MARGEN_X + (c == 0 ? 0 : suma(anchos, c));
                    trazos.add(new Trazo(x, y, cabecera ? F2 : F1, TAM_TABLA, celda.get(linea)));
                }
                y -= altoLinea;
            }
        }

        /** Cabecera de columna repetida al saltar de página en medio de una tabla. */
        private void repeticionDeCabecera(int columnas, double[] anchos) {
            for (int c = 0; c < columnas && c < ultimaCabecera.size(); c++) {
                List<String> celda = ultimaCabecera.get(c);
                double x = MARGEN_X + (c == 0 ? 0 : suma(ultimosAnchos, c));
                trazos.add(new Trazo(x, y, F2, TAM_TABLA, celda.get(0)));
            }
            y -= TAM_TABLA * 1.5;
            reglas.add(new Regla(MARGEN_X, y, MARGEN_X + suma(ultimosAnchos), y));
            y -= 4;
        }

        void salto(double puntos) {
            y -= puntos;
            if (y < MARGEN_BAJO) {
                nuevaPagina();
            }
        }

        private void nuevaPagina() {
            paginas.add(serializarPagina(trazos, reglas));
            trazos.clear();
            reglas.clear();
            numeroPagina++;
            encabezado();
        }

        byte[] cerrar() {
            paginas.add(serializarPagina(trazos, reglas));
            return serializarDocumento(paginas);
        }
    }

    /** Anchos de columna en puntos, proporcionales al contenido y con mínimo legible. */
    private static double[] anchos(String[] columnas, List<Object[]> filas) {
        double total = ANCHO - 2 * MARGEN_X;
        double[] deseados = new double[columnas.length];
        double suma = 0;
        for (int c = 0; c < columnas.length; c++) {
            double maximo = anchoTexto(columnas[c], TAM_TABLA, true) + 6;
            for (Object[] fila : filas) {
                String texto = fila.length > c && fila[c] != null ? String.valueOf(fila[c]) : "";
                maximo = Math.max(maximo, anchoTexto(texto, TAM_TABLA, false) + 6);
            }
            deseados[c] = Math.min(190, Math.max(42, maximo));
            suma += deseados[c];
        }
        double factor = suma > total ? total / suma : 1.0;
        double[] anchos = new double[columnas.length];
        double sumaFinal = 0;
        for (int c = 0; c < columnas.length; c++) {
            anchos[c] = Math.max(36, deseados[c] * factor);
            sumaFinal += anchos[c];
        }
        if (sumaFinal > total) {
            for (int c = 0; c < columnas.length; c++) {
                anchos[c] = anchos[c] * total / sumaFinal;
            }
        }
        return anchos;
    }

    private static double suma(double[] valores) {
        return suma(valores, valores.length);
    }

    private static double suma(double[] valores, int hasta) {
        double total = 0;
        for (int i = 0; i < hasta; i++) {
            total += valores[i];
        }
        return total;
    }

    /** Ancho estimado en puntos con métrica promedio de Helvetica (0.5 em por carácter). */
    private static double anchoTexto(String texto, int tamano, boolean negrita) {
        double factor = negrita ? 0.54 : 0.50;
        double ancho = 0;
        for (char c : texto.toCharArray()) {
            ancho += c == ' ' ? tamano * 0.28 : tamano * factor;
        }
        return ancho;
    }

    /** Parte el texto en líneas que caben en el ancho disponible, sin cortar palabras cuando es posible. */
    private static List<String> quebrar(String texto, double ancho, int tamano) {
        List<String> lineas = new ArrayList<>();
        String[] palabras = texto.split(" ", -1);
        StringBuilder actual = new StringBuilder();
        for (String palabra : palabras) {
            String candidato = actual.length() == 0 ? palabra : actual + " " + palabra;
            if (anchoTexto(candidato, tamano, false) <= ancho || actual.length() == 0) {
                if (actual.length() > 0) {
                    actual.append(' ');
                }
                actual.append(palabra);
                continue;
            }
            lineas.add(actual.toString());
            actual = new StringBuilder();
            while (anchoTexto(palabra, tamano, false) > ancho) {
                int corte = Math.max(1, (int) (palabra.length() * ancho
                        / anchoTexto(palabra, tamano, false)));
                lineas.add(palabra.substring(0, corte));
                palabra = palabra.substring(corte);
            }
            actual.append(palabra);
        }
        if (actual.length() > 0 || lineas.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    // -------------------------------------------------------------------------
    // Serialización
    // -------------------------------------------------------------------------

    private static final class Trazo {
        final double x;
        final double y;
        final String fuente;
        final int tamano;
        final String texto;

        Trazo(double x, double y, String fuente, int tamano, String texto) {
            this.x = x;
            this.y = y;
            this.fuente = fuente;
            this.tamano = tamano;
            this.texto = texto;
        }
    }

    private static final class Regla {
        final double x1;
        final double y1;
        final double x2;
        final double y2;

        Regla(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private static byte[] serializarPagina(List<Trazo> trazos, List<Regla> reglas) {
        ByteArrayOutputStream contenido = new ByteArrayOutputStream();
        for (Regla regla : reglas) {
            linea(contenido, "0.5 w " + n(regla.x1) + " " + n(regla.y1)
                    + " m " + n(regla.x2) + " " + n(regla.y2) + " l S");
        }
        for (Trazo trazo : trazos) {
            linea(contenido, "BT /" + trazo.fuente + " " + trazo.tamano + " Tf 1 0 0 1 "
                    + n(trazo.x) + " " + n(trazo.y) + " Tm ("
                    + escapar(trazo.texto) + ") Tj ET");
        }
        return contenido.toByteArray();
    }

    private static void linea(ByteArrayOutputStream salida, String comando) {
        byte[] bytes = comando.getBytes(StandardCharsets.ISO_8859_1);
        salida.write(bytes, 0, bytes.length);
        salida.write('\n');
    }

    private static String n(double valor) {
        return String.format(java.util.Locale.ROOT, "%.2f", valor);
    }

    private static String escapar(String texto) {
        StringBuilder salida = new StringBuilder(texto.length());
        CharsetEncoder codificador = Charset.forName("windows-1252").newEncoder();
        for (char c : texto.toCharArray()) {
            if (c == '\\' || c == '(' || c == ')') {
                salida.append('\\').append(c);
            } else if (c == '\n' || c == '\r' || c == '\t') {
                salida.append(' ');
            } else if (codificador.canEncode(c)) {
                salida.append(c);
            } else {
                salida.append('?');
            }
        }
        return salida.toString();
    }

    private static byte[] serializarDocumento(List<byte[]> paginas) {
        int totalObjetos = 4 + paginas.size() * 2;
        List<byte[]> objetos = new ArrayList<>();
        objetos.add(null); // el índice 0 no se usa
        objetos.add(bytes(StandardCharsets.ISO_8859_1, "<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder chicos = new StringBuilder("<< /Type /Pages /Kids [");
        for (int i = 0; i < paginas.size(); i++) {
            chicos.append(5 + i * 2).append(" 0 R ");
        }
        chicos.append("] /Count ").append(paginas.size()).append(" >>");
        objetos.add(bytes(StandardCharsets.ISO_8859_1, chicos.toString()));
        objetos.add(bytes(StandardCharsets.ISO_8859_1,
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objetos.add(bytes(StandardCharsets.ISO_8859_1,
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        for (int i = 0; i < paginas.size(); i++) {
            int numeroPagina = 5 + i * 2;
            int numeroContenido = numeroPagina + 1;
            objetos.add(bytes(StandardCharsets.ISO_8859_1,
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                            + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> "
                            + "/Contents " + numeroContenido + " 0 R >>"));
            objetos.add(contenido(paginas.get(i)));
        }
        return ensamblar(objetos, totalObjetos);
    }

    private static byte[] contenido(byte[] flujo) {
        ByteArrayOutputStream objeto = new ByteArrayOutputStream();
        byte[] cabecera = bytes(StandardCharsets.ISO_8859_1,
                "<< /Length " + flujo.length + " >>\nstream\n");
        objeto.write(cabecera, 0, cabecera.length);
        objeto.write(flujo, 0, flujo.length);
        byte[] pie = bytes(StandardCharsets.ISO_8859_1, "\nendstream");
        objeto.write(pie, 0, pie.length);
        return objeto.toByteArray();
    }

    private static byte[] ensamblar(List<byte[]> objetos, int totalObjetos) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        escribir(salida, "%PDF-1.4\n");
        long[] desplazamientos = new long[objetos.size()];
        for (int i = 1; i < objetos.size(); i++) {
            desplazamientos[i] = salida.size();
            escribir(salida, i + " 0 obj\n");
            byte[] cuerpo = objetos.get(i);
            salida.write(cuerpo, 0, cuerpo.length);
            escribir(salida, "\nendobj\n");
        }
        long inicioXref = salida.size();
        int totalEntradas = totalObjetos + 1;
        escribir(salida, "xref\n0 " + totalEntradas + "\n");
        escribir(salida, "0000000000 65535 f \n");
        for (int i = 1; i < objetos.size(); i++) {
            escribir(salida, String.format(java.util.Locale.ROOT, "%010d 00000 n \n", desplazamientos[i]));
        }
        escribir(salida, "trailer\n<< /Size " + totalEntradas + " /Root 1 0 R >>\n");
        escribir(salida, "startxref\n" + inicioXref + "\n%%EOF");
        return salida.toByteArray();
    }

    private static void escribir(ByteArrayOutputStream salida, String texto) {
        byte[] bytes = texto.getBytes(StandardCharsets.ISO_8859_1);
        salida.write(bytes, 0, bytes.length);
    }

    private static byte[] bytes(Charset charset, String texto) {
        return texto.getBytes(charset);
    }
}
