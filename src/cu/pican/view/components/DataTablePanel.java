package cu.pican.view.components;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Panel reutilizable que envuelve una tabla de consulta.
 * Mantiene compatibilidad con llamadas existentes (título por defecto para datos provisionales).
 */
public class DataTablePanel extends JPanel {

    private final DefaultTableModel tableModel;
    private final JTable table;

    public DataTablePanel(String[] columns, Object[][] rows) {
        this("Registros (datos ficticios)", columns, rows);
    }

    public DataTablePanel(String title, String[] columns, Object[][] rows) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER), title));

        tableModel = new DefaultTableModel(rows, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setFont(UITheme.NORMAL_FONT);
        table.setRowHeight(24);
        table.getTableHeader().setFont(UITheme.NORMAL_FONT);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public JTable getTable() {
        return table;
    }

    public DefaultTableModel getTableModel() {
        return tableModel;
    }
}
