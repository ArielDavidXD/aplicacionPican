package cu.pican.view;

import cu.pican.controller.NavigationController;
import cu.pican.view.components.UITheme;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Menú lateral para navegar entre los módulos principales.
 */
public class SideMenuPanel extends JPanel {

    public SideMenuPanel(NavigationController navigationController) {
        this(navigationController, null);
    }

    public SideMenuPanel(NavigationController navigationController, String rol) {
        setLayout(new GridLayout(0, 1, 0, 4));
        setBackground(UITheme.SIDEBAR_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, UITheme.BORDER),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)));
        setPreferredSize(new Dimension(250, 0));

        Map<String, String> modules = new LinkedHashMap<>();
        modules.put("inicio", "Inicio / Dashboard");
        modules.put("referencias", "Referencias (CU-01 a CU-03)");
        modules.put("huevos", "Producción de huevos");
        modules.put("carne", "Producción de carne");
        modules.put("consolidacion", "Consolidación de producción");
        modules.put("entregas", "Entregas y vales");
        modules.put("recepcion", "Recepción de entregas");
        modules.put("incidencias", "Incidencias");
        modules.put("informes", "Análisis e informes");
        modules.put("sincronizacion", "Sincronización mediante USB");
        modules.put("administracion", "Administración de usuarios y catálogos");

        Set<String> permitidos = PermisosInterfaz.modulosPermitidos(rol);
        for (Map.Entry<String, String> entry : modules.entrySet()) {
            if (permitidos.contains(entry.getKey())) {
                add(createMenuButton(entry.getKey(), entry.getValue(), navigationController));
            }
        }
    }

    private JButton createMenuButton(String moduleId, String label, NavigationController navigationController) {
        JButton button = new JButton(label);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setFocusPainted(false);
        button.setBackground(Color.WHITE);
        button.setForeground(UITheme.TEXT);
        button.setFont(new Font("SansSerif", Font.PLAIN, 13));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        button.addActionListener(e -> navigationController.showModule(moduleId));
        return button;
    }
}
