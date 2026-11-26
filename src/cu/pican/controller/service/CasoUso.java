package cu.pican.controller.service;

import cu.pican.model.Rol;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Catálogo único de casos de uso que participan en la autorización de PICAN.
 *
 * Cada caso tiene un actor responsable y un conjunto de lectores. Las escrituras usan
 * {@link AutorizacionService#requerir(CasoUso...)}; las consultas usan
 * {@link AutorizacionService#requerirLectura(CasoUso...)}.
 */
public enum CasoUso {

    CU_01("CU-01", "Consultar referencias históricas de destinos", Rol.ESPECIALISTA_PRODUCCION,
            Rol.JEFE_UEB, Rol.ESPECIALISTA_ESTADISTICA),
    CU_02("CU-02", "Registrar y consultar metas estratégicas", Rol.ESPECIALISTA_PRODUCCION,
            Rol.ESPECIALISTA_ESTADISTICA),
    CU_03("CU-03", "Actualizar infraestructura", Rol.ESPECIALISTA_PRODUCCION,
            Rol.ESPECIALISTA_ESTADISTICA),
    CU_04("CU-04", "Gestionar camiones y choferes (fuera de alcance)"),
    CU_05("CU-05", "Registrar parte diario de producción", Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA),
    CU_06("CU-06", "Registrar producción de carne de ave", Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA),
    CU_07("CU-07", "Consolidar parte de la UEB", Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA),
    CU_08("CU-08", "Enviar parte al central", Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA),
    CU_09("CU-09", "Recibir paquetes en el central", Rol.ESPECIALISTA_ESTADISTICA,
            Rol.ESPECIALISTA_PRODUCCION),
    CU_10("CU-10", "Emitir vale de entrega", Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA),
    CU_11("CU-11", "Confirmar recepción", Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA),
    CU_12("CU-12", "Gestionar incidencia de entrega", Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA),
    CU_13("CU-13", "Anular o reimprimir vale", Rol.JEFE_UEB,
            Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA),
    CU_14("CU-14", "Analizar resultados", Rol.ESPECIALISTA_ESTADISTICA,
            Rol.ESPECIALISTA_PRODUCCION),
    CU_15("CU-15", "Generar y entregar informe", Rol.ESPECIALISTA_ESTADISTICA,
            Rol.ESPECIALISTA_PRODUCCION),
    CU_16("CU-16", "Gestionar usuarios", Rol.ADMINISTRADOR),
    CU_17("CU-17", "Gestionar catálogos", Rol.ADMINISTRADOR,
            Rol.ESPECIALISTA_PRODUCCION, Rol.JEFE_UEB, Rol.ESPECIALISTA_ESTADISTICA),
    CU_18("CU-18", "Importar datos desde Excel", Rol.ADMINISTRADOR),
    CU_19("CU-19", "Hacer y restaurar copias de seguridad", Rol.ADMINISTRADOR);

    private final String codigo;
    private final String descripcion;
    private final Set<String> rolesPermitidos;
    private final Set<String> rolesLectura;
    private final boolean habilitado;

    CasoUso(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.rolesPermitidos = Collections.emptySet();
        this.rolesLectura = Collections.emptySet();
        this.habilitado = false;
    }

    CasoUso(String codigo, String descripcion, String actor, String... lectores) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.rolesPermitidos = Collections.singleton(actor);
        Set<String> lectura = new LinkedHashSet<>(Arrays.asList(lectores));
        lectura.add(actor);
        this.rolesLectura = Collections.unmodifiableSet(lectura);
        this.habilitado = true;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Set<String> getRolesPermitidos() {
        return rolesPermitidos;
    }

    public Set<String> getRolesLectura() {
        return rolesLectura;
    }

    /** CU-04 permanece documentado pero fuera de alcance. */
    public boolean isHabilitado() {
        return habilitado;
    }

    @Override
    public String toString() {
        return codigo + " - " + descripcion;
    }
}
