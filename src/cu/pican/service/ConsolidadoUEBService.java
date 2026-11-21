package cu.pican.service;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.repository.ConsolidadoUEBRepository;
import cu.pican.repository.UEBRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para consolidados por UEB.
 * Registra/gestiona el consolidado sin modificar ni eliminar los registros originales de producción.
 * Pendiente: políticas definitivas de consolidaciones parciales y autorizaciones (configurables/documentadas).
 */
public class ConsolidadoUEBService extends ServicioAutorizado {

    private final ConsolidadoUEBRepository consolidadoRepository;
    private final UEBRepository uebRepository;
    private SessionUserIdProvider proveedorSesion;

    public ConsolidadoUEBService(ConsolidadoUEBRepository consolidadoRepository, UEBRepository uebRepository) {
        this.consolidadoRepository = consolidadoRepository;
        this.uebRepository = uebRepository;
    }

    /**
     * Registra un consolidado. Solo persiste la entidad ConsolidadoUEB;
     * no altera ni elimina partes de producción ni registros de carne originales.
     */
    /** Inyectado desde AppContext; conserva los identificadores ya registrados. */
    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    public ConsolidadoUEB registrar(ConsolidadoUEB consolidado) {
        autorizacion().requerir(CasoUso.CU_07);
        if (consolidado != null) {
            consolidado.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, consolidado.getIdUsuario()));
            validarUEBActiva(consolidado.getIdUEB());
        }
        validar(consolidado);
        return consolidadoRepository.save(consolidado);
    }

    public ConsolidadoUEB actualizar(ConsolidadoUEB consolidado) {
        autorizacion().requerir(CasoUso.CU_07);
        if (consolidado != null) {
            consolidado.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, consolidado.getIdUsuario()));
        }
        validar(consolidado);
        return consolidadoRepository.update(consolidado);
    }

    public Optional<ConsolidadoUEB> buscarPorId(UUID idConsolidado) {
        autorizacion().requerir(CasoUso.CU_07, CasoUso.CU_14);
        return consolidadoRepository.findById(idConsolidado);
    }

    public List<ConsolidadoUEB> listarTodos() {
        autorizacion().requerir(CasoUso.CU_07, CasoUso.CU_14);
        return consolidadoRepository.findAll();
    }

    public List<ConsolidadoUEB> buscarPorUEB(UUID idUEB) {
        autorizacion().requerir(CasoUso.CU_07, CasoUso.CU_14);
        return consolidadoRepository.findByUEB(idUEB);
    }

    private void validar(ConsolidadoUEB consolidado) {
        ValidationUtils.requireNonNull(consolidado, "El consolidado es obligatorio.");
        ValidationUtils.requireUuid(consolidado.getIdConsolidado(), "El identificador UUID del consolidado es obligatorio.");
        ValidationUtils.requireUuid(consolidado.getIdUEB(), "La UEB del consolidado es obligatoria.");
        if (!uebRepository.findById(consolidado.getIdUEB()).isPresent()) {
            throw new ValidacionException("La UEB indicada no existe.");
        }
        ValidationUtils.requireNonNull(consolidado.getFechaInicio(), "La fecha de inicio es obligatoria.");
        ValidationUtils.requireNonNull(consolidado.getFechaFin(), "La fecha de fin es obligatoria.");
        if (consolidado.getFechaFin().isBefore(consolidado.getFechaInicio())) {
            throw new ValidacionException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        // Consolidaciones parciales/autorizaciones: sin política definitiva (pendiente).
    }

    /** CU-17: una UEB desactivada no puede elegirse en nuevas operaciones. */
    private void validarUEBActiva(UUID idUEB) {
        ValidationUtils.requireUuid(idUEB, "La UEB del consolidado es obligatoria.");
        cu.pican.model.UEB ueb = uebRepository.findById(idUEB)
                .orElseThrow(() -> new ValidacionException("La UEB indicada no existe."));
        if (!ueb.isActiva()) {
            throw new ValidacionException("No se puede Consolidar con una UEB desactivada.");
        }
    }
}
