package cu.pican.view.components;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Encabezado reutilizable para cada módulo (título y descripción breve).
 */
public class ModuleHeader extends JPanel {
    public ModuleHeader(String title, String description) {
        setLayout(new BorderLayout(0, 4));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.SECTION_FONT);
        titleLabel.setForeground(UITheme.TEXT);

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(UITheme.SMALL_FONT);
        descLabel.setForeground(UITheme.MUTED_TEXT);

        add(titleLabel, BorderLayout.NORTH);
        add(descLabel, BorderLayout.CENTER);
    }
}
