package cu.pican.controller;

import java.awt.CardLayout;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;

/**
 * Controla la navegación entre módulos en el panel central usando CardLayout.
 */
public class NavigationController {

    private final JPanel contentPanel;
    private final CardLayout cardLayout;
    private final Map<String, String> moduleTitles = new HashMap<>();
    private Map<String, java.util.Set<String>> rolesPorModulo;
    private String rolActual;
    private String currentModuleId;

    public NavigationController(JPanel contentPanel, CardLayout cardLayout) {
        this.contentPanel = contentPanel;
        this.cardLayout = cardLayout;
    }

    public void registerModule(String moduleId, String title, JPanel modulePanel) {
        moduleTitles.put(moduleId, title);
        contentPanel.add(modulePanel, moduleId);
    }

    /** Segunda barrera: impide navegar por código a un módulo ajeno al rol de la sesión. */
    public void aplicarPermisos(Map<String, java.util.Set<String>> rolesPorModulo, String rolActual) {
        this.rolesPorModulo = rolesPorModulo;
        this.rolActual = rolActual;
    }

    public boolean puedeMostrar(String moduleId) {
        if (rolesPorModulo == null) {
            return true;
        }
        if (rolActual == null) {
            return false;
        }
        java.util.Set<String> permitidos = rolesPorModulo.get(moduleId);
        return permitidos != null && permitidos.contains(rolActual);
    }

    public void showModule(String moduleId) {
        if (!moduleTitles.containsKey(moduleId) || !puedeMostrar(moduleId)) {
            return;
        }
        currentModuleId = moduleId;
        cardLayout.show(contentPanel, moduleId);
    }

    public String getCurrentModuleTitle() {
        return moduleTitles.getOrDefault(currentModuleId, "");
    }

    public String getCurrentModuleId() {
        return currentModuleId;
    }
}
