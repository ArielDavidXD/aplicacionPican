package cu.pican.view.modules;

import cu.pican.model.Rol;
import cu.pican.model.Usuario;
import cu.pican.service.UsuarioService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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
 * Gestión de cuentas PICAN (CU-16). La tabla nunca muestra contraseñas ni hashes.
 * Solo se permite baja lógica (activar/desactivar); la baja física está bloqueada por el servicio.
 */
public class UsuarioAdminPanel extends JPanel {

    private final UsuarioService usuarioService;
    private final DataTablePanel tablePanel;

    private final JTextField nombreUsuarioField = new JTextField(18);
    private final JTextField nombreCompletoField = new JTextField(26);
    private final JPasswordField contrasenaField = new JPasswordField(18);
    private final JComboBox<String> rolCombo = new JComboBox<>();
    private final JCheckBox activoCheck = new JCheckBox("Activa", true);
    private final JLabel idLabel = new JLabel("-");
    private final JLabel notaLabel = new JLabel("La contraseña no se muestra; se cambia en el primer acceso.");

    private UUID selectedId;

    public UsuarioAdminPanel(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (Rol rol : usuarioService.rolesDisponibles()) {
            rolCombo.addItem(rol.getNombre());
        }

        String[] cols = {"Usuario", "Nombre completo", "Rol", "Estado", "Creado", "ID (UUID)"};
        tablePanel = new DataTablePanel("Usuarios registrados", cols, new Object[0][0]);
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
                "Registro / consulta de usuarios"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Nombre de usuario:"), c);
        c.gridx = 1;
        form.add(nombreUsuarioField, c);

        c.gridx = 2;
        form.add(new JLabel("Nombre completo:"), c);
        c.gridx = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        form.add(nombreCompletoField, c);

        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Contraseña inicial:"), c);
        c.gridx = 1;
        form.add(contrasenaField, c);

        c.gridx = 2;
        form.add(new JLabel("Rol:"), c);
        c.gridx = 3;
        form.add(rolCombo, c);

        c.gridx = 0; c.gridy = 2;
        form.add(new JLabel("Estado:"), c);
        c.gridx = 1;
        form.add(activoCheck, c);

        c.gridx = 2;
        form.add(new JLabel("ID (consulta):"), c);
        c.gridx = 3;
        idLabel.setFont(UITheme.SMALL_FONT);
        idLabel.setForeground(UITheme.MUTED_TEXT);
        form.add(idLabel, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 4;
        notaLabel.setFont(UITheme.SMALL_FONT);
        notaLabel.setForeground(UITheme.MUTED_TEXT);
        form.add(notaLabel, c);
        return form;
    }

    private JPanel buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);

        AppButton nuevoBtn = new AppButton("Nuevo");
        AppButton registrarBtn = new AppButton("Registrar");
        AppButton actualizarBtn = new AppButton("Actualizar datos");
        AppButton rolBtn = new AppButton("Asignar rol");
        AppButton activarBtn = new AppButton("Activar");
        AppButton desactivarBtn = new AppButton("Desactivar");

        nuevoBtn.addActionListener(e -> limpiarFormulario());
        registrarBtn.addActionListener(e -> registrar());
        actualizarBtn.addActionListener(e -> actualizar());
        rolBtn.addActionListener(e -> asignarRol());
        activarBtn.addActionListener(e -> cambiarEstado(true));
        desactivarBtn.addActionListener(e -> cambiarEstado(false));

        actions.add(nuevoBtn);
        actions.add(registrarBtn);
        actions.add(actualizarBtn);
        actions.add(rolBtn);
        actions.add(activarBtn);
        actions.add(desactivarBtn);
        return actions;
    }

    private void registrar() {
        try {
            Usuario creado = usuarioService.registrar(nombreUsuarioField.getText(), nombreCompletoField.getText(),
                    new String(contrasenaField.getPassword()), (String) rolCombo.getSelectedItem());
            MessageHelper.showInfo(this, "Usuario registrado: " + creado.getNombreUsuario()
                    + ". Deberá cambiar su contraseña en el primer acceso.");
            limpiarFormulario();
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar el usuario: " + ex.getMessage());
        }
    }

    private void actualizar() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un usuario de la tabla.");
            return;
        }
        try {
            usuarioService.actualizarDatos(selectedId, nombreCompletoField.getText(), (String) rolCombo.getSelectedItem());
            MessageHelper.showInfo(this, "Datos del usuario actualizados.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void asignarRol() {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un usuario de la tabla.");
            return;
        }
        try {
            usuarioService.asignarRol(selectedId, (String) rolCombo.getSelectedItem());
            MessageHelper.showInfo(this, "Rol asignado correctamente.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void cambiarEstado(boolean activar) {
        if (selectedId == null) {
            MessageHelper.showValidation(this, "Seleccione un usuario de la tabla.");
            return;
        }
        try {
            if (activar) {
                usuarioService.activar(selectedId);
            } else {
                usuarioService.desactivar(selectedId);
            }
            MessageHelper.showInfo(this, activar ? "Cuenta activada." : "Cuenta desactivada (baja lógica).");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void cargarSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0) {
            return;
        }
        try {
            selectedId = UUID.fromString(String.valueOf(tablePanel.getTableModel().getValueAt(row, 5)));
            usuarioService.buscarPorId(selectedId).ifPresent(this::cargarEnFormulario);
        } catch (IllegalArgumentException ignored) {
            selectedId = null;
        }
    }

    private void cargarEnFormulario(Usuario usuario) {
        selectedId = usuario.getIdUsuario();
        nombreUsuarioField.setText(usuario.getNombreUsuario());
        nombreUsuarioField.setEditable(false);
        nombreCompletoField.setText(usuario.getNombreCompleto());
        contrasenaField.setText("");
        if (usuario.getRol() != null) {
            rolCombo.setSelectedItem(usuario.getRol().getNombre());
        }
        activoCheck.setSelected(usuario.isActivo());
        idLabel.setText(usuario.getIdUsuario() != null ? usuario.getIdUsuario().toString() : "-");
    }

    private void limpiarFormulario() {
        selectedId = null;
        nombreUsuarioField.setText("");
        nombreUsuarioField.setEditable(true);
        nombreCompletoField.setText("");
        contrasenaField.setText("");
        if (rolCombo.getItemCount() > 0) {
            rolCombo.setSelectedIndex(0);
        }
        activoCheck.setSelected(true);
        idLabel.setText("-");
        tablePanel.getTable().clearSelection();
    }

    private void refrescarTabla() {
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        List<Usuario> usuarios = usuarioService.listarTodos();
        for (Usuario usuario : usuarios) {
            model.addRow(new Object[]{
                    usuario.getNombreUsuario(),
                    usuario.getNombreCompleto(),
                    usuario.getRol() == null ? "-" : usuario.getRol().getNombre(),
                    usuario.isActivo() ? "Activa" : "Inactiva",
                    usuario.getFechaCreacion() == null ? "-" : usuario.getFechaCreacion().toString(),
                    usuario.getIdUsuario()
            });
        }
    }

    /** Permite refrescar la lista cuando se abre la pestaña de administración. */
    public void recargar() {
        refrescarTabla();
    }
}
