package cu.pican.controller.service;

import cu.pican.model.Incidencia;
import cu.pican.model.Recepcion;
import cu.pican.model.ValeEntrega;
import cu.pican.model.repository.IncidenciaRepository;
import cu.pican.model.persistence.NoOpTransactionManager;
import cu.pican.model.persistence.TransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de incidencias de entrega (CU-11/CU-12).
 * Reutiliza la incidencia mínima generada en CU-11; permite completarla, registrar incidencias
 * adicionales para problemas distintos y cerrar cada incidencia. No implementa CU-13 ni sincronización.
 *
 * Coherencia en memoria: se valida primero y se usan compensaciones ante fallos de guardado; no hay transacción real.
 */
public class IncidenciaService extends ServicioAutorizado {

    private static final Set<String> TIPOS_PERMITIDOS = new HashSet<>(Arrays.asList(
            Incidencia.TIPO_FALTANTE, Incidencia.TIPO_SOBRANTE, Incidencia.TIPO_CALIDAD,
            Incidencia.TIPO_ROTURA, Incidencia.TIPO_OTRO));

    private final IncidenciaRepository incidenciaRepository;
    private final RecepcionService recepcionService;
    private final ValeEntregaService valeEntregaService;
    private SessionUserIdProvider proveedorSesion;
    private final TransactionManager transacciones;
    private final Object lock = new Object();

    public IncidenciaService(IncidenciaRepository incidenciaRepository,
                             RecepcionService recepcionService,
                             ValeEntregaService valeEntregaService) {
        this(incidenciaRepository, recepcionService, valeEntregaService, new NoOpTransactionManager());
    }

    public IncidenciaService(IncidenciaRepository incidenciaRepository,
                             RecepcionService recepcionService,
                             ValeEntregaService valeEntregaService,
                             TransactionManager transacciones) {
        this.incidenciaRepository = incidenciaRepository;
        this.recepcionService = recepcionService;
        this.valeEntregaService = valeEntregaService;
        this.transacciones = transacciones == null ? new NoOpTransactionManager() : transacciones;
    }

    /** Inyectado desde AppContext; no altera el flujo de incidencias. */
    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    /**
     * Incidencia mínima generada automáticamente por una recepción NO_CONFORME (CU-11).
     * Conserva identidad/asociaciones y deja la incidencia en estado ABIERTA para completarse en CU-12.
     */
    public Incidencia registrarDesdeRecepcionNoConforme(UUID idVale, UUID idRecepcion, String observaciones, LocalDateTime fechaHoraRegistro) {
        autorizacion().requerir(CasoUso.CU_11, CasoUso.CU_12);
        return registrarDesdeRecepcionNoConforme(idVale, idRecepcion, observaciones, fechaHoraRegistro, null);
    }

    /** Variante que recibe el responsable de la recepción para conservarlo en la incidencia generada. */
    public Incidencia registrarDesdeRecepcionNoConforme(UUID idVale, UUID idRecepcion, String observaciones,
                                                        LocalDateTime fechaHoraRegistro, UUID idUsuario) {
        autorizacion().requerir(CasoUso.CU_11, CasoUso.CU_12);
        ValidationUtils.requireUuid(idVale, "El vale asociado a la incidencia es obligatorio.");
        ValidationUtils.requireUuid(idRecepcion, "La recepción asociada a la incidencia es obligatoria.");
        ValidationUtils.requireNotBlank(observaciones, "Las observaciones de la incidencia son obligatorias.");
        ValidationUtils.requireNonNull(fechaHoraRegistro, "La fecha y hora de registro de la incidencia es obligatoria.");

        Incidencia incidencia = new Incidencia();
        incidencia.setIdIncidencia(UUID.randomUUID());
        incidencia.setIdVale(idVale);
        incidencia.setIdRecepcion(idRecepcion);
        incidencia.setObservaciones(observaciones.trim());
        incidencia.setFechaHoraRegistro(fechaHoraRegistro);
        incidencia.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, idUsuario));
        incidencia.setEstado(Incidencia.ESTADO_ABIERTA);
        incidencia.setFechaCreacion(LocalDateTime.now());
        incidencia.setVersion(1L);
        return incidenciaRepository.save(incidencia);
    }

    /**
     * Permite registrar incidencias separadas para problemas distintos de una misma recepción,
     * conservando las asociaciones originales. No altera la recepción ni crea una segunda incidencia
     * automática; esta operación es explícita desde CU-12.
     */
    public Incidencia registrarOtraParaLaMismaRecepcion(UUID idRecepcion, String observaciones) {
        autorizacion().requerir(CasoUso.CU_12);
        synchronized (lock) {
            Recepcion recepcion = validarRecepcionBase(idRecepcion);
            return registrarDesdeRecepcionNoConforme(
                    recepcion.getIdVale(),
                    recepcion.getIdRecepcion(),
                    observaciones,
                    LocalDateTime.now(),
                    null);
        }
    }

    /**
     * Completa una incidencia ABIERTA conservando su UUID, asociaciones y observaciones originales.
     */
    public Incidencia completar(UUID idIncidencia, String tipo, BigDecimal cantidadAfectada, String descripcion,
                                String medidaAcordada, LocalDateTime fechaCompromiso) {
        autorizacion().requerir(CasoUso.CU_12);
        synchronized (lock) {
            Incidencia incidencia = buscarPorId(idIncidencia);
            validarCompletar(incidencia, tipo, cantidadAfectada, descripcion, medidaAcordada, fechaCompromiso);

            // Se modifica una copia: si falla el guardado, el objeto almacenado permanece intacto.
            Incidencia modificada = copiar(incidencia);
            modificada.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, modificada.getIdUsuario()));
            modificada.setTipo(tipo.trim());
            modificada.setCantidadAfectada(cantidadAfectada);
            modificada.setDescripcion(descripcion.trim());
            modificada.setMedidaAcordada(medidaAcordada.trim());
            modificada.setFechaCompromiso(fechaCompromiso);
            modificada.setVersion((incidencia.getVersion() == null ? 1L : incidencia.getVersion()) + 1);
            return incidenciaRepository.update(modificada);
        }
    }

    private void validarCompletar(Incidencia incidencia, String tipo, BigDecimal cantidadAfectada, String descripcion,
                                  String medidaAcordada, LocalDateTime fechaCompromiso) {
        ValidationUtils.requireNonNull(incidencia, "La incidencia indicada no existe.");
        if (Incidencia.ESTADO_CERRADA.equals(incidencia.getEstado())) {
            throw new ValidacionException("No se puede modificar una incidencia cerrada.");
        }
        if (!Incidencia.ESTADO_ABIERTA.equals(incidencia.getEstado())) {
            throw new ValidacionException("La incidencia debe estar ABIERTA para completarse.");
        }

        // 1 y 2. Vale existe y está RECIBIDO_CON_INCIDENCIA
        ValeEntrega vale = valeEntregaService.buscarPorId(incidencia.getIdVale())
                .orElseThrow(() -> new ValidacionException("El vale asociado a la incidencia no existe."));
        if (!ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(vale.getEstado())) {
            throw new ValidacionException("El vale asociado debe estar en estado RECIBIDO_CON_INCIDENCIA.");
        }

        // 3. Recepción existe y es NO_CONFORME
        Recepcion recepcion = recepcionService.buscarPorId(incidencia.getIdRecepcion())
                .orElseThrow(() -> new ValidacionException("La recepción asociada a la incidencia no existe."));
        if (!Recepcion.RESULTADO_NO_CONFORME.equals(recepcion.getResultado())) {
            throw new ValidacionException("La recepción asociada debe ser NO_CONFORME.");
        }

        // 4. Asociaciones correctas
        if (!vale.getIdVale().equals(recepcion.getIdVale())) {
            throw new ValidacionException("La incidencia no está asociada al vale y recepción correctos.");
        }

        // 5. Tipo permitido
        ValidationUtils.requireNotBlank(tipo, "El tipo de incidencia es obligatorio.");
        if (!TIPOS_PERMITIDOS.contains(tipo.trim())) {
            throw new ValidacionException("El tipo de incidencia no es válido.");
        }

        // 6. Cantidad > 0
        ValidationUtils.requireNonNull(cantidadAfectada, "La cantidad afectada es obligatoria.");
        if (cantidadAfectada.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("La cantidad afectada debe ser mayor que cero.");
        }

        // 7. Cantidad <= cantidad despachada del vale (sin modificar el vale)
        if (vale.getCantidadDespachada() == null || cantidadAfectada.compareTo(vale.getCantidadDespachada()) > 0) {
            throw new ValidacionException("La cantidad afectada no puede superar la cantidad despachada del vale.");
        }

        // 8. Descripción >= 10 caracteres
        if (descripcion == null || descripcion.trim().length() < 10) {
            throw new ValidacionException("La descripción debe contener al menos 10 caracteres.");
        }

        // 9. Medida acordada obligatoria (texto libre hasta que exista catálogo aprobado)
        ValidationUtils.requireNotBlank(medidaAcordada, "La medida acordada es obligatoria.");

        // 10 y 11. Fecha compromiso opcional; formato ya validado por el tipo LocalDateTime; sin restricción de fecha futura
        // 12. Estado inicial ABIERTA (verificado arriba)
    }

    /**
     * Cierra una incidencia ABIERTA y, si es la última abierta del vale, transita el vale a CERRADO.
     * Coherencia en memoria: si falla el cierre del vale, se revierten incidencia y cierre previo del vale.
     */
    public Incidencia cerrar(UUID idIncidencia, LocalDateTime fechaCierre, String resultadoSolucion) {
        autorizacion().requerir(CasoUso.CU_12);
        return transacciones.ejecutar(() -> cerrarInterno(idIncidencia, fechaCierre, resultadoSolucion));
    }

    private synchronized Incidencia cerrarInterno(UUID idIncidencia, LocalDateTime fechaCierre, String resultadoSolucion) {
        Incidencia incidencia = buscarPorId(idIncidencia);
        ValidationUtils.requireNonNull(incidencia, "La incidencia indicada no existe.");
        if (!Incidencia.ESTADO_ABIERTA.equals(incidencia.getEstado())) {
            throw new ValidacionException("Solo se pueden cerrar incidencias ABIERTA; no se pueden reabrir incidencias cerradas.");
        }

        ValeEntrega vale = valeEntregaService.buscarPorId(incidencia.getIdVale())
                .orElseThrow(() -> new ValidacionException("El vale asociado a la incidencia no existe."));
        if (!ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(vale.getEstado())) {
            throw new ValidacionException("El vale asociado debe estar en estado RECIBIDO_CON_INCIDENCIA.");
        }

        ValidationUtils.requireNonNull(fechaCierre, "La fecha de cierre es obligatoria.");
        ValidationUtils.requireNotBlank(resultadoSolucion, "El resultado de la solución es obligatorio.");

        Incidencia anterior = copiar(incidencia);
        String estadoValeAnterior = vale.getEstado();

        // Se actualiza una copia para no mutar el objeto almacenado antes de confirmar el guardado.
        Incidencia modificada = copiar(incidencia);
        modificada.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, modificada.getIdUsuario()));
        modificada.setEstado(Incidencia.ESTADO_CERRADA);
        modificada.setFechaCierre(fechaCierre);
        modificada.setResultadoSolucion(resultadoSolucion.trim());
        modificada.setVersion((incidencia.getVersion() == null ? 1L : incidencia.getVersion()) + 1);
        Incidencia cerrada = incidenciaRepository.update(modificada);

        boolean quedanAbiertas = incidenciaRepository.findByVale(incidencia.getIdVale()).stream()
                .anyMatch(i -> Incidencia.ESTADO_ABIERTA.equals(i.getEstado()));
        if (!quedanAbiertas) {
            try {
                valeEntregaService.cerrarValeConIncidenciasResueltas(incidencia.getIdVale());
            } catch (RuntimeException ex) {
                // Compensación controlada: se restaura la incidencia ABIERTA y el estado previo del vale.
                try {
                    incidenciaRepository.update(anterior);
                } catch (RuntimeException ignored) {
                    // Se prioriza informar el fallo original; limitación documentada de la coherencia en memoria.
                }
                valeEntregaService.revertirEstadoPorFalloCierre(incidencia.getIdVale(), ValeEntrega.ESTADO_CERRADO, estadoValeAnterior);
                throw ex;
            }
        }
        return cerrada;
    }

    public Incidencia buscarPorId(UUID idIncidencia) {
        autorizacion().requerirLectura(CasoUso.CU_12);
        ValidationUtils.requireUuid(idIncidencia, "El identificador de la incidencia es obligatorio.");
        return incidenciaRepository.findById(idIncidencia).orElse(null);
    }

    public Optional<Incidencia> buscarOpcionalPorId(UUID idIncidencia) {
        autorizacion().requerirLectura(CasoUso.CU_12);
        if (idIncidencia == null) return Optional.empty();
        return incidenciaRepository.findById(idIncidencia);
    }

    public List<Incidencia> buscarPorRecepcion(UUID idRecepcion) {
        autorizacion().requerirLectura(CasoUso.CU_12);
        return incidenciaRepository.findByRecepcion(idRecepcion);
    }

    public List<Incidencia> buscarPorVale(UUID idVale) {
        autorizacion().requerirLectura(CasoUso.CU_12);
        return incidenciaRepository.findByVale(idVale);
    }

    public List<Incidencia> buscarPorEstado(String estado) {
        autorizacion().requerirLectura(CasoUso.CU_12);
        return incidenciaRepository.findByEstado(estado);
    }

    public List<Incidencia> listarTodas() {
        autorizacion().requerirLectura(CasoUso.CU_12);
        return incidenciaRepository.findAll();
    }

    public boolean quedanIncidenciasAbiertas(UUID idVale) {
        autorizacion().requerirLectura(CasoUso.CU_12);
        return incidenciaRepository.findByVale(idVale).stream()
                .anyMatch(i -> Incidencia.ESTADO_ABIERTA.equals(i.getEstado()));
    }

    private Recepcion validarRecepcionBase(UUID idRecepcion) {
        ValidationUtils.requireUuid(idRecepcion, "La recepción asociada es obligatoria.");
        Recepcion recepcion = recepcionService.buscarPorId(idRecepcion)
                .orElseThrow(() -> new ValidacionException("La recepción indicada no existe."));
        if (!Recepcion.RESULTADO_NO_CONFORME.equals(recepcion.getResultado())) {
            throw new ValidacionException("Solo se pueden registrar incidencias a partir de recepciones NO_CONFORME.");
        }
        ValeEntrega vale = valeEntregaService.buscarPorId(recepcion.getIdVale())
                .orElseThrow(() -> new ValidacionException("El vale asociado a la recepción no existe."));
        if (!ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(vale.getEstado())) {
            throw new ValidacionException("El vale asociado debe estar en estado RECIBIDO_CON_INCIDENCIA.");
        }
        return recepcion;
    }

    private Incidencia copiar(Incidencia i) {
        Incidencia copia = new Incidencia();
        copia.setIdIncidencia(i.getIdIncidencia());
        copia.setIdVale(i.getIdVale());
        copia.setIdRecepcion(i.getIdRecepcion());
        copia.setTipo(i.getTipo());
        copia.setCantidadAfectada(i.getCantidadAfectada());
        copia.setDescripcion(i.getDescripcion());
        copia.setMedidaAcordada(i.getMedidaAcordada());
        copia.setFechaCompromiso(i.getFechaCompromiso());
        copia.setEstado(i.getEstado());
        copia.setObservaciones(i.getObservaciones());
        copia.setFechaHoraRegistro(i.getFechaHoraRegistro());
        copia.setIdUsuario(i.getIdUsuario());
        copia.setFechaCreacion(i.getFechaCreacion());
        copia.setFechaCierre(i.getFechaCierre());
        copia.setResultadoSolucion(i.getResultadoSolucion());
        copia.setVersion(i.getVersion());
        return copia;
    }
}
