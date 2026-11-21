package cu.pican.repository.memory;

import cu.pican.repository.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación temporal en memoria para repositorios de entidades identificadas por ID.
 * No contiene reglas de negocio; solo operaciones de almacenamiento y consulta.
 * Las colecciones internas no se exponen directamente.
 */
public abstract class InMemoryRepository<T, ID> implements Repository<T, ID> {

    protected final Map<ID, T> store = new LinkedHashMap<>();

    /**
     * Obtiene el identificador de la entidad.
     */
    protected abstract ID getId(T entity);

    @Override
    public List<T> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("La entidad no puede ser nula.");
        }
        ID id = getId(entity);
        if (id == null) {
            throw new IllegalArgumentException("La entidad debe tener un identificador no nulo.");
        }
        store.put(id, entity);
        return entity;
    }

    @Override
    public void deleteById(ID id) {
        if (id != null) {
            store.remove(id);
        }
    }
}
