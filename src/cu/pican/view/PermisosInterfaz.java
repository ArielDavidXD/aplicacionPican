package cu.pican.view;

import cu.pican.controller.service.CasoUso;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Matriz ejecutable de módulos por rol. Se usa tanto para construir el menú como
 * para impedir la navegación directa a un módulo no autorizado.
 *
 * Las reglas se derivan de {@link CasoUso}: esta clase ya no duplica la lista de
 * roles. Es una segunda barrera; los servicios aplican la misma autoridad central.
 */
public final class PermisosInterfaz {

    private static final Map<String, CasoUso[]> CASOS_POR_MODULO;
    private static final Map<String, Set<String>> MODULOS;

    static {
        Map<String, CasoUso[]> casos = new LinkedHashMap<>();
        casos.put("inicio", new CasoUso[0]);
        casos.put("referencias", new CasoUso[]{CasoUso.CU_01, CasoUso.CU_02, CasoUso.CU_03});
        casos.put("huevos", new CasoUso[]{CasoUso.CU_05});
        casos.put("carne", new CasoUso[]{CasoUso.CU_06});
        casos.put("consolidacion", new CasoUso[]{CasoUso.CU_07});
        casos.put("entregas", new CasoUso[]{CasoUso.CU_10, CasoUso.CU_13});
        casos.put("recepcion", new CasoUso[]{CasoUso.CU_11});
        casos.put("incidencias", new CasoUso[]{CasoUso.CU_12});
        casos.put("informes", new CasoUso[]{CasoUso.CU_14, CasoUso.CU_15});
        casos.put("sincronizacion", new CasoUso[]{CasoUso.CU_08, CasoUso.CU_09});
        casos.put("administracion", new CasoUso[]{CasoUso.CU_16, CasoUso.CU_17, CasoUso.CU_18, CasoUso.CU_19});
        CASOS_POR_MODULO = Collections.unmodifiableMap(casos);

        Map<String, Set<String>> mapa = new LinkedHashMap<>();
        for (Map.Entry<String, CasoUso[]> entry : casos.entrySet()) {
            Set<String> roles = new LinkedHashSet<>();
            for (CasoUso caso : entry.getValue()) {
                roles.addAll(caso.getRolesPermitidos());
                // Leer un catálogo no concede acceso al módulo administrativo completo.
                if (!"administracion".equals(entry.getKey())) {
                    roles.addAll(caso.getRolesLectura());
                }
            }
            if ("inicio".equals(entry.getKey())) {
                roles.add("ADMINISTRADOR");
                roles.add("JEFE_DE_UEB");
                roles.add("ESPECIALISTA_DE_PRODUCCION");
                roles.add("ESPECIALISTA_DE_ESTADISTICA");
            }
            mapa.put(entry.getKey(), Collections.unmodifiableSet(roles));
        }
        MODULOS = Collections.unmodifiableMap(mapa);
    }

    private PermisosInterfaz() {
    }

    public static Map<String, Set<String>> restricciones() {
        return MODULOS;
    }

    public static boolean puedeAcceder(String moduloId, String rol) {
        if (rol == null) {
            return true;
        }
        Set<String> permitidos = MODULOS.get(moduloId);
        return permitidos != null && permitidos.contains(rol);
    }

    public static Set<String> modulosPermitidos(String rol) {
        if (rol == null) {
            return MODULOS.keySet();
        }
        Set<String> permitidos = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : MODULOS.entrySet()) {
            if (puedeAcceder(entry.getKey(), rol)) {
                permitidos.add(entry.getKey());
            }
        }
        return permitidos;
    }

    /** Casos de uso que respaldan un módulo, para trazabilidad con la matriz central. */
    public static Set<CasoUso> casosDelModulo(String modulo) {
        CasoUso[] casos = CASOS_POR_MODULO.get(modulo);
        if (casos == null) {
            return Collections.emptySet();
        }
        Set<CasoUso> resultado = new LinkedHashSet<>();
        Collections.addAll(resultado, casos);
        return Collections.unmodifiableSet(resultado);
    }
}
