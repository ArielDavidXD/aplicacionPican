package cu.pican.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Unidad Asociada (no estatal).
 * Las unidades no estatales no tendrán usuarios ni bases de datos propias;
 * la UEB registrará la información recibida mediante documentos en papel.
 * Pendiente de confirmar: asignación de unidades asociadas a las UEB.
 */
public class UnidadAsociada {

    private UUID idUnidad;
    private String nombre;
    private String codigo;
    private boolean activa;
    // Pendiente de confirmar: vínculo explícito con UEB (idUEB u otra relación).

    public UnidadAsociada() {
    }

    public UnidadAsociada(UUID idUnidad, String nombre, String codigo, boolean activa) {
        this.idUnidad = idUnidad;
        this.nombre = nombre;
        this.codigo = codigo;
        this.activa = activa;
    }

    public UUID getIdUnidad() {
        return idUnidad;
    }

    public void setIdUnidad(UUID idUnidad) {
        this.idUnidad = idUnidad;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
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
        if (!(o instanceof UnidadAsociada)) return false;
        UnidadAsociada that = (UnidadAsociada) o;
        return Objects.equals(idUnidad, that.idUnidad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idUnidad);
    }
}
