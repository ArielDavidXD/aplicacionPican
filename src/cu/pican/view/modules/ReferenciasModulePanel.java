package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.service.AnalisisService;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;

/**
 * Módulo de solo lectura para el Especialista de Producción (CU-01 a CU-03).
 * No muestra análisis operativo ni datos de producción: ese contenido pertenece a CU-14.
 */
public class ReferenciasModulePanel extends JPanel {

    public ReferenciasModulePanel(AppContext context) {
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Referencias históricas (CU-01 a CU-03)",
                "Consulta de solo lectura de las cuatro tablas oficiales PICAN. No se modifica su contenido."),
                BorderLayout.NORTH);

        DefaultTableModel referencias = new DefaultTableModel(
                new Object[]{"Tabla", "Indicador", "Año", "Valor", "Unidad", "Fuente"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        DefaultTableModel metas = new DefaultTableModel(
                new Object[]{"Indicador", "Real 2019", "Estimado 2030", "Incremento %", "Unidad", "Fuente"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        AnalisisService analisis = context == null ? null : context.getAnalisisService();
        if (analisis != null) {
            try {
                for (IndicadorHistorico h : analisis.consultarTodasLasReferencias()) {
                    referencias.addRow(new Object[]{h.getTabla().getTitulo(), h.getIndicador(), h.getAnio(),
                            h.getValor(), h.getUnidadMedida(), h.getFuente()});
                }
                for (MetaEstrategica m : analisis.consultarMetas()) {
                    metas.addRow(new Object[]{m.getIndicador(), m.getValorReal2019(), m.getValorEstimado2030(),
                            m.getIncrementoPorcentual(), m.getUnidadMedida(), m.getFuente()});
                }
            } catch (RuntimeException ex) {
                referencias.addRow(new Object[]{"No disponible", ex.getMessage(), "", "", "", ""});
            }
        }

        JTable tablaReferencias = new JTable(referencias);
        JTable tablaMetas = new JTable(metas);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tablaReferencias), new JScrollPane(tablaMetas));
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);
    }

    public ReferenciasModulePanel() {
        this(new AppContext());
    }
}
