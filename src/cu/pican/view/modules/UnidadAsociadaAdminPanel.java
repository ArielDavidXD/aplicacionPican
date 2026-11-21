package cu.pican.view.modules;

import cu.pican.model.UnidadAsociada;
import cu.pican.service.UnidadAsociadaService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
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
import java.util.List;
import java.util.UUID;

/**
 * Gestión de unidades asociadas (datos generales).
 * No se asignan a UEB ni se inventan campos productivos; relación con UEB pendiente de confirmación.
 */
public class UnidadAsociadaAdminPanel extends JPanel {

    private final UnidadAsociadaService unidadAsociadaService;
    private final DataTablePanel tablePanel;

    private final JTextField codigoField = new JTextField(18);
    private final JTextField nombreField = new JTextField(28);
    private final JCheckBox activaCheck = new JCheckBox("Activa", true);
    private final JLabel idLabel = new JLabel("-");

    private UUID selectedId;

    public UnidadAsociadaAdminPanel(UnidadAsociadaService unidadAsociadaService) {
        this.unidadAsociadaService = unidadAsociadaService;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[] cols = {"Código", "Nombre", "Activa", "ID (UUID)"};
        tablePanel = new DataTablePanel("Unidades asociadas registradas", cols, new Object[0][0]);
        tablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSeleccion();
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(buildFormPanel(), BorderLayout.NORTH);
        center.add(tablePanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(buildActionsPanel(), BorderLayout.SOUTH);

        refrescarTabla();
    }

    private JPanel buildFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Registro / consulta de unidad asociada (sin asignación a UEB)"));
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
        form.add(new JLabel("Estado:"), c);
        c.gridx = 1;
        form.add(activaCheck, c);

        c.gridx = 2;
        form.add(new JLabel("ID (consulta):"), c);
        c.gridx = 3;
        idLabel.setFont(UITheme.SMALL_FONT);
        idLabel.setForeground(UITheme.MUTED_TEXT);
        form.add(idLabel, c);

        return form;
    }

    private JPanel buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);

        AppButton nuevoBtn = new AppButton("Nuevo");
        AppButton registrarBtn = new AppButton("Registrar");
        AppButton actualizarBtn = new AppButton("Actualizar");
        AppButton activarBtn = new AppButton("Activar");
        AppButton desactivarBtn = new AppButton("Desactivar");
        AppButton consultarBtn = new AppButton("Consultar selección");

        nuevoBtn.addActionListener(e -> limpiarFormulario());
        registrarBtn.addActionListener(e -> registrar());
        actualizarBtn.addActionListener(e -> actualizar());
        activarBtn.addActionListener(e -> cambiarEstado(true));
        desactivarBtn.addActionListener(e -> cambiarEstado(false));
        consultarBtn.addActionListener(e -> consultarSeleccion());

        actions.add(nuevoBtn);
        actions.add(registrarBtn);
        actions.add(actualizarBtn);
        actions.add(activarBtn);
        actions.add(desactivarBtn);
        actions.add(consultarBtn);
        return actions;
    }

    private void registrar() {
        try {
            UnidadAsociada ua = new UnidadAsociada(UUID.randomUUID(), nombreField.getText(), codigoField.getText(), activaCheck.isSelected());
            unidadAsociadaService.registrar(ua);
            MessageHelper.showInfo(this, "Unidad asociada registrada correctamente.");
            limpiarFormulario();
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la unidad asociada: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una unidad asociada para actualizar.");
            return;
        }
        try {
            UnidadAsociada ua = new UnidadAsociada(selectedId, nombreField.getText(), codigoField.getText(), activaCheck.isSelected());
            unidadAsociadaService.actualizar(ua);
            MessageHelper.showInfo(this, "Unidad asociada actualizada correctamente.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar la unidad asociada: " + ex.getMessage());
        }
    }

    private void cambiarEstado(boolean activa) {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una unidad asociada en la tabla.");
            return;
        }
        try {
            UnidadAsociada ua = activa
                    ? unidadAsociadaService.activar(selectedId)
                    : unidadAsociadaService.desactivar(selectedId);
            cargarEnFormulario(ua);
            MessageHelper.showInfo(this, activa ? "Unidad asociada activada." : "Unidad asociada desactivada.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void consultarSeleccion() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una unidad asociada para consultar sus datos.");
            return;
        }
        unidadAsociadaService.buscarPorId(selectedId).ifPresentOrElse(ua -> {
            cargarEnFormulario(ua);
            MessageHelper.showInfo(this, "Datos de la unidad asociada cargados (consulta).");
        }, () -> MessageHelper.showValidation(this, "La unidad asociada seleccionada ya no existe."));
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0) {
            return;
        }
        String idStr = String.valueOf(tablePanel.getTableModel().getValueAt(row, 3));
        try {
            selectedId = UUID.fromString(idStr);
            unidadAsociadaService.buscarPorId(selectedId).ifPresent(this::cargarEnFormulario);
        } catch (IllegalArgumentException ignored) {
            selectedId = null;
        }
    }

    private void cargarEnFormulario(UnidadAsociada ua) {
        selectedId = ua.getIdUnidad();
        codigoField.setText(ua.getCodigo() != null ? ua.getCodigo() : "");
        nombreField.setText(ua.getNombre() != null ? ua.getNombre() : "");
        activaCheck.setSelected(ua.isActiva());
        idLabel.setText(ua.getIdUnidad() != null ? ua.getIdUnidad().toString() : "-");
    }

    private void limpiarFormulario() {
        selectedId = null;
        codigoField.setText("");
        nombreField.setText("");
        activaCheck.setSelected(true);
        idLabel.setText("-");
        tablePanel.getTable().clearSelection();
    }

    private void refrescarTabla() {
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        List<UnidadAsociada> unidades = unidadAsociadaService.listarTodas();
        for (UnidadAsociada ua : unidades) {
            model.addRow(new Object[]{
                    ua.getCodigo(),
                    ua.getNombre(),
                    ua.isActiva() ? "Sí" : "No",
                    ua.getIdUnidad()
            });
        }
    }
}
