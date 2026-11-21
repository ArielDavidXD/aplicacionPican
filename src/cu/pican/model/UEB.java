package cu.pican.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: UEB (Unidad Empresarial de Base).
 * Propuesta de modelo de datos (no definitiva). Identificador UUID para intercambio entre bases.
 */
public class UEB {

    private UUID idUEB;
    private String nombre;
    private String codigo;
    private boolean activa;

    public UEB() {
    }

    public UEB(UUID idUEB, String nombre, String codigo, boolean activa) {
        this.idUEB = idUEB;
        this.nombre = nombre;
        this.codigo = codigo;
        this.activa = activa;
    }

    public UUID getIdUEB() {
        return idUEB;
    }

    public void setIdUEB(UUID idUEB) {
        this.idUEB = idUEB;
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
        if (!(o instanceof UEB)) return false;
        UEB ueb = (UEB) o;
        return Objects.equals(idUEB, ueb.idUEB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idUEB);
    }
}
