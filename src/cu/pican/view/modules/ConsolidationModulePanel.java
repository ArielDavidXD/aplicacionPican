package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.service.ConsolidadoUEBService;
import cu.pican.service.NaveService;
import cu.pican.service.ParteNaveService;
import cu.pican.service.ProduccionCarneService;
import cu.pican.service.UEBService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Módulo de consolidación de producción por UEB y período (integrado con servicios en memoria).
 * Permite registrar/consultar consolidaciones e identificar partes comprendidos en el período
 * sin modificar ni eliminar registros originales. No incorpora datos de unidades asociadas (pendientes).
 */
public class ConsolidationModulePanel extends JPanel {

    private static final String TODAS = "Todas";

    private final ConsolidadoUEBService consolidadoService;
    private final UEBService uebService;
    private final ParteNaveService parteNaveService;
    private final ProduccionCarneService produccionCarneService;
    private final NaveService naveService;

    private final DataTablePanel consolidadoTablePanel;
    private final DataTablePanel huevosTablePanel;
    private final DataTablePanel carneTablePanel;

    private final List<ConsolidadoUEB> currentRows = new ArrayList<>();

    // Formulario
    private final JComboBox<String> uebFormCombo = new JComboBox<>();
    private final JTextField fechaInicioField = new JTextField(10);
    private final JTextField fechaFinField = new JTextField(10);
    private final JTextField estadoField = new JTextField(16);
    private final JTextField motivoParcialField = new JTextField(24);

    // Filtros
    private final JComboBox<String> uebFilterCombo = new JComboBox<>();
    private final JTextField filtroInicioField = new JTextField(10);
    private final JTextField filtroFinField = new JTextField(10);

    private final Map<String, UUID> uebLabelToId = new HashMap<>();
    private UUID selectedConsolidadoId;

    public ConsolidationModulePanel(AppContext context) {
        this.consolidadoService = context.getConsolidadoUEBService();
        this.uebService = context.getUebService();
        this.parteNaveService = context.getParteNaveService();
        this.produccionCarneService = context.getProduccionCarneService();
        this.naveService = context.getNaveService();

        setLayout(new BorderLayout(0, 10));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Consolidación de producción",
                "Registro y consulta de consolidaciones por UEB y período. Identifica partes comprendidos sin modificar originales. " +
                        "No se incorporan datos de unidades asociadas (formulario/relación pendiente)."), BorderLayout.NORTH);

        String[] consCols = {"UEB", "Fecha inicio", "Fecha fin", "Estado", "Motivo de parcialidad"};
        consolidadoTablePanel = new DataTablePanel("Consolidaciones registradas", consCols, new Object[0][0]);
        consolidadoTablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        consolidadoTablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSeleccion();
            }
        });

        String[] huevosCols = {"Fecha", "Nave", "Huevos producidos", "Huevos rotos", "Gallinas vivas", "Muertas", "Enfermas", "Pienso (kg)", "Observaciones"};
        huevosTablePanel = new DataTablePanel("Partes de huevos de naves comprendidos en el período (UEB seleccionada)", huevosCols, new Object[0][0]);
        huevosTablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        String[] carneCols = {"Fecha", "UEB", "Cantidad (toneladas)", "Observaciones"};
        carneTablePanel = new DataTablePanel("Registros de producción de carne comprendidos en el período (UEB seleccionada)", carneCols, new Object[0][0]);
        carneTablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel details = new JPanel(new GridLayout(1, 2, 8, 0));
        details.setOpaque(false);
        details.add(huevosTablePanel);
        details.add(carneTablePanel);

        JPanel detailsWrap = new JPanel(new BorderLayout(0, 4));
        detailsWrap.setOpaque(false);
        JLabel notaUA = new JLabel("Nota: no se suman unidades incompatibles ni se combinan huevos con toneladas. " +
                "Pendiente: partes de unidades asociadas (sin datos incorporados).");
        notaUA.setFont(UITheme.SMALL_FONT);
        notaUA.setForeground(UITheme.MUTED_TEXT);
        detailsWrap.add(notaUA, BorderLayout.NORTH);
        detailsWrap.add(details, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, consolidadoTablePanel, detailsWrap);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        split.setOpaque(false);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(buildTopPanel(), BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(buildActionsPanel(), BorderLayout.SOUTH);

        recargarUEBs();
        refrescarTabla(consolidadoService.listarTodos());
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

        c.gridx = 0; c.gridy = 2; form.add(new JLabel("Estado:"), c);
        c.gridx = 1; form.add(estadoField, c);
        c.gridx = 2; form.add(new JLabel("Motivo parcial (si corresponde):"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0; form.add(motivoParcialField, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 4; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel nota = new JLabel("El estado y la autorización no son definitivos; no se asume obligatoriedad de incluir todos los partes del período.");
        nota.setFont(UITheme.SMALL_FONT);
        nota.setForeground(UITheme.MUTED_TEXT);
        form.add(nota, c);

        return form;
    }

    private JPanel buildFilterPanel() {
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filter.setOpaque(false);
        filter.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Filtros de consolidaciones"));
        filter.add(new JLabel("UEB:"));
        filter.add(uebFilterCombo);
        filter.add(new JLabel("Período desde:"));
        filter.add(filtroInicioField);
        filter.add(new JLabel("hasta:"));
        filter.add(filtroFinField);

        AppButton aplicarBtn = new AppButton("Aplicar filtros");
        AppButton todasBtn = new AppButton("Mostrar todas");
        AppButton recargarBtn = new AppButton("Recargar UEB");
        aplicarBtn.addActionListener(e -> aplicarFiltros());
        todasBtn.addActionListener(e -> mostrarTodas());
        recargarBtn.addActionListener(e -> { recargarUEBs(); MessageHelper.showInfo(this, "Listado de UEB actualizado."); });
        filter.add(aplicarBtn);
        filter.add(todasBtn);
        filter.add(recargarBtn);
        return filter;
    }

    private JPanel buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);
        AppButton nuevoBtn = new AppButton("Nuevo");
        AppButton registrarBtn = new AppButton("Registrar");
        AppButton actualizarBtn = new AppButton("Actualizar");
        AppButton consultarBtn = new AppButton("Consultar selección");
        nuevoBtn.addActionListener(e -> limpiarFormulario());
        registrarBtn.addActionListener(e -> registrar());
        actualizarBtn.addActionListener(e -> actualizar());
        consultarBtn.addActionListener(e -> consultarSeleccion());
        actions.add(nuevoBtn);
        actions.add(registrarBtn);
        actions.add(actualizarBtn);
        actions.add(consultarBtn);
        return actions;
    }

    private void registrar() {
        try {
            ConsolidadoUEB c = buildFromForm(UUID.randomUUID());
            consolidadoService.registrar(c);
            MessageHelper.showInfo(this, "Consolidación registrada correctamente.");
            limpiarFormulario();
            refrescarTabla(consolidadoService.listarTodos());
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la consolidación: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedConsolidadoId == null) {
            MessageHelper.showValidation(this, "Seleccione una consolidación de la tabla para actualizar.");
            return;
        }
        try {
            ConsolidadoUEB c = buildFromForm(selectedConsolidadoId);
            consolidadoService.actualizar(c);
            MessageHelper.showInfo(this, "Consolidación actualizada correctamente (UUID conservado).");
            refrescarTabla(consolidadoService.listarTodos());
            // recargar detalle del consolidado actualizado
            consolidadoService.buscarPorId(selectedConsolidadoId).ifPresent(this::cargarDetallePeriodo);
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar la consolidación: " + ex.getMessage());
        }
    }

    private ConsolidadoUEB buildFromForm(UUID id) {
        ConsolidadoUEB c = new ConsolidadoUEB();
        c.setIdConsolidado(id);
        c.setIdUEB(getSelectedUebId(uebFormCombo));
        c.setFechaInicio(parseFecha(fechaInicioField.getText(), "fecha de inicio"));
        c.setFechaFin(parseFecha(fechaFinField.getText(), "fecha de fin"));
        c.setEstado(emptyToNull(estadoField.getText()));
        c.setMotivoParcial(emptyToNull(motivoParcialField.getText()));
        c.setCreadoEn(LocalDateTime.now());
        c.setVersion(1L);
        return c;
    }

    private void consultarSeleccion() {
        if (selectedConsolidadoId == null) {
            MessageHelper.showValidation(this, "Seleccione una consolidación para consultar sus datos.");
            return;
        }
        consolidadoService.buscarPorId(selectedConsolidadoId).ifPresentOrElse(c -> {
            cargarEnFormulario(c);
            cargarDetallePeriodo(c);
            MessageHelper.showInfo(this, "Datos de la consolidación y partes del período cargados (consulta).");
        }, () -> MessageHelper.showValidation(this, "La consolidación seleccionada ya no existe."));
    }

    private void cargarSeleccion() {
        int row = consolidadoTablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= currentRows.size()) {
            return;
        }
        ConsolidadoUEB c = currentRows.get(row);
        if (c != null) {
            consolidadoService.buscarPorId(c.getIdConsolidado()).ifPresent(found -> {
                cargarEnFormulario(found);
                cargarDetallePeriodo(found);
            });
        }
    }

    private void cargarEnFormulario(ConsolidadoUEB c) {
        selectedConsolidadoId = c.getIdConsolidado();
        seleccionarUebEnCombo(uebFormCombo, c.getIdUEB());
        fechaInicioField.setText(c.getFechaInicio() != null ? c.getFechaInicio().toString() : "");
        fechaFinField.setText(c.getFechaFin() != null ? c.getFechaFin().toString() : "");
        estadoField.setText(c.getEstado() != null ? c.getEstado() : "");
        motivoParcialField.setText(c.getMotivoParcial() != null ? c.getMotivoParcial() : "");
    }

    private void limpiarFormulario() {
        selectedConsolidadoId = null;
        if (uebFormCombo.getItemCount() > 0) uebFormCombo.setSelectedIndex(0);
        fechaInicioField.setText("");
        fechaFinField.setText("");
        estadoField.setText("");
        motivoParcialField.setText("");
        consolidadoTablePanel.getTable().clearSelection();
        limpiarDetalle();
    }

    private void aplicarFiltros() {
        UUID uebId = getSelectedUebId(uebFilterCombo);
        LocalDate fInicio;
        LocalDate fFin;
        try {
            fInicio = parseFecha(filtroInicioField.getText(), "fecha desde (filtro)");
            fFin = parseFecha(filtroFinField.getText(), "fecha hasta (filtro)");
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
            return;
        }
        if ((fInicio == null) != (fFin == null)) {
            // permitir filtro parcial de período? aceptar: si solo uno, se interpreta como límite abierto
        }
        if (fInicio != null && fFin != null && fFin.isBefore(fInicio)) {
            MessageHelper.showValidation(this, "En el filtro, la fecha 'hasta' no puede ser anterior a la fecha 'desde'.");
            return;
        }

        List<ConsolidadoUEB> base = (uebId != null) ? consolidadoService.buscarPorUEB(uebId) : consolidadoService.listarTodos();
        List<ConsolidadoUEB> filtrados = new ArrayList<>();
        for (ConsolidadoUEB c : base) {
            boolean ok = true;
            // solapamiento de períodos si se indicó algún límite
            if (fInicio != null && c.getFechaFin() != null && c.getFechaFin().isBefore(fInicio)) ok = false;
            if (fFin != null && c.getFechaInicio() != null && c.getFechaInicio().isAfter(fFin)) ok = false;
            if (ok) filtrados.add(c);
        }
        refrescarTabla(filtrados);
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
        for (ConsolidadoUEB c : consolidados) {
            currentRows.add(c);
            String nombreUeb = "(UEB no encontrada)";
            if (c.getIdUEB() != null) {
                UEB u = uebService.buscarPorId(c.getIdUEB()).orElse(null);
                if (u != null) nombreUeb = safe(u.getNombre());
            }
            model.addRow(new Object[]{
                    nombreUeb,
                    c.getFechaInicio() != null ? c.getFechaInicio().toString() : "",
                    c.getFechaFin() != null ? c.getFechaFin().toString() : "",
                    safe(c.getEstado()),
                    safe(c.getMotivoParcial())
            });
        }
    }

    private void cargarDetallePeriodo(ConsolidadoUEB c) {
        limpiarDetalle();
        if (c == null || c.getIdUEB() == null || c.getFechaInicio() == null || c.getFechaFin() == null) {
            return;
        }
        LocalDate inicio = c.getFechaInicio();
        LocalDate fin = c.getFechaFin();

        // Partes de huevos de naves de la UEB en el período (servicios existentes)
        DefaultTableModel huevosModel = huevosTablePanel.getTableModel();
        List<Nave> navesUeb = naveService.buscarPorUEB(c.getIdUEB());
        for (Nave n : navesUeb) {
            List<ParteNave> partes = parteNaveService.buscarPorNave(n.getIdNave());
            for (ParteNave p : partes) {
                if (enPeriodo(p.getFechaProduccion(), inicio, fin)) {
                    huevosModel.addRow(new Object[]{
                            p.getFechaProduccion() != null ? p.getFechaProduccion().toString() : "",
                            safe(n.getNombre()),
                            toText(p.getHuevosProducidos()),
                            toText(p.getHuevosRotos()),
                            toText(p.getGallinasVivas()),
                            toText(p.getGallinasMuertas()),
                            toText(p.getGallinasEnfermas()),
                            toText(p.getPiensoKg()),
                            safe(p.getObservaciones())
                    });
                }
            }
        }

        // Producción de carne de la UEB en el período
        DefaultTableModel carneModel = carneTablePanel.getTableModel();
        String nombreUeb = safe(uebService.buscarPorId(c.getIdUEB()).map(UEB::getNombre).orElse("(UEB no encontrada)"));
        List<ProduccionCarne> carnes = produccionCarneService.buscarPorUEB(c.getIdUEB());
        for (ProduccionCarne pc : carnes) {
            if (enPeriodo(pc.getFechaProduccion(), inicio, fin)) {
                carneModel.addRow(new Object[]{
                        pc.getFechaProduccion() != null ? pc.getFechaProduccion().toString() : "",
                        nombreUeb,
                        pc.getCantidadToneladas() != null ? pc.getCantidadToneladas().toPlainString() : "",
                        safe(pc.getObservaciones())
                });
            }
        }
    }

    private boolean enPeriodo(LocalDate fecha, LocalDate inicio, LocalDate fin) {
        if (fecha == null) return false;
        return (fecha.isEqual(inicio) || fecha.isAfter(inicio)) && (fecha.isEqual(fin) || fecha.isBefore(fin));
    }

    private void limpiarDetalle() {
        huevosTablePanel.getTableModel().setRowCount(0);
        carneTablePanel.getTableModel().setRowCount(0);
    }

    private void recargarUEBs() {
        String prevForm = (String) uebFormCombo.getSelectedItem();
        String prevFilter = (String) uebFilterCombo.getSelectedItem();
        uebLabelToId.clear();
        uebFormCombo.removeAllItems();
        uebFilterCombo.removeAllItems();
        uebFilterCombo.addItem(TODAS);
        for (UEB u : uebService.listarTodas()) {
            String label = safe(u.getCodigo()) + " - " + safe(u.getNombre());
            uebLabelToId.put(label, u.getIdUEB());
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
        String sel = (String) combo.getSelectedItem();
        if (sel == null || TODAS.equals(sel)) return null;
        return uebLabelToId.get(sel);
    }

    private void seleccionarUebEnCombo(JComboBox<String> combo, UUID idUEB) {
        if (idUEB == null) return;
        for (Map.Entry<String, UUID> e : uebLabelToId.entrySet()) {
            if (idUEB.equals(e.getValue())) {
                combo.setSelectedItem(e.getKey());
                return;
            }
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String emptyToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s.trim(); }
    private String toText(Object v) { return v == null ? "" : String.valueOf(v); }

    private LocalDate parseFecha(String text, String campo) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("La " + campo + " debe tener formato AAAA-MM-DD.");
        }
    }
}
