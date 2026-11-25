import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifica la estructura interna del PDF: xref, offsets y emparejamiento BT/ET. */
public class ExportarPdfVerificar {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        String texto = new String(bytes, StandardCharsets.ISO_8859_1);
        int errores = 0;

        if (!texto.startsWith("%PDF-1.4")) {
            System.out.println("ERROR: cabecera %PDF-1.4 ausente");
            errores++;
        }
        if (!texto.trim().endsWith("%%EOF")) {
            System.out.println("ERROR: marca %%EOF ausente");
            errores++;
        }
        int idxStartxref = texto.lastIndexOf("startxref");
        int idxXref = texto.lastIndexOf("\nxref\n") + 1;
        if (idxStartxref < 0 || idxXref < 0) {
            System.out.println("ERROR: startxref o xref ausente");
            errores++;
        } else {
            long[] offsets = new long[1024];
            int maxNumero = -1;
            boolean tabla = false;
            for (String linea : texto.substring(idxXref, idxStartxref).split("\n")) {
                String l = linea.trim();
                if (l.equals("xref")) { tabla = true; continue; }
                if (!tabla) continue;
                if (l.isEmpty() || l.matches("\\d+ \\d+")) continue; // cabecera "0 N"
                if (l.startsWith("trailer") || l.startsWith("<<")) break;
                if (l.length() >= 18 && l.endsWith("n")) {
                    long offset = Long.parseLong(l.substring(0, 10).trim());
                    offsets[++maxNumero] = offset;
                    if (offset > 20000) System.out.println("DEBUG entrada " + maxNumero + ": \"" + l + "\" -> " + offset);
                } else if (l.length() >= 18 && l.endsWith("f")) {
                    ++maxNumero; // entrada libre (objeto 0)
                } else {
                    System.out.println("ERROR: línea xref no reconocida: \"" + l + "\"");
                    errores++;
                }
            }
            for (int numero = 1; numero <= maxNumero; numero++) {
                String marca = numero + " 0 obj";
                int offset = (int) offsets[numero];
                if (offset + marca.length() > texto.length()) {
                    System.out.println("ERROR: offset del objeto " + numero + " fuera del archivo");
                    errores++;
                    continue;
                }
                String real = texto.substring(offset, offset + marca.length());
                if (!marca.equals(real)) {
                    System.out.println("ERROR: offset del objeto " + numero + " apunta a \""
                            + real.replace("\n", "\\n") + "\" en vez de \"" + marca + "\"");
                    errores++;
                }
            }
            System.out.println("Entradas xref verificadas: " + (maxNumero + 1));
        }
        int pos = 0;
        int streams = 0;
        while ((pos = texto.indexOf("\nstream\n", pos)) != -1) {
            pos += 1; // apunta a la 's' de stream
            int fin = texto.indexOf("\nendstream", pos);
            if (fin < 0) {
                System.out.println("DEBUG: 'stream\\n' sin '\\nendstream' en posición " + pos);
                break;
            }
            String flujo = texto.substring(pos + 7, fin);
            int bt = flujo.split("BT", -1).length - 1;
            int et = flujo.split("ET", -1).length - 1;
            if (bt != et) {
                System.out.println("ERROR: stream " + streams + " con BT=" + bt + " y ET=" + et);
                errores++;
            }
            if (flujo.contains("undefined")) {
                System.out.println("ERROR: stream " + streams + " contiene \"undefined\"");
                errores++;
            }
            streams++;
            pos = fin + 1;
        }
        System.out.println("Streams de contenido: " + streams);
        if (errores == 0) {
            System.out.println("PDF estructuralmente válido.");
        } else {
            System.exit(1);
        }
    }
}
