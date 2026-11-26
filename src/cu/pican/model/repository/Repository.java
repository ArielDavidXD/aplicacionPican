package cu.pican.model.repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato genérico de acceso a datos.
 * Permitirá sustituir implementaciones en memoria por PostgreSQL/JDBC en el futuro.
 */
public interface Repository<T, ID> {
    List<T> findAll();
    Optional<T> findById(ID id);
    T save(T entity);
    void deleteById(ID id);

    /**
     * Actualiza una entidad existente. Implementación por defecto basada en {@link #save(Object)}.
     */
    default T update(T entity) {
        return save(entity);
    }
}
