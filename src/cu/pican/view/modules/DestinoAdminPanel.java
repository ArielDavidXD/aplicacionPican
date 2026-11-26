package cu.pican.view.modules;

import cu.pican.model.Destino;
import cu.pican.controller.service.DestinoService;
import cu.pican.controller.service.ValidacionException;
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
 * Gestión de destinos del catálogo operativo (CU-17).
 * No se precargan destinos históricos; se registran los datos confirmados. No se eliminan físicamente.
 */
public class DestinoAdminPanel extends JPanel {

    private final DestinoService destinoService;
    private final DataTablePanel tablePanel;

    private final JTextField codigoField = new JTextField(18);
    private final JTextField nombreField = new JTextField(28);
    private final JCheckBox activaCheck = new JCheckBox("Activo", true);
    private final JLabel idLabel = new JLabel("-");

    private UUID selectedId;

    public DestinoAdminPanel(DestinoService destinoService) {
        this.destinoService = destinoService;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[] cols = {"Código", "Nombre", "Activo", "ID (UUID)"};
        tablePanel = new DataTablePanel("Destinos registrados", cols, new Object[0][0]);
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
                "Registro / consulta de destino"));
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

        c.gridx = 0; c.gridy = 2; c.gridwidth = 4;
        JLabel nota = new JLabel("Catálogo operativo (CU-17). No se precargan destinos históricos como catálogo oficial completo.");
        nota.setFont(UITheme.SMALL_FONT);
        nota.setForeground(UITheme.MUTED_TEXT);
        form.add(nota, c);

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
            Destino d = new Destino(UUID.randomUUID(), codigoField.getText(), nombreField.getText(), activaCheck.isSelected());
            destinoService.registrar(d);
            MessageHelper.showInfo(this, "Destino registrado correctamente.");
            limpiarFormulario();
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar el destino: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un destino de la tabla para actualizar.");
            return;
        }
        try {
            Destino d = new Destino(selectedId, codigoField.getText(), nombreField.getText(), activaCheck.isSelected());
            destinoService.actualizar(d);
            MessageHelper.showInfo(this, "Destino actualizado correctamente (UUID conservado).");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar el destino: " + ex.getMessage());
        }
    }

    private void cambiarEstado(boolean activa) {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un destino en la tabla.");
            return;
        }
        try {
            Destino d = activa ? destinoService.activar(selectedId) : destinoService.desactivar(selectedId);
            cargarEnFormulario(d);
            MessageHelper.showInfo(this, activa ? "Destino activado." : "Destino desactivado.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void consultarSeleccion() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un destino para consultar sus datos.");
            return;
        }
        destinoService.buscarPorId(selectedId).ifPresentOrElse(d -> {
            cargarEnFormulario(d);
            MessageHelper.showInfo(this, "Datos del destino cargados (consulta).");
        }, () -> MessageHelper.showValidation(this, "El destino seleccionado ya no existe."));
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0) return;
        String idStr = String.valueOf(tablePanel.getTableModel().getValueAt(row, 3));
        try {
            selectedId = UUID.fromString(idStr);
            destinoService.buscarPorId(selectedId).ifPresent(this::cargarEnFormulario);
        } catch (IllegalArgumentException ignored) {
            selectedId = null;
        }
    }

    private void cargarEnFormulario(Destino d) {
        selectedId = d.getIdDestino();
        codigoField.setText(d.getCodigo() != null ? d.getCodigo() : "");
        nombreField.setText(d.getNombre() != null ? d.getNombre() : "");
        activaCheck.setSelected(d.isActiva());
        idLabel.setText(d.getIdDestino() != null ? d.getIdDestino().toString() : "-");
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
        List<Destino> destinos = destinoService.listarTodos();
        for (Destino d : destinos) {
            model.addRow(new Object[]{
                    d.getCodigo(),
                    d.getNombre(),
                    d.isActiva() ? "Sí" : "No",
                    d.getIdDestino()
            });
        }
    }
}
