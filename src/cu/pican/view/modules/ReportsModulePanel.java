package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.view.components.UITheme;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Módulo de informes: mantiene el análisis del CU-14 y agrega la elaboración y entrega del CU-15
 * en una pestaña separada. No sustituye ni modifica la funcionalidad de análisis existente.
 */
public class ReportsModulePanel extends JPanel {

    public ReportsModulePanel(AppContext context) {
        setLayout(new BorderLayout());
        setBackground(UITheme.CONTENT_BG);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Análisis e indicadores (CU-14)", new AnalysisModulePanel(context));
        tabs.addTab("Elaboración y entrega (CU-15)", new ReportManagementPanel(context));
        add(tabs, BorderLayout.CENTER);
    }

    /** Constructor de compatibilidad con el registro anterior del módulo. */
    public ReportsModulePanel() {
        this(new AppContext());
    }
}