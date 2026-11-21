package cu.pican.service;

import cu.pican.model.Rol;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Catálogo único de casos de uso que participan en la autorización de PICAN.
 *
 * La responsabilidad se toma de los DCUS y de {@code Rol.java}. No se inventan permisos
 * internos: cuando una operación técnica necesita más de un caso de uso (por ejemplo,
 * una lectura de catálogo desde CU-05), el servicio lo declara explícitamente pasando
 * varios valores a {@link AutorizacionService#requerir(CasoUso...)}.
 */
public enum CasoUso {

    CU_01("CU-01", "Consultar referencias históricas de destinos", Rol.ESPECIALISTA_PRODUCCION),
    CU_02("CU-02", "Consultar metas estratégicas", Rol.ESPECIALISTA_PRODUCCION),
    CU_03("CU-03", "Consultar infraestructura", Rol.ESPECIALISTA_PRODUCCION),
    CU_04("CU-04", "Gestionar camiones y choferes (fuera de alcance)"),
    CU_05("CU-05", "Registrar partes diarios de huevos", Rol.JEFE_UEB),
    CU_06("CU-06", "Registrar producción de carne", Rol.JEFE_UEB),
    CU_07("CU-07", "Consolidar información de la UEB", Rol.JEFE_UEB),
    CU_08("CU-08", "Exportar e importar paquetes en la UEB", Rol.JEFE_UEB),
    CU_09("CU-09", "Recibir, validar e importar paquetes en la central", Rol.ESPECIALISTA_ESTADISTICA),
    CU_10("CU-10", "Emitir vales de entrega", Rol.JEFE_UEB),
    CU_11("CU-11", "Confirmar recepciones", Rol.JEFE_UEB),
    CU_12("CU-12", "Gestionar incidencias", Rol.JEFE_UEB),
    CU_13("CU-13", "Anular vales", Rol.JEFE_UEB),
    CU_14("CU-14", "Analizar información", Rol.ESPECIALISTA_ESTADISTICA),
    CU_15("CU-15", "Elaborar, gestionar y entregar informes", Rol.ESPECIALISTA_ESTADISTICA),
    CU_16("CU-16", "Gestionar usuarios y autenticación", Rol.ADMINISTRADOR),
    CU_17("CU-17", "Gestionar catálogos", Rol.ADMINISTRADOR),
    CU_18("CU-18", "Importar información desde Excel", Rol.ADMINISTRADOR),
    CU_19("CU-19", "Hacer y restaurar copias de seguridad", Rol.ADMINISTRADOR);

    private final String codigo;
    private final String descripcion;
    private final Set<String> rolesPermitidos;
    private final boolean habilitado;

    CasoUso(String codigo, String descripcion, String... rolesPermitidos) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.rolesPermitidos = Collections.unmodifiableSet(
                new LinkedHashSet<>(Arrays.asList(rolesPermitidos)));
        this.habilitado = !this.rolesPermitidos.isEmpty();
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

    /** CU-04 permanece documentado pero fuera de alcance; CU-19 está implementado en la etapa 4.18. */
    public boolean isHabilitado() {
        return habilitado;
    }

    @Override
    public String toString() {
        return codigo + " - " + descripcion;
    }
}
