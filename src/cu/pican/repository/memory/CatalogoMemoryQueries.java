package cu.pican.repository.memory;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Consultas reutilizables para repositorios en memoria de catálogos (CU-17).
 * La comparación ignora mayúsculas y espacios periféricos; la unicidad definitiva
 * en PostgreSQL deberá apoyarse en una restricción equivalente (decisión pendiente).
 */
final class CatalogoMemoryQueries {

    private CatalogoMemoryQueries() {
    }

    static <T> Optional<T> findByCodigo(Collection<T> entities,
                                        Function<T, String> getter,
                                        String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Optional.empty();
        }
        String clave = normalizar(codigo);
        return entities.stream()
                .filter(e -> {
                    String valor = getter.apply(e);
                    return valor != null && clave.equals(normalizar(valor));
                })
                .findFirst();
    }

    private static String normalizar(String valor) {
        return valor.trim().toLowerCase(Locale.ROOT);
    }
}
