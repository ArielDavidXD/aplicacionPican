package cu.pican.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Nave.
 * Relación: pertenece a una UEB; puede tener varios partes de producción.
 * Propuesta de modelo de datos (no definitiva).
 */
public class Nave {

    private UUID idNave;
    private UUID idUEB;
    private String nombre;
    private String codigo;
    private boolean activa;

    public Nave() {
    }

    public Nave(UUID idNave, UUID idUEB, String nombre, String codigo, boolean activa) {
        this.idNave = idNave;
        this.idUEB = idUEB;
        this.nombre = nombre;
        this.codigo = codigo;
        this.activa = activa;
    }

    public UUID getIdNave() {
        return idNave;
    }

    public void setIdNave(UUID idNave) {
        this.idNave = idNave;
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
        if (!(o instanceof Nave)) return false;
        Nave nave = (Nave) o;
        return Objects.equals(idNave, nave.idNave);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idNave);
    }
}
