package cu.pican.controller.service;

import cu.pican.controller.export.ExportadorPdf;
import cu.pican.controller.export.ExportadorXlsx;
import cu.pican.model.DecisionDirectivo;
import cu.pican.model.DestinatarioInstitucional;
import cu.pican.model.EntregaInforme;
import cu.pican.model.FuenteInforme;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.Informe;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.OpcionFuente;
import cu.pican.model.RegistroAnalisis;
import cu.pican.model.TablaHistorica;
import cu.pican.model.TipoFuenteInforme;
import cu.pican.model.repository.DecisionDirectivoRepository;
import cu.pican.model.repository.EntregaInformeRepository;
import cu.pican.model.repository.FuenteInformeRepository;
import cu.pican.model.repository.InformeRepository;
import cu.pican.model.persistence.NoOpTransactionManager;
import cu.pican.model.persistence.TransactionManager;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CU-15: elaboración, decisión, entrega y consulta de informes estadísticos.
 * El informe se estructura en ocho secciones (resumen, producción frente a metas, entregas,
 * infraestructura, calidad de los datos, alertas, causas y medidas, y aprobación) y se exporta
 * a PDF o Excel. Las opciones de fuente se obtienen exclusivamente desde AnalisisService.
 */
public class InformeService extends ServicioAutorizado {

    private final InformeRepository informeRepository;
    private final FuenteInformeRepository fuenteRepository;
    private final DecisionDirectivoRepository decisionRepository;
    private final EntregaInformeRepository entregaRepository;
    private final AnalisisService analisisService;
    private final TransactionManager transacciones;
    private SessionUserIdProvider proveedorSesion;

    public InformeService(InformeRepository informeRepository,
                          FuenteInformeRepository fuenteRepository,
                          DecisionDirectivoRepository decisionRepository,
                          EntregaInformeRepository entregaRepository,
                          AnalisisService analisisService) {
        this(informeRepository, fuenteRepository, decisionRepository, entregaRepository,
                analisisService, new NoOpTransactionManager());
    }

    public InformeService(InformeRepository informeRepository,
                          FuenteInformeRepository fuenteRepository,
                          DecisionDirectivoRepository decisionRepository,
                          EntregaInformeRepository entregaRepository,
                          AnalisisService analisisService,
                          TransactionManager transacciones) {
        this.informeRepository = informeRepository;
        this.fuenteRepository = fuenteRepository;
        this.decisionRepository = decisionRepository;
        this.entregaRepository = entregaRepository;
        this.analisisService = analisisService;
        this.transacciones = transacciones == null ? new NoOpTransactionManager() : transacciones;
    }

    /** Inyectado desde AppContext; no sustituye al especialista responsable declarado en el informe. */
    public void setProveedorSesion(SessionUserIdProvider proveedorSesion) {
        this.proveedorSesion = proveedorSesion;
    }

    // -------------------- Elaboración y corrección --------------------

    public Informe crearInforme(String numeroInforme, String titulo, LocalDate periodoDesde,
                                LocalDate periodoHasta, LocalDate fechaElaboracion,
                                String especialistaResponsable, String contenidoResumen) {
        autorizacion().requerir(CasoUso.CU_15);
        validarDatosInforme(titulo, periodoDesde, periodoHasta, fechaElaboracion,
                especialistaResponsable, contenidoResumen);

        Informe informe = new Informe();
        UUID id = UUID.randomUUID();
        if (informeRepository.findById(id).isPresent()) {
            throw new ValidacionException("El identificador generado ya existe. Intente nuevamente.");
        }
        informe.setIdInforme(id);
        informe.setIdInformeRaiz(id);
        informe.setIdInformeAnterior(null);
        informe.setNumeroInforme(vacioANulo(numeroInforme));
        informe.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, null));
        aplicarDatosInforme(informe, titulo, periodoDesde, periodoHasta, fechaElaboracion,
                especialistaResponsable, contenidoResumen);
        informe.setEstado(Informe.ESTADO_BORRADOR);
        informe.setFechaCreacion(LocalDateTime.now());
        informe.setVersion(1L);
        informe.setNumeroRevision(1);
        return informeRepository.save(informe);
    }

    /**
     * Crea una nueva revisión después de una devolución o de una entrega.
     * El informe anterior NO se modifica: conserva su contenido, decisiones y entregas.
     */
    public Informe crearRevision(UUID idInformeAnterior, String titulo, LocalDate periodoDesde,
                                 LocalDate periodoHasta, LocalDate fechaElaboracion,
                                 String especialistaResponsable, String contenidoResumen) {
        autorizacion().requerir(CasoUso.CU_15);
        return transacciones.ejecutar(() -> crearRevisionInterno(idInformeAnterior, titulo, periodoDesde,
                periodoHasta, fechaElaboracion, especialistaResponsable, contenidoResumen));
    }

    private Informe crearRevisionInterno(UUID idInformeAnterior, String titulo, LocalDate periodoDesde,
                                         LocalDate periodoHasta, LocalDate fechaElaboracion,
                                         String especialistaResponsable, String contenidoResumen) {
        ValidationUtils.requireUuid(idInformeAnterior, "El informe anterior es obligatorio.");
        Informe anterior = informeRepository.findById(idInformeAnterior)
                .orElseThrow(() -> new ValidacionException("El informe anterior no existe."));
        boolean corregible = Informe.ESTADO_DEVUELTO.equals(anterior.getEstado())
                || Informe.ESTADO_ENTREGADO.equals(anterior.getEstado());
        if (!corregible) {
            throw new ValidacionException("Solo se puede crear una revisión desde un informe DEVUELTO o ENTREGADO.");
        }
        validarDatosInforme(titulo, periodoDesde, periodoHasta, fechaElaboracion,
                especialistaResponsable, contenidoResumen);

        Informe revision = new Informe();
        UUID id = UUID.randomUUID();
        revision.setIdInforme(id);
        revision.setIdInformeRaiz(anterior.getIdInformeRaiz() == null
                ? anterior.getIdInforme() : anterior.getIdInformeRaiz());
        revision.setIdInformeAnterior(anterior.getIdInforme());
        revision.setNumeroInforme(anterior.getNumeroInforme());
        revision.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, null));
        aplicarDatosInforme(revision, titulo, periodoDesde, periodoHasta, fechaElaboracion,
                especialistaResponsable, contenidoResumen);
        copiarSecciones(anterior, revision);
        revision.setEstado(Informe.ESTADO_BORRADOR);
        revision.setFechaCreacion(LocalDateTime.now());
        revision.setVersion(1L);
        revision.setNumeroRevision((anterior.getNumeroRevision() == null ? 1 : anterior.getNumeroRevision()) + 1);
        Informe guardada = informeRepository.save(revision);

        // Las fuentes se referencian de nuevo, sin copiar los registros originales.
        for (FuenteInforme fuente : fuenteRepository.findByIdInforme(anterior.getIdInforme())) {
            fuenteRepository.save(new FuenteInforme(UUID.randomUUID(), guardada.getIdInforme(),
                    fuente.getTipo(), fuente.getIdRegistroOrigen(), fuente.getDescripcion(),
                    fuente.getFuente(), fuente.getDetalleConsulta()));
        }
        return guardada;
    }

    /** Solo se permite editar contenido mientras el informe está en elaboración o devuelto. */
    public Informe actualizarContenido(UUID idInforme, String titulo, LocalDate periodoDesde,
                                       LocalDate periodoHasta, LocalDate fechaElaboracion,
                                       String especialistaResponsable, String contenidoResumen) {
        autorizacion().requerir(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        Informe actual = informeRepository.findById(idInforme)
                .orElseThrow(() -> new ValidacionException("El informe indicado no existe."));
        if (Informe.ESTADO_ENTREGADO.equals(actual.getEstado())
                || Informe.ESTADO_APROBADO.equals(actual.getEstado())
                || Informe.ESTADO_EN_REVISION.equals(actual.getEstado())) {
            throw new ValidacionException("Solo se edita el contenido de un informe en BORRADOR o DEVUELTO.");
        }
        validarDatosInforme(titulo, periodoDesde, periodoHasta, fechaElaboracion,
                especialistaResponsable, contenidoResumen);

        Informe copia = copiar(actual);
        copia.setIdUsuario(UsuarioSesionSupport.resolver(proveedorSesion, copia.getIdUsuario()));
        aplicarDatosInforme(copia, titulo, periodoDesde, periodoHasta, fechaElaboracion,
                especialistaResponsable, contenidoResumen);
        copia.setVersion((actual.getVersion() == null ? 1L : actual.getVersion()) + 1);
        return informeRepository.update(copia);
    }

    // -------------------- Fuentes --------------------

    public FuenteInforme agregarFuente(UUID idInforme, TipoFuenteInforme tipo, String idRegistroOrigen,
                                       String descripcion, String fuente, String detalleConsulta) {
        autorizacion().requerir(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        Informe informe = informeRepository.findById(idInforme)
                .orElseThrow(() -> new ValidacionException("El informe indicado no existe."));
        validarInformeEditable(informe);
        ValidationUtils.requireNonNull(tipo, "El tipo de fuente es obligatorio.");
        ValidationUtils.requireNotBlank(idRegistroOrigen, "La identificación del registro de origen es obligatoria.");
        ValidationUtils.requireNotBlank(descripcion, "La descripción de la fuente es obligatoria.");
        ValidationUtils.requireNotBlank(fuente, "La procedencia de la fuente es obligatoria.");

        String origen = idRegistroOrigen.trim();
        boolean duplicada = fuenteRepository.findByIdInforme(idInforme).stream()
                .anyMatch(f -> f.getTipo() == tipo && origen.equals(f.getIdRegistroOrigen()));
        if (duplicada) {
            throw new ValidacionException("Esa fuente ya está registrada en el informe.");
        }
        FuenteInforme fuenteInforme = new FuenteInforme(UUID.randomUUID(), idInforme, tipo, origen,
                descripcion.trim(), fuente.trim(), vacioANulo(detalleConsulta));
        return fuenteRepository.save(fuenteInforme);
    }

    public List<FuenteInforme> consultarFuentes(UUID idInforme) {
        autorizacion().requerirLectura(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        return fuenteRepository.findByIdInforme(idInforme);
    }

    /**
     * Opciones de fuente construidas únicamente con consultas del CU-14.
     * NO se copian valores: solo se entregan identificadores, etiquetas y procedencia.
     */
    public List<OpcionFuente> consultarFuentesDisponibles(LocalDate desde, LocalDate hasta) {
        autorizacion().requerirLectura(CasoUso.CU_15);
        List<OpcionFuente> out = new ArrayList<>();
        if (analisisService == null) {
            return out;
        }
        for (IndicadorHistorico i : analisisService.consultarTodasLasReferencias()) {
            out.add(new OpcionFuente(TipoFuenteInforme.REFERENCIA_HISTORICA, i.getId(),
                    i.getTabla().getTitulo() + " - " + i.getIndicador() + " (" + i.getAnio() + ")",
                    i.getFuente(), "Unidad original: " + i.getUnidadMedida()));
        }
        for (MetaEstrategica m : analisisService.consultarMetas()) {
            out.add(new OpcionFuente(TipoFuenteInforme.REFERENCIA_HISTORICA, m.getId(),
                    TablaHistorica.METAS_ESTRATEGICAS.getTitulo() + " - " + m.getIndicador(),
                    m.getFuente(), "Unidad original: " + m.getUnidadMedida()));
        }
        for (RegistroAnalisis r : analisisService.consultarHuevosPorPeriodo(desde, hasta)) {
            out.add(new OpcionFuente(TipoFuenteInforme.PRODUCCION_HUEVOS, claveOperativa("HUEVOS", r),
                    "Parte de nave " + r.getElemento() + " - " + r.getFecha(),
                    r.getFuente(), "Unidad original: " + r.getUnidadMedida()));
        }
        for (RegistroAnalisis r : analisisService.consultarCarnePorPeriodo(desde, hasta)) {
            out.add(new OpcionFuente(TipoFuenteInforme.PRODUCCION_CARNE, claveOperativa("CARNE", r),
                    "Producción de carne " + r.getElemento() + " - " + r.getFecha(),
                    r.getFuente(), "Unidad original: " + r.getUnidadMedida()));
        }
        for (RegistroAnalisis r : analisisService.consultarConsolidaciones(desde, hasta)) {
            out.add(new OpcionFuente(TipoFuenteInforme.CONSOLIDACION_UEB, claveOperativa("CONSOLIDADO", r),
                    "Consolidado UEB " + r.getElemento() + " - inicio " + r.getFecha(),
                    r.getFuente(), "No se suma a los partes; sin cantidad copiada."));
        }
        return out;
    }

    // -------------------- Secciones y flujo de revisión --------------------

    /** Guarda las secciones 2 a 7. La 1 (resumen) se edita con actualizarContenido; la 8 se compone al exportar. */
    public Informe guardarSecciones(UUID idInforme, String produccionMetas, String entregas,
                                    String infraestructura, String calidadDatos, String alertas,
                                    String causasMedidas) {
        autorizacion().requerir(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        Informe actual = informeRepository.findById(idInforme)
                .orElseThrow(() -> new ValidacionException("El informe indicado no existe."));
        validarInformeEditable(actual);
        Informe copia = copiar(actual);
        copia.setSeccionProduccionMetas(textoOpcional(produccionMetas));
        copia.setSeccionEntregas(textoOpcional(entregas));
        copia.setSeccionInfraestructura(textoOpcional(infraestructura));
        copia.setSeccionCalidadDatos(textoOpcional(calidadDatos));
        copia.setSeccionAlertas(textoOpcional(alertas));
        copia.setSeccionCausasMedidas(textoOpcional(causasMedidas));
        copia.setVersion((actual.getVersion() == null ? 1L : actual.getVersion()) + 1);
        return informeRepository.update(copia);
    }

    /** Envía el borrador a revisión del directivo; exige que las siete secciones redactables estén completas. */
    public Informe enviarARevision(UUID idInforme) {
        autorizacion().requerir(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        Informe actual = informeRepository.findById(idInforme)
                .orElseThrow(() -> new ValidacionException("El informe indicado no existe."));
        if (!Informe.ESTADO_BORRADOR.equals(actual.getEstado())) {
            throw new ValidacionException("Solo un informe en BORRADOR se puede enviar a revisión.");
        }
        if (actual.getContenidoResumen() == null || actual.getContenidoResumen().trim().isEmpty()
                || actual.getSeccionProduccionMetas() == null || actual.getSeccionProduccionMetas().trim().isEmpty()
                || actual.getSeccionEntregas() == null || actual.getSeccionEntregas().trim().isEmpty()
                || actual.getSeccionInfraestructura() == null || actual.getSeccionInfraestructura().trim().isEmpty()
                || actual.getSeccionCalidadDatos() == null || actual.getSeccionCalidadDatos().trim().isEmpty()
                || actual.getSeccionAlertas() == null || actual.getSeccionAlertas().trim().isEmpty()
                || actual.getSeccionCausasMedidas() == null || actual.getSeccionCausasMedidas().trim().isEmpty()) {
            throw new ValidacionException("El informe está incompleto: redacte el resumen y las secciones 2 a 7 antes de enviarlo a revisión.");
        }
        Informe copia = copiar(actual);
        copia.setEstado(Informe.ESTADO_EN_REVISION);
        copia.setVersion((actual.getVersion() == null ? 1L : actual.getVersion()) + 1);
        return informeRepository.update(copia);
    }

    /**
     * Compone las ocho secciones del informe. La sección 8 (aprobación) se deriva de las
     * decisiones y entregas registradas; no se almacena como texto libre.
     */
    public Map<String, String> componerSecciones(UUID idInforme) {
        autorizacion().requerirLectura(CasoUso.CU_15);
        Informe informe = buscarPorId(idInforme);
        Map<String, String> secciones = new LinkedHashMap<>();
        secciones.put("1. Resumen", textoSeccion(informe.getContenidoResumen()));
        secciones.put("2. Producción frente a metas", textoSeccion(informe.getSeccionProduccionMetas()));
        secciones.put("3. Entregas", textoSeccion(informe.getSeccionEntregas()));
        secciones.put("4. Infraestructura", textoSeccion(informe.getSeccionInfraestructura()));
        secciones.put("5. Calidad de los datos", textoSeccion(informe.getSeccionCalidadDatos()));
        secciones.put("6. Alertas", textoSeccion(informe.getSeccionAlertas()));
        secciones.put("7. Causas y medidas", textoSeccion(informe.getSeccionCausasMedidas()));
        secciones.put("8. Aprobación", componerAprobacion(idInforme));
        return secciones;
    }

    private String componerAprobacion(UUID idInforme) {
        List<DecisionDirectivo> decisiones = new ArrayList<>(decisionRepository.findByIdInforme(idInforme));
        decisiones.sort(Comparator.comparingInt(d -> d.getNumeroDecision() == null ? 0 : d.getNumeroDecision()));
        List<EntregaInforme> entregas = new ArrayList<>(entregaRepository.findByIdInforme(idInforme));
        if (decisiones.isEmpty() && entregas.isEmpty()) {
            return "Sin decisión ni entrega registradas.";
        }
        StringBuilder texto = new StringBuilder();
        for (DecisionDirectivo d : decisiones) {
            texto.append("Decisión ").append(d.getNumeroDecision() == null ? "-" : d.getNumeroDecision())
                    .append(": ").append(d.getDecision())
                    .append(" de ").append(d.getNombreDirectivo())
                    .append(d.getFechaDecision() == null ? "" : " (" + d.getFechaDecision() + ")")
                    .append(d.getObservacion() == null ? "" : ". Observación: " + d.getObservacion())
                    .append(".\n");
        }
        for (EntregaInforme e : entregas) {
            texto.append("Entregado a ").append(e.getDestinatario())
                    .append(e.getFechaEntrega() == null ? "" : " el " + e.getFechaEntrega())
                    .append(" (registrado por ").append(e.getResponsableRegistro()).append(").\n");
        }
        return texto.toString().trim();
    }

    private String textoSeccion(String contenido) {
        return contenido == null || contenido.trim().isEmpty() ? "(sin redactar)" : contenido.trim();
    }

    private String textoOpcional(String contenido) {
        return contenido == null || contenido.trim().isEmpty() ? "" : contenido.trim();
    }

    private void copiarSecciones(Informe origen, Informe destino) {
        destino.setSeccionProduccionMetas(origen.getSeccionProduccionMetas());
        destino.setSeccionEntregas(origen.getSeccionEntregas());
        destino.setSeccionInfraestructura(origen.getSeccionInfraestructura());
        destino.setSeccionCalidadDatos(origen.getSeccionCalidadDatos());
        destino.setSeccionAlertas(origen.getSeccionAlertas());
        destino.setSeccionCausasMedidas(origen.getSeccionCausasMedidas());
    }

    // -------------------- Exportación PDF / Excel --------------------

    public void exportarPdf(Path destino, UUID idInforme) {
        autorizacion().requerir(CasoUso.CU_15);
        Informe informe = buscarPorId(idInforme);
        List<ExportadorPdf.Seccion> secciones = new ArrayList<>();
        secciones.add(new ExportadorPdf.Seccion("Identificación del informe",
                new String[]{"Campo", "Valor"},
                List.of(
                        new Object[]{"Número", nulo(informe.getNumeroInforme())},
                        new Object[]{"Título", nulo(informe.getTitulo())},
                        new Object[]{"Período analizado", informe.getPeriodoAnalizado()},
                        new Object[]{"Fecha de elaboración", nulo(informe.getFechaElaboracion())},
                        new Object[]{"Especialista responsable", nulo(informe.getEspecialistaResponsable())},
                        new Object[]{"Revisión", nulo(informe.getNumeroRevision())},
                        new Object[]{"Estado", nulo(informe.getEstado())})));
        for (Map.Entry<String, String> seccion : componerSecciones(idInforme).entrySet()) {
            List<Object[]> filas = new ArrayList<>();
            for (String parrafo : seccion.getValue().split("\n")) {
                if (!parrafo.trim().isEmpty()) {
                    filas.add(new Object[]{parrafo.trim()});
                }
            }
            if (filas.isEmpty()) {
                filas.add(new Object[]{"(sin contenido)"});
            }
            secciones.add(new ExportadorPdf.Seccion(seccion.getKey(), new String[]{"Detalle"}, filas));
        }
        ExportadorPdf.escribir(destino, "Informe " + nulo(informe.getNumeroInforme()) + " - " + nulo(informe.getTitulo()), secciones);
    }

    public void exportarExcel(Path destino, UUID idInforme) {
        autorizacion().requerir(CasoUso.CU_15);
        Informe informe = buscarPorId(idInforme);
        ExportadorXlsx.Libro libro = new ExportadorXlsx.Libro();
        libro.hoja("Identificación", new String[]{"Campo", "Valor"},
                List.of(
                        new Object[]{"Número", nulo(informe.getNumeroInforme())},
                        new Object[]{"Título", nulo(informe.getTitulo())},
                        new Object[]{"Período analizado", informe.getPeriodoAnalizado()},
                        new Object[]{"Fecha de elaboración", nulo(informe.getFechaElaboracion())},
                        new Object[]{"Especialista responsable", nulo(informe.getEspecialistaResponsable())},
                        new Object[]{"Revisión", nulo(informe.getNumeroRevision())},
                        new Object[]{"Estado", nulo(informe.getEstado())}));
        List<Object[]> filasSecciones = new ArrayList<>();
        for (Map.Entry<String, String> seccion : componerSecciones(idInforme).entrySet()) {
            filasSecciones.add(new Object[]{seccion.getKey(), seccion.getValue()});
        }
        libro.hoja("Secciones", new String[]{"Sección", "Contenido"}, filasSecciones);
        libro.escribir(destino);
    }

    // -------------------- Decisión del directivo --------------------

    public DecisionDirectivo registrarDecision(UUID idInforme, String nombreDirectivo, String decision,
                                               LocalDate fechaDecision, String observacion) {
        autorizacion().requerir(CasoUso.CU_15);
        return transacciones.ejecutar(() -> registrarDecisionInterno(idInforme, nombreDirectivo, decision,
                fechaDecision, observacion));
    }

    private DecisionDirectivo registrarDecisionInterno(UUID idInforme, String nombreDirectivo, String decision,
                                                       LocalDate fechaDecision, String observacion) {
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        Informe informe = informeRepository.findById(idInforme)
                .orElseThrow(() -> new ValidacionException("El informe indicado no existe."));

        String estado = informe.getEstado();
        if (Informe.ESTADO_APROBADO.equals(estado) || Informe.ESTADO_ENTREGADO.equals(estado)) {
            throw new ValidacionException("Ya existe una decisión registrada; no se puede sobrescribir.");
        }
        if (Informe.ESTADO_DEVUELTO.equals(estado)) {
            throw new ValidacionException("El informe fue devuelto; cree una nueva revisión para otra decisión.");
        }
        if (!Informe.ESTADO_EN_REVISION.equals(estado)) {
            throw new ValidacionException("Solo se puede decidir sobre un informe EN REVISIÓN.");
        }
        ValidationUtils.requireNotBlank(nombreDirectivo, "El nombre del directivo es obligatorio.");
        ValidationUtils.requireNonNull(fechaDecision, "La fecha de la decisión es obligatoria.");
        ValidationUtils.requireNotBlank(decision, "La decisión es obligatoria.");
        String tipoDecision = decision.trim().toUpperCase();
        if (!DecisionDirectivo.APROBADO.equals(tipoDecision) && !DecisionDirectivo.DEVUELTO.equals(tipoDecision)) {
            throw new ValidacionException("La decisión debe ser APROBADO o DEVUELTO.");
        }
        if (DecisionDirectivo.DEVUELTO.equals(tipoDecision)) {
            ValidationUtils.requireNotBlank(observacion, "La observación es obligatoria cuando el informe es DEVUELTO.");
        }
        if (!decisionRepository.findByIdInforme(idInforme).isEmpty()) {
            throw new ValidacionException("Ya existe una decisión registrada; no se puede sobrescribir.");
        }

        int numero = decisionRepository.findByIdInforme(idInforme).size() + 1;
        DecisionDirectivo registrada = new DecisionDirectivo(UUID.randomUUID(), idInforme,
                nombreDirectivo.trim(), tipoDecision, fechaDecision, vacioANulo(observacion),
                LocalDateTime.now(), numero);
        DecisionDirectivo guardada = decisionRepository.save(registrada);

        Informe copia = copiar(informe);
        copia.setEstado(tipoDecision);
        copia.setVersion((informe.getVersion() == null ? 1L : informe.getVersion()) + 1);
        informeRepository.update(copia);
        return guardada;
    }

    public List<DecisionDirectivo> consultarHistorialDecisiones(UUID idInforme) {
        autorizacion().requerirLectura(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        return decisionRepository.findByIdInforme(idInforme);
    }

    // -------------------- Entrega --------------------

    public EntregaInforme registrarEntrega(UUID idInforme, String destinatario, LocalDate fechaEntrega,
                                           String responsableRegistro, String observaciones) {
        autorizacion().requerir(CasoUso.CU_15);
        return transacciones.ejecutar(() -> registrarEntregaInterno(idInforme, destinatario, fechaEntrega,
                responsableRegistro, observaciones));
    }

    private EntregaInforme registrarEntregaInterno(UUID idInforme, String destinatario, LocalDate fechaEntrega,
                                                   String responsableRegistro, String observaciones) {
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        Informe informe = informeRepository.findById(idInforme)
                .orElseThrow(() -> new ValidacionException("El informe indicado no existe."));
        String estado = informe.getEstado();
        if (!Informe.ESTADO_APROBADO.equals(estado) && !Informe.ESTADO_ENTREGADO.equals(estado)) {
            throw new ValidacionException("No se puede entregar un informe que no está APROBADO.");
        }
        DestinatarioInstitucional institucion;
        try {
            institucion = DestinatarioInstitucional.desdeTexto(destinatario);
        } catch (IllegalArgumentException ex) {
            throw new ValidacionException(ex.getMessage());
        }
        ValidationUtils.requireNonNull(fechaEntrega, "La fecha de entrega es obligatoria.");
        ValidationUtils.requireNotBlank(responsableRegistro, "El responsable que registra la entrega es obligatorio.");
        if (entregaRepository.existsByIdInformeAndDestinatario(idInforme, institucion)) {
            throw new ValidacionException("Ya existe una entrega registrada para " + institucion + ".");
        }

        EntregaInforme entrega = new EntregaInforme(UUID.randomUUID(), idInforme, institucion,
                fechaEntrega, responsableRegistro.trim(), vacioANulo(observaciones), LocalDateTime.now());
        EntregaInforme guardada = entregaRepository.save(entrega);

        Informe copia = copiar(informe);
        copia.setEstado(Informe.ESTADO_ENTREGADO);
        copia.setVersion((informe.getVersion() == null ? 1L : informe.getVersion()) + 1);
        informeRepository.update(copia);
        return guardada;
    }

    public List<EntregaInforme> consultarEntregas(UUID idInforme) {
        autorizacion().requerirLectura(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        return entregaRepository.findByIdInforme(idInforme);
    }

    // -------------------- Consultas --------------------

    public List<Informe> listarInformes() {
        autorizacion().requerirLectura(CasoUso.CU_15);
        List<Informe> out = new ArrayList<>(informeRepository.findAll());
        out.sort(Comparator.comparing(Informe::getFechaCreacion,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public Informe buscarPorId(UUID idInforme) {
        autorizacion().requerirLectura(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInforme, "El informe es obligatorio.");
        return informeRepository.findById(idInforme)
                .orElseThrow(() -> new ValidacionException("El informe indicado no existe."));
    }

    public List<Informe> filtrarPorEstado(String estado) {
        autorizacion().requerirLectura(CasoUso.CU_15);
        ValidationUtils.requireNotBlank(estado, "El estado es obligatorio.");
        return informeRepository.findByEstado(estado.trim());
    }

    public List<Informe> consultarRevisiones(UUID idInformeRaiz) {
        autorizacion().requerirLectura(CasoUso.CU_15);
        ValidationUtils.requireUuid(idInformeRaiz, "El informe raíz es obligatorio.");
        List<Informe> out = new ArrayList<>(informeRepository.findByIdInformeRaiz(idInformeRaiz));
        out.sort(Comparator.comparingInt(i -> i.getNumeroRevision() == null ? 0 : i.getNumeroRevision()));
        return out;
    }

    // -------------------- Auxiliares --------------------

    private void validarDatosInforme(String titulo, LocalDate desde, LocalDate hasta, LocalDate fechaElaboracion,
                                     String especialista, String contenido) {
        ValidationUtils.requireNotBlank(titulo, "El título del informe es obligatorio.");
        ValidationUtils.requireNonNull(desde, "La fecha inicial del período analizado es obligatoria.");
        ValidationUtils.requireNonNull(hasta, "La fecha final del período analizado es obligatoria.");
        if (desde.isAfter(hasta)) {
            throw new ValidacionException("El período analizado es inválido: la fecha inicial no puede ser posterior a la final.");
        }
        ValidationUtils.requireNonNull(fechaElaboracion, "La fecha de elaboración es obligatoria.");
        ValidationUtils.requireNotBlank(especialista, "El especialista responsable es obligatorio.");
        ValidationUtils.requireNotBlank(contenido, "El contenido o resumen del informe es obligatorio.");
    }

    private void aplicarDatosInforme(Informe informe, String titulo, LocalDate desde, LocalDate hasta,
                                     LocalDate fechaElaboracion, String especialista, String contenido) {
        informe.setTitulo(titulo.trim());
        informe.setPeriodoDesde(desde);
        informe.setPeriodoHasta(hasta);
        informe.setFechaElaboracion(fechaElaboracion);
        informe.setEspecialistaResponsable(especialista.trim());
        informe.setContenidoResumen(contenido.trim());
    }

    private void validarInformeEditable(Informe informe) {
        if (Informe.ESTADO_APROBADO.equals(informe.getEstado())
                || Informe.ESTADO_ENTREGADO.equals(informe.getEstado())
                || Informe.ESTADO_EN_REVISION.equals(informe.getEstado())) {
            throw new ValidacionException("Un informe que no está en BORRADOR o DEVUELTO no admite cambios en sus fuentes.");
        }
    }

    private String claveOperativa(String prefijo, RegistroAnalisis r) {
        return prefijo + "|" + r.getFecha() + "|" + r.getElemento();
    }

    private String vacioANulo(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        return texto.trim();
    }

    private String nulo(Object valor) {
        return valor == null ? "-" : String.valueOf(valor);
    }

    private Informe copiar(Informe o) {
        Informe c = new Informe();
        c.setIdInforme(o.getIdInforme());
        c.setIdInformeRaiz(o.getIdInformeRaiz());
        c.setIdInformeAnterior(o.getIdInformeAnterior());
        c.setNumeroInforme(o.getNumeroInforme());
        c.setTitulo(o.getTitulo());
        c.setPeriodoDesde(o.getPeriodoDesde());
        c.setPeriodoHasta(o.getPeriodoHasta());
        c.setFechaElaboracion(o.getFechaElaboracion());
        c.setEspecialistaResponsable(o.getEspecialistaResponsable());
        c.setIdUsuario(o.getIdUsuario());
        c.setContenidoResumen(o.getContenidoResumen());
        copiarSecciones(o, c);
        c.setEstado(o.getEstado());
        c.setFechaCreacion(o.getFechaCreacion());
        c.setVersion(o.getVersion());
        c.setNumeroRevision(o.getNumeroRevision());
        return c;
    }
}