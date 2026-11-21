package cu.pican.service;

/**
 * Excepción para errores de validación de reglas de negocio/entrada en la capa de servicios.
 */
public class ValidacionException extends RuntimeException {

    public ValidacionException(String message) {
        super(message);
    }
}
