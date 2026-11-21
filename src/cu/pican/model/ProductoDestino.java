package cu.pican.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Relación operativa entre un Producto y un Destino autorizado (catálogo administrado en CU-17).
 * Conserva identificadores UUID y permite activar/desactivar la relación.
 * No se permite duplicar una misma relación producto-destino.
 */
public class ProductoDestino {

    private UUID idRelacion;
    private UUID idProducto;
    private UUID idDestino;
    private boolean activa;

    public ProductoDestino() {
    }

    public ProductoDestino(UUID idRelacion, UUID idProducto, UUID idDestino, boolean activa) {
        this.idRelacion = idRelacion;
        this.idProducto = idProducto;
        this.idDestino = idDestino;
        this.activa = activa;
    }

    public UUID getIdRelacion() {
        return idRelacion;
    }

    public void setIdRelacion(UUID idRelacion) {
        this.idRelacion = idRelacion;
    }

    public UUID getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(UUID idProducto) {
        this.idProducto = idProducto;
    }

    public UUID getIdDestino() {
        return idDestino;
    }

    public void setIdDestino(UUID idDestino) {
        this.idDestino = idDestino;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductoDestino)) return false;
        ProductoDestino that = (ProductoDestino) o;
        return Objects.equals(idRelacion, that.idRelacion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idRelacion);
    }
}
