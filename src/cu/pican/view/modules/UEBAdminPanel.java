package cu.pican.view.modules;

import cu.pican.model.UEB;
import cu.pican.service.UEBService;
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
 * Gestión de UEB dentro de Administración (integrada con UEBService).
 */
public class UEBAdminPanel extends JPanel {

    private final UEBService uebService;
    private final DataTablePanel tablePanel;

    private final JTextField codigoField = new JTextField(18);
    private final JTextField nombreField = new JTextField(28);
    private final JCheckBox activaCheck = new JCheckBox("Activa", true);
    private final JLabel idLabel = new JLabel("-");

    private UUID selectedId;

    public UEBAdminPanel(UEBService uebService) {
        this.uebService = uebService;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[] cols = {"Código", "Nombre", "Activa", "ID (UUID)"};
        tablePanel = new DataTablePanel("UEB registradas", cols, new Object[0][0]);
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
                "Registro / consulta de UEB"));
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
            UEB ueb = new UEB(UUID.randomUUID(), nombreField.getText(), codigoField.getText(), activaCheck.isSelected());
            uebService.registrar(ueb);
            MessageHelper.showInfo(this, "UEB registrada correctamente.");
            limpiarFormulario();
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la UEB: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una UEB de la tabla para actualizar.");
            return;
        }
        try {
            UEB ueb = new UEB(selectedId, nombreField.getText(), codigoField.getText(), activaCheck.isSelected());
            uebService.actualizar(ueb);
            MessageHelper.showInfo(this, "UEB actualizada correctamente.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar la UEB: " + ex.getMessage());
        }
    }

    private void cambiarEstado(boolean activa) {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una UEB de la tabla para cambiar su estado.");
            return;
        }
        try {
            UEB ueb = activa ? uebService.activar(selectedId) : uebService.desactivar(selectedId);
            cargarEnFormulario(ueb);
            MessageHelper.showInfo(this, activa ? "UEB activada correctamente." : "UEB desactivada correctamente.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void consultarSeleccion() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una UEB en la tabla para consultar sus datos.");
            return;
        }
        uebService.buscarPorId(selectedId).ifPresentOrElse(ueb -> {
            cargarEnFormulario(ueb);
            MessageHelper.showInfo(this, "Datos de la UEB cargados en el formulario (consulta).");
        }, () -> MessageHelper.showValidation(this, "La UEB seleccionada ya no existe."));
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0) {
            return;
        }
        String idStr = String.valueOf(tablePanel.getTableModel().getValueAt(row, 3));
        try {
            selectedId = UUID.fromString(idStr);
            uebService.buscarPorId(selectedId).ifPresent(this::cargarEnFormulario);
        } catch (IllegalArgumentException ignored) {
            selectedId = null;
        }
    }

    private void cargarEnFormulario(UEB ueb) {
        selectedId = ueb.getIdUEB();
        codigoField.setText(ueb.getCodigo() != null ? ueb.getCodigo() : "");
        nombreField.setText(ueb.getNombre() != null ? ueb.getNombre() : "");
        activaCheck.setSelected(ueb.isActiva());
        idLabel.setText(ueb.getIdUEB() != null ? ueb.getIdUEB().toString() : "-");
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
        List<UEB> uebs = uebService.listarTodas();
        for (UEB ueb : uebs) {
            model.addRow(new Object[]{
                    ueb.getCodigo(),
                    ueb.getNombre(),
                    ueb.isActiva() ? "Sí" : "No",
                    ueb.getIdUEB()
            });
        }
    }

    /** Permite a otros paneles refrescar información dependiente de UEB cuando sea necesario. */
    public void recargar() {
        refrescarTabla();
    }
}
