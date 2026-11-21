package cu.pican.model;

/** Opción de fuente obtenida desde AnalisisService; no contiene cantidades ni valores copiados. */
public final class OpcionFuente {

    private final TipoFuenteInforme tipo;
    private final String idRegistroOrigen;
    private final String descripcion;
    private final String fuente;
    private final String detalleConsulta;

    public OpcionFuente(TipoFuenteInforme tipo, String idRegistroOrigen, String descripcion,
                        String fuente, String detalleConsulta) {
        this.tipo = tipo;
        this.idRegistroOrigen = idRegistroOrigen;
        this.descripcion = descripcion;
        this.fuente = fuente;
        this.detalleConsulta = detalleConsulta;
    }

    public TipoFuenteInforme getTipo() { return tipo; }
    public String getIdRegistroOrigen() { return idRegistroOrigen; }
    public String getDescripcion() { return descripcion; }
    public String getFuente() { return fuente; }
    public String getDetalleConsulta() { return detalleConsulta; }

    public String getClave() {
        return tipo.name() + "|" + idRegistroOrigen;
    }

    @Override
    public String toString() {
        return tipo.name() + " - " + descripcion;
    }
}