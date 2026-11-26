package cu.pican.view;

import cu.pican.controller.app.AppContext;
import cu.pican.controller.NavigationController;
import cu.pican.view.components.UITheme;
import cu.pican.view.modules.AdminModulePanel;
import cu.pican.view.modules.ConsolidationModulePanel;
import cu.pican.view.modules.DashboardModulePanel;
import cu.pican.view.modules.DeliveriesModulePanel;
import cu.pican.view.modules.EggProductionModulePanel;
import cu.pican.view.modules.IncidentsModulePanel;
import cu.pican.view.modules.MeatProductionModulePanel;
import cu.pican.view.modules.ReceptionModulePanel;
import cu.pican.view.modules.ReferenciasModulePanel;
import cu.pican.view.modules.ReportsModulePanel;
import cu.pican.view.modules.SyncModulePanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.function.Supplier;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Ventana principal de PICAN con menú lateral, encabezado y área central de módulos.
 */
public class MainFrame extends JFrame {

    private final AppContext appContext;

    public MainFrame(AppContext appContext) {
        super("PICAN - Sistema de Gestión Avícola");
        this.appContext = appContext;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setSize(1200, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel contentPanel = new JPanel(new CardLayout());
        contentPanel.setBackground(UITheme.CONTENT_BG);

        NavigationController navigationController = new NavigationController(contentPanel, (CardLayout) contentPanel.getLayout());
        String rol = rolActual();
        navigationController.aplicarPermisos(PermisosInterfaz.restricciones(), rol);
        registerModules(navigationController, rol);

        add(new HeaderPanel(appContext.getAuthService().getUsuarioSesion(), this::cerrarSesion), BorderLayout.NORTH);
        add(new SideMenuPanel(navigationController, rol), BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        navigationController.showModule("inicio");
    }

    /** Cierra la sesión compartida y vuelve a la pantalla de acceso; la ventana de módulos se descarta. */
    private void cerrarSesion() {
        appContext.getAuthService().logout();
        dispose();
        SwingUtilities.invokeLater(() -> {
            if (LoginDialog.mostrar(null, appContext)) {
                new MainFrame(appContext).setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }

    private String rolActual() {
        cu.pican.model.Usuario usuario = appContext.getAuthService().getUsuarioAutenticado();
        return usuario == null || usuario.getRol() == null ? null : usuario.getRol().getNombre();
    }

    private void registerModules(NavigationController navigationController, String rol) {
        registrar(navigationController, rol, "inicio", "Inicio / Dashboard",
                () -> new DashboardModulePanel(appContext));
        registrar(navigationController, rol, "referencias", "Referencias (CU-01 a CU-03)",
                () -> new ReferenciasModulePanel(appContext));
        registrar(navigationController, rol, "huevos", "Producción de huevos",
                () -> new EggProductionModulePanel(appContext));
        registrar(navigationController, rol, "carne", "Producción de carne",
                () -> new MeatProductionModulePanel(appContext));
        registrar(navigationController, rol, "consolidacion", "Consolidación de producción",
                () -> new ConsolidationModulePanel(appContext));
        registrar(navigationController, rol, "entregas", "Entregas y vales",
                () -> new DeliveriesModulePanel(appContext));
        registrar(navigationController, rol, "recepcion", "Recepción de entregas",
                () -> new ReceptionModulePanel(appContext));
        registrar(navigationController, rol, "incidencias", "Incidencias",
                () -> new IncidentsModulePanel(appContext));
        registrar(navigationController, rol, "informes", "Análisis e informes",
                () -> new ReportsModulePanel(appContext));
        registrar(navigationController, rol, "sincronizacion", "Sincronización mediante USB",
                () -> new SyncModulePanel(appContext));
        registrar(navigationController, rol, "administracion", "Administración de usuarios y catálogos",
                () -> new AdminModulePanel(appContext));
    }

    /** Solo se construyen los paneles autorizados: evita consultar servicios ajenos al rol. */
    private void registrar(NavigationController navigationController, String rol,
                           String id, String titulo, Supplier<JPanel> fabrica) {
        if (PermisosInterfaz.puedeAcceder(id, rol)) {
            navigationController.registerModule(id, titulo, fabrica.get());
        }
    }
}
