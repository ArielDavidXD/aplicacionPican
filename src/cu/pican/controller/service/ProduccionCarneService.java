package cu.pican.controller.service;

import cu.pican.model.ProduccionCarne;
import cu.pican.model.repository.ProduccionCarneRepository;
import cu.pican.model.repository.UEBRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para producción de carne.
 * Cantidad en toneladas, sin separación especializada/alternativa y sin unicidad UEB+fecha.
 */
public class ProduccionCarneService extends ServicioAutorizado {

    private final ProduccionCarneRepository produccionCarneRepository;
    private final UEBRepository uebRepository;
    private SessionUserIdProvider proveedorSesion;

    public ProduccionCarneService(ProduccionCarneRepository produccionCarneRepository, UEBRepository uebRepository) {
        this.produccionCarneRepository = produccionCarneRepository;
        this.uebRepository = uebRepository;
    }

    /** Inyectado desde AppContext; no modifica las reglas de producción. */
    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    public ProduccionCarne registrar(ProduccionCarne produccion) {
        autorizacion().requerir(CasoUso.CU_06);
        if (produccion != null) {
            produccion.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, produccion.getIdUsuario()));
            validarUEBActiva(produccion.getIdUEB());
        }
        validar(produccion);
        return produccionCarneRepository.save(produccion);
    }

    public ProduccionCarne actualizar(ProduccionCarne produccion) {
        autorizacion().requerir(CasoUso.CU_06);
        if (produccion != null) {
            produccion.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, produccion.getIdUsuario()));
        }
        validar(produccion);
        return produccionCarneRepository.update(produccion);
    }

    public Optional<ProduccionCarne> buscarPorId(UUID idProduccion) {
        autorizacion().requerirLectura(CasoUso.CU_06, CasoUso.CU_14);
        return produccionCarneRepository.findById(idProduccion);
    }

    public List<ProduccionCarne> listarTodas() {
        autorizacion().requerirLectura(CasoUso.CU_06, CasoUso.CU_14);
        return produccionCarneRepository.findAll();
    }

    public List<ProduccionCarne> buscarPorUEB(UUID idUEB) {
        autorizacion().requerirLectura(CasoUso.CU_06, CasoUso.CU_14);
        return produccionCarneRepository.findByUEB(idUEB);
    }

    public List<ProduccionCarne> buscarPorFecha(LocalDate fecha) {
        autorizacion().requerirLectura(CasoUso.CU_06, CasoUso.CU_14);
        return produccionCarneRepository.findByFecha(fecha);
    }

    private void validar(ProduccionCarne produccion) {
        ValidationUtils.requireNonNull(produccion, "La producción de carne es obligatoria.");
        ValidationUtils.requireUuid(produccion.getIdProduccion(), "El identificador UUID de la producción es obligatorio.");
        ValidationUtils.requireUuid(produccion.getIdUEB(), "La UEB de la producción es obligatoria.");
        if (!uebRepository.findById(produccion.getIdUEB()).isPresent()) {
            throw new ValidacionException("La UEB indicada no existe.");
        }
        ValidationUtils.requireNonNull(produccion.getFechaProduccion(), "La fecha de producción es obligatoria.");
        if (produccion.getFechaProduccion().isAfter(LocalDate.now())) {
            throw new ValidacionException("La fecha de producción no puede ser futura.");
        }
        ValidationUtils.requireNonNull(produccion.getCantidadToneladas(),
                "El campo 'cantidadToneladas' es obligatorio.");
        if (produccion.getCantidadToneladas().signum() <= 0) {
            throw new ValidacionException("La producción total de carne debe ser mayor que cero.");
        }
    }

    /** CU-17: una UEB desactivada no puede elegirse en nuevas operaciones. */
    private void validarUEBActiva(UUID idUEB) {
        ValidationUtils.requireUuid(idUEB, "La UEB de la producción es obligatoria.");
        cu.pican.model.UEB ueb = uebRepository.findById(idUEB)
                .orElseThrow(() -> new ValidacionException("La UEB indicada no existe."));
        if (!ueb.isActiva()) {
            throw new ValidacionException("No se puede registrar producción con una UEB desactivada.");
        }
    }
}
