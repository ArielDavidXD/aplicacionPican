package cu.pican.controller.service;

import cu.pican.model.Incidencia;
import cu.pican.model.Recepcion;
import cu.pican.model.ValeEntrega;
import cu.pican.model.repository.RecepcionRepository;
import cu.pican.model.persistence.NoOpTransactionManager;
import cu.pican.model.persistence.TransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para registro y consulta de recepciones de vales (CU-11).
 * Conserva la información original del vale y registra separadamente lo realmente recibido.
 *
 * Coherencia en memoria (limitación): no existe transacción real. La operación valida primero,
 * luego transita el estado del vale, guarda la recepción y, si es NO_CONFORME, registra la incidencia asociada;
 * ante fallos se aplican compensaciones para no dejar recepción/estado sin su incidencia obligatoria.
 */
public class RecepcionService extends ServicioAutorizado {

    private final RecepcionRepository recepcionRepository;
    private final ValeEntregaService valeEntregaService;
    private IncidenciaService incidenciaService;
    private SessionUserIdProvider proveedorSesion;
    private final TransactionManager transacciones;
    private final Object registroLock = new Object();

    public RecepcionService(RecepcionRepository recepcionRepository, ValeEntregaService valeEntregaService,
                            IncidenciaService incidenciaService) {
        this(recepcionRepository, valeEntregaService, incidenciaService, new NoOpTransactionManager());
    }

    public RecepcionService(RecepcionRepository recepcionRepository, ValeEntregaService valeEntregaService,
                            IncidenciaService incidenciaService, TransactionManager transacciones) {
        this.recepcionRepository = recepcionRepository;
        this.valeEntregaService = valeEntregaService;
        this.incidenciaService = incidenciaService;
        this.transacciones = transacciones == null ? new NoOpTransactionManager() : transacciones;
    }

    /** Permite resolver la dependencia circular creada por CU-11/CU-12 (recepción -> incidencia -> recepción). */
    public void setIncidenciaService(IncidenciaService incidenciaService) {
        this.incidenciaService = incidenciaService;
    }

    /** Inyectado desde AppContext; la recepción transmite el responsable a la incidencia automática. */
    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    public Recepcion registrar(Recepcion recepcion) {
        autorizacion().requerir(CasoUso.CU_11);
        return transacciones.ejecutar(() -> registrarInterno(recepcion));
    }

    private Recepcion registrarInterno(Recepcion recepcion) {
        ValidationUtils.requireNonNull(recepcion, "La recepción es obligatoria.");
        recepcion.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, recepcion.getIdUsuario()));
        synchronized (registroLock) {
            if (recepcion.getIdRecepcion() == null) {
                recepcion.setIdRecepcion(UUID.randomUUID());
            }
            if (recepcion.getFechaCreacion() == null) {
                recepcion.setFechaCreacion(LocalDateTime.now());
            }
            if (recepcion.getVersion() == null) {
                recepcion.setVersion(1L);
            }

            ValeEntrega vale = validarYObtenerVale(recepcion);

            String nuevoEstado = Recepcion.RESULTADO_CONFORME.equals(recepcion.getResultado())
                    ? ValeEntrega.ESTADO_CERRADO
                    : ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA;

            // 1) Transición controlada del vale (solo si todas las validaciones pasaron)
            ValeEntrega valeActualizado = valeEntregaService.cambiarEstadoPorRecepcion(vale.getIdVale(), nuevoEstado);
            Recepcion recepcionGuardada = null;
            try {
                // 2) Persistir recepción
                recepcionGuardada = recepcionRepository.save(recepcion);

                // 3) Toda recepción NO_CONFORME debe generar obligatoriamente una incidencia asociada (mínimo CU-11)
                if (Recepcion.RESULTADO_NO_CONFORME.equals(recepcionGuardada.getResultado())) {
                    incidenciaService.registrarDesdeRecepcionNoConforme(
                            recepcionGuardada.getIdVale(),
                            recepcionGuardada.getIdRecepcion(),
                            recepcionGuardada.getObservaciones(),
                            recepcionGuardada.getFechaHoraRecepcion(),
                            recepcionGuardada.getIdUsuario());
                }
                return recepcionGuardada;
            } catch (RuntimeException ex) {
                // Compensación: no debe quedar recepción guardada ni vale cambiado si falla el registro (incluida la incidencia)
                try {
                    if (recepcionGuardada != null) {
                        recepcionRepository.deleteById(recepcionGuardada.getIdRecepcion());
                    }
                } catch (RuntimeException ignored) {
                    // Se prioriza informar el fallo original; la limitación de memoria queda documentada.
                }
                try {
                    valeEntregaService.revertirAEmitidoPorFalloRecepcion(vale.getIdVale(), valeActualizado.getEstado());
                } catch (RuntimeException ignored) {
                    // Se prioriza informar el fallo original; la limitación de memoria queda documentada.
                }
                throw ex;
            }
        }
    }

    private ValeEntrega validarYObtenerVale(Recepcion recepcion) {
        ValidationUtils.requireUuid(recepcion.getIdRecepcion(), "El identificador UUID de la recepción es obligatorio.");
        ValidationUtils.requireUuid(recepcion.getIdVale(), "El vale asociado es obligatorio.");

        // 1. El vale debe existir
        ValeEntrega vale = valeEntregaService.buscarPorId(recepcion.getIdVale())
                .orElseThrow(() -> new ValidacionException("El vale indicado no existe."));

        // 2. El vale debe encontrarse en estado EMITIDO
        if (!ValeEntrega.ESTADO_EMITIDO.equals(vale.getEstado())) {
            throw new ValidacionException("Solo se pueden recibir vales en estado EMITIDO.");
        }

        // 3. No debe existir otra recepción para ese vale
        if (recepcionRepository.findByVale(recepcion.getIdVale()).isPresent()) {
            throw new ValidacionException("El vale ya tiene una recepción registrada.");
        }

        // 4. Cantidad recibida > 0
        ValidationUtils.requireNonNull(recepcion.getCantidadRecibida(), "La cantidad recibida es obligatoria.");
        if (recepcion.getCantidadRecibida().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("La cantidad recibida debe ser mayor que cero.");
        }

        // 5. Unidad de medida informada y correspondiente con la del vale
        ValidationUtils.requireNotBlank(recepcion.getUnidadMedida(), "La unidad de medida es obligatoria.");
        if (vale.getUnidadMedida() == null || !vale.getUnidadMedida().trim().equals(recepcion.getUnidadMedida().trim())) {
            throw new ValidacionException("La unidad de medida recibida debe corresponder con la unidad del vale.");
        }

        // 6. Fecha y hora válidas
        ValidationUtils.requireNonNull(recepcion.getFechaHoraRecepcion(), "La fecha y hora de recepción son obligatorias.");
        if (recepcion.getFechaHoraRecepcion().isAfter(LocalDateTime.now())) {
            throw new ValidacionException("La fecha y hora de recepción no puede ser futura.");
        }
        if (vale.getFechaHoraEmision() != null
                && recepcion.getFechaHoraRecepcion().isBefore(vale.getFechaHoraEmision())) {
            throw new ValidacionException("La fecha y hora de recepción no puede ser anterior a la emisión del vale.");
        }

        // 7 y 8. Resultado y reglas asociadas
        ValidationUtils.requireNotBlank(recepcion.getResultado(), "El resultado de la recepción es obligatorio.");
        String resultado = recepcion.getResultado().trim();
        if (!Recepcion.RESULTADO_CONFORME.equals(resultado) && !Recepcion.RESULTADO_NO_CONFORME.equals(resultado)) {
            throw new ValidacionException("El resultado de la recepción no es válido.");
        }
        recepcion.setResultado(resultado);

        if (Recepcion.RESULTADO_CONFORME.equals(resultado)) {
            if (vale.getCantidadDespachada() == null
                    || recepcion.getCantidadRecibida().compareTo(vale.getCantidadDespachada()) != 0) {
                throw new ValidacionException("La recepción conforme debe coincidir exactamente con la cantidad despachada.");
            }
        } else { // NO_CONFORME
            if (recepcion.getObservaciones() == null || recepcion.getObservaciones().trim().isEmpty()) {
                throw new ValidacionException("La recepción no conforme debe registrar observaciones que expliquen el problema.");
            }
        }

        return vale;
    }

    public Optional<Recepcion> buscarPorId(UUID idRecepcion) {
        autorizacion().requerirLectura(CasoUso.CU_11);
        return recepcionRepository.findById(idRecepcion);
    }

    public Optional<Recepcion> buscarPorVale(UUID idVale) {
        autorizacion().requerirLectura(CasoUso.CU_11);
        return recepcionRepository.findByVale(idVale);
    }

    public List<Recepcion> listarPorPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        autorizacion().requerirLectura(CasoUso.CU_11);
        return recepcionRepository.findByPeriodo(desde, hasta);
    }

    public List<Recepcion> listarTodas() {
        autorizacion().requerirLectura(CasoUso.CU_11);
        return recepcionRepository.findAll();
    }
}
