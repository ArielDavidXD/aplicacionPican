package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.UEB;
import cu.pican.service.NaveService;
import cu.pican.service.ParteNaveService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Módulo Producción de huevos (naves) integrado con servicios en memoria.
 * Los partes de unidades asociadas permanecen pendientes hasta confirmar el formulario real
 * (no se inventan campos productivos ni relaciones).
 */
public class EggProductionModulePanel extends JPanel {

    private static final String TODOS = "Todos";
    private static final String TODAS = "Todas";

    private final ParteNaveService parteNaveService;
    private final NaveService naveService;
    private final UEBService uebService;

    private final DataTablePanel tablePanel;
    private final List<ParteNave> currentRows = new ArrayList<>();

    // Formulario
    private final JComboBox<String> uebFormCombo = new JComboBox<>();
    private final JComboBox<String> naveFormCombo = new JComboBox<>();
    private final JTextField fechaField = new JTextField(10);
    private final JTextField gallinasVivasField = new JTextField(8);
    private final JTextField gallinasMuertasField = new JTextField(8);
    private final JTextField gallinasEnfermasField = new JTextField(8);
    private final JTextField huevosProducidosField = new JTextField(8);
    private final JTextField huevosRotosField = new JTextField(8);
    private final JTextField piensoKgField = new JTextField(8);
    private final JTextArea observacionesArea = new JTextArea(3, 30);

    // Filtros
    private final JComboBox<String> uebFilterCombo = new JComboBox<>();
    private final JComboBox<String> naveFilterCombo = new JComboBox<>();
    private final JTextField fechaFilterField = new JTextField(10);

    private final Map<String, UUID> uebLabelToId = new HashMap<>();
    private final Map<String, UUID> naveFormLabelToId = new HashMap<>();
    private final Map<String, UUID> naveFilterLabelToId = new HashMap<>();

    private UUID selectedParteId;

    public EggProductionModulePanel(AppContext context) {
        this.parteNaveService = context.getParteNaveService();
        this.naveService = context.getNaveService();
        this.uebService = context.getUebService();

        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Producción de huevos (naves)",
                "Registro, consulta y edición de partes diarios de naves (datos en memoria). " +
                        "Pendiente: partes de unidades asociadas según formulario real (sin campos inventados)."), BorderLayout.NORTH);

        String[] cols = {"Fecha de producción", "UEB", "Nave", "Gallinas vivas", "Gallinas muertas",
                "Gallinas enfermas", "Huevos producidos", "Huevos rotos", "Pienso (kg)"};
        tablePanel = new DataTablePanel("Partes de producción registrados", cols, new Object[0][0]);
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

        uebFormCombo.addActionListener(e -> onUebFormChanged());
        uebFilterCombo.addActionListener(e -> onUebFilterChanged());

        recargarCombos();
        refrescarTabla(parteNaveService.listarTodos());
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
                "Registro / edición de parte diario de nave"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        c.gridx = 0; c.gridy = row; form.add(new JLabel("UEB:"), c);
        c.gridx = 1; c.gridwidth = 2; form.add(uebFormCombo, c);
        c.gridwidth = 1; c.gridx = 3; form.add(new JLabel("Nave:"), c);
        c.gridx = 4; c.gridwidth = 2; form.add(naveFormCombo, c);

        row++;
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = row; form.add(new JLabel("Fecha (AAAA-MM-DD):"), c);
        c.gridx = 1; form.add(fechaField, c);
        c.gridx = 3; form.add(new JLabel("Pienso (kg):"), c);
        c.gridx = 4; form.add(piensoKgField, c);

        row++;
        c.gridx = 0; c.gridy = row; form.add(new JLabel("Gallinas vivas:"), c);
        c.gridx = 1; form.add(gallinasVivasField, c);
        c.gridx = 2; form.add(new JLabel("Muertas:"), c);
        c.gridx = 3; form.add(gallinasMuertasField, c);
        c.gridx = 4; form.add(new JLabel("Enfermas:"), c);
        c.gridx = 5; form.add(gallinasEnfermasField, c);

        row++;
        c.gridx = 0; c.gridy = row; form.add(new JLabel("Huevos producidos:"), c);
        c.gridx = 1; form.add(huevosProducidosField, c);
        c.gridx = 2; form.add(new JLabel("Huevos rotos:"), c);
        c.gridx = 3; form.add(huevosRotosField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Observaciones:"), c);
        c.gridx = 1; c.gridwidth = 5; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        observacionesArea.setLineWrap(true);
        observacionesArea.setWrapStyleWord(true);
        observacionesArea.setFont(UITheme.NORMAL_FONT);
        JScrollPane obsScroll = new JScrollPane(observacionesArea);
        obsScroll.setPreferredSize(new Dimension(400, 55));
        form.add(obsScroll, c);

        row++;
        c.gridx = 0; c.gridy = row; c.gridwidth = 6; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        c.anchor = GridBagConstraints.WEST;
        JLabel nota = new JLabel("Campos numéricos vacíos = dato no informado (no se convierten a 0).");
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
        filter.add(new JLabel("Nave:"));
        filter.add(naveFilterCombo);
        filter.add(new JLabel("Fecha (AAAA-MM-DD):"));
        filter.add(fechaFilterField);

        AppButton aplicarBtn = new AppButton("Aplicar filtros");
        AppButton todosBtn = new AppButton("Mostrar todos");
        AppButton recargarBtn = new AppButton("Recargar listas");
        aplicarBtn.addActionListener(e -> aplicarFiltros());
        todosBtn.addActionListener(e -> mostrarTodos());
        recargarBtn.addActionListener(e -> { recargarCombos(); MessageHelper.showInfo(this, "Listas de UEB y naves actualizadas."); });
        filter.add(aplicarBtn);
        filter.add(todosBtn);
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
            ParteNave parte = buildParteFromForm(UUID.randomUUID());
            parteNaveService.registrar(parte);
            MessageHelper.showInfo(this, "Parte de producción registrado correctamente.");
            limpiarFormulario();
            refrescarTabla(parteNaveService.listarTodos());
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar el parte: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedParteId == null) {
            MessageHelper.showValidation(this, "Seleccione un parte de la tabla para actualizar.");
            return;
        }
        try {
            ParteNave parte = buildParteFromForm(selectedParteId);
            parteNaveService.actualizar(parte);
            MessageHelper.showInfo(this, "Parte de producción actualizado correctamente (UUID conservado).");
            refrescarTabla(parteNaveService.listarTodos());
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar el parte: " + ex.getMessage());
        }
    }

    private ParteNave buildParteFromForm(UUID idParte) {
        ParteNave parte = new ParteNave();
        parte.setIdParte(idParte);
        parte.setIdNave(getSelectedId(naveFormCombo, naveFormLabelToId));
        parte.setFechaProduccion(parseFecha(fechaField.getText(), "fecha de producción"));
        parte.setGallinasVivas(parseInteger(gallinasVivasField.getText(), "Gallinas vivas"));
        parte.setGallinasMuertas(parseInteger(gallinasMuertasField.getText(), "Gallinas muertas"));
        parte.setGallinasEnfermas(parseInteger(gallinasEnfermasField.getText(), "Gallinas enfermas"));
        parte.setHuevosProducidos(parseInteger(huevosProducidosField.getText(), "Huevos producidos"));
        parte.setHuevosRotos(parseInteger(huevosRotosField.getText(), "Huevos rotos"));
        parte.setPiensoKg(parseBigDecimal(piensoKgField.getText(), "Pienso (kg)"));
        String obs = observacionesArea.getText();
        parte.setObservaciones(obs != null && !obs.trim().isEmpty() ? obs.trim() : null);
        parte.setCreadoEn(LocalDateTime.now());
        parte.setVersion(1L);
        // idUsuario y estado no se gestionan en esta etapa de UI.
        return parte;
    }

    private void consultarSeleccion() {
        if (selectedParteId == null) {
            MessageHelper.showValidation(this, "Seleccione un parte en la tabla para consultar sus datos.");
            return;
        }
        parteNaveService.buscarPorId(selectedParteId).ifPresentOrElse(p -> {
            cargarEnFormulario(p);
            MessageHelper.showInfo(this, "Datos del parte cargados en el formulario (consulta).");
        }, () -> MessageHelper.showValidation(this, "El parte seleccionado ya no existe."));
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= currentRows.size()) {
            return;
        }
        ParteNave parte = currentRows.get(row);
        if (parte != null) {
            parteNaveService.buscarPorId(parte.getIdParte()).ifPresent(this::cargarEnFormulario);
        }
    }

    private void cargarEnFormulario(ParteNave parte) {
        selectedParteId = parte.getIdParte();
        // Seleccionar UEB según la nave del parte
        Nave nave = parte.getIdNave() != null ? naveService.buscarPorId(parte.getIdNave()).orElse(null) : null;
        if (nave != null) {
            seleccionarEnCombo(uebFormCombo, uebLabelToId, nave.getIdUEB());
            cargarNavesFormParaUeb(nave.getIdUEB());
            seleccionarEnCombo(naveFormCombo, naveFormLabelToId, nave.getIdNave());
        } else {
            if (uebFormCombo.getItemCount() > 0) uebFormCombo.setSelectedIndex(0);
            cargarNavesFormParaUeb(getSelectedId(uebFormCombo, uebLabelToId));
        }
        fechaField.setText(parte.getFechaProduccion() != null ? parte.getFechaProduccion().toString() : "");
        gallinasVivasField.setText(toText(parte.getGallinasVivas()));
        gallinasMuertasField.setText(toText(parte.getGallinasMuertas()));
        gallinasEnfermasField.setText(toText(parte.getGallinasEnfermas()));
        huevosProducidosField.setText(toText(parte.getHuevosProducidos()));
        huevosRotosField.setText(toText(parte.getHuevosRotos()));
        piensoKgField.setText(toText(parte.getPiensoKg()));
        observacionesArea.setText(parte.getObservaciones() != null ? parte.getObservaciones() : "");
    }

    private void limpiarFormulario() {
        selectedParteId = null;
        if (uebFormCombo.getItemCount() > 0) uebFormCombo.setSelectedIndex(0);
        cargarNavesFormParaUeb(getSelectedId(uebFormCombo, uebLabelToId));
        fechaField.setText("");
        gallinasVivasField.setText("");
        gallinasMuertasField.setText("");
        gallinasEnfermasField.setText("");
        huevosProducidosField.setText("");
        huevosRotosField.setText("");
        piensoKgField.setText("");
        observacionesArea.setText("");
        tablePanel.getTable().clearSelection();
    }

    private void aplicarFiltros() {
        UUID uebId = getSelectedId(uebFilterCombo, uebLabelToId);
        UUID naveId = getSelectedId(naveFilterCombo, naveFilterLabelToId);
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

        // Base: reutilizar métodos existentes cuando es posible
        List<ParteNave> base;
        if (naveId != null) {
            base = parteNaveService.buscarPorNave(naveId);
        } else if (fecha != null) {
            base = parteNaveService.buscarPorFecha(fecha);
        } else {
            base = parteNaveService.listarTodos();
        }

        Set<UUID> navesDeUeb = null;
        if (uebId != null) {
            navesDeUeb = new HashSet<>();
            for (Nave n : naveService.buscarPorUEB(uebId)) {
                navesDeUeb.add(n.getIdNave());
            }
        }

        List<ParteNave> filtrados = new ArrayList<>();
        for (ParteNave p : base) {
            if (navesDeUeb != null && !navesDeUeb.contains(p.getIdNave())) continue;
            if (fecha != null && !fecha.equals(p.getFechaProduccion())) continue;
            if (naveId != null && !naveId.equals(p.getIdNave())) continue;
            filtrados.add(p);
        }
        refrescarTabla(filtrados);
    }

    private void mostrarTodos() {
        uebFilterCombo.setSelectedItem(TODAS);
        onUebFilterChanged();
        naveFilterCombo.setSelectedItem(TODAS);
        fechaFilterField.setText("");
        refrescarTabla(parteNaveService.listarTodos());
    }

    private void refrescarTabla(List<ParteNave> partes) {
        currentRows.clear();
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        if (partes == null) return;
        for (ParteNave p : partes) {
            currentRows.add(p);
            Nave nave = p.getIdNave() != null ? naveService.buscarPorId(p.getIdNave()).orElse(null) : null;
            String nombreNave = nave != null ? safe(nave.getNombre()) : "(nave no encontrada)";
            String nombreUeb = "(UEB no encontrada)";
            if (nave != null && nave.getIdUEB() != null) {
                UEB ueb = uebService.buscarPorId(nave.getIdUEB()).orElse(null);
                if (ueb != null) nombreUeb = safe(ueb.getNombre());
            }
            model.addRow(new Object[]{
                    p.getFechaProduccion() != null ? p.getFechaProduccion().toString() : "",
                    nombreUeb,
                    nombreNave,
                    toText(p.getGallinasVivas()),
                    toText(p.getGallinasMuertas()),
                    toText(p.getGallinasEnfermas()),
                    toText(p.getHuevosProducidos()),
                    toText(p.getHuevosRotos()),
                    toText(p.getPiensoKg())
            });
        }
    }

    private void recargarCombos() {
        cargarUebCombos();
        onUebFormChanged();
        onUebFilterChanged();
    }

    private void cargarUebCombos() {
        uebLabelToId.clear();
        uebFormCombo.removeAllItems();
        uebFilterCombo.removeAllItems();
        uebFilterCombo.addItem(TODAS);
        List<UEB> uebs = uebService.listarTodas();
        for (UEB u : uebs) {
            String label = formatUeb(u);
            uebLabelToId.put(label, u.getIdUEB());
            uebFormCombo.addItem(label);
            uebFilterCombo.addItem(label);
        }
    }

    private void onUebFormChanged() {
        UUID uebId = getSelectedId(uebFormCombo, uebLabelToId);
        cargarNavesFormParaUeb(uebId);
    }

    private void onUebFilterChanged() {
        UUID uebId = getSelectedId(uebFilterCombo, uebLabelToId);
        cargarNavesFilterParaUeb(uebId);
    }

    private void cargarNavesFormParaUeb(UUID uebId) {
        naveFormLabelToId.clear();
        naveFormCombo.removeAllItems();
        if (uebId == null) return;
        for (Nave n : naveService.buscarPorUEB(uebId)) {
            String label = formatNave(n);
            naveFormLabelToId.put(label, n.getIdNave());
            naveFormCombo.addItem(label);
        }
    }

    private void cargarNavesFilterParaUeb(UUID uebId) {
        naveFilterLabelToId.clear();
        naveFilterCombo.removeAllItems();
        naveFilterCombo.addItem(TODAS);
        List<Nave> naves = (uebId == null) ? naveService.listarTodas() : naveService.buscarPorUEB(uebId);
        for (Nave n : naves) {
            String label = formatNave(n);
            naveFilterLabelToId.put(label, n.getIdNave());
            naveFilterCombo.addItem(label);
        }
    }

    private UUID getSelectedId(JComboBox<String> combo, Map<String, UUID> map) {
        String sel = (String) combo.getSelectedItem();
        if (sel == null || TODOS.equals(sel) || TODAS.equals(sel)) return null;
        return map.get(sel);
    }

    private void seleccionarEnCombo(JComboBox<String> combo, Map<String, UUID> map, UUID id) {
        if (id == null) return;
        for (Map.Entry<String, UUID> e : map.entrySet()) {
            if (id.equals(e.getValue())) {
                combo.setSelectedItem(e.getKey());
                return;
            }
        }
    }

    private String formatUeb(UEB u) {
        return safe(u.getCodigo()) + " - " + safe(u.getNombre());
    }

    private String formatNave(Nave n) {
        return safe(n.getCodigo()) + " - " + safe(n.getNombre());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String toText(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private LocalDate parseFecha(String text, String campo) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("La " + campo + " debe tener formato AAAA-MM-DD.");
        }
    }

    private Integer parseInteger(String text, String campo) {
        if (text == null || text.trim().isEmpty()) return null; // no informado
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El campo '" + campo + "' debe ser un número entero válido.");
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
