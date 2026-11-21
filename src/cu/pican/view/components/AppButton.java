package cu.pican.view.components;

import java.awt.Cursor;
import java.awt.event.ActionListener;
import javax.swing.JButton;

/**
 * Botón con estilo básico consistente para acciones identificables.
 */
public class AppButton extends JButton {
    public AppButton(String text) {
        super(text);
        setFont(UITheme.NORMAL_FONT);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public AppButton(String text, ActionListener accion) {
        this(text);
        addActionListener(accion);
    }
}
