package cu.pican.controller.service;

import cu.pican.model.UnidadAsociada;
import cu.pican.model.repository.UnidadAsociadaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Servicio de negocio para Unidad Asociada (no estatal), CU-17.
 * No asigna unidades a UEB: ese vínculo sigue pendiente de aprobación documental.
 * No precarga las 28 unidades porque la fuente oficial con códigos y denominaciones
 * no está disponible en los documentos revisados.
 */
public class UnidadAsociadaService extends ServicioAutorizado {

    private final UnidadAsociadaRepository unidadAsociadaRepository;
    private Predicate<UUID> verificadorOperaciones = id -> false;

    public UnidadAsociadaService(UnidadAsociadaRepository unidadAsociadaRepository) {
        this.unidadAsociadaRepository = unidadAsociadaRepository;
    }

    /** Inyectado desde AppContext; por defecto no bloquea para no romper demos aisladas. */
    public void setVerificadorOperaciones(Predicate<UUID> verificadorOperaciones) {
        this.verificadorOperaciones = verificadorOperaciones;
    }

    public UnidadAsociada registrar(UnidadAsociada unidad) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(unidad);
        return unidadAsociadaRepository.save(unidad);
    }

    public UnidadAsociada actualizar(UnidadAsociada unidad) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(unidad);
        return unidadAsociadaRepository.update(unidad);
    }

    public Optional<UnidadAsociada> buscarPorId(UUID idUnidad) {
        autorizacion().requerirLecturaCatalogo();
        return unidadAsociadaRepository.findById(idUnidad);
    }

    public Optional<UnidadAsociada> buscarPorCodigo(String codigo) {
        autorizacion().requerirLecturaCatalogo();
        return unidadAsociadaRepository.findByCodigo(codigo);
    }

    public List<UnidadAsociada> listarTodas() {
        autorizacion().requerirLecturaCatalogo();
        return unidadAsociadaRepository.findAll();
    }

    public UnidadAsociada activar(UUID idUnidad) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idUnidad, true);
    }

    public UnidadAsociada desactivar(UUID idUnidad) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idUnidad, false);
    }

    public void eliminar(UUID idUnidad) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireUuid(idUnidad, "El identificador UUID de la unidad asociada es obligatorio.");
        unidadAsociadaRepository.findById(idUnidad)
                .orElseThrow(() -> new ValidacionException("No existe la unidad asociada indicada."));
        if (verificadorOperaciones.test(idUnidad)) {
            throw new ValidacionException(
                    "No se puede eliminar la unidad asociada porque tiene partes registrados. Desactívela en su lugar.");
        }
        unidadAsociadaRepository.deleteById(idUnidad);
    }

    private UnidadAsociada cambiarEstado(UUID idUnidad, boolean activa) {
        ValidationUtils.requireUuid(idUnidad, "El identificador UUID de la unidad asociada es obligatorio.");
        UnidadAsociada actual = unidadAsociadaRepository.findById(idUnidad)
                .orElseThrow(() -> new ValidacionException("No existe la unidad asociada indicada."));
        UnidadAsociada copia = copiar(actual);
        copia.setActiva(activa);
        return unidadAsociadaRepository.update(copia);
    }

    private void validar(UnidadAsociada unidad) {
        ValidationUtils.requireNonNull(unidad, "La unidad asociada es obligatoria.");
        ValidationUtils.requireUuid(unidad.getIdUnidad(), "El identificador UUID de la unidad asociada es obligatorio.");
        ValidationUtils.requireNotBlank(unidad.getCodigo(), "El código de la unidad asociada es obligatorio.");
        ValidationUtils.requireNotBlank(unidad.getNombre(), "El nombre de la unidad asociada es obligatorio.");

        Optional<UnidadAsociada> porCodigo = unidadAsociadaRepository.findByCodigo(unidad.getCodigo());
        if (porCodigo.isPresent() && !porCodigo.get().getIdUnidad().equals(unidad.getIdUnidad())) {
            throw new ValidacionException(
                    "Ya existe una unidad asociada con el código '" + unidad.getCodigo().trim() + "'.");
        }
    }

    private UnidadAsociada copiar(UnidadAsociada actual) {
        return new UnidadAsociada(actual.getIdUnidad(), actual.getNombre(),
                actual.getCodigo(), actual.isActiva());
    }
}
