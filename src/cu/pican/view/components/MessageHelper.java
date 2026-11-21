package cu.pican.view.components;

import java.awt.Component;
import javax.swing.JOptionPane;

/**
 * Utilidad para mostrar mensajes consistentes de información, validación y confirmación.
 */
public final class MessageHelper {
    private MessageHelper() {}

    public static void showInfo(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Información", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showValidation(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Validación", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        int result = JOptionPane.showConfirmDialog(parent, message, "Confirmación",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }
}
