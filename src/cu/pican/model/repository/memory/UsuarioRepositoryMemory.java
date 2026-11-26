package cu.pican.model.repository.memory;

import cu.pican.model.Usuario;
import cu.pican.model.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Implementación en memoria del repositorio de usuarios (CU-16).
 * Mantiene un índice por nombre de usuario (sin distinción de mayúsculas) para garantizar unicidad
 * y un predicado opcional que impide borrar físicamente cuentas ya usadas en operaciones.
 * Las validaciones de cuenta completa (hash y rol) se hacen en UsuarioService; aquí se garantizan
 * los datos de identidad para no romper las demostraciones históricas que siembran usuarios mínimos.
 */
public class UsuarioRepositoryMemory extends InMemoryRepository<Usuario, UUID> implements UsuarioRepository {

    private final Map<String, Usuario> porNombre = new LinkedHashMap<>();
    private final Predicate<UUID> referenciasOperaciones;

    public UsuarioRepositoryMemory() {
        this(id -> false);
    }

    public UsuarioRepositoryMemory(Predicate<UUID> referenciasOperaciones) {
        this.referenciasOperaciones = referenciasOperaciones == null ? id -> false : referenciasOperaciones;
    }

    @Override
    protected UUID getId(Usuario entity) {
        return entity.getIdUsuario();
    }

    @Override
    public Optional<Usuario> findByNombreUsuario(String nombreUsuario) {
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(porNombre.get(clave(nombreUsuario)));
    }

    @Override
    public List<Usuario> findByActivo(boolean activo) {
        List<Usuario> resultado = new ArrayList<>();
        for (Usuario usuario : store.values()) {
            if (usuario.isActivo() == activo) {
                resultado.add(usuario);
            }
        }
        return resultado;
    }

    @Override
    public boolean existsByNombreUsuario(String nombreUsuario) {
        return findByNombreUsuario(nombreUsuario).isPresent();
    }

    @Override
    public boolean estaAsociadoAOperaciones(UUID idUsuario) {
        return idUsuario != null && referenciasOperaciones.test(idUsuario);
    }

    @Override
    public Usuario save(Usuario entity) {
        validarDatosIdentidad(entity);
        Usuario anterior = entity.getIdUsuario() == null ? null : store.get(entity.getIdUsuario());
        String clave = clave(entity.getNombreUsuario());
        Usuario duplicado = porNombre.get(clave);
        if (duplicado != null && !duplicado.getIdUsuario().equals(entity.getIdUsuario())) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre de usuario.");
        }
        if (anterior != null && !clave(anterior.getNombreUsuario()).equals(clave)) {
            porNombre.remove(clave(anterior.getNombreUsuario()));
        }
        Usuario guardado = super.save(entity);
        porNombre.put(clave, guardado);
        return guardado;
    }

    @Override
    public void deleteById(UUID id) {
        if (estaAsociadoAOperaciones(id)) {
            throw new IllegalStateException("El usuario tiene operaciones asociadas; solo se permite desactivar la cuenta.");
        }
        Usuario eliminado = store.get(id);
        if (eliminado != null) {
            porNombre.remove(clave(eliminado.getNombreUsuario()));
        }
        super.deleteById(id);
    }

    private void validarDatosIdentidad(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("El usuario no puede ser nulo.");
        }
        if (usuario.getIdUsuario() == null) {
            throw new IllegalArgumentException("El usuario debe tener un identificador no nulo.");
        }
        if (usuario.getNombreUsuario() == null || usuario.getNombreUsuario().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        if (usuario.getNombreCompleto() == null || usuario.getNombreCompleto().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio.");
        }
    }

    private String clave(String nombreUsuario) {
        return nombreUsuario.trim().toUpperCase(Locale.ROOT);
    }
}
