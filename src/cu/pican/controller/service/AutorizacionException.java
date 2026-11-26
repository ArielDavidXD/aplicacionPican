package cu.pican.controller.service;

/**
 * Rechazo controlado de una operación por falta de sesión o de rol autorizado.
 * No contiene datos sensibles y puede mostrarse de forma resumida en la interfaz.
 */
public class AutorizacionException extends RuntimeException {

    public AutorizacionException(String message) {
        super(message);
    }
}
