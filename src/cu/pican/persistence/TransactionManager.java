package cu.pican.persistence;

import java.util.function.Supplier;

/** Ejecuta una operación de negocio con límites transaccionales explícitos. */
public interface TransactionManager {

    <T> T ejecutar(Supplier<T> operacion);

    default void ejecutar(Runnable operacion) {
        ejecutar(() -> {
            operacion.run();
            return null;
        });
    }
}
