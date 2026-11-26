package cu.pican.controller.importacion;

/** Detalle por fila mostrado en la vista previa y en el resumen. */
public final class ResultadoFila {

    private final int numero;
    private final EstadoFila estado;
    private final String motivo;

    public ResultadoFila(int numero, EstadoFila estado, String motivo) {
        this.numero = numero;
        this.estado = estado;
        this.motivo = motivo == null ? "" : motivo;
    }

    public int getNumero() {
        return numero;
    }

    public EstadoFila getEstado() {
        return estado;
    }

    public String getMotivo() {
        return motivo;
    }
}
