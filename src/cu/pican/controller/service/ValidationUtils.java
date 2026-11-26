package cu.pican.controller.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Utilidades de validación reutilizables para servicios.
 * Centraliza comprobaciones comunes y evita duplicación.
 */
public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new ValidacionException(message);
        }
    }

    public static void requireNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidacionException(message);
        }
    }

    public static void requireUuid(UUID value, String message) {
        if (value == null) {
            throw new ValidacionException(message);
        }
    }

    public static void requireNonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new ValidacionException("El campo '" + fieldName + "' no puede ser negativo.");
        }
    }

    public static void requireNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidacionException("El campo '" + fieldName + "' no puede ser negativo.");
        }
    }
}
