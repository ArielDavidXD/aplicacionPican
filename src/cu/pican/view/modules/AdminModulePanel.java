package cu.pican.view.modules;

import cu.pican.controller.app.AppContext;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/**
 * Módulo de Administración con secciones internas (pestañas) para usuarios (CU-16), estructura y catálogos (CU-17)
 * e importación desde Excel (CU-18).
 * La relación Unidad Asociada-UEB sigue pendiente de confirmación documental.
 */
public class AdminModulePanel extends JPanel {

    public AdminModulePanel(AppContext context) {
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Administración",
                "Gestión de usuarios y roles (CU-16) y de estructura y catálogos operativos (CU-17). " +
                        "Persistencia configurable (MEMORY o PostgreSQL) según el despliegue."), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UITheme.NORMAL_FONT);
        NaveAdminPanel navePanel = new NaveAdminPanel(context.getNaveService(), context.getUebService());
        ProductoDestinoAdminPanel productoDestinoPanel = new ProductoDestinoAdminPanel(
                context.getProductoDestinoService(), context.getProductoService(), context.getDestinoService());
        UsuarioAdminPanel usuariosPanel = new UsuarioAdminPanel(context.getUsuarioService());

        tabs.addTab("Usuarios y roles", usuariosPanel);
        tabs.addTab("UEB", new UEBAdminPanel(context.getUebService()));
        tabs.addTab("Naves", navePanel);
        tabs.addTab("Unidades asociadas", new UnidadAsociadaAdminPanel(context.getUnidadAsociadaService()));
        tabs.addTab("Productos", new ProductoAdminPanel(context.getProductoService()));
        tabs.addTab("Destinos", new DestinoAdminPanel(context.getDestinoService()));
        tabs.addTab("Relaciones producto-destino", productoDestinoPanel);
        tabs.addTab("Referencias oficiales",
                new ReferenciasOficialesAdminPanel(context.getGestionReferenciasService()));
        tabs.addTab("Importar datos", new ImportacionAdminPanel(context));
        tabs.addTab("Copias de seguridad", new CopiasSeguridadPanel(context));

        tabs.addChangeListener(e -> {
            Object selected = tabs.getSelectedComponent();
            if (selected == usuariosPanel) {
                usuariosPanel.recargar();
            } else if (selected == navePanel) {
                navePanel.recargarUEBs();
            } else if (selected == productoDestinoPanel) {
                productoDestinoPanel.recargarCatalogos();
                productoDestinoPanel.revalidate();
                productoDestinoPanel.repaint();
            }
        });

        add(tabs, BorderLayout.CENTER);
    }
}
