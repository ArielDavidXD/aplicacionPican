package cu.pican.model.persistence;

import java.util.function.Supplier;

/** Transacciones simuladas para las demostraciones con repositorios en memoria. */
public final class NoOpTransactionManager implements TransactionManager {

    @Override
    public <T> T ejecutar(Supplier<T> operacion) {
        return operacion.get();
    }
}
