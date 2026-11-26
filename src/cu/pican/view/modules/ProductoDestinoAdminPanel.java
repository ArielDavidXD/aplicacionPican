package cu.pican.view.modules;

import cu.pican.model.Destino;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.controller.service.DestinoService;
import cu.pican.controller.service.ProductoDestinoService;
import cu.pican.controller.service.ProductoService;
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
 * Gestión de relaciones producto-destino autorizadas (catálogo operativo, CU-17).
 * Permite seleccionar producto y destino existentes y registrar/editar/activar/desactivar la asociación.
 * No se permiten relaciones duplicadas (mismo producto-destino).
 */
public class ProductoDestinoAdminPanel extends JPanel {

    private final ProductoDestinoService productoDestinoService;
    private final ProductoService productoService;
    private final DestinoService destinoService;
    private final DataTablePanel tablePanel;

    private final JComboBox<String> productoCombo = new JComboBox<>();
    private final JComboBox<String> destinoCombo = new JComboBox<>();
    private final JCheckBox activaCheck = new JCheckBox("Relación activa", true);
    private final JLabel idLabel = new JLabel("-");

    private final Map<String, UUID> productoLabelToId = new HashMap<>();
    private final Map<String, UUID> destinoLabelToId = new HashMap<>();

    private UUID selectedId;

    public ProductoDestinoAdminPanel(ProductoDestinoService productoDestinoService,
                                     ProductoService productoService,
                                     DestinoService destinoService) {
        this.productoDestinoService = productoDestinoService;
        this.productoService = productoService;
        this.destinoService = destinoService;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String[] cols = {"Producto", "Destino", "Relación activa", "ID relación (UUID)"};
        tablePanel = new DataTablePanel("Relaciones producto-destino registradas", cols, new Object[0][0]);
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

        recargarCatalogos();
        refrescarTabla();
    }

    private JPanel buildFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Registro / consulta de relación producto-destino"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Producto:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(productoCombo, c);

        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(new JLabel("Destino:"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(destinoCombo, c);

        c.fill = GridBagConstraints.NONE; c.weightx = 0;
        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Estado:"), c);
        c.gridx = 1;
        form.add(activaCheck, c);

        c.gridx = 2;
        form.add(new JLabel("ID relación (consulta):"), c);
        c.gridx = 3;
        idLabel.setFont(UITheme.SMALL_FONT);
        idLabel.setForeground(UITheme.MUTED_TEXT);
        form.add(idLabel, c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 4;
        JLabel nota = new JLabel("Seleccione producto y destino existentes. No se permiten duplicados; desactivar conserva historial/referencias.");
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
        AppButton recargarBtn = new AppButton("Recargar catálogos");

        nuevoBtn.addActionListener(e -> limpiarFormulario());
        registrarBtn.addActionListener(e -> registrar());
        actualizarBtn.addActionListener(e -> actualizar());
        activarBtn.addActionListener(e -> cambiarEstado(true));
        desactivarBtn.addActionListener(e -> cambiarEstado(false));
        consultarBtn.addActionListener(e -> consultarSeleccion());
        recargarBtn.addActionListener(e -> { recargarCatalogos(); refrescarTabla(); MessageHelper.showInfo(this, "Catálogos de productos y destinos recargados."); });

        actions.add(nuevoBtn);
        actions.add(registrarBtn);
        actions.add(actualizarBtn);
        actions.add(activarBtn);
        actions.add(desactivarBtn);
        actions.add(consultarBtn);
        actions.add(recargarBtn);
        return actions;
    }

    private void registrar() {
        try {
            UUID idProducto = getSelectedId(productoCombo, productoLabelToId);
            UUID idDestino = getSelectedId(destinoCombo, destinoLabelToId);
            ProductoDestino rel = new ProductoDestino(UUID.randomUUID(), idProducto, idDestino, activaCheck.isSelected());
            productoDestinoService.registrar(rel);
            MessageHelper.showInfo(this, "Relación producto-destino registrada correctamente.");
            limpiarFormulario();
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la relación: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una relación de la tabla para actualizar.");
            return;
        }
        try {
            UUID idProducto = getSelectedId(productoCombo, productoLabelToId);
            UUID idDestino = getSelectedId(destinoCombo, destinoLabelToId);
            ProductoDestino rel = new ProductoDestino(selectedId, idProducto, idDestino, activaCheck.isSelected());
            productoDestinoService.actualizar(rel);
            MessageHelper.showInfo(this, "Relación actualizada correctamente (UUID conservado).");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo actualizar la relación: " + ex.getMessage());
        }
    }

    private void cambiarEstado(boolean activa) {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una relación en la tabla.");
            return;
        }
        try {
            ProductoDestino r = activa
                    ? productoDestinoService.activar(selectedId)
                    : productoDestinoService.desactivar(selectedId);
            cargarEnFormulario(r);
            MessageHelper.showInfo(this, activa ? "Relación activada." : "Relación desactivada.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void consultarSeleccion() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione una relación para consultar sus datos.");
            return;
        }
        productoDestinoService.buscarPorId(selectedId).ifPresentOrElse(r -> {
            cargarEnFormulario(r);
            MessageHelper.showInfo(this, "Datos de la relación cargados (consulta).");
        }, () -> MessageHelper.showValidation(this, "La relación seleccionada ya no existe."));
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0) return;
        String idStr = String.valueOf(tablePanel.getTableModel().getValueAt(row, 3));
        try {
            selectedId = UUID.fromString(idStr);
            productoDestinoService.buscarPorId(selectedId).ifPresent(this::cargarEnFormulario);
        } catch (IllegalArgumentException ignored) {
            selectedId = null;
        }
    }

    private void cargarEnFormulario(ProductoDestino r) {
        selectedId = r.getIdRelacion();
        seleccionarEnCombo(productoCombo, productoLabelToId, r.getIdProducto());
        seleccionarEnCombo(destinoCombo, destinoLabelToId, r.getIdDestino());
        activaCheck.setSelected(r.isActiva());
        idLabel.setText(r.getIdRelacion() != null ? r.getIdRelacion().toString() : "-");
    }

    private void limpiarFormulario() {
        selectedId = null;
        if (productoCombo.getItemCount() > 0) productoCombo.setSelectedIndex(0);
        if (destinoCombo.getItemCount() > 0) destinoCombo.setSelectedIndex(0);
        activaCheck.setSelected(true);
        idLabel.setText("-");
        tablePanel.getTable().clearSelection();
    }

    private void refrescarTabla() {
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        List<ProductoDestino> relaciones = productoDestinoService.listarTodas();
        for (ProductoDestino r : relaciones) {
            String prod = formatProducto(productoService.buscarPorId(r.getIdProducto()).orElse(null));
            String dest = formatDestino(destinoService.buscarPorId(r.getIdDestino()).orElse(null));
            model.addRow(new Object[]{
                    prod,
                    dest,
                    r.isActiva() ? "Sí" : "No",
                    r.getIdRelacion()
            });
        }
    }

    /** Recarga las listas de productos y destinos disponibles en los selectores. */
    public void recargarCatalogos() {
        UUID prevProd = getSelectedId(productoCombo, productoLabelToId);
        UUID prevDest = getSelectedId(destinoCombo, destinoLabelToId);
        productoLabelToId.clear();
        destinoLabelToId.clear();
        productoCombo.removeAllItems();
        destinoCombo.removeAllItems();
        for (Producto p : productoService.listarTodos()) {
            if (!p.isActiva()) continue;
            String label = formatProducto(p);
            productoLabelToId.put(label, p.getIdProducto());
            productoCombo.addItem(label);
        }
        for (Destino d : destinoService.listarTodos()) {
            if (!d.isActiva()) continue;
            String label = formatDestino(d);
            destinoLabelToId.put(label, d.getIdDestino());
            destinoCombo.addItem(label);
        }
        seleccionarEnCombo(productoCombo, productoLabelToId, prevProd);
        seleccionarEnCombo(destinoCombo, destinoLabelToId, prevDest);
    }

    private UUID getSelectedId(JComboBox<String> combo, Map<String, UUID> map) {
        String sel = (String) combo.getSelectedItem();
        if (sel == null) return null;
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

    private String formatProducto(Producto p) {
        if (p == null) return "(producto no encontrado)";
        return safe(p.getCodigo()) + " - " + safe(p.getNombre());
    }

    private String formatDestino(Destino d) {
        if (d == null) return "(destino no encontrado)";
        return safe(d.getCodigo()) + " - " + safe(d.getNombre());
    }

    private String safe(String s) { return s == null ? "" : s; }
}
