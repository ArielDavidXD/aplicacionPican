package cu.pican.repository;

import cu.pican.model.Usuario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de cuentas de usuario (CU-16).
 * Amplía el contrato genérico con búsquedas por nombre de usuario y estado, control de duplicados
 * y comprobación de operaciones asociadas antes de permitir una eliminación física.
 */
public interface UsuarioRepository extends Repository<Usuario, UUID> {

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    List<Usuario> findByActivo(boolean activo);

    boolean existsByNombreUsuario(String nombreUsuario);

    /** Indica si el usuario ya aparece como responsable en alguna operación registrada. */
    boolean estaAsociadoAOperaciones(UUID idUsuario);
}
