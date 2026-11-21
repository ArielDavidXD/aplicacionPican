package cu.pican.view;

import cu.pican.service.AuthService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Diálogo obligatorio cuando la contraseña entregada es temporal (primera cuenta o alta por administrador). */
public class CambioContrasenaDialog extends JDialog {

    private final AuthService authService;
    private final JPasswordField actualField = new JPasswordField(22);
    private final JPasswordField nuevaField = new JPasswordField(22);
    private final JPasswordField confirmarField = new JPasswordField(22);
    private boolean cambiada;

    public CambioContrasenaDialog(Window owner, AuthService authService) {
        super(owner, "PICAN - Cambio de contraseña obligatorio", ModalityType.APPLICATION_MODAL);
        this.authService = authService;
        setResizable(false);
        setLayout(new BorderLayout(12, 12));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        ((JPanel) getContentPane()).setBackground(UITheme.CONTENT_BG);

        JLabel titulo = new JLabel("Debe establecer una contraseña propia para continuar");
        titulo.setFont(UITheme.SECTION_FONT);
        titulo.setForeground(UITheme.PRIMARY_DARK);
        add(titulo, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Contraseña actual:"), c);
        c.gridx = 1;
        form.add(actualField, c);

        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Nueva contraseña:"), c);
        c.gridx = 1;
        form.add(nuevaField, c);

        c.gridx = 0; c.gridy = 2;
        form.add(new JLabel("Confirmar nueva:"), c);
        c.gridx = 1;
        form.add(confirmarField, c);
        add(form, BorderLayout.CENTER);

        AppButton aceptar = new AppButton("Guardar y continuar");
        AppButton cancelar = new AppButton("Cancelar");
        getRootPane().setDefaultButton(aceptar);
        aceptar.addActionListener(e -> cambiar());
        cancelar.addActionListener(e -> {
            cambiada = false;
            dispose();
        });

        JPanel botones = new JPanel();
        botones.setOpaque(false);
        botones.add(aceptar);
        botones.add(cancelar);
        add(botones, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cambiada = false;
                dispose();
            }
        });
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isCambiada() {
        return cambiada;
    }

    private void cambiar() {
        char[] nueva = nuevaField.getPassword();
        char[] confirmar = confirmarField.getPassword();
        if (!java.util.Arrays.equals(nueva, confirmar)) {
            java.util.Arrays.fill(nueva, '\0');
            java.util.Arrays.fill(confirmar, '\0');
            JOptionPane.showMessageDialog(this, "Las contraseñas nuevas no coinciden.", "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        char[] actual = actualField.getPassword();
        try {
            authService.cambiarContrasenaSesion(new String(actual), new String(nueva));
            cambiada = true;
            dispose();
        } catch (ValidacionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validación", JOptionPane.WARNING_MESSAGE);
        } finally {
            java.util.Arrays.fill(actual, '\0');
            java.util.Arrays.fill(nueva, '\0');
            java.util.Arrays.fill(confirmar, '\0');
        }
    }
}
