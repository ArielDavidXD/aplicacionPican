package cu.pican.view.modules;

import cu.pican.model.Producto;
import cu.pican.controller.service.ProductoService;
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
 * Gestión de productos del catálogo operativo (CU-17).
 * Unidad de medida opcional (obligatoriedad pendiente de confirmación). No se eliminan físicamente.
 */
public class ProductoAdminPanel extends JPanel {

    private final ProductoService productoService;
    private final DataTablePanel tablePanel;

    private final JTextField codigoField = new JTextField(18);
    private final JTextField nombreField = new JTextField(28);
    private final JTextField unidadMedidaField = new JTextField(12);
    private final JCheckBox activaCheck = new JCheckBox("Activo", true);
    private final JLabel idLabel = new JLabel("-");

    private UUID selectedId;

    public ProductoAdminPanel(ProductoService productoService) {
        this.productoService = productoService;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[] cols = {"Código", "Nombre", "Unidad de medida", "Activo", "ID (UUID)"};
        tablePanel = new DataTablePanel("Productos registrados", cols, new Object[0][0]);
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
                "Registro / consulta de producto"));
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
        form.add(new JLabel("Unidad de medida (opcional):"), c);
        c.gridx = 1;
        form.add(unidadMedidaField, c);

        c.gridx = 2;
        form.add(new JLabel("Estado:"), c);
        c.gridx = 3;
        form.add(activaCheck, c);

        c.gridx = 0; c.gridy = 2;
        form.add(new JLabel("ID (consulta):"), c);
        c.gridx = 1; c.gridwidth = 3;
        idLabel.setFont(UITheme.SMALL_FONT);
        idLabel.setForeground(UITheme.MUTED_TEXT);
        form.add(idLabel, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 4;
        JLabel nota = new JLabel("Catálogo operativo (CU-17). No se definen categorías/tipos; unidad de medida no obligatoria (pendiente).");
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
            Producto p = new Producto(UUID.randomUUID(), codigoField.getText(), nombreField.getText(),
                    activaCheck.isSelected(), emptyToNull(unidadMedidaField.getText()));
            productoService.registrar(p);
            MessageHelper.showInfo(this, "Producto registrado correctamente.");
            limpiarFormulario();
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar el producto: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un producto de la tabla para actualizar.");
            return;
        }
        try {
            Producto p = new Producto(selectedId, codigoField.getText(), nombreField.getText(),
                    activaCheck.isSelected(), emptyToNull(unidadMedidaField.getText()));
            productoService.actualizar(p);
            MessageHelper.showInfo(this, "Producto actualizado correctamente (UUID conservado).");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar el producto: " + ex.getMessage());
        }
    }

    private void cambiarEstado(boolean activa) {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un producto en la tabla.");
            return;
        }
        try {
            Producto p = activa ? productoService.activar(selectedId) : productoService.desactivar(selectedId);
            cargarEnFormulario(p);
            MessageHelper.showInfo(this, activa ? "Producto activado." : "Producto desactivado.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void consultarSeleccion() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un producto para consultar sus datos.");
            return;
        }
        productoService.buscarPorId(selectedId).ifPresentOrElse(p -> {
            cargarEnFormulario(p);
            MessageHelper.showInfo(this, "Datos del producto cargados (consulta).");
        }, () -> MessageHelper.showValidation(this, "El producto seleccionado ya no existe."));
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0) return;
        String idStr = String.valueOf(tablePanel.getTableModel().getValueAt(row, 4));
        try {
            selectedId = UUID.fromString(idStr);
            productoService.buscarPorId(selectedId).ifPresent(this::cargarEnFormulario);
        } catch (IllegalArgumentException ignored) {
            selectedId = null;
        }
    }

    private void cargarEnFormulario(Producto p) {
        selectedId = p.getIdProducto();
        codigoField.setText(p.getCodigo() != null ? p.getCodigo() : "");
        nombreField.setText(p.getNombre() != null ? p.getNombre() : "");
        unidadMedidaField.setText(p.getUnidadMedida() != null ? p.getUnidadMedida() : "");
        activaCheck.setSelected(p.isActiva());
        idLabel.setText(p.getIdProducto() != null ? p.getIdProducto().toString() : "-");
    }

    private void limpiarFormulario() {
        selectedId = null;
        codigoField.setText("");
        nombreField.setText("");
        unidadMedidaField.setText("");
        activaCheck.setSelected(true);
        idLabel.setText("-");
        tablePanel.getTable().clearSelection();
    }

    private void refrescarTabla() {
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        List<Producto> productos = productoService.listarTodos();
        for (Producto p : productos) {
            model.addRow(new Object[]{
                    p.getCodigo(),
                    p.getNombre(),
                    p.getUnidadMedida() != null ? p.getUnidadMedida() : "",
                    p.isActiva() ? "Sí" : "No",
                    p.getIdProducto()
            });
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
}
