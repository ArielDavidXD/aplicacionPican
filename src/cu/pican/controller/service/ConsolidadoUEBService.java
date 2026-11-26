package cu.pican.controller.service;

import cu.pican.model.AclaracionOperativa;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UnidadAsociada;
import cu.pican.model.UEB;
import cu.pican.model.persistence.TransactionManager;
import cu.pican.model.repository.AclaracionOperativaRepository;
import cu.pican.model.repository.ConsolidadoUEBRepository;
import cu.pican.model.repository.NaveRepository;
import cu.pican.model.repository.ParteNaveRepository;
import cu.pican.model.repository.ParteUnidadAsociadaRepository;
import cu.pican.model.repository.ProduccionCarneRepository;
import cu.pican.model.repository.UEBRepository;
import cu.pican.model.repository.UnidadAsociadaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Consolida una fotografía de totales y vínculos sin modificar los registros originales. */
public class ConsolidadoUEBService extends ServicioAutorizado {

    private final TransactionManager transacciones;
    private final ConsolidadoUEBRepository consolidadoRepository;
    private final UEBRepository uebRepository;
    private final NaveRepository naveRepository;
    private final ParteNaveRepository parteNaveRepository;
    private final ParteUnidadAsociadaRepository parteUnidadRepository;
    private final UnidadAsociadaRepository unidadRepository;
    private final ProduccionCarneRepository carneRepository;
    private final AclaracionOperativaRepository aclaracionRepository;
    private SessionUserIdProvider proveedorSesion;

    public ConsolidadoUEBService(TransactionManager transacciones,
                                 ConsolidadoUEBRepository consolidadoRepository,
                                 UEBRepository uebRepository,
                                 NaveRepository naveRepository,
                                 ParteNaveRepository parteNaveRepository,
                                 ParteUnidadAsociadaRepository parteUnidadRepository,
                                 UnidadAsociadaRepository unidadRepository,
                                 ProduccionCarneRepository carneRepository,
                                 AclaracionOperativaRepository aclaracionRepository) {
        this.transacciones = transacciones;
        this.consolidadoRepository = consolidadoRepository;
        this.uebRepository = uebRepository;
        this.naveRepository = naveRepository;
        this.parteNaveRepository = parteNaveRepository;
        this.parteUnidadRepository = parteUnidadRepository;
        this.unidadRepository = unidadRepository;
        this.carneRepository = carneRepository;
        this.aclaracionRepository = aclaracionRepository;
    }

    public record DetalleConsolidado(ConsolidadoUEB consolidado,
                                     List<UUID> partesNave,
                                     List<UUID> partesUnidad,
                                     List<UUID> produccionesCarne,
                                     List<AclaracionOperativa> aclaraciones) {
    }

    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    public ConsolidadoUEB registrar(ConsolidadoUEB consolidado) {
        autorizacion().requerir(CasoUso.CU_07);
        if (consolidado != null) {
            consolidado.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, consolidado.getIdUsuario()));
        }
        return transacciones.ejecutar(() -> calcularGuardar(consolidado, false));
    }

    public ConsolidadoUEB actualizar(ConsolidadoUEB consolidado) {
        autorizacion().requerir(CasoUso.CU_07);
        if (consolidado != null) {
            consolidado.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, consolidado.getIdUsuario()));
        }
        return transacciones.ejecutar(() -> calcularGuardar(consolidado, true));
    }

    private ConsolidadoUEB calcularGuardar(ConsolidadoUEB consolidado, boolean actualizacion) {
        validar(consolidado);
        if (actualizacion && !consolidadoRepository.findById(consolidado.getIdConsolidado()).isPresent()) {
            throw new ValidacionException("La consolidación que intenta actualizar no existe.");
        }
        if (!actualizacion) {
            validarUEBActiva(consolidado.getIdUEB());
        }

        List<Nave> naves = naveRepository.findByUEB(consolidado.getIdUEB());
        Set<UUID> navesIds = new HashSet<>();
        for (Nave nave : naves) {
            navesIds.add(nave.getIdNave());
        }

        List<ParteNave> partesNavePeriodo = new ArrayList<>();
        for (UUID idNave : navesIds) {
            for (ParteNave parte : parteNaveRepository.findByNave(idNave)) {
                if (enPeriodo(parte.getFechaProduccion(), consolidado)) {
                    partesNavePeriodo.add(parte);
                }
            }
        }

        List<ParteUnidadAsociada> partesUnidadPeriodo = new ArrayList<>();
        List<ParteUnidadAsociada> partesUnidadUEB = parteUnidadRepository.findByUEB(consolidado.getIdUEB());
        for (ParteUnidadAsociada parte : partesUnidadUEB) {
            if (enPeriodo(parte.getFechaProduccion(), consolidado)) {
                partesUnidadPeriodo.add(parte);
            }
        }

        List<ProduccionCarne> carnesPeriodo = new ArrayList<>();
        for (ProduccionCarne carne : carneRepository.findByUEB(consolidado.getIdUEB())) {
            if (enPeriodo(carne.getFechaProduccion(), consolidado)) {
                carnesPeriodo.add(carne);
            }
        }

        int pendientes = contarAclaracionesPendientes(partesNavePeriodo, partesUnidadPeriodo, carnesPeriodo);
        if (pendientes > 0) {
            throw new ValidacionException("No se puede consolidar: existen " + pendientes
                    + " aclaraciones pendientes en los registros del período.");
        }

        List<UUID> idsNaveIncluidos = new ArrayList<>();
        List<UUID> idsUnidadIncluidos = new ArrayList<>();
        List<UUID> idsCarneIncluidos = new ArrayList<>();
        int excluidosNave = 0;
        int excluidosUnidad = 0;
        reiniciarTotales(consolidado);

        for (ParteNave parte : partesNavePeriodo) {
            if (ParteNave.ESTADO_REGISTRADO.equals(parte.getEstado())) {
                idsNaveIncluidos.add(parte.getIdParte());
                consolidado.setTotalHuevos(consolidado.getTotalHuevos().add(entero(parte.getHuevosProducidos())));
                consolidado.setTotalHuevosRotos(consolidado.getTotalHuevosRotos().add(entero(parte.getHuevosRotos())));
                consolidado.setTotalGallinasVivas(consolidado.getTotalGallinasVivas() + largo(parte.getGallinasVivas()));
                consolidado.setTotalGallinasMuertas(consolidado.getTotalGallinasMuertas() + largo(parte.getGallinasMuertas()));
                consolidado.setTotalGallinasEnfermas(consolidado.getTotalGallinasEnfermas() + largo(parte.getGallinasEnfermas()));
                consolidado.setTotalPiensoKg(consolidado.getTotalPiensoKg().add(decimal(parte.getPiensoKg())));
            } else {
                excluidosNave++;
            }
        }
        for (ParteUnidadAsociada parte : partesUnidadPeriodo) {
            if (ParteUnidadAsociada.ESTADO_REGISTRADO.equals(parte.getEstado())) {
                idsUnidadIncluidos.add(parte.getIdParteAsociado());
            } else {
                excluidosUnidad++;
            }
        }
        for (ProduccionCarne carne : carnesPeriodo) {
            idsCarneIncluidos.add(carne.getIdProduccion());
            consolidado.setTotalCarne(consolidado.getTotalCarne().add(decimal(carne.getCantidadToneladas())));
        }

        consolidado.setPartesNaveIncluidos(idsNaveIncluidos.size());
        consolidado.setPartesNaveExcluidos(excluidosNave);
        consolidado.setPartesUnidadIncluidos(idsUnidadIncluidos.size());
        consolidado.setPartesUnidadExcluidos(excluidosUnidad);
        consolidado.setRegistrosCarneIncluidos(idsCarneIncluidos.size());
        consolidado.setAclaracionesPendientes(0);

        Set<UUID> navesConParte = new HashSet<>();
        for (ParteNave parte : partesNavePeriodo) {
            if (idsNaveIncluidos.contains(parte.getIdParte())) {
                navesConParte.add(parte.getIdNave());
            }
        }
        consolidado.setNavesSinParte(contarSinParte(naves, navesConParte));

        Set<UUID> unidadesAsignadas = new HashSet<>();
        for (ParteUnidadAsociada parte : partesUnidadUEB) {
            unidadesAsignadas.add(parte.getIdUnidad());
        }
        Set<UUID> unidadesConParte = new HashSet<>();
        for (ParteUnidadAsociada parte : partesUnidadPeriodo) {
            if (idsUnidadIncluidos.contains(parte.getIdParteAsociado())) {
                unidadesConParte.add(parte.getIdUnidad());
            }
        }
        int unidadesSinParte = 0;
        for (UUID idUnidad : unidadesAsignadas) {
            UnidadAsociada unidad = unidadRepository.findById(idUnidad).orElse(null);
            if (unidad != null && unidad.isActiva() && !unidadesConParte.contains(idUnidad)) {
                unidadesSinParte++;
            }
        }
        consolidado.setUnidadesSinParte(unidadesSinParte);

        boolean sinRegistros = idsNaveIncluidos.isEmpty() && idsUnidadIncluidos.isEmpty() && idsCarneIncluidos.isEmpty();
        boolean parcial = sinRegistros || excluidosNave > 0 || excluidosUnidad > 0;
        consolidado.setEstado(parcial ? ConsolidadoUEB.ESTADO_PARCIAL : ConsolidadoUEB.ESTADO_COMPLETO);
        if (parcial) {
            if (consolidado.getMotivoParcial() == null || consolidado.getMotivoParcial().isBlank()) {
                throw new ValidacionException("El consolidado queda PARCIAL; indique el motivo de parcialidad.");
            }
            consolidado.setMotivoParcial(consolidado.getMotivoParcial().trim());
        } else {
            consolidado.setMotivoParcial(null);
        }

        consolidadoRepository.save(consolidado);
        consolidadoRepository.guardarRelaciones(consolidado.getIdConsolidado(), consolidado.getIdUEB(),
                idsNaveIncluidos, idsUnidadIncluidos, idsCarneIncluidos);
        return consolidado;
    }

    private void reiniciarTotales(ConsolidadoUEB c) {
        c.setTotalHuevos(BigDecimal.ZERO);
        c.setTotalHuevosRotos(BigDecimal.ZERO);
        c.setTotalGallinasVivas(0);
        c.setTotalGallinasMuertas(0);
        c.setTotalGallinasEnfermas(0);
        c.setTotalPiensoKg(BigDecimal.ZERO);
        c.setTotalCarne(BigDecimal.ZERO);
    }

    private int contarSinParte(List<Nave> naves, Set<UUID> conParte) {
        int total = 0;
        for (Nave nave : naves) {
            if (nave.isActiva() && !conParte.contains(nave.getIdNave())) {
                total++;
            }
        }
        return total;
    }

    private int contarAclaracionesPendientes(List<ParteNave> naves,
                                             List<ParteUnidadAsociada> unidades,
                                             List<ProduccionCarne> carnes) {
        Set<String> origenes = new HashSet<>();
        for (ParteNave parte : naves) {
            origenes.add(clave(AclaracionOperativa.PARTE_NAVE, parte.getIdParte()));
        }
        for (ParteUnidadAsociada parte : unidades) {
            origenes.add(clave(AclaracionOperativa.PARTE_UNIDAD, parte.getIdParteAsociado()));
        }
        for (ProduccionCarne carne : carnes) {
            origenes.add(clave(AclaracionOperativa.PRODUCCION_CARNE, carne.getIdProduccion()));
        }
        int total = 0;
        for (AclaracionOperativa aclaracion : aclaracionRepository.findPendientes()) {
            if (origenes.contains(clave(aclaracion.getTipoOrigen(), aclaracion.getIdOrigen()))) {
                total++;
            }
        }
        return total;
    }

    public Optional<ConsolidadoUEB> buscarPorId(UUID idConsolidado) {
        autorizacion().requerirLectura(CasoUso.CU_07, CasoUso.CU_14);
        return consolidadoRepository.findById(idConsolidado);
    }

    public Optional<DetalleConsolidado> obtenerDetalle(UUID idConsolidado) {
        autorizacion().requerirLectura(CasoUso.CU_07, CasoUso.CU_14);
        Optional<ConsolidadoUEB> consolidado = consolidadoRepository.findById(idConsolidado);
        if (consolidado.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> naves = consolidadoRepository.findPartesNave(idConsolidado);
        List<UUID> unidades = consolidadoRepository.findPartesUnidad(idConsolidado);
        List<UUID> carnes = consolidadoRepository.findProduccionesCarne(idConsolidado);
        Set<String> origenes = new HashSet<>();
        naves.forEach(id -> origenes.add(clave(AclaracionOperativa.PARTE_NAVE, id)));
        unidades.forEach(id -> origenes.add(clave(AclaracionOperativa.PARTE_UNIDAD, id)));
        carnes.forEach(id -> origenes.add(clave(AclaracionOperativa.PRODUCCION_CARNE, id)));
        List<AclaracionOperativa> aclaraciones = aclaracionRepository.findAll().stream()
                .filter(a -> origenes.contains(clave(a.getTipoOrigen(), a.getIdOrigen())))
                .toList();
        return Optional.of(new DetalleConsolidado(consolidado.get(), naves, unidades, carnes, aclaraciones));
    }

    public List<ConsolidadoUEB> listarTodos() {
        autorizacion().requerirLectura(CasoUso.CU_07, CasoUso.CU_14);
        return consolidadoRepository.findAll();
    }

    public List<ConsolidadoUEB> buscarPorUEB(UUID idUEB) {
        autorizacion().requerirLectura(CasoUso.CU_07, CasoUso.CU_14);
        return consolidadoRepository.findByUEB(idUEB);
    }

    public AclaracionOperativa solicitarAclaracion(String tipoOrigen, UUID idOrigen, String motivo) {
        autorizacion().requerir(CasoUso.CU_07);
        validarTipoOrigen(tipoOrigen);
        ValidationUtils.requireUuid(idOrigen, "El registro que requiere aclaración es obligatorio.");
        if (motivo == null || motivo.isBlank()) {
            throw new ValidacionException("El motivo de la aclaración es obligatorio.");
        }
        return transacciones.ejecutar(() -> {
            UUID idUEB = buscarUEBOrigen(tipoOrigen, idOrigen);
            boolean yaPendiente = aclaracionRepository.findByOrigen(tipoOrigen, idOrigen).stream()
                    .anyMatch(a -> AclaracionOperativa.PENDIENTE.equals(a.getEstado()));
            if (yaPendiente) {
                throw new ValidacionException("El registro ya tiene una aclaración pendiente.");
            }
            AclaracionOperativa aclaracion = new AclaracionOperativa();
            aclaracion.setIdAclaracion(UUID.randomUUID());
            aclaracion.setIdUEBOrigen(idUEB);
            aclaracion.setTipoOrigen(tipoOrigen);
            aclaracion.setIdOrigen(idOrigen);
            aclaracion.setMotivo(motivo.trim());
            aclaracion.setEstado(AclaracionOperativa.PENDIENTE);
            aclaracion.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, null));
            aclaracion.setFechaSolicitud(LocalDateTime.now());
            return aclaracionRepository.save(aclaracion);
        });
    }

    public AclaracionOperativa resolverAclaracion(UUID idAclaracion, String observacion) {
        autorizacion().requerir(CasoUso.CU_07);
        ValidationUtils.requireUuid(idAclaracion, "La aclaración es obligatoria.");
        if (observacion == null || observacion.isBlank()) {
            throw new ValidacionException("La observación de resolución es obligatoria.");
        }
        return transacciones.ejecutar(() -> {
            AclaracionOperativa aclaracion = aclaracionRepository.findById(idAclaracion)
                    .orElseThrow(() -> new ValidacionException("La aclaración indicada no existe."));
            if (!AclaracionOperativa.PENDIENTE.equals(aclaracion.getEstado())) {
                throw new ValidacionException("La aclaración ya está resuelta.");
            }
            aclaracion.setEstado(AclaracionOperativa.RESUELTA);
            aclaracion.setFechaResolucion(LocalDateTime.now());
            aclaracion.setObservacionResolucion(observacion.trim());
            return aclaracionRepository.update(aclaracion);
        });
    }

    public List<AclaracionOperativa> listarAclaraciones(String tipoOrigen, UUID idOrigen) {
        autorizacion().requerirLectura(CasoUso.CU_07, CasoUso.CU_14);
        validarTipoOrigen(tipoOrigen);
        ValidationUtils.requireUuid(idOrigen, "El registro es obligatorio.");
        return aclaracionRepository.findByOrigen(tipoOrigen, idOrigen);
    }

    private UUID buscarUEBOrigen(String tipoOrigen, UUID idOrigen) {
        return switch (tipoOrigen) {
            case AclaracionOperativa.PARTE_NAVE -> parteNaveRepository.findById(idOrigen)
                    .map(ParteNave::getIdNave)
                    .flatMap(naveRepository::findById)
                    .map(Nave::getIdUEB)
                    .orElseThrow(() -> new ValidacionException("La parte de nave indicada no existe."));
            case AclaracionOperativa.PARTE_UNIDAD -> parteUnidadRepository.findById(idOrigen)
                    .map(ParteUnidadAsociada::getIdUEB)
                    .orElseThrow(() -> new ValidacionException("La parte de unidad asociada indicada no existe."));
            case AclaracionOperativa.PRODUCCION_CARNE -> carneRepository.findById(idOrigen)
                    .map(ProduccionCarne::getIdUEB)
                    .orElseThrow(() -> new ValidacionException("La producción de carne indicada no existe."));
            default -> throw new ValidacionException("El tipo de origen no es válido.");
        };
    }

    private void validarTipoOrigen(String tipo) {
        if (!AclaracionOperativa.PARTE_NAVE.equals(tipo)
                && !AclaracionOperativa.PARTE_UNIDAD.equals(tipo)
                && !AclaracionOperativa.PRODUCCION_CARNE.equals(tipo)) {
            throw new ValidacionException("El tipo de origen de la aclaración no es válido.");
        }
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
    }

    private void validarUEBActiva(UUID idUEB) {
        UEB ueb = uebRepository.findById(idUEB)
                .orElseThrow(() -> new ValidacionException("La UEB indicada no existe."));
        if (!ueb.isActiva()) {
            throw new ValidacionException("No se puede consolidar con una UEB desactivada.");
        }
    }

    private boolean enPeriodo(LocalDate fecha, ConsolidadoUEB consolidado) {
        return fecha != null && !fecha.isBefore(consolidado.getFechaInicio())
                && !fecha.isAfter(consolidado.getFechaFin());
    }

    private String clave(String tipo, UUID id) {
        return tipo + ":" + id;
    }

    private BigDecimal entero(Integer valor) {
        return BigDecimal.valueOf(valor == null ? 0 : valor);
    }

    private BigDecimal decimal(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private long largo(Integer valor) {
        return valor == null ? 0L : valor;
    }
}
