package cu.pican.importacion;

/** Clasificación de cada fila según VAL-96 y VAL-97. */
public enum EstadoFila {
    VALIDA("Válida"),
    RECHAZADA("Rechazada"),
    REPETIDA("Repetida");

    private final String etiqueta;

    EstadoFila(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
