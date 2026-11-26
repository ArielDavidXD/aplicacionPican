package cu.pican.verify;

import cu.pican.model.Rol;
import cu.pican.view.PermisosInterfaz;

import java.util.Set;

/** Verificación headless de la matriz de módulos por rol usada por menú y navegación. */
public final class PermisosInterfazTest {

    public static void main(String[] args) {
        int aprobadas = 0;
        int totales = 0;
        totales++;
        check(Set.of("inicio", "administracion").equals(
                PermisosInterfaz.modulosPermitidos(Rol.ADMINISTRADOR)),
                "ADMINISTRADOR no debe ver producción, estadística ni sincronización");
        aprobadas++;
        totales++;
        check(!PermisosInterfaz.puedeAcceder("sincronizacion", Rol.ADMINISTRADOR),
                "ADMINISTRADOR no debe entrar a sincronización");
        aprobadas++;
        totales++;
        check(Set.of("inicio", "referencias", "huevos", "carne", "consolidacion",
                        "entregas", "recepcion", "incidencias", "sincronizacion").equals(
                        PermisosInterfaz.modulosPermitidos(Rol.JEFE_UEB)),
                "Matriz incorrecta para JEFE_DE_UEB");
        aprobadas++;
        totales++;
        check(Set.of("inicio", "referencias", "huevos", "carne", "consolidacion",
                        "entregas", "recepcion", "incidencias", "informes", "sincronizacion").equals(
                        PermisosInterfaz.modulosPermitidos(Rol.ESPECIALISTA_ESTADISTICA)),
                "Matriz incorrecta para ESPECIALISTA_DE_ESTADISTICA");
        aprobadas++;
        totales++;
        check(Set.of("inicio", "referencias", "huevos", "carne", "consolidacion",
                        "entregas", "recepcion", "incidencias", "informes", "sincronizacion").equals(
                        PermisosInterfaz.modulosPermitidos(Rol.ESPECIALISTA_PRODUCCION)),
                "ESPECIALISTA_DE_PRODUCCION debe ver referencias y consultas operativas");
        aprobadas++;
        totales++;
        check(PermisosInterfaz.puedeAcceder("administracion", null)
                && PermisosInterfaz.modulosPermitidos(null).size() == 11,
                "El constructor heredado sin sesión debe mantener compatibilidad");
        aprobadas++;

        System.out.println("=== MATRIZ DE MÓDULOS POR ROL ===");
        System.out.println("[RESUMEN] " + aprobadas + "/" + totales + " comprobaciones aprobadas");
        System.exit(aprobadas == totales ? 0 : 1);
    }

    private static void check(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
