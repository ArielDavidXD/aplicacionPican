package cu.pican.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Destino (catálogo operativo, administrado en CU-17).
 * Identificado por UUID, código, nombre y estado activo/inactivo.
 * No se precargan destinos históricos como catálogo oficial completo.
 */
public class Destino {

    private UUID idDestino;
    private String codigo;
    private String nombre;
    private boolean activa;

    public Destino() {
    }

    public Destino(UUID idDestino, String codigo, String nombre, boolean activa) {
        this.idDestino = idDestino;
        this.codigo = codigo;
        this.nombre = nombre;
        this.activa = activa;
    }

    public UUID getIdDestino() {
        return idDestino;
    }

    public void setIdDestino(UUID idDestino) {
        this.idDestino = idDestino;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Destino)) return false;
        Destino destino = (Destino) o;
        return Objects.equals(idDestino, destino.idDestino);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idDestino);
    }
}
