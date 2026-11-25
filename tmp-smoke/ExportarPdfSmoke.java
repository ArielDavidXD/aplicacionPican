import cu.pican.export.ExportadorPdf;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ExportarPdfSmoke {
    public static void main(String[] args) throws Exception {
        List<ExportadorPdf.Seccion> secciones = new ArrayList<>();
        List<Object[]> filas = new ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            filas.add(new Object[]{"Fila " + i, "Texto largo de prueba con palabras que deben partirse correctamente en varias líneas de la tabla número " + i, "Valor " + i});
        }
        secciones.add(new ExportadorPdf.Seccion("Tabla extensa que debe ocupar varias páginas del documento",
                new String[]{"Número", "Descripción", "Importe"}, filas));
        secciones.add(new ExportadorPdf.Seccion("Segunda sección", new String[]{"Campo"},
                java.util.Collections.singletonList(new Object[]{"á é í ó ú ñ Á É Í Ó Ú Ñ ¿ ¡"})));
        Path destino = Path.of("tmp-smoke/prueba.pdf");
        ExportadorPdf.escribir(destino, "Prueba de estructura PDF (PICAN)", secciones);
        System.out.println("PDF generado: " + destino.toAbsolutePath() + " (" + destino.toFile().length() + " bytes)");
    }
}
