package cu.pican.view.modules;

import cu.pican.model.Nave;
import cu.pican.model.UEB;
import cu.pican.controller.service.NaveService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestión de naves dentro de Administración (integrada con NaveService).
 * Las naves se asocian a UEB existentes mediante selección en desplegable.
 */
public class NaveAdminPanel extends JPanel {

    private static final String TODAS = "Todas las UEB";

    private final NaveService naveService;
    private final UEBService uebService;
    private final DataTablePanel tablePanel;

    private final JTextField codigoField = new JTextField(18);
    private final JTextField nombreField = new JTextField(28);
    private final JCheckBox activaCheck = new JCheckBox("Activa", true);
    private final JComboBox<String> uebCombo = new JComboBox<>();
    private final JComboBox<String> filtroUebCombo = new JComboBox<>();
    private final JLabel idLabel = new JLabel("-");

    private final Map<String, UUID> labelToUebId = new HashMap<>();
    private UUID selectedId;
    private UUID filtroUebId; // null = todas

    public NaveAdminPanel(NaveService naveService, UEBService uebService) {
        this.naveService = naveService;
        this.uebService = uebService;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[] cols = {"Código", "Nombre", "Estado", "UEB", "ID (UUID)"};
        tablePanel = new DataTablePanel("Naves registradas", cols, new Object[0][0]);
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
        refrescarTabla();
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
                "Registro / consulta de nave"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Código:"), c);
        c.gridx = 1;
        form.add(codigoField, c);

        c.gridx = 2;
        form.add(new JLabel("Nombre:"), c);
        c.gridx = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        form.add(nombreField, c);

        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("UEB:"), c);
        c.gridx = 1; c.gridwidth = 2;
        form.add(uebCombo, c);

        c.gridwidth = 1;
        c.gridx = 3;
        form.add(activaCheck, c);

        c.gridx = 0; c.gridy = 2;
        form.add(new JLabel("ID (consulta):"), c);
        c.gridx = 1; c.gridwidth = 3;
        idLabel.setFont(UITheme.SMALL_FONT);
        idLabel.setForeground(UITheme.MUTED_TEXT);
        form.add(idLabel, c);

        return form;
    }

    private JPanel buildFilterPanel() {
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filter.setOpaque(false);
        filter.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Consulta por UEB"));
        filter.add(new JLabel("UEB:"));
        filter.add(filtroUebCombo);
        AppButton filtrarBtn = new AppButton("Consultar naves de UEB");
        AppButton todasBtn = new AppButton("Mostrar todas");
        filtrarBtn.addActionListener(e -> aplicarFiltro());
        todasBtn.addActionListener(e -> mostrarTodas());
        filter.add(filtrarBtn);
        filter.add(todasBtn);
        return filter;
    }

    private JPanel buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);

        AppButton nuevoBtn = new AppButton("Nuevo");
        AppButton registrarBtn = new AppButton("Registrar");
        AppButton actualizarBtn = new AppButton("Actualizar");
        AppButton cambiarUebBtn = new AppButton("Cambiar UEB");
        AppButton activarBtn = new AppButton("Activar");
        AppButton desactivarBtn = new AppButton("Desactivar");
        AppButton consultarBtn = new AppButton("Consultar selección");
        AppButton recargarUebBtn = new AppButton("Recargar UEB");

        nuevoBtn.addActionListener(e -> limpiarFormulario());
        registrarBtn.addActionListener(e -> registrar());
        actualizarBtn.addActionListener(e -> actualizar());
        cambiarUebBtn.addActionListener(e -> cambiarUEB());
        activarBtn.addActionListener(e -> cambiarEstado(true));
        desactivarBtn.addActionListener(e -> cambiarEstado(false));
        consultarBtn.addActionListener(e -> consultarSeleccion());
        recargarUebBtn.addActionListener(e -> { recargarUEBs(); MessageHelper.showInfo(this, "Listado de UEB actualizado."); });

        actions.add(nuevoBtn);
        actions.add(registrarBtn);
        actions.add(actualizarBtn);
        actions.add(cambiarUebBtn);
        actions.add(activarBtn);
        actions.add(desactivarBtn);
        actions.add(consultarBtn);
        actions.add(recargarUebBtn);
        return actions;
    }

    private void registrar() {
        try {
            UUID idUEB = getSelectedUebId(uebCombo);
            Nave nave = new Nave(UUID.randomUUID(), idUEB, nombreField.getText(), codigoField.getText(), activaCheck.isSelected());
            naveService.registrar(nave);
            MessageHelper.showInfo(this, "Nave registrada correctamente.");
            limpiarFormulario();
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la nave: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una nave de la tabla para actualizar.");
            return;
        }
        try {
            UUID idUEB = getSelectedUebId(uebCombo);
            Nave nave = new Nave(selectedId, idUEB, nombreField.getText(), codigoField.getText(), activaCheck.isSelected());
            naveService.actualizar(nave);
            MessageHelper.showInfo(this, "Nave actualizada; la UEB asociada se conserva. Para cambiarla use 'Cambiar UEB'.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar la nave: " + ex.getMessage());
        }
    }

    private void cambiarUEB() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una nave para cambiar su UEB.");
            return;
        }
        UUID idUEB = getSelectedUebId(uebCombo);
        if (idUEB == null) {
            MessageHelper.showValidation(this, "Seleccione una UEB activa en el desplegable.");
            return;
        }
        try {
            Nave nave = naveService.cambiarUEB(selectedId, idUEB);
            cargarEnFormulario(nave);
            MessageHelper.showInfo(this, "UEB de la nave cambiada y validada correctamente.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void cambiarEstado(boolean activa) {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una nave de la tabla para cambiar su estado.");
            return;
        }
        try {
            Nave nave = activa ? naveService.activar(selectedId) : naveService.desactivar(selectedId);
            cargarEnFormulario(nave);
            MessageHelper.showInfo(this, activa ? "Nave activada correctamente." : "Nave desactivada correctamente.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void consultarSeleccion() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una nave para consultar sus datos.");
            return;
        }
        naveService.buscarPorId(selectedId).ifPresentOrElse(n -> {
            cargarEnFormulario(n);
            MessageHelper.showInfo(this, "Datos de la nave cargados (consulta).");
        }, () -> MessageHelper.showValidation(this, "La nave seleccionada ya no existe."));
    }

    private void aplicarFiltro() {
        String sel = (String) filtroUebCombo.getSelectedItem();
        if (sel == null || TODAS.equals(sel)) {
            filtroUebId = null;
        } else {
            filtroUebId = labelToUebId.get(sel);
            if (filtroUebId == null) {
                MessageHelper.showValidation(this, "Seleccione una UEB válida para filtrar.");
                return;
            }
        }
        refrescarTabla();
    }

    private void mostrarTodas() {
        filtroUebId = null;
        filtroUebCombo.setSelectedItem(TODAS);
        refrescarTabla();
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0) return;
        String idStr = String.valueOf(tablePanel.getTableModel().getValueAt(row, 4));
        try {
            selectedId = UUID.fromString(idStr);
            naveService.buscarPorId(selectedId).ifPresent(this::cargarEnFormulario);
        } catch (IllegalArgumentException ignored) {
            selectedId = null;
        }
    }

    private void cargarEnFormulario(Nave nave) {
        selectedId = nave.getIdNave();
        codigoField.setText(nave.getCodigo() != null ? nave.getCodigo() : "");
        nombreField.setText(nave.getNombre() != null ? nave.getNombre() : "");
        activaCheck.setSelected(nave.isActiva());
        seleccionarUebEnCombo(uebCombo, nave.getIdUEB());
        idLabel.setText(nave.getIdNave() != null ? nave.getIdNave().toString() : "-");
    }

    private void limpiarFormulario() {
        selectedId = null;
        codigoField.setText("");
        nombreField.setText("");
        activaCheck.setSelected(true);
        if (uebCombo.getItemCount() > 0) uebCombo.setSelectedIndex(0);
        idLabel.setText("-");
        tablePanel.getTable().clearSelection();
    }

    private void refrescarTabla() {
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        List<Nave> naves = (filtroUebId == null) ? naveService.listarTodas() : naveService.buscarPorUEB(filtroUebId);
        for (Nave n : naves) {
            model.addRow(new Object[]{
                    n.getCodigo(),
                    n.getNombre(),
                    n.isActiva() ? "Activa" : "Inactiva",
                    etiquetaUEB(n.getIdUEB()),
                    n.getIdNave()
            });
        }
    }

    public void recargarUEBs() {
        String prevForm = (String) uebCombo.getSelectedItem();
        String prevFilter = (String) filtroUebCombo.getSelectedItem();
        labelToUebId.clear();
        uebCombo.removeAllItems();
        filtroUebCombo.removeAllItems();
        filtroUebCombo.addItem(TODAS);
        List<UEB> uebs = uebService.listarTodas();
        for (UEB u : uebs) {
            String label = formatUeb(u);
            labelToUebId.put(label, u.getIdUEB());
            filtroUebCombo.addItem(label);
            // Solo UEB activas pueden elegirse para registrar o cambiar la UEB de una nave.
            if (u.isActiva()) {
                uebCombo.addItem(label);
            }
        }
        if (prevForm != null && labelToUebId.containsKey(prevForm)) uebCombo.setSelectedItem(prevForm);
        if (prevFilter != null && (TODAS.equals(prevFilter) || labelToUebId.containsKey(prevFilter))) {
            filtroUebCombo.setSelectedItem(prevFilter);
        } else {
            filtroUebCombo.setSelectedItem(TODAS);
        }
    }

    private UUID getSelectedUebId(JComboBox<String> combo) {
        String sel = (String) combo.getSelectedItem();
        if (sel == null) return null;
        return labelToUebId.get(sel);
    }

    private void seleccionarUebEnCombo(JComboBox<String> combo, UUID idUEB) {
        if (idUEB == null) return;
        for (Map.Entry<String, UUID> e : labelToUebId.entrySet()) {
            if (idUEB.equals(e.getValue())) {
                combo.setSelectedItem(e.getKey());
                return;
            }
        }
    }

    private String etiquetaUEB(UUID idUEB) {
        if (idUEB == null) return "-";
        return uebService.buscarPorId(idUEB)
                .map(this::formatUeb)
                .orElse(idUEB.toString());
    }

    private String formatUeb(UEB u) {
        String codigo = u.getCodigo() != null ? u.getCodigo() : "";
        String nombre = u.getNombre() != null ? u.getNombre() : "";
        return codigo + " - " + nombre;
    }
}
