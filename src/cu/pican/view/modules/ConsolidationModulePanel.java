package cu.pican.view.modules;

import cu.pican.controller.app.AppContext;
import cu.pican.controller.service.ConsolidadoUEBService;
import cu.pican.controller.service.ConsolidadoUEBService.DetalleConsolidado;
import cu.pican.controller.service.NaveService;
import cu.pican.controller.service.ParteNaveService;
import cu.pican.controller.service.ParteUnidadAsociadaService;
import cu.pican.controller.service.ProduccionCarneService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.UnidadAsociadaService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.model.AclaracionOperativa;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConsolidationModulePanel extends JPanel {

    private static final String TODAS = "Todas";

    private final ConsolidadoUEBService consolidadoService;
    private final UEBService uebService;
    private final ParteNaveService parteNaveService;
    private final ParteUnidadAsociadaService parteUnidadService;
    private final ProduccionCarneService produccionCarneService;
    private final NaveService naveService;
    private final UnidadAsociadaService unidadService;

    private final DataTablePanel consolidadoTablePanel;
    private final DataTablePanel huevosTablePanel;
    private final DataTablePanel carneTablePanel;
    private final DataTablePanel unidadesTablePanel;
    private final DataTablePanel aclaracionesTablePanel;
    private final List<ConsolidadoUEB> currentRows = new ArrayList<>();
    private final Map<String, UUID> uebLabelToId = new HashMap<>();
    private final Map<Integer, UUID> huevosRowIds = new HashMap<>();
    private final Map<Integer, UUID> carneRowIds = new HashMap<>();
    private final Map<Integer, UUID> unidadesRowIds = new HashMap<>();
    private final Map<Integer, UUID> aclaracionesRowIds = new HashMap<>();

    private final JComboBox<String> uebFormCombo = new JComboBox<>();
    private final JTextField fechaInicioField = new JTextField(10);
    private final JTextField fechaFinField = new JTextField(10);
    private final JTextField estadoField = new JTextField(16);
    private final JTextField motivoParcialField = new JTextField(28);
    private final JComboBox<String> uebFilterCombo = new JComboBox<>();
    private final JTextField filtroInicioField = new JTextField(10);
    private final JTextField filtroFinField = new JTextField(10);
    private final JLabel resumenPeriodo = new JLabel(" ");

    private UUID selectedConsolidadoId;
    private String selectedOrigenTipo;
    private UUID selectedOrigenId;
    private UUID selectedAclaracionId;

    public ConsolidationModulePanel(AppContext context) {
        this.consolidadoService = context.getConsolidadoUEBService();
        this.uebService = context.getUebService();
        this.parteNaveService = context.getParteNaveService();
        this.parteUnidadService = context.getParteUnidadAsociadaService();
        this.produccionCarneService = context.getProduccionCarneService();
        this.naveService = context.getNaveService();
        this.unidadService = context.getUnidadAsociadaService();

        setLayout(new BorderLayout(0, 10));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(new ModuleHeader("Consolidación de producción",
                "Calcula y guarda totales, vínculos y cobertura por UEB y período. Los borradores no se suman "
                        + "y una aclaración pendiente impide consolidar."), BorderLayout.NORTH);

        consolidadoTablePanel = new DataTablePanel("Consolidaciones registradas", new String[]{
                "UEB", "Inicio", "Fin", "Estado", "Huevos (U)", "Carne (t)",
                "Incluidos", "Excluidos", "Cobertura", "Motivo"
        }, new Object[0][0]);
        consolidadoTablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        consolidadoTablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSeleccion();
            }
        });

        huevosTablePanel = new DataTablePanel("Partes de nave incluidos", new String[]{
                "Fecha", "Nave", "Huevos", "Rotos", "Vivas", "Muertas", "Enfermas", "Pienso (kg)", "Observaciones"
        }, new Object[0][0]);
        carneTablePanel = new DataTablePanel("Producción de carne incluida", new String[]{
                "Fecha", "UEB", "Toneladas", "Observaciones"
        }, new Object[0][0]);
        unidadesTablePanel = new DataTablePanel("Partes de unidades asociadas incluidos", new String[]{
                "Fecha", "Unidad", "UEB", "Estado", "Referencia papel", "Observaciones"
        }, new Object[0][0]);
        aclaracionesTablePanel = new DataTablePanel("Aclaraciones de los registros incluidos", new String[]{
                "Tipo", "Origen", "Estado", "Motivo", "Solicitada", "Resolución"
        }, new Object[0][0]);

        for (DataTablePanel panel : List.of(huevosTablePanel, carneTablePanel, unidadesTablePanel, aclaracionesTablePanel)) {
            panel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        }
        configurarSeleccionOrigen(huevosTablePanel, huevosRowIds, AclaracionOperativa.PARTE_NAVE);
        configurarSeleccionOrigen(carneTablePanel, carneRowIds, AclaracionOperativa.PRODUCCION_CARNE);
        configurarSeleccionOrigen(unidadesTablePanel, unidadesRowIds, AclaracionOperativa.PARTE_UNIDAD);
        aclaracionesTablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedAclaracionId = aclaracionesRowIds.get(aclaracionesTablePanel.getTable().getSelectedRow());
            }
        });

        JTabbedPane detalles = new JTabbedPane();
        detalles.addTab("Huevos", huevosTablePanel);
        detalles.addTab("Carne", carneTablePanel);
        detalles.addTab("Unidades asociadas", unidadesTablePanel);
        detalles.addTab("Aclaraciones", aclaracionesTablePanel);

        JPanel detalleWrap = new JPanel(new BorderLayout(0, 6));
        detalleWrap.setOpaque(false);
        JLabel nota = new JLabel("La cobertura es observacional: no presume un calendario diario de reportes. "
                + "Los totales son la fotografía guardada al consolidar.");
        nota.setFont(UITheme.SMALL_FONT);
        nota.setForeground(UITheme.MUTED_TEXT);
        resumenPeriodo.setFont(UITheme.SMALL_FONT);
        detalleWrap.add(nota, BorderLayout.NORTH);
        detalleWrap.add(resumenPeriodo, BorderLayout.CENTER);
        detalleWrap.add(detalles, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, consolidadoTablePanel, detalleWrap);
        split.setResizeWeight(0.45);
        split.setBorder(null);
        split.setOpaque(false);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(buildTopPanel(), BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(buildActionsPanel(), BorderLayout.SOUTH);

        try {
            recargarUEBs();
            refrescarTabla(consolidadoService.listarTodos());
        } catch (RuntimeException ex) {
            limpiarDetalle();
            MessageHelper.showValidation(this, "No se pudieron cargar las consolidaciones: " + ex.getMessage());
        }
    }

    private JPanel buildTopPanel() {
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(buildFormPanel(), BorderLayout.CENTER);
        top.add(buildFilterPanel(), BorderLayout.SOUTH);
        return top;
    }

    private JPanel buildFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Registro / edición de consolidación"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("UEB:"), c);
        c.gridx = 1; c.gridwidth = 3; form.add(uebFormCombo, c);
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Fecha inicio (AAAA-MM-DD):"), c);
        c.gridx = 1; form.add(fechaInicioField, c);
        c.gridx = 2; form.add(new JLabel("Fecha fin (AAAA-MM-DD):"), c);
        c.gridx = 3; form.add(fechaFinField, c);
        c.gridx = 0; c.gridy = 2; form.add(new JLabel("Estado calculado:"), c);
        estadoField.setEditable(false);
        c.gridx = 1; form.add(estadoField, c);
        c.gridx = 2; form.add(new JLabel("Motivo parcial:"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0; form.add(motivoParcialField, c);
        return form;
    }

    private JPanel buildFilterPanel() {
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filter.setOpaque(false);
        filter.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Filtros de consolidaciones"));
        filter.add(new JLabel("UEB:"));
        filter.add(uebFilterCombo);
        filter.add(new JLabel("Desde:"));
        filter.add(filtroInicioField);
        filter.add(new JLabel("Hasta:"));
        filter.add(filtroFinField);
        AppButton aplicar = new AppButton("Aplicar filtros");
        AppButton todas = new AppButton("Mostrar todas");
        AppButton recargar = new AppButton("Recargar UEB");
        aplicar.addActionListener(e -> aplicarFiltros());
        todas.addActionListener(e -> mostrarTodas());
        recargar.addActionListener(e -> {
            recargarUEBs();
            MessageHelper.showInfo(this, "Listado de UEB actualizado.");
        });
        filter.add(aplicar);
        filter.add(todas);
        filter.add(recargar);
        return filter;
    }

    private JPanel buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);
        AppButton nuevo = new AppButton("Nuevo");
        AppButton registrar = new AppButton("Registrar");
        AppButton actualizar = new AppButton("Actualizar");
        AppButton consultar = new AppButton("Consultar selección");
        AppButton solicitar = new AppButton("Solicitar aclaración");
        AppButton resolver = new AppButton("Resolver aclaración");
        nuevo.addActionListener(e -> limpiarFormulario());
        registrar.addActionListener(e -> registrar());
        actualizar.addActionListener(e -> actualizar());
        consultar.addActionListener(e -> consultarSeleccion());
        solicitar.addActionListener(e -> solicitarAclaracion());
        resolver.addActionListener(e -> resolverAclaracion());
        actions.add(nuevo);
        actions.add(registrar);
        actions.add(actualizar);
        actions.add(consultar);
        actions.add(solicitar);
        actions.add(resolver);
        return actions;
    }

    private void registrar() {
        try {
            ConsolidadoUEB consolidado = buildFromForm(UUID.randomUUID());
            consolidado.setCreadoEn(LocalDateTime.now());
            consolidado.setVersion(1L);
            consolidadoService.registrar(consolidado);
            MessageHelper.showInfo(this, "Consolidación registrada correctamente.");
            limpiarFormulario();
            refrescarTabla(consolidadoService.listarTodos());
        } catch (ValidacionException | IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la consolidación: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedConsolidadoId == null) {
            MessageHelper.showValidation(this, "Seleccione una consolidación para actualizar.");
            return;
        }
        try {
            ConsolidadoUEB existente = consolidadoService.buscarPorId(selectedConsolidadoId).orElse(null);
            if (existente == null) {
                MessageHelper.showValidation(this, "La consolidación seleccionada ya no existe.");
                return;
            }
            ConsolidadoUEB consolidado = buildFromForm(selectedConsolidadoId);
            consolidado.setCreadoEn(existente.getCreadoEn());
            consolidado.setVersion(existente.getVersion());
            consolidadoService.actualizar(consolidado);
            MessageHelper.showInfo(this, "Consolidación actualizada y recalculada correctamente.");
            refrescarTabla(consolidadoService.listarTodos());
            consolidadoService.buscarPorId(selectedConsolidadoId).ifPresent(this::cargarDetallePeriodo);
        } catch (ValidacionException | IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar la consolidación: " + ex.getMessage());
        }
    }

    private ConsolidadoUEB buildFromForm(UUID id) {
        ConsolidadoUEB consolidado = new ConsolidadoUEB();
        consolidado.setIdConsolidado(id);
        consolidado.setIdUEB(getSelectedUebId(uebFormCombo));
        consolidado.setFechaInicio(parseFecha(fechaInicioField.getText(), "fecha de inicio"));
        consolidado.setFechaFin(parseFecha(fechaFinField.getText(), "fecha de fin"));
        consolidado.setMotivoParcial(emptyToNull(motivoParcialField.getText()));
        return consolidado;
    }

    private void consultarSeleccion() {
        if (selectedConsolidadoId == null) {
            MessageHelper.showValidation(this, "Seleccione una consolidación para consultar.");
            return;
        }
        consolidadoService.buscarPorId(selectedConsolidadoId).ifPresentOrElse(consolidado -> {
            cargarEnFormulario(consolidado);
            cargarDetallePeriodo(consolidado);
            MessageHelper.showInfo(this, "Consolidación y vínculos guardados cargados.");
        }, () -> MessageHelper.showValidation(this, "La consolidación seleccionada ya no existe."));
    }

    private void cargarSeleccion() {
        int row = consolidadoTablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= currentRows.size()) {
            return;
        }
        ConsolidadoUEB consolidado = currentRows.get(row);
        consolidadoService.buscarPorId(consolidado.getIdConsolidado()).ifPresent(found -> {
            cargarEnFormulario(found);
            cargarDetallePeriodo(found);
        });
    }

    private void cargarEnFormulario(ConsolidadoUEB consolidado) {
        selectedConsolidadoId = consolidado.getIdConsolidado();
        seleccionarUebEnCombo(uebFormCombo, consolidado.getIdUEB());
        fechaInicioField.setText(consolidado.getFechaInicio() == null ? "" : consolidado.getFechaInicio().toString());
        fechaFinField.setText(consolidado.getFechaFin() == null ? "" : consolidado.getFechaFin().toString());
        estadoField.setText(safe(consolidado.getEstado()));
        motivoParcialField.setText(safe(consolidado.getMotivoParcial()));
    }

    private void limpiarFormulario() {
        selectedConsolidadoId = null;
        if (uebFormCombo.getItemCount() > 0) {
            uebFormCombo.setSelectedIndex(0);
        }
        fechaInicioField.setText("");
        fechaFinField.setText("");
        estadoField.setText("");
        motivoParcialField.setText("");
        consolidadoTablePanel.getTable().clearSelection();
        limpiarDetalle();
    }

    private void aplicarFiltros() {
        try {
            UUID idUEB = getSelectedUebId(uebFilterCombo);
            LocalDate inicio = parseFecha(filtroInicioField.getText(), "fecha desde");
            LocalDate fin = parseFecha(filtroFinField.getText(), "fecha hasta");
            if (inicio != null && fin != null && fin.isBefore(inicio)) {
                MessageHelper.showValidation(this, "La fecha 'hasta' no puede ser anterior a 'desde'.");
                return;
            }
            List<ConsolidadoUEB> base = idUEB == null
                    ? consolidadoService.listarTodos() : consolidadoService.buscarPorUEB(idUEB);
            List<ConsolidadoUEB> filtrados = new ArrayList<>();
            for (ConsolidadoUEB consolidado : base) {
                if (inicio != null && consolidado.getFechaFin().isBefore(inicio)) continue;
                if (fin != null && consolidado.getFechaInicio().isAfter(fin)) continue;
                filtrados.add(consolidado);
            }
            refrescarTabla(filtrados);
        } catch (ValidacionException | IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void mostrarTodas() {
        uebFilterCombo.setSelectedItem(TODAS);
        filtroInicioField.setText("");
        filtroFinField.setText("");
        refrescarTabla(consolidadoService.listarTodos());
    }

    private void refrescarTabla(List<ConsolidadoUEB> consolidados) {
        currentRows.clear();
        DefaultTableModel model = consolidadoTablePanel.getTableModel();
        model.setRowCount(0);
        if (consolidados == null) return;
        for (ConsolidadoUEB consolidado : consolidados) {
            currentRows.add(consolidado);
            String nombreUEB = uebService.buscarPorId(consolidado.getIdUEB())
                    .map(UEB::getNombre).orElse("(UEB no encontrada)");
            int incluidos = consolidado.getPartesNaveIncluidos() + consolidado.getPartesUnidadIncluidos()
                    + consolidado.getRegistrosCarneIncluidos();
            int excluidos = consolidado.getPartesNaveExcluidos() + consolidado.getPartesUnidadExcluidos();
            model.addRow(new Object[]{
                    safe(nombreUEB), consolidado.getFechaInicio(), consolidado.getFechaFin(),
                    safe(consolidado.getEstado()), num(consolidado.getTotalHuevos()), num(consolidado.getTotalCarne()),
                    incluidos, excluidos,
                    "Naves sin parte: " + consolidado.getNavesSinParte()
                            + "; unidades sin parte: " + consolidado.getUnidadesSinParte(),
                    safe(consolidado.getMotivoParcial())
            });
        }
    }

    private void cargarDetallePeriodo(ConsolidadoUEB consolidado) {
        limpiarDetalle();
        if (consolidado == null) return;
        selectedConsolidadoId = consolidado.getIdConsolidado();
        consolidadoService.obtenerDetalle(consolidado.getIdConsolidado()).ifPresent(detalle -> poblarDetalle(detalle));
    }

    private void poblarDetalle(DetalleConsolidado detalle) {
        ConsolidadoUEB consolidado = detalle.consolidado();
        DefaultTableModel huevos = huevosTablePanel.getTableModel();
        for (UUID id : detalle.partesNave()) {
            parteNaveService.buscarPorId(id).ifPresent(parte -> {
                int row = huevos.getRowCount();
                huevosRowIds.put(row, id);
                String nave = naveService.buscarPorId(parte.getIdNave()).map(Nave::getNombre).orElse("(no encontrada)");
                huevos.addRow(new Object[]{
                        parte.getFechaProduccion(), safe(nave), toText(parte.getHuevosProducidos()),
                        toText(parte.getHuevosRotos()), toText(parte.getGallinasVivas()),
                        toText(parte.getGallinasMuertas()), toText(parte.getGallinasEnfermas()),
                        toText(parte.getPiensoKg()), safe(parte.getObservaciones())
                });
            });
        }

        DefaultTableModel carnes = carneTablePanel.getTableModel();
        for (UUID id : detalle.produccionesCarne()) {
            produccionCarneService.buscarPorId(id).ifPresent(carne -> {
                int row = carnes.getRowCount();
                carneRowIds.put(row, id);
                String ueb = uebService.buscarPorId(carne.getIdUEB()).map(UEB::getNombre).orElse("(no encontrada)");
                carnes.addRow(new Object[]{
                        carne.getFechaProduccion(), safe(ueb), toText(carne.getCantidadToneladas()),
                        safe(carne.getObservaciones())
                });
            });
        }

        DefaultTableModel unidades = unidadesTablePanel.getTableModel();
        for (UUID id : detalle.partesUnidad()) {
            parteUnidadService.buscarPorId(id).ifPresent(parte -> {
                int row = unidades.getRowCount();
                unidadesRowIds.put(row, id);
                String unidad = unidadService.buscarPorId(parte.getIdUnidad())
                        .map(UnidadAsociada::getNombre).orElse("(no encontrada)");
                String ueb = uebService.buscarPorId(parte.getIdUEB()).map(UEB::getNombre).orElse("(no encontrada)");
                unidades.addRow(new Object[]{
                        parte.getFechaProduccion(), safe(unidad), safe(ueb), safe(parte.getEstado()),
                        safe(parte.getReferenciaPapel()), safe(parte.getObservaciones())
                });
            });
        }

        DefaultTableModel aclaraciones = aclaracionesTablePanel.getTableModel();
        for (AclaracionOperativa aclaracion : detalle.aclaraciones()) {
            int row = aclaraciones.getRowCount();
            aclaracionesRowIds.put(row, aclaracion.getIdAclaracion());
            String resolucion = aclaracion.getObservacionResolucion() == null
                    ? "" : aclaracion.getObservacionResolucion();
            aclaraciones.addRow(new Object[]{
                    safe(aclaracion.getTipoOrigen()), aclaracion.getIdOrigen(), safe(aclaracion.getEstado()),
                    safe(aclaracion.getMotivo()), aclaracion.getFechaSolicitud(), resolucion
            });
        }

        String resumen = "<html><div style='width:1100px'>"
                + "Huevos: " + num(consolidado.getTotalHuevos()) + " U; rotos: " + num(consolidado.getTotalHuevosRotos())
                + "; gallinas vivas: " + consolidado.getTotalGallinasVivas()
                + "; muertas: " + consolidado.getTotalGallinasMuertas()
                + "; enfermas: " + consolidado.getTotalGallinasEnfermas()
                + "; pienso: " + num(consolidado.getTotalPiensoKg()) + " kg. "
                + "Carne: " + num(consolidado.getTotalCarne()) + " t. "
                + "Incluidos: nave " + consolidado.getPartesNaveIncluidos()
                + ", unidad " + consolidado.getPartesUnidadIncluidos()
                + ", carne " + consolidado.getRegistrosCarneIncluidos() + ". "
                + "Excluidos: nave " + consolidado.getPartesNaveExcluidos()
                + ", unidad " + consolidado.getPartesUnidadExcluidos() + ". "
                + "Cobertura observacional: " + consolidado.getNavesSinParte() + " naves y "
                + consolidado.getUnidadesSinParte() + " unidades sin parte en el período. "
                + "Aclaraciones pendientes al guardar: " + consolidado.getAclaracionesPendientes()
                + "</div></html>";
        resumenPeriodo.setText(resumen);
    }

    private void configurarSeleccionOrigen(DataTablePanel panel, Map<Integer, UUID> ids, String tipo) {
        panel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedOrigenTipo = tipo;
                selectedOrigenId = ids.get(panel.getTable().getSelectedRow());
            }
        });
    }

    private void solicitarAclaracion() {
        if (selectedConsolidadoId == null || selectedOrigenId == null || selectedOrigenTipo == null) {
            MessageHelper.showValidation(this, "Seleccione un registro de huevos, carne o unidad asociada.");
            return;
        }
        String motivo = JOptionPane.showInputDialog(this, "Motivo de la aclaración:", "Solicitar aclaración",
                JOptionPane.QUESTION_MESSAGE);
        if (motivo == null || motivo.isBlank()) return;
        try {
            consolidadoService.solicitarAclaracion(selectedOrigenTipo, selectedOrigenId, motivo);
            MessageHelper.showInfo(this, "Aclaración registrada. El consolidado no podrá actualizarse hasta resolverla.");
            consolidadoService.buscarPorId(selectedConsolidadoId).ifPresent(this::cargarDetallePeriodo);
        } catch (ValidacionException | IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la aclaración: " + ex.getMessage());
        }
    }

    private void resolverAclaracion() {
        if (selectedAclaracionId == null) {
            MessageHelper.showValidation(this, "Seleccione una aclaración en la pestaña Aclaraciones.");
            return;
        }
        String observacion = JOptionPane.showInputDialog(this, "Observación de resolución:", "Resolver aclaración",
                JOptionPane.QUESTION_MESSAGE);
        if (observacion == null || observacion.isBlank()) return;
        try {
            consolidadoService.resolverAclaracion(selectedAclaracionId, observacion);
            MessageHelper.showInfo(this, "Aclaración resuelta correctamente.");
            consolidadoService.buscarPorId(selectedConsolidadoId).ifPresent(this::cargarDetallePeriodo);
        } catch (ValidacionException | IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo resolver la aclaración: " + ex.getMessage());
        }
    }

    private void limpiarDetalle() {
        huevosTablePanel.getTableModel().setRowCount(0);
        carneTablePanel.getTableModel().setRowCount(0);
        unidadesTablePanel.getTableModel().setRowCount(0);
        aclaracionesTablePanel.getTableModel().setRowCount(0);
        huevosRowIds.clear();
        carneRowIds.clear();
        unidadesRowIds.clear();
        aclaracionesRowIds.clear();
        selectedOrigenTipo = null;
        selectedOrigenId = null;
        selectedAclaracionId = null;
        resumenPeriodo.setText(" ");
    }

    private void recargarUEBs() {
        String prevForm = (String) uebFormCombo.getSelectedItem();
        String prevFilter = (String) uebFilterCombo.getSelectedItem();
        uebLabelToId.clear();
        uebFormCombo.removeAllItems();
        uebFilterCombo.removeAllItems();
        uebFilterCombo.addItem(TODAS);
        for (UEB ueb : uebService.listarTodas()) {
            String label = safe(ueb.getCodigo()) + " - " + safe(ueb.getNombre());
            uebLabelToId.put(label, ueb.getIdUEB());
            uebFormCombo.addItem(label);
            uebFilterCombo.addItem(label);
        }
        if (prevForm != null && uebLabelToId.containsKey(prevForm)) uebFormCombo.setSelectedItem(prevForm);
        if (prevFilter != null && (TODAS.equals(prevFilter) || uebLabelToId.containsKey(prevFilter))) {
            uebFilterCombo.setSelectedItem(prevFilter);
        } else {
            uebFilterCombo.setSelectedItem(TODAS);
        }
    }

    private UUID getSelectedUebId(JComboBox<String> combo) {
        String seleccion = (String) combo.getSelectedItem();
        return seleccion == null || TODAS.equals(seleccion) ? null : uebLabelToId.get(seleccion);
    }

    private void seleccionarUebEnCombo(JComboBox<String> combo, UUID idUEB) {
        for (Map.Entry<String, UUID> entrada : uebLabelToId.entrySet()) {
            if (idUEB.equals(entrada.getValue())) {
                combo.setSelectedItem(entrada.getKey());
                return;
            }
        }
    }

    private String safe(String valor) { return valor == null ? "" : valor; }
    private String emptyToNull(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private String toText(Object valor) { return valor == null ? "" : String.valueOf(valor); }
    private String num(BigDecimal valor) { return valor == null ? "0" : valor.stripTrailingZeros().toPlainString(); }

    private LocalDate parseFecha(String texto, String campo) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("La " + campo + " debe tener formato AAAA-MM-DD.");
        }
    }
}
