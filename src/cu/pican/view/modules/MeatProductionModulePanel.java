package cu.pican.view.modules;

import cu.pican.controller.app.AppContext;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.controller.service.ProduccionCarneService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
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

/**
 * Módulo Producción de carne integrado con servicios en memoria.
 * Cantidad en toneladas, sin separación especializada/alternativa y sin unicidad UEB+fecha.
 */
public class MeatProductionModulePanel extends JPanel {

    private static final String TODAS = "Todas";

    private final ProduccionCarneService produccionCarneService;
    private final UEBService uebService;

    private final DataTablePanel tablePanel;
    private final List<ProduccionCarne> currentRows = new ArrayList<>();

    // Formulario
    private final JComboBox<String> uebFormCombo = new JComboBox<>();
    private final JTextField fechaField = new JTextField(10);
    private final JTextField cantidadField = new JTextField(10);
    private final JTextArea observacionesArea = new JTextArea(3, 30);

    // Filtros
    private final JComboBox<String> uebFilterCombo = new JComboBox<>();
    private final JTextField fechaFilterField = new JTextField(10);

    private final Map<String, UUID> uebLabelToId = new HashMap<>();

    private UUID selectedProduccionId;

    public MeatProductionModulePanel(AppContext context) {
        this.produccionCarneService = context.getProduccionCarneService();
        this.uebService = context.getUebService();

        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Producción de carne",
                "Registro, consulta y edición de producción de carne por UEB (toneladas, datos en memoria)."), BorderLayout.NORTH);

        String[] cols = {"Fecha de producción", "UEB", "Cantidad (toneladas)", "Observaciones"};
        tablePanel = new DataTablePanel("Producciones registradas", cols, new Object[0][0]);
        tablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSeleccion();
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(buildTopPanel(), BorderLayout.NORTH);
        center.add(tablePanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(buildActionsPanel(), BorderLayout.SOUTH);

        recargarUEBs();
        refrescarTabla(produccionCarneService.listarTodas());
    }

    private JPanel buildTopPanel() {
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(buildFormPanel(), BorderLayout.CENTER);
        top.add(buildFilterPanel(), BorderLayout.SOUTH);
        return top;
    }

    private JPanel buildFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Registro / edición de producción de carne"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("UEB:"), c);
        c.gridx = 1; c.gridwidth = 2;
        form.add(uebFormCombo, c);

        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Fecha (AAAA-MM-DD):"), c);
        c.gridx = 1;
        form.add(fechaField, c);
        c.gridx = 2;
        form.add(new JLabel("Cantidad (toneladas):"), c);
        c.gridx = 3;
        form.add(cantidadField, c);

        c.gridx = 0; c.gridy = 2; c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Observaciones:"), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        observacionesArea.setLineWrap(true);
        observacionesArea.setWrapStyleWord(true);
        observacionesArea.setFont(UITheme.NORMAL_FONT);
        JScrollPane obsScroll = new JScrollPane(observacionesArea);
        obsScroll.setPreferredSize(new Dimension(420, 55));
        form.add(obsScroll, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 4; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        c.anchor = GridBagConstraints.WEST;
        JLabel nota = new JLabel("Cantidad vacía = dato no informado (no se convierte a 0). 0 se permite si se introduce explícitamente.");
        nota.setFont(UITheme.SMALL_FONT);
        nota.setForeground(UITheme.MUTED_TEXT);
        form.add(nota, c);

        return form;
    }

    private JPanel buildFilterPanel() {
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filter.setOpaque(false);
        filter.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Consulta y filtros"));
        filter.add(new JLabel("UEB:"));
        filter.add(uebFilterCombo);
        filter.add(new JLabel("Fecha (AAAA-MM-DD):"));
        filter.add(fechaFilterField);

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
            ProduccionCarne prod = buildFromForm(UUID.randomUUID());
            produccionCarneService.registrar(prod);
            MessageHelper.showInfo(this, "Producción de carne registrada correctamente.");
            limpiarFormulario();
            refrescarTabla(produccionCarneService.listarTodas());
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la producción: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedProduccionId == null) {
            MessageHelper.showValidation(this, "Seleccione una producción de la tabla para actualizar.");
            return;
        }
        try {
            ProduccionCarne prod = buildFromForm(selectedProduccionId);
            produccionCarneService.actualizar(prod);
            MessageHelper.showInfo(this, "Producción de carne actualizada correctamente (UUID conservado).");
            refrescarTabla(produccionCarneService.listarTodas());
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar la producción: " + ex.getMessage());
        }
    }

    private ProduccionCarne buildFromForm(UUID idProduccion) {
        ProduccionCarne prod = new ProduccionCarne();
        prod.setIdProduccion(idProduccion);
        prod.setIdUEB(getSelectedUebId(uebFormCombo));
        prod.setFechaProduccion(parseFecha(fechaField.getText(), "fecha de producción"));
        prod.setCantidadToneladas(parseBigDecimal(cantidadField.getText(), "Cantidad (toneladas)"));
        String obs = observacionesArea.getText();
        prod.setObservaciones(obs != null && !obs.trim().isEmpty() ? obs.trim() : null);
        prod.setCreadoEn(LocalDateTime.now());
        prod.setVersion(1L);
        // idUsuario no se gestiona en esta etapa de UI.
        return prod;
    }

    private void consultarSeleccion() {
        if (selectedProduccionId == null) {
            MessageHelper.showValidation(this, "Seleccione una producción para consultar sus datos.");
            return;
        }
        produccionCarneService.buscarPorId(selectedProduccionId).ifPresentOrElse(p -> {
            cargarEnFormulario(p);
            MessageHelper.showInfo(this, "Datos de la producción cargados en el formulario (consulta).");
        }, () -> MessageHelper.showValidation(this, "La producción seleccionada ya no existe."));
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= currentRows.size()) {
            return;
        }
        ProduccionCarne prod = currentRows.get(row);
        if (prod != null) {
            produccionCarneService.buscarPorId(prod.getIdProduccion()).ifPresent(this::cargarEnFormulario);
        }
    }

    private void cargarEnFormulario(ProduccionCarne prod) {
        selectedProduccionId = prod.getIdProduccion();
        seleccionarUebEnCombo(uebFormCombo, prod.getIdUEB());
        fechaField.setText(prod.getFechaProduccion() != null ? prod.getFechaProduccion().toString() : "");
        cantidadField.setText(prod.getCantidadToneladas() != null ? prod.getCantidadToneladas().toPlainString() : "");
        observacionesArea.setText(prod.getObservaciones() != null ? prod.getObservaciones() : "");
    }

    private void limpiarFormulario() {
        selectedProduccionId = null;
        if (uebFormCombo.getItemCount() > 0) uebFormCombo.setSelectedIndex(0);
        fechaField.setText("");
        cantidadField.setText("");
        observacionesArea.setText("");
        tablePanel.getTable().clearSelection();
    }

    private void aplicarFiltros() {
        UUID uebId = getSelectedUebId(uebFilterCombo);
        LocalDate fecha = null;
        String fechaTxt = fechaFilterField.getText();
        if (fechaTxt != null && !fechaTxt.trim().isEmpty()) {
            try {
                fecha = LocalDate.parse(fechaTxt.trim());
            } catch (DateTimeParseException ex) {
                MessageHelper.showValidation(this, "La fecha de filtro debe tener formato AAAA-MM-DD.");
                return;
            }
        }

        List<ProduccionCarne> base;
        if (uebId != null && fecha != null) {
            // Combinar filtros reutilizando servicios: buscar por UEB y filtrar por fecha en memoria
            base = new ArrayList<>();
            for (ProduccionCarne p : produccionCarneService.buscarPorUEB(uebId)) {
                if (fecha.equals(p.getFechaProduccion())) {
                    base.add(p);
                }
            }
        } else if (uebId != null) {
            base = produccionCarneService.buscarPorUEB(uebId);
        } else if (fecha != null) {
            base = produccionCarneService.buscarPorFecha(fecha);
        } else {
            base = produccionCarneService.listarTodas();
        }
        refrescarTabla(base);
    }

    private void mostrarTodas() {
        uebFilterCombo.setSelectedItem(TODAS);
        fechaFilterField.setText("");
        refrescarTabla(produccionCarneService.listarTodas());
    }

    private void refrescarTabla(List<ProduccionCarne> producciones) {
        currentRows.clear();
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        if (producciones == null) return;
        for (ProduccionCarne p : producciones) {
            currentRows.add(p);
            String nombreUeb = "(UEB no encontrada)";
            if (p.getIdUEB() != null) {
                UEB ueb = uebService.buscarPorId(p.getIdUEB()).orElse(null);
                if (ueb != null) nombreUeb = safe(ueb.getNombre());
            }
            model.addRow(new Object[]{
                    p.getFechaProduccion() != null ? p.getFechaProduccion().toString() : "",
                    nombreUeb,
                    p.getCantidadToneladas() != null ? p.getCantidadToneladas().toPlainString() : "",
                    p.getObservaciones() != null ? p.getObservaciones() : ""
            });
        }
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

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private LocalDate parseFecha(String text, String campo) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("La " + campo + " debe tener formato AAAA-MM-DD.");
        }
    }

    private BigDecimal parseBigDecimal(String text, String campo) {
        if (text == null || text.trim().isEmpty()) return null; // no informado
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El campo '" + campo + "' debe ser un número válido (use punto decimal).");
        }
    }
}
