package cu.pican.view;

import cu.pican.controller.app.AppContext;
import cu.pican.controller.service.AuthService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Pantalla de acceso de PICAN (CU-16). La aplicación no muestra módulos sin autenticación.
 * Los mensajes distinguen credenciales incorrectas de cuenta desactivada.
 */
public class LoginDialog extends JDialog {

    private final AuthService authService;
    private final JTextField usuarioField = new JTextField(20);
    private final JPasswordField contrasenaField = new JPasswordField(20);
    private final JLabel mensajeLabel = new JLabel(" ");
    private boolean autenticado;

    public LoginDialog(Window owner, AuthService authService) {
        super(owner, "PICAN - Inicio de sesión", ModalityType.APPLICATION_MODAL);
        this.authService = authService;
        setResizable(false);
        setLayout(new BorderLayout(12, 12));
        JPanel content = (JPanel) getContentPane();
        content.setBackground(UITheme.CONTENT_BG);
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));

        JLabel titulo = new JLabel("Sistema de Gestión Avícola PICAN");
        titulo.setFont(UITheme.TITLE_FONT);
        titulo.setForeground(UITheme.PRIMARY_DARK);
        JLabel subtitulo = new JLabel("Acceso para trabajadores del sistema");
        subtitulo.setFont(UITheme.SMALL_FONT);
        subtitulo.setForeground(UITheme.MUTED_TEXT);
        JPanel norte = new JPanel(new BorderLayout(2, 2));
        norte.setOpaque(false);
        norte.add(titulo, BorderLayout.NORTH);
        norte.add(subtitulo, BorderLayout.CENTER);
        add(norte, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Nombre de usuario:"), c);
        c.gridx = 1;
        form.add(usuarioField, c);

        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Contraseña:"), c);
        c.gridx = 1;
        form.add(contrasenaField, c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        mensajeLabel.setForeground(new Color(170, 40, 40));
        mensajeLabel.setFont(UITheme.SMALL_FONT);
        form.add(mensajeLabel, c);
        add(form, BorderLayout.CENTER);

        AppButton entrar = new AppButton("Iniciar sesión");
        AppButton salir = new AppButton("Salir");
        getRootPane().setDefaultButton(entrar);
        entrar.addActionListener(e -> intentarAcceso());
        salir.addActionListener(e -> {
            autenticado = false;
            dispose();
        });

        JPanel botones = new JPanel();
        botones.setOpaque(false);
        botones.add(entrar);
        botones.add(salir);
        add(botones, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                autenticado = false;
                dispose();
            }
        });
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(usuarioField::requestFocusInWindow);
    }

    /** Muestra el acceso y devuelve si la autenticación (incluido el cambio obligatorio) terminó bien. */
    public static boolean mostrar(Component parent, AppContext context) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        LoginDialog dialog = new LoginDialog(owner, context.getAuthService());
        dialog.setVisible(true);
        return dialog.isAutenticado();
    }

    public boolean isAutenticado() {
        return autenticado;
    }

    private void intentarAcceso() {
        mensajeLabel.setText(" ");
        char[] contrasena = contrasenaField.getPassword();
        try {
            authService.login(usuarioField.getText(), new String(contrasena));
        } catch (ValidacionException ex) {
            mensajeLabel.setText(ex.getMessage());
            return;
        } finally {
            java.util.Arrays.fill(contrasena, '\0');
        }
        if (authService.requiereCambioContrasena()) {
            CambioContrasenaDialog cambio = new CambioContrasenaDialog(this, authService);
            cambio.setVisible(true);
            if (!cambio.isCambiada()) {
                authService.logout();
                mensajeLabel.setText("Debe cambiar la contraseña temporal para acceder al sistema.");
                return;
            }
        }
        autenticado = true;
        dispose();
    }
}
