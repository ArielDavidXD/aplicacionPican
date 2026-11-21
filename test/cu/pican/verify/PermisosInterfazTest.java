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
        check(PermisosInterfaz.puedeAcceder("sincronizacion", Rol.JEFE_UEB)
                && PermisosInterfaz.puedeAcceder("huevos", Rol.JEFE_UEB)
                && !PermisosInterfaz.puedeAcceder("informes", Rol.JEFE_UEB)
                && !PermisosInterfaz.puedeAcceder("administracion", Rol.JEFE_UEB),
                "Matriz incorrecta para JEFE_DE_UEB");
        aprobadas++;
        totales++;
        check(PermisosInterfaz.puedeAcceder("sincronizacion", Rol.ESPECIALISTA_ESTADISTICA)
                && PermisosInterfaz.puedeAcceder("informes", Rol.ESPECIALISTA_ESTADISTICA)
                && !PermisosInterfaz.puedeAcceder("huevos", Rol.ESPECIALISTA_ESTADISTICA),
                "Matriz incorrecta para ESPECIALISTA_DE_ESTADISTICA");
        aprobadas++;
        totales++;
        check(Set.of("inicio", "referencias").equals(
                PermisosInterfaz.modulosPermitidos(Rol.ESPECIALISTA_PRODUCCION)),
                "ESPECIALISTA_DE_PRODUCCION debe ver inicio y referencias (CU-01 a CU-03)");
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
