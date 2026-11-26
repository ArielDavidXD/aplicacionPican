package cu.pican.controller.service;

import java.util.UUID;

/**
 * Contrato mínimo para que los servicios de negocio conozcan al usuario autenticado
 * sin depender de la interfaz ni crear sesiones propias.
 * Lo implementa AuthService, que es la única instancia compartida desde AppContext.
 */
public interface SessionUserIdProvider {

    /** Devuelve el UUID del usuario autenticado, o {@code null} si no hay sesión. */
    UUID getIdUsuarioAutenticado();
}
