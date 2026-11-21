package cu.pican.view;

import cu.pican.model.User;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.UITheme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Encabezado superior con nombre del sistema y usuario activo.
 */
public class HeaderPanel extends JPanel {

    public HeaderPanel(User activeUser) {
        this(activeUser, null);
    }

    /** Constructor con acción de cierre de sesión (CU-16). */
    public HeaderPanel(User activeUser, Runnable onLogout) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(0, 56));
        setBackground(UITheme.PRIMARY);
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JLabel systemName = new JLabel("PICAN - Sistema de Gestión Avícola");
        systemName.setForeground(Color.WHITE);
        systemName.setFont(UITheme.TITLE_FONT.deriveFont(18f));

        String userText = "Usuario activo: " + activeUser.getDisplayName() + " (" + activeUser.getRole() + ")";
        JLabel userLabel = new JLabel(userText);
        userLabel.setForeground(new Color(235, 242, 248));
        userLabel.setFont(UITheme.NORMAL_FONT);

        add(systemName, BorderLayout.WEST);
        if (onLogout == null) {
            add(userLabel, BorderLayout.EAST);
        } else {
            JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            derecha.setOpaque(false);
            derecha.add(userLabel);
            AppButton logoutButton = new AppButton("Cerrar sesión");
            logoutButton.addActionListener(e -> onLogout.run());
            derecha.add(logoutButton);
            add(derecha, BorderLayout.EAST);
        }
    }
}
