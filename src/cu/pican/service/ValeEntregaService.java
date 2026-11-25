package cu.pican.service;

import cu.pican.model.Destino;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.UEB;
import cu.pican.model.ValeEntrega;
import cu.pican.repository.RecepcionRepository;
import cu.pican.repository.ValeEntregaRepository;
import cu.pican.persistence.NoOpTransactionManager;
import cu.pican.persistence.TransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Servicio de negocio para emisión y consulta de vales de entrega.
 * Un vale contiene exactamente un producto (sin detalles múltiples).
 *
 * Numeración consecutiva por UEB (persistente):
 * - La secuencia vive en el repositorio: tabla vale_secuencia en PostgreSQL (atómica por UEB)
 *   o contador en memoria para el modo demostración.
 * - Los números no se repiten y no se reutilizan (la secuencia solo avanza).
 * - El formato utilizado es provisional (p. ej. CODIGO_UEB-000001, basado en el código de la UEB) y
 *   NO constituye una decisión oficial aprobada.
 */
public class ValeEntregaService extends ServicioAutorizado {

    private final ValeEntregaRepository valeEntregaRepository;
    private final UEBService uebService;
    private final ProductoService productoService;
    private final DestinoService destinoService;
    private final ProductoDestinoService productoDestinoService;
    // Inyección opcional para no alterar los constructores usados por etapas anteriores.
    // Se configura desde AppContext (y desde las demos que ejercitan CU-13).
    private RecepcionRepository recepcionRepository;
    private SessionUserIdProvider proveedorSesion;
    private BooleanSupplier bloqueoPostRestauracion = () -> false;
    private final TransactionManager transacciones;

    // Evita condiciones de carrera dentro de la JVM; la persistencia real de la secuencia
    // se garantiza en el repositorio (tabla vale_secuencia en PostgreSQL).
    private final Object secuenciaLock = new Object();

    public ValeEntregaService(ValeEntregaRepository valeEntregaRepository,
                              UEBService uebService,
                              ProductoService productoService,
                              DestinoService destinoService,
                              ProductoDestinoService productoDestinoService) {
        this(valeEntregaRepository, uebService, productoService, destinoService,
                productoDestinoService, new NoOpTransactionManager());
    }

    public ValeEntregaService(ValeEntregaRepository valeEntregaRepository,
                              UEBService uebService,
                              ProductoService productoService,
                              DestinoService destinoService,
                              ProductoDestinoService productoDestinoService,
                              TransactionManager transacciones) {
        this.valeEntregaRepository = valeEntregaRepository;
        this.uebService = uebService;
        this.productoService = productoService;
        this.destinoService = destinoService;
        this.productoDestinoService = productoDestinoService;
        this.transacciones = transacciones == null ? new NoOpTransactionManager() : transacciones;
    }

    /** Permite comprobar la ausencia de recepciones al anular un vale, sin cambiar constructores existentes. */
    public void setRecepcionRepository(RecepcionRepository recepcionRepository) {
        this.recepcionRepository = recepcionRepository;
    }

    /** Inyectado desde AppContext; completa el usuario emisor/anulador sin cambiar reglas de vales. */
    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    /** SIN-13: inyectado desde AppContext; bloquea emitter vales tras una restauracion LOCAL. */
    public void setBloqueoPostRestauracion(BooleanSupplier bloqueoPostRestauracion) {
        this.bloqueoPostRestauracion = bloqueoPostRestauracion == null ? () -> false : bloqueoPostRestauracion;
    }

    private void verificarBloqueoPostRestauracion() {
        if (bloqueoPostRestauracion.getAsBoolean()) {
            throw new ValidacionException(
                    "SIN-13: la UEB fue restaurada; no se pueden emitir vales hasta importar la bajada completa del central.");
        }
    }

    /**
     * Emite un vale: asigna identificadores/fechas por defecto si faltan, genera número consecutivo por UEB,
     * establece estado EMITIDO, valida y persiste. No permite editar/eliminar vales emitidos.
     */
    public ValeEntrega emitir(ValeEntrega vale) {
        verificarBloqueoPostRestauracion();
        autorizacion().requerir(CasoUso.CU_10);
        ValidationUtils.requireNonNull(vale, "El vale de entrega es obligatorio.");
        // CU-13: un vale anulado (o cualquier vale ya emitido) no puede volver a grabarse con el mismo ID.
        if (vale.getIdVale() != null && valeEntregaRepository.findById(vale.getIdVale()).isPresent()) {
            throw new ValidacionException("El vale ya existe; no se permite modificar un vale emitido o anulado.");
        }

        if (vale.getIdVale() == null) {
            vale.setIdVale(UUID.randomUUID());
        }
        if (vale.getFechaCreacion() == null) {
            vale.setFechaCreacion(LocalDateTime.now());
        }
        if (vale.getVersion() == null) {
            vale.setVersion(1L);
        }
        vale.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, vale.getIdUsuario()));
        vale.setEstado(ValeEntrega.ESTADO_EMITIDO);

        // Resolver UEB para validaciones y generación de número
        UEB ueb = resolverUEBActiva(vale.getIdUEB());

        // Generación aislada y segura del número consecutivo por UEB
        String numero = generarNumeroVale(ueb);
        vale.setNumeroVale(numero);

        validar(vale, ueb);

        return valeEntregaRepository.save(vale);
    }

    /**
     * Genera el siguiente número consecutivo para la UEB indicada.
     * La secuencia es persistente (PostgreSQL) y no se reutiliza.
     * Formato provisional no oficial.
     */
    public String generarNumeroVale(UUID idUEB) {
        verificarBloqueoPostRestauracion();
        autorizacion().requerir(CasoUso.CU_10);
        UEB ueb = uebService.buscarPorId(idUEB)
                .orElseThrow(() -> new ValidacionException("La UEB indicada no existe."));
        return generarNumeroVale(ueb);
    }

    private String generarNumeroVale(UEB ueb) {
        synchronized (secuenciaLock) {
            String numero;
            do {
                int actual = valeEntregaRepository.siguienteSecuencia(ueb.getIdUEB());
                numero = formatearNumero(ueb, actual);
                // Seguridad ante vales importados por sincronización que ocupen el número generado.
            } while (valeEntregaRepository.findByNumeroVale(numero).isPresent());
            return numero;
        }
    }

    private String formatearNumero(UEB ueb, int secuencia) {
        String base = (ueb.getCodigo() != null && !ueb.getCodigo().trim().isEmpty())
                ? ueb.getCodigo().trim()
                : "UEB-" + shortId(ueb.getIdUEB());
        // Formato provisional (no oficial): <codigoUEB>-<consecutivo>
        return String.format("%s-%06d", base, secuencia);
    }

    private String shortId(UUID id) {
        if (id == null) return "DESCONOCIDA";
        String s = id.toString().replace("-", "");
        return s.substring(0, Math.min(8, s.length())).toUpperCase();
    }

    private void validar(ValeEntrega vale, UEB uebResuelta) {
        ValidationUtils.requireUuid(vale.getIdVale(), "El identificador UUID del vale es obligatorio.");
        ValidationUtils.requireNotBlank(vale.getNumeroVale(), "El número del vale es obligatorio.");
        ValidationUtils.requireUuid(vale.getIdUEB(), "La UEB emisora es obligatoria.");
        ValidationUtils.requireUuid(vale.getIdDestino(), "El destino es obligatorio.");
        ValidationUtils.requireUuid(vale.getIdProducto(), "El producto es obligatorio.");

        // 1. UEB existe y está activa
        UEB ueb = (uebResuelta != null) ? uebResuelta : resolverUEBActiva(vale.getIdUEB());
        if (ueb == null) { // resolver lanza excepción, pero por seguridad
            throw new ValidacionException("La UEB indicada no existe o no está activa.");
        }

        // 2. Destino existe y está activo
        Destino destino = destinoService.buscarPorId(vale.getIdDestino())
                .orElseThrow(() -> new ValidacionException("El destino indicado no existe."));
        if (!destino.isActiva()) {
            throw new ValidacionException("El destino indicado no está activo.");
        }

        // 3. Producto existe y está activo
        Producto producto = productoService.buscarPorId(vale.getIdProducto())
                .orElseThrow(() -> new ValidacionException("El producto indicado no existe."));
        if (!producto.isActiva()) {
            throw new ValidacionException("El producto indicado no está activo.");
        }

        // 4. Relación producto-destino existe y está activa
        ProductoDestino relacion = productoDestinoService.buscarPorProducto(vale.getIdProducto()).stream()
                .filter(r -> vale.getIdDestino().equals(r.getIdDestino()))
                .findFirst()
                .orElse(null);
        if (relacion == null) {
            throw new ValidacionException("No existe una relación autorizada entre el producto y el destino seleccionados.");
        }
        if (!relacion.isActiva()) {
            throw new ValidacionException("La relación entre el producto y el destino no está activa.");
        }

        // 5. Cantidad > 0
        ValidationUtils.requireNonNull(vale.getCantidadDespachada(), "La cantidad despachada es obligatoria.");
        if (vale.getCantidadDespachada().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("La cantidad despachada debe ser mayor que cero.");
        }

        // 6. Unidad de medida informada
        ValidationUtils.requireNotBlank(vale.getUnidadMedida(), "La unidad de medida es obligatoria.");

        // 7. Fecha y hora válidas
        ValidationUtils.requireNonNull(vale.getFechaHoraEmision(), "La fecha y hora de emisión son obligatorias.");

        // 8. Número de vale único
        Optional<ValeEntrega> existenteNumero = valeEntregaRepository.findByNumeroVale(vale.getNumeroVale());
        if (existenteNumero.isPresent() && !existenteNumero.get().getIdVale().equals(vale.getIdVale())) {
            throw new ValidacionException("El número de vale ya existe. No se permiten números duplicados.");
        }

        // 9. Estado inicial EMITIDO
        if (!ValeEntrega.ESTADO_EMITIDO.equals(vale.getEstado())) {
            throw new ValidacionException("El estado inicial del vale debe ser EMITIDO.");
        }
    }

    private UEB resolverUEBActiva(UUID idUEB) {
        ValidationUtils.requireUuid(idUEB, "La UEB emisora es obligatoria.");
        UEB ueb = uebService.buscarPorId(idUEB)
                .orElseThrow(() -> new ValidacionException("La UEB indicada no existe."));
        if (!ueb.isActiva()) {
            throw new ValidacionException("La UEB indicada no está activa.");
        }
        return ueb;
    }

    public Optional<ValeEntrega> buscarPorId(UUID idVale) {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findById(idVale);
    }

    public Optional<ValeEntrega> buscarPorNumero(String numeroVale) {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findByNumeroVale(numeroVale);
    }

    public List<ValeEntrega> listarTodos() {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findAll();
    }

    public List<ValeEntrega> filtrarPorUEB(UUID idUEB) {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findByUEB(idUEB);
    }

    public List<ValeEntrega> filtrarPorDestino(UUID idDestino) {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findByDestino(idDestino);
    }

    public List<ValeEntrega> filtrarPorProducto(UUID idProducto) {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findByProducto(idProducto);
    }

    public List<ValeEntrega> filtrarPorPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findByPeriodo(desde, hasta);
    }

    /** Consulta de vales por estado (incluye ANULADO). Los anulados no son pendientes de recepción. */
    public List<ValeEntrega> filtrarPorEstado(String estado) {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findByEstado(estado);
    }

    public List<ValeEntrega> obtenerPorUEBOrdenadosPorFecha(UUID idUEB) {
        autorizacion().requerir(CasoUso.CU_10, CasoUso.CU_11, CasoUso.CU_12, CasoUso.CU_13);
        return valeEntregaRepository.findByUEBOrdenadosPorFecha(idUEB);
    }

    /**
     * Transición controlada del estado del vale como consecuencia de una recepción (CU-11).
     * Permitidas únicamente: EMITIDO -> CERRADO (conforme) y EMITIDO -> RECIBIDO_CON_INCIDENCIA (no conforme).
     * Operación en memoria (sin transacción real); se usa de forma coordinada con RecepcionService para mantener coherencia.
     */
    public synchronized ValeEntrega cambiarEstadoPorRecepcion(UUID idVale, String nuevoEstado) {
        autorizacion().requerir(CasoUso.CU_11, CasoUso.CU_12);
        ValidationUtils.requireUuid(idVale, "El identificador del vale es obligatorio.");
        ValidationUtils.requireNotBlank(nuevoEstado, "El nuevo estado del vale es obligatorio.");

        if (!ValeEntrega.ESTADO_CERRADO.equals(nuevoEstado)
                && !ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(nuevoEstado)) {
            throw new ValidacionException("Transición de estado no permitida para recepción.");
        }

        ValeEntrega vale = valeEntregaRepository.findById(idVale)
                .orElseThrow(() -> new ValidacionException("El vale indicado no existe."));

        if (!ValeEntrega.ESTADO_EMITIDO.equals(vale.getEstado())) {
            throw new ValidacionException("Solo se pueden recibir vales en estado EMITIDO.");
        }

        vale.setEstado(nuevoEstado);
        vale.setVersion((vale.getVersion() == null ? 1L : vale.getVersion()) + 1);
        return valeEntregaRepository.update(vale);
    }

    /**
     * CU-13: anulación lógica de un vale EMITIDO. Conserva el registro y todos sus datos originales.
     * La confirmación de que el producto no salió físicamente debe ser explícita (no se infiere del estado).
     * Se actualiza una copia: si falla el guardado, el vale almacenado permanece intacto (coherencia en memoria).
     * No se implanta auditoría general; solo los datos propios de la anulación.
     */
    public ValeEntrega anular(UUID idVale, String motivo,
                              boolean productoNoSalioFisicamente, UUID idUsuario) {
        autorizacion().requerir(CasoUso.CU_13);
        return transacciones.ejecutar(() -> anularInterno(idVale, motivo, productoNoSalioFisicamente, idUsuario));
    }

    private synchronized ValeEntrega anularInterno(UUID idVale, String motivo,
                                                   boolean productoNoSalioFisicamente, UUID idUsuario) {
        ValidationUtils.requireUuid(idVale, "El identificador del vale es obligatorio.");
        if (!productoNoSalioFisicamente) {
            throw new ValidacionException("Debe confirmar explícitamente que el producto no ha salido físicamente de la UEB.");
        }
        ValidationUtils.requireNotBlank(motivo, "El motivo de anulación es obligatorio.");
        if (recepcionRepository == null) {
            throw new ValidacionException("No es posible verificar las recepciones del vale en este momento.");
        }

        ValeEntrega vale = valeEntregaRepository.findById(idVale)
                .orElseThrow(() -> new ValidacionException("El vale indicado no existe."));

        String estado = vale.getEstado();
        if (ValeEntrega.ESTADO_ANULADO.equals(estado)) {
            throw new ValidacionException("El vale ya está anulado.");
        }
        if (ValeEntrega.ESTADO_CERRADO.equals(estado)
                || ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(estado)) {
            throw new ValidacionException("No se puede anular un vale que ya fue recibido.");
        }
        if (!ValeEntrega.ESTADO_EMITIDO.equals(estado)) {
            throw new ValidacionException("Solo se pueden anular vales en estado EMITIDO.");
        }
        if (recepcionRepository.findByVale(idVale).isPresent()) {
            throw new ValidacionException("El vale ya tiene una recepción registrada; no se puede anular.");
        }

        ValeEntrega modificada = copiar(vale);
        modificada.setEstado(ValeEntrega.ESTADO_ANULADO);
        modificada.setMotivoAnulacion(motivo.trim());
        modificada.setFechaHoraAnulacion(LocalDateTime.now());
        modificada.setIdUsuarioAnulacion(UsuarioSesionSupport.resolver(proveedorSesion, idUsuario));
        modificada.setVersion((vale.getVersion() == null ? 1L : vale.getVersion()) + 1);
        return valeEntregaRepository.update(modificada);
    }

    /**
     * Reversión controlada a EMITIDO para mantener coherencia ante fallos al persistir la recepción (solo memoria).
     * No expone una transición funcional general.
     */
    synchronized void revertirAEmitidoPorFalloRecepcion(UUID idVale, String estadoPrevioEsperado) {
        autorizacion().requerir(CasoUso.CU_11, CasoUso.CU_12);
        if (idVale == null) return;
        ValeEntrega vale = valeEntregaRepository.findById(idVale).orElse(null);
        if (vale == null) return;
        if (estadoPrevioEsperado != null && estadoPrevioEsperado.equals(vale.getEstado())) {
            vale.setEstado(ValeEntrega.ESTADO_EMITIDO);
            vale.setVersion((vale.getVersion() == null ? 1L : vale.getVersion()) + 1);
            valeEntregaRepository.update(vale);
        }
    }

    /**
     * CU-12: cierre del vale cuando todas sus incidencias están CERRADAS.
     * Transición permitida únicamente: RECIBIDO_CON_INCIDENCIA -> CERRADO.
     * No es pública: la verificación de "todas las incidencias cerradas" la realiza IncidenciaService,
     * que es la única capa que conoce el estado de las incidencias. No se expone cierre directo a la vista.
     * Se actualiza una copia para no mutar el registro almacenado si falla el guardado.
     */
    synchronized ValeEntrega cerrarValeConIncidenciasResueltas(UUID idVale) {
        autorizacion().requerir(CasoUso.CU_12);
        ValidationUtils.requireUuid(idVale, "El identificador del vale es obligatorio.");
        ValeEntrega vale = valeEntregaRepository.findById(idVale)
                .orElseThrow(() -> new ValidacionException("El vale indicado no existe."));
        if (!ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(vale.getEstado())) {
            throw new ValidacionException("Solo se puede cerrar un vale en estado RECIBIDO_CON_INCIDENCIA.");
        }
        ValeEntrega modificada = copiar(vale);
        modificada.setEstado(ValeEntrega.ESTADO_CERRADO);
        modificada.setVersion((vale.getVersion() == null ? 1L : vale.getVersion()) + 1);
        return valeEntregaRepository.update(modificada);
    }

    /** Reversión controlada usada por IncidenciaService si falla el cierre del vale. */
    synchronized void revertirEstadoPorFalloCierre(UUID idVale, String estadoEsperado, String estadoPrevio) {
        autorizacion().requerir(CasoUso.CU_12);
        if (idVale == null) return;
        ValeEntrega vale = valeEntregaRepository.findById(idVale).orElse(null);
        if (vale == null) return;
        if (estadoEsperado == null || estadoEsperado.equals(vale.getEstado())) {
            ValeEntrega modificada = copiar(vale);
            modificada.setEstado(estadoPrevio);
            modificada.setVersion((vale.getVersion() == null ? 1L : vale.getVersion()) + 1);
            valeEntregaRepository.update(modificada);
        }
    }

    private ValeEntrega copiar(ValeEntrega v) {
        ValeEntrega copia = new ValeEntrega();
        copia.setIdVale(v.getIdVale());
        copia.setNumeroVale(v.getNumeroVale());
        copia.setIdUEB(v.getIdUEB());
        copia.setIdDestino(v.getIdDestino());
        copia.setIdProducto(v.getIdProducto());
        copia.setCantidadDespachada(v.getCantidadDespachada());
        copia.setUnidadMedida(v.getUnidadMedida());
        copia.setFechaHoraEmision(v.getFechaHoraEmision());
        copia.setEstado(v.getEstado());
        copia.setIdUsuario(v.getIdUsuario());
        copia.setFechaCreacion(v.getFechaCreacion());
        copia.setMotivoAnulacion(v.getMotivoAnulacion());
        copia.setFechaHoraAnulacion(v.getFechaHoraAnulacion());
        copia.setIdUsuarioAnulacion(v.getIdUsuarioAnulacion());
        copia.setVersion(v.getVersion());
        return copia;
    }
}
