package cu.pican.controller.importacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fila cruda leída de una hoja, con su número original para informar errores. */
public final class FilaExcel {

    private final int numero;
    private final List<String> celdas;

    public FilaExcel(int numero, List<String> celdas) {
        this.numero = numero;
        List<String> copia = new ArrayList<>();
        if (celdas != null) {
            copia.addAll(celdas);
        }
        this.celdas = Collections.unmodifiableList(copia);
    }

    public int getNumero() {
        return numero;
    }

    public List<String> getCeldas() {
        return celdas;
    }

    public String celda(int indice) {
        return indice >= 0 && indice < celdas.size() ? celdas.get(indice).trim() : "";
    }
}
