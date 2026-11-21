package cu.pican.service;

import cu.pican.model.Destino;
import cu.pican.repository.DestinoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Servicio de negocio para el catálogo operativo de Destinos (CU-17).
 * No precarga los destinos históricos de la Tabla clave 3 como asignaciones operativas.
 * Registro, consulta, edición, activación/desactivación y bloqueo de baja física
 * cuando existen vales o relaciones asociadas.
 */
public class DestinoService extends ServicioAutorizado {

    private final DestinoRepository destinoRepository;
    private Predicate<UUID> verificadorOperaciones = id -> false;

    public DestinoService(DestinoRepository destinoRepository) {
        this.destinoRepository = destinoRepository;
    }

    /** Inyectado desde AppContext; por defecto no bloquea para no romper demos aisladas. */
    public void setVerificadorOperaciones(Predicate<UUID> verificadorOperaciones) {
        this.verificadorOperaciones = verificadorOperaciones;
    }

    public Destino registrar(Destino destino) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(destino);
        return destinoRepository.save(destino);
    }

    public Destino actualizar(Destino destino) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(destino);
        return destinoRepository.update(destino);
    }

    public Optional<Destino> buscarPorId(UUID idDestino) {
        autorizacion().requerirLecturaCatalogo();
        return destinoRepository.findById(idDestino);
    }

    public Optional<Destino> buscarPorCodigo(String codigo) {
        autorizacion().requerirLecturaCatalogo();
        return destinoRepository.findByCodigo(codigo);
    }

    public List<Destino> listarTodos() {
        autorizacion().requerirLecturaCatalogo();
        return destinoRepository.findAll();
    }

    public Destino activar(UUID idDestino) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idDestino, true);
    }

    public Destino desactivar(UUID idDestino) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idDestino, false);
    }

    public void eliminar(UUID idDestino) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireUuid(idDestino, "El identificador UUID del destino es obligatorio.");
        destinoRepository.findById(idDestino)
                .orElseThrow(() -> new ValidacionException("No existe el destino indicado."));
        if (verificadorOperaciones.test(idDestino)) {
            throw new ValidacionException(
                    "No se puede eliminar el destino porque tiene vales o relaciones asociadas. Desactívelo en su lugar.");
        }
        destinoRepository.deleteById(idDestino);
    }

    private Destino cambiarEstado(UUID idDestino, boolean activa) {
        ValidationUtils.requireUuid(idDestino, "El identificador UUID del destino es obligatorio.");
        Destino actual = destinoRepository.findById(idDestino)
                .orElseThrow(() -> new ValidacionException("No existe el destino indicado."));
        Destino copia = copiar(actual);
        copia.setActiva(activa);
        return destinoRepository.update(copia);
    }

    private void validar(Destino destino) {
        ValidationUtils.requireNonNull(destino, "El destino es obligatorio.");
        ValidationUtils.requireUuid(destino.getIdDestino(), "El identificador UUID del destino es obligatorio.");
        ValidationUtils.requireNotBlank(destino.getCodigo(), "El código del destino es obligatorio.");
        ValidationUtils.requireNotBlank(destino.getNombre(), "El nombre del destino es obligatorio.");

        Optional<Destino> porCodigo = destinoRepository.findByCodigo(destino.getCodigo());
        if (porCodigo.isPresent() && !porCodigo.get().getIdDestino().equals(destino.getIdDestino())) {
            throw new ValidacionException("Ya existe un destino con el código '" + destino.getCodigo().trim() + "'.");
        }
    }

    private Destino copiar(Destino actual) {
        return new Destino(actual.getIdDestino(), actual.getCodigo(), actual.getNombre(), actual.isActiva());
    }
}
