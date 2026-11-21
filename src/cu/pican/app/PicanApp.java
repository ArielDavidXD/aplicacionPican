package cu.pican.app;

import cu.pican.view.LoginDialog;
import cu.pican.view.MainFrame;
import cu.pican.persistence.PersistenciaException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Punto de entrada de la aplicación PICAN.
 * El modo de persistencia se decide en AppContext según la configuración externa.
 * Los datos de demostración solo se cargan en modo memoria; PostgreSQL conserva datos reales.
 */
public class PicanApp {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Se mantiene el LookAndFeel por defecto si falla.
        }

        SwingUtilities.invokeLater(() -> {
            AppContext context;
            try {
                context = new AppContext();
            } catch (PersistenciaException ex) {
                JOptionPane.showMessageDialog(null,
                        "No se pudo iniciar PICAN con la persistencia configurada.\n\n"
                        + ex.getMessage()
                        + "\n\nRevise la configuración y el estado del servidor PostgreSQL.",
                        "Error de persistencia", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (context.esMemoria()) {
                // Datos de demostración únicamente en memoria; nunca se escriben en PostgreSQL.
                DemoDataInitializer.initialize(context);
            }
            // CU-16: el acceso es obligatorio antes de mostrar cualquier módulo.
            if (!LoginDialog.mostrar(null, context)) {
                return;
            }
            MainFrame frame = new MainFrame(context);
            frame.setVisible(true);
        });
    }
}
