package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.importacion.EstadoFila;
import cu.pican.importacion.ResultadoFila;
import cu.pican.importacion.ResultadoImportacion;
import cu.pican.importacion.TipoImportacion;
import cu.pican.model.Rol;
import cu.pican.service.ImportacionService;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;

/**
 * CU-18. La interfaz solo selecciona archivo/tipo y muestra resultados.
 * Toda validación, autorización y persistencia permanece en ImportacionService.
 */
public class ImportacionAdminPanel extends JPanel {

    private final ImportacionService servicio;
    private final JComboBox<TipoImportacion> tipos = new JComboBox<>(TipoImportacion.values());
    private final JTextField ruta = new JTextField(34);
    private final DataTablePanel vistaPrevia;
    private final JLabel resumen = new JLabel("Seleccione el tipo de datos y un archivo .xlsx.");
    private ResultadoImportacion ultimaVista;

    public ImportacionAdminPanel(AppContext context) {
        this.servicio = context == null ? null : context.getImportacionService();
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel seleccion = new JPanel();
        seleccion.setOpaque(false);
        seleccion.setLayout(new BoxLayout(seleccion, BoxLayout.Y_AXIS));
        seleccion.add(new JLabel("Tipo de datos oficial"));
        tipos.setFont(UITheme.NORMAL_FONT);
        tipos.addActionListener((ActionEvent e) -> limpiarVista());
        seleccion.add(tipos);
        seleccion.add(new JLabel("Archivo Excel (.xlsx)"));
        ruta.setFont(UITheme.NORMAL_FONT);
        ruta.setEditable(false);
        seleccion.add(ruta);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.setOpaque(false);
        botones.add(new AppButton("Examinar…", e -> examinar()));
        botones.add(new AppButton("Previsualizar", e -> previsualizar()));
        botones.add(new AppButton("Confirmar importación", e -> confirmar()));
        seleccion.add(botones);
        add(seleccion, BorderLayout.NORTH);

        vistaPrevia = new DataTablePanel("Vista previa de filas",
                new String[]{"Fila", "Estado", "Motivo"}, new Object[0][3]);
        add(vistaPrevia, BorderLayout.CENTER);

        resumen.setFont(UITheme.NORMAL_FONT);
        add(resumen, BorderLayout.SOUTH);

        if (!esAdministrador(context)) {
            tipos.setEnabled(false);
            ruta.setEnabled(false);
            resumen.setText("CU-18 está disponible solo para el Administrador; con otro rol la importación se rechaza en el servicio.");
        }
    }

    private boolean esAdministrador(AppContext context) {
        if (context == null || context.getAuthService().getUsuarioAutenticado() == null
                || context.getAuthService().getUsuarioAutenticado().getRol() == null) {
            return false;
        }
        return Rol.ADMINISTRADOR.equals(
                context.getAuthService().getUsuarioAutenticado().getRol().getNombre());
    }

    private void examinar() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Seleccionar archivo Excel");
        selector.setFileFilter(new FileNameExtensionFilter("Excel 2007-365 (.xlsx)", "xlsx"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();
        ruta.setText(archivo == null ? "" : archivo.getAbsolutePath());
        limpiarVista();
    }

    private void previsualizar() {
        try {
            if (servicio == null) {
                throw new IllegalStateException("No hay contexto de aplicación inicializado.");
            }
            if (ruta.getText().isBlank()) {
                MessageHelper.showValidation(this, "Seleccione un archivo .xlsx.");
                return;
            }
            ultimaVista = servicio.preparar((TipoImportacion) tipos.getSelectedItem(), Path.of(ruta.getText()));
            Object[][] filas = ultimaVista.getDetalles().stream()
                    .map(this::aFila)
                    .toArray(Object[][]::new);
            vistaPrevia.getTableModel().setDataVector(filas,
                    new String[]{"Fila", "Estado", "Motivo"});
            resumen.setText(ultimaVista.resumen());
        } catch (RuntimeException ex) {
            limpiarVista();
            MessageHelper.showValidation(this, mensaje(ex));
        }
    }

    private void confirmar() {
        try {
            if (ultimaVista == null) {
                MessageHelper.showValidation(this, "Debe previsualizar antes de confirmar.");
                return;
            }
            if (!MessageHelper.confirm(this,
                    "Se cargarán solo las filas válidas.\n" + ultimaVista.resumen()
                            + "\n\n¿Desea continuar?")) {
                return;
            }
            ResultadoImportacion confirmada = servicio.confirmar(ultimaVista);
            resumen.setText(confirmada.resumen());
            MessageHelper.showInfo(this, "Importación terminada.\n" + confirmada.resumen());
            ultimaVista = null;
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, mensaje(ex));
        }
    }

    private Object[] aFila(ResultadoFila detalle) {
        EstadoFila estado = detalle.getEstado();
        return new Object[]{detalle.getNumero(), estado.getEtiqueta(), detalle.getMotivo()};
    }

    private void limpiarVista() {
        ultimaVista = null;
        vistaPrevia.getTableModel().setDataVector(new Object[0][3],
                new String[]{"Fila", "Estado", "Motivo"});
        resumen.setText("Vista previa pendiente; todavía no se ha guardado nada.");
    }

    private static String mensaje(RuntimeException ex) {
        String texto = ex.getMessage();
        return texto == null || texto.isBlank() ? ex.getClass().getSimpleName() : texto;
    }
}
