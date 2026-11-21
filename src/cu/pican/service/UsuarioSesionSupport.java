package cu.pican.service;

import java.util.UUID;

/**
 * Apoyo para integrar la sesión en los servicios existentes sin alterar sus reglas de negocio.
 * Solo completa el identificador cuando la operación no trae uno: los identificadores
 * previamente registrados se conservan íntegramente.
 */
public final class UsuarioSesionSupport {

    private UsuarioSesionSupport() {}

    public static UUID resolver(SessionUserIdProvider proveedor, UUID idYaRegistrado) {
        if (idYaRegistrado != null) {
            return idYaRegistrado;
        }
        return proveedor == null ? null : proveedor.getIdUsuarioAutenticado();
    }
}
