package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Módulo Inicio / Dashboard con indicadores ficticios.
 * Requiere definición funcional de indicadores antes de sustituir los datos de ejemplo
 * por consultas reales; no se inventan métricas en esta etapa.
 */
public class DashboardModulePanel extends JPanel {
    public DashboardModulePanel(AppContext context) {
        this();
    }

    public DashboardModulePanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Inicio / Dashboard",
                "Resumen general del sistema. Datos mostrados a modo de ejemplo (ficticios)."), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);

        JPanel cards = new JPanel(new GridLayout(1, 3, 12, 12));
        cards.setOpaque(false);
        cards.add(createCard("Producción de huevos", "Dato ficticio: pendiente de consolidar"));
        cards.add(createCard("Producción de carne", "Dato ficticio: pendiente de consolidar"));
        cards.add(createCard("Programa Avícola 2030", "Dato ficticio: seguimiento en definición"));

        String[] cols = {"Indicador", "Valor (ficticio)", "Observación"};
        Object[][] rows = {
                {"Huevos reportados", "-", "Pendiente definir estructura de datos"},
                {"Carne reportada", "-", "Pendiente definir estructura de datos"},
                {"Entregas registradas", "-", "Pendiente definir flujo detallado"}
        };
        center.add(cards, BorderLayout.NORTH);
        center.add(new DataTablePanel(cols, rows), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private JPanel createCard(String title, String value) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UITheme.SIDEBAR_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JLabel t = new JLabel(title);
        t.setFont(UITheme.NORMAL_FONT);
        t.setForeground(UITheme.TEXT);
        JLabel v = new JLabel(value);
        v.setFont(UITheme.SMALL_FONT);
        v.setForeground(UITheme.MUTED_TEXT);
        panel.add(t, BorderLayout.NORTH);
        panel.add(v, BorderLayout.CENTER);
        return panel;
    }
}
