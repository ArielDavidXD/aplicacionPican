package cu.pican.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio: Rol de usuario (CU-16).
 * Roles previstos: Jefe de UEB, Especialista de Producción, Especialista de Estadística, Administrador.
 * El Director no tendrá cuenta de usuario en el sistema: el especialista registra su nombre y decisión.
 * No se define una estructura compleja de permisos: la descripción resume los permisos generales
 * y cualquier división finer-grained requiere aprobación documental.
 */
public class Rol {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String JEFE_UEB = "JEFE_DE_UEB";
    public static final String ESPECIALISTA_PRODUCCION = "ESPECIALISTA_DE_PRODUCCION";
    public static final String ESPECIALISTA_ESTADISTICA = "ESPECIALISTA_DE_ESTADISTICA";

    private UUID idRol;
    private String nombre;
    private String descripcion;

    public Rol() {
    }

    public Rol(UUID idRol, String nombre, String descripcion) {
        this.idRol = idRol;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public UUID getIdRol() {
        return idRol;
    }

    public void setIdRol(UUID idRol) {
        this.idRol = idRol;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    /**
     * Devuelve uno de los roles previstos en el DCUS, con identificador estable y descripción
     * de sus permisos generales. No crea roles nuevos ni inventa permisos no aprobados.
     */
    public static Rol predefinido(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String clave = nombre.trim().toUpperCase();
        String descripcion;
        if (ADMINISTRADOR.equals(clave)) {
            descripcion = "Gestiona usuarios, roles y catálogos; no sustituye al directivo externo.";
        } else if (JEFE_UEB.equals(clave)) {
            descripcion = "Registra partes, producción, consolidaciones, vales, recepciones e incidencias de su UEB.";
        } else if (ESPECIALISTA_PRODUCCION.equals(clave)) {
            descripcion = "Consulta referencias oficiales, metas e infraestructura y apoya el registro de producción.";
        } else if (ESPECIALISTA_ESTADISTICA.equals(clave)) {
            descripcion = "Analiza resultados y gestiona informes; registra la decisión del directivo externo.";
        } else {
            return null;
        }
        UUID id = UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8));
        return new Rol(id, clave, descripcion);
    }

    /** Devuelve el rol previsto cuyo identificador estable coincide, o null. */
    public static Rol desdeId(UUID idRol) {
        if (idRol == null) {
            return null;
        }
        for (String nombre : new String[]{ADMINISTRADOR, JEFE_UEB,
                ESPECIALISTA_PRODUCCION, ESPECIALISTA_ESTADISTICA}) {
            Rol rol = predefinido(nombre);
            if (rol != null && rol.getIdRol().equals(idRol)) {
                return rol;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rol)) return false;
        Rol rol = (Rol) o;
        return Objects.equals(idRol, rol.idRol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idRol);
    }
}
