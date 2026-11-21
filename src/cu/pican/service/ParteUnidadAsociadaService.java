package cu.pican.service;

import cu.pican.model.ParteUnidadAsociada;
import cu.pican.repository.ParteUnidadAsociadaRepository;
import cu.pican.repository.UnidadAsociadaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para partes de producción de unidades asociadas.
 * IMPORTANTE: no se inventan campos productivos ni validaciones basadas en ParteNave.
 * Pendiente de confirmar: formulario real en papel y campos productivos específicos.
 */
public class ParteUnidadAsociadaService extends ServicioAutorizado {

    private final ParteUnidadAsociadaRepository parteRepository;
    private final UnidadAsociadaRepository unidadAsociadaRepository;
    private SessionUserIdProvider proveedorSesion;

    public ParteUnidadAsociadaService(ParteUnidadAsociadaRepository parteRepository,
                                      UnidadAsociadaRepository unidadAsociadaRepository) {
        this.parteRepository = parteRepository;
        this.unidadAsociadaRepository = unidadAsociadaRepository;
    }

    /** Inyectado desde AppContext; solo completa el usuario cuando la operación no lo trae. */
    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    public ParteUnidadAsociada registrar(ParteUnidadAsociada parte) {
        autorizacion().requerir(CasoUso.CU_05);
        if (parte != null) {
            parte.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, parte.getIdUsuario()));
            validarUnidadActiva(parte.getIdUnidad());
        }
        validar(parte);
        return parteRepository.save(parte);
    }

    public ParteUnidadAsociada actualizar(ParteUnidadAsociada parte) {
        autorizacion().requerir(CasoUso.CU_05);
        if (parte != null) {
            parte.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, parte.getIdUsuario()));
        }
        validar(parte);
        return parteRepository.update(parte);
    }

    public Optional<ParteUnidadAsociada> buscarPorId(UUID idParteAsociado) {
        autorizacion().requerir(CasoUso.CU_05, CasoUso.CU_14);
        return parteRepository.findById(idParteAsociado);
    }

    public List<ParteUnidadAsociada> listarTodos() {
        autorizacion().requerir(CasoUso.CU_05, CasoUso.CU_14);
        return parteRepository.findAll();
    }

    public List<ParteUnidadAsociada> buscarPorUnidad(UUID idUnidad) {
        autorizacion().requerir(CasoUso.CU_05, CasoUso.CU_14);
        return parteRepository.findByUnidad(idUnidad);
    }

    public List<ParteUnidadAsociada> buscarPorFecha(LocalDate fecha) {
        autorizacion().requerir(CasoUso.CU_05, CasoUso.CU_14);
        return parteRepository.findByFecha(fecha);
    }

    private void validar(ParteUnidadAsociada parte) {
        ValidationUtils.requireNonNull(parte, "El parte de unidad asociada es obligatorio.");
        ValidationUtils.requireUuid(parte.getIdParteAsociado(), "El identificador UUID del parte asociado es obligatorio.");
        ValidationUtils.requireUuid(parte.getIdUnidad(), "La unidad asociada es obligatoria.");
        if (!unidadAsociadaRepository.findById(parte.getIdUnidad()).isPresent()) {
            throw new ValidacionException("La unidad asociada indicada no existe.");
        }
        ValidationUtils.requireNonNull(parte.getFechaProduccion(), "La fecha de producción es obligatoria.");
        // Sin validaciones de campos productivos: pendientes del formulario real en papel.
    }

    /** CU-17: una unidad desactivada no puede elegirse en nuevas operaciones. */
    private void validarUnidadActiva(UUID idUnidad) {
        ValidationUtils.requireUuid(idUnidad, "La unidad asociada es obligatoria.");
        cu.pican.model.UnidadAsociada unidad = unidadAsociadaRepository.findById(idUnidad)
                .orElseThrow(() -> new ValidacionException("La unidad asociada indicada no existe."));
        if (!unidad.isActiva()) {
            throw new ValidacionException("No se puede registrar un parte con una unidad asociada desactivada.");
        }
    }
}
