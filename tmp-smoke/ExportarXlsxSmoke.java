import cu.pican.export.ExportadorXlsx;
import cu.pican.importacion.ArchivoExcel;
import cu.pican.importacion.FilaExcel;
import cu.pican.importacion.LectorXlsx;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExportarXlsxSmoke {
    public static void main(String[] args) throws Exception {
        List<Object[]> filas = new ArrayList<>();
        filas.add(new Object[]{"Texto con ñ y tildes á é í ó ú", 123, 45.67, "última"});
        filas.add(new Object[]{"Segunda fila & <especial> \"comillas\"", 0L, -3.5, ""});
        ExportadorXlsx.Libro libro = new ExportadorXlsx.Libro();
        libro.hoja("Hoja 1", new String[]{"Texto", "Entero", "Decimal", "Otro"}, filas);
        libro.hoja("Segunda hoja con nombre largo que excede el límite permitido", new String[]{"Única"},
                Collections.singletonList(new Object[]{"valor"}));
        Path destino = Path.of("tmp-smoke/prueba.xlsx");
        libro.escribir(destino);
        System.out.println("XLSX generado: " + destino.toFile().length() + " bytes");

        ArchivoExcel leido = LectorXlsx.leer(destino);
        System.out.println("Hoja leída: " + leido.getHoja() + " (filas: " + leido.getFilas().size() + ")");
        for (FilaExcel fila : leido.getFilas()) {
            System.out.println("  " + fila.getNumero() + ": " + fila.getCeldas());
        }
        int errores = 0;
        FilaExcel datos = leido.getFilas().get(1);
        if (!"Texto con ñ y tildes á é í ó ú".equals(datos.celda(0))) {
            System.out.println("ERROR: el texto con tildes no se recuperó igual");
            errores++;
        }
        if (!"123".equals(datos.celda(1))) {
            System.out.println("ERROR: el entero no se recuperó: " + datos.celda(1));
            errores++;
        }
        if (!"45.67".equals(datos.celda(2))) {
            System.out.println("ERROR: el decimal no se recuperó: " + datos.celda(2));
            errores++;
        }
        if (errores == 0) {
            System.out.println("XLSX redondeado correctamente (escritura -> LectorXlsx).");
        } else {
            System.exit(1);
        }
    }
}
