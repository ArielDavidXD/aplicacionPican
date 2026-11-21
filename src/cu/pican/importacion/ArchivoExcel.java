package cu.pican.importacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resultado de la lectura segura de un archivo .xlsx. */
public final class ArchivoExcel {

    private final String nombre;
    private final String hoja;
    private final List<FilaExcel> filas;

    public ArchivoExcel(String nombre, String hoja, List<FilaExcel> filas) {
        this.nombre = nombre == null ? "" : nombre;
        this.hoja = hoja == null ? "" : hoja;
        this.filas = Collections.unmodifiableList(new ArrayList<>(filas));
    }

    public String getNombre() {
        return nombre;
    }

    public String getHoja() {
        return hoja;
    }

    public List<FilaExcel> getFilas() {
        return filas;
    }
}
