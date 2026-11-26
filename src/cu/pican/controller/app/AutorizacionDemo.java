package cu.pican.controller.app;

import cu.pican.model.Rol;
import cu.pican.model.Usuario;
import cu.pican.controller.service.AutorizacionService;
import cu.pican.controller.service.CasoUso;
import cu.pican.controller.service.SesionAutorizacionProvider;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Instalación explícita de una sesión de demostración para los arneses y demos
 * históricos que construyen servicios sin pasar por {@link AppContext}.
 *
 * No se usa en la aplicación real: {@code AppContext} instala la sesión de
 * {@code AuthService}. No hay autorización permisiva por defecto; si un arnés
 * olvida instalar esta sesión, las operaciones protegidas fallan cerradas.
 */
public final class AutorizacionDemo {

    private AutorizacionDemo() {
    }

    /** Instala una política de demostración con los roles indicados. */
    public static void instalar(String... nombresRoles) {
        AutorizacionService.instalar(politica(nombresRoles));
    }

    /**
     * Crea una política de demostración sin instalarla globalmente. La usa
     * {@link DemoDataInitializer} para sembrar datos no oficiales antes del login.
     */
    public static AutorizacionService politica(String... nombresRoles) {
        Set<String> roles = new LinkedHashSet<>();
        for (String nombre : nombresRoles) {
            Rol rol = Rol.predefinido(nombre);
            if (rol != null) {
                roles.add(rol.getNombre());
            }
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos un rol de demostración válido.");
        }
        String primerRol = roles.iterator().next();
        Usuario usuarioDemo = new Usuario(UUID.randomUUID(), "demo_autorizacion", "Sesión de demostración",
                null, Rol.predefinido(primerRol), true, LocalDateTime.now(), false);
        SesionAutorizacionProvider proveedor = new SesionAutorizacionProvider() {
            @Override
            public Usuario getUsuarioAutenticado() {
                return usuarioDemo;
            }

            @Override
            public UUID getIdUsuarioAutenticado() {
                return usuarioDemo.getIdUsuario();
            }
        };
        return new AutorizacionService(proveedor) {
            @Override
            public boolean puedeAcceder(CasoUso... casos) {
                if (!estaAutenticado() || casos == null) {
                    return false;
                }
                for (CasoUso caso : casos) {
                    if (caso != null && caso.isHabilitado()
                            && roles.stream().anyMatch(caso.getRolesPermitidos()::contains)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean puedeLeer(CasoUso... casos) {
                if (!estaAutenticado() || casos == null) {
                    return false;
                }
                for (CasoUso caso : casos) {
                    if (caso != null && caso.isHabilitado()
                            && (roles.stream().anyMatch(caso.getRolesPermitidos()::contains)
                                || roles.stream().anyMatch(caso.getRolesLectura()::contains))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public static AutorizacionService politicaTodosLosRoles() {
        return politica(Rol.ADMINISTRADOR, Rol.JEFE_UEB,
                Rol.ESPECIALISTA_PRODUCCION, Rol.ESPECIALISTA_ESTADISTICA);
    }

    public static void instalarTodosLosRoles() {
        AutorizacionService.instalar(politicaTodosLosRoles());
    }
}
