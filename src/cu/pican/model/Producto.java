package cu.pican.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Producto (catálogo operativo, administrado en CU-17).
 * Identificado por UUID, código, nombre y estado activo/inactivo.
 * Nota: no se definen categorías ni tipos de producción. La unidad de medida se contempla
 * como atributo opcional (no obligatorio) pendiente de confirmación.
 */
public class Producto {

    private UUID idProducto;
    private String codigo;
    private String nombre;
    private boolean activa;
    /**
     * Unidad de medida opcional (obligatoriedad pendiente de confirmación).
     */
    private String unidadMedida;

    public Producto() {
    }

    public Producto(UUID idProducto, String codigo, String nombre, boolean activa) {
        this.idProducto = idProducto;
        this.codigo = codigo;
        this.nombre = nombre;
        this.activa = activa;
    }

    public Producto(UUID idProducto, String codigo, String nombre, boolean activa, String unidadMedida) {
        this.idProducto = idProducto;
        this.codigo = codigo;
        this.nombre = nombre;
        this.activa = activa;
        this.unidadMedida = unidadMedida;
    }

    public UUID getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(UUID idProducto) {
        this.idProducto = idProducto;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Producto)) return false;
        Producto producto = (Producto) o;
        return Objects.equals(idProducto, producto.idProducto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idProducto);
    }
}
