package cu.pican.model;

import java.util.UUID;

/**
 * Referencia a una fuente utilizada por un informe. NO copia valores de los registros originales:
 * solo guarda el tipo, el identificador del registro de origen y su etiqueta de procedencia.
 */
public final class FuenteInforme {

    private final UUID idFuente;
    private final UUID idInforme;
    private final TipoFuenteInforme tipo;
    private final String idRegistroOrigen;
    private final String descripcion;
    private final String fuente;
    private final String detalleConsulta;

    public FuenteInforme(UUID idFuente, UUID idInforme, TipoFuenteInforme tipo,
                         String idRegistroOrigen, String descripcion, String fuente, String detalleConsulta) {
        this.idFuente = idFuente;
        this.idInforme = idInforme;
        this.tipo = tipo;
        this.idRegistroOrigen = idRegistroOrigen;
        this.descripcion = descripcion;
        this.fuente = fuente;
        this.detalleConsulta = detalleConsulta;
    }

    public UUID getIdFuente() { return idFuente; }
    public UUID getIdInforme() { return idInforme; }
    public TipoFuenteInforme getTipo() { return tipo; }
    public String getIdRegistroOrigen() { return idRegistroOrigen; }
    public String getDescripcion() { return descripcion; }
    public String getFuente() { return fuente; }
    public String getDetalleConsulta() { return detalleConsulta; }
}