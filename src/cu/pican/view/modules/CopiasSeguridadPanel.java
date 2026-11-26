package cu.pican.view.modules;

import cu.pican.controller.app.AppContext;
import cu.pican.controller.backup.InformacionCopia;
import cu.pican.controller.backup.ResultadoCopia;
import cu.pican.controller.backup.ResultadoRestauracion;
import cu.pican.model.BackupAuditoria;
import cu.pican.model.Rol;
import cu.pican.controller.service.BackupService;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** CU-19: generacion manual, inspeccion y restauracion segura. Solo ADMINISTRADOR. */
public final class CopiasSeguridadPanel extends JPanel {

    private static final String CONFIRMACION_TEXTO = "RESTAURAR";

    private final AppContext context;
    private final BackupService servicio;
    private final JTextField rutaDestino = new JTextField(34);
    private final JTextField rutaCopia = new JTextField(34);
    private final JTextArea detalle = new JTextArea(12, 60);
    private final JLabel estado = new JLabel("Seleccione una carpeta para generar copias o un archivo .zip para restaurar.");
    private final JButton botonCrear = new AppButton("Crear copia manual");
    private final JButton botonInspeccionar = new AppButton("Verificar copia");
    private final JButton botonRestaurar = new AppButton("Restaurar copia");
    private final JButton botonHistorial = new AppButton("Ver auditorIA-a");

    public CopiasSeguridadPanel(AppContext context) {
        this.context = context;
        this.servicio = context == null ? null : context.getBackupService();
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(4, 4, 4, 4);
        formulario.add(new JLabel("Carpeta de destino de la copia"), g);
        g.gridx = 1;
        rutaDestino.setFont(UITheme.NORMAL_FONT);
        rutaDestino.setEditable(false);
        formulario.add(rutaDestino, g);
        g.gridx = 2;
        formulario.add(new AppButton("Examinar...", e -> examinarDestino()), g);

        g.gridx = 0;
        g.gridy = 1;
        formulario.add(new JLabel("Archivo de copia (.zip)"), g);
        g.gridx = 1;
        rutaCopia.setFont(UITheme.NORMAL_FONT);
        rutaCopia.setEditable(false);
        formulario.add(rutaCopia, g);
        g.gridx = 2;
        formulario.add(new AppButton("Examinar...", e -> examinarCopia()), g);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.setOpaque(false);
        botonCrear.addActionListener(e -> crearCopia());
        botonInspeccionar.addActionListener(e -> inspeccionar());
        botonRestaurar.addActionListener(e -> restaurar());
        botonHistorial.addActionListener(e -> historial());
        acciones.add(botonCrear);
        acciones.add(botonInspeccionar);
        acciones.add(botonRestaurar);
        acciones.add(botonHistorial);

        JPanel norte = new JPanel(new BorderLayout(0, 8));
        norte.setOpaque(false);
        norte.add(formulario, BorderLayout.NORTH);
        norte.add(acciones, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);

        detalle.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        detalle.setEditable(false);
        detalle.setLineWrap(false);
        JScrollPane desplazamiento = new JScrollPane(detalle);
        desplazamiento.setBorder(BorderFactory.createTitledBorder("Resultado de la operacion"));
        add(desplazamiento, BorderLayout.CENTER);

        estado.setFont(UITheme.NORMAL_FONT);
        add(estado, BorderLayout.SOUTH);

        if (!esAdministrador()) {
            establecerBotones(false);
            estado.setText("CU-19 es exclusivo del Administrador; el servicio rechaza cualquier otro rol.");
        } else if (context == null || context.esMemoria()) {
            estado.setText("Las copias reales requieren PostgreSQL. En modo MEMORY solo se permite la inspeccion de archivos.");
        }
    }

    private boolean esAdministrador() {
        if (context == null || context.getAuthService().getUsuarioAutenticado() == null
                || context.getAuthService().getUsuarioAutenticado().getRol() == null) {
            return false;
        }
        return Rol.ADMINISTRADOR.equals(
                context.getAuthService().getUsuarioAutenticado().getRol().getNombre());
    }

    private void examinarDestino() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Carpeta donde se publicara la copia");
        selector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File seleccion = selector.getSelectedFile();
        rutaDestino.setText(seleccion == null ? "" : seleccion.getAbsolutePath());
    }

    private void examinarCopia() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Archivo de copia PICAN");
        selector.setFileFilter(new FileNameExtensionFilter("Copias PICAN (*.zip)", "zip"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File seleccion = selector.getSelectedFile();
        rutaCopia.setText(seleccion == null ? "" : seleccion.getAbsolutePath());
        detalle.setText("");
    }

    private void crearCopia() {
        Path destino = Path.of(rutaDestino.getText().trim());
        ejecutar("Creando copia manual...", () -> {
            ResultadoCopia resultado = servicio.crearCopia(destino);
            if (!resultado.isExitoso()) {
                throw new IllegalStateException(resultado.getMensaje());
            }
            return "Copia publicada: " + resultado.getArchivo() + System.lineSeparator()
                    + "Tamano: " + resultado.getTamanoBytes() + " bytes" + System.lineSeparator()
                    + "SHA-256 del volcado: " + resultado.getSha256();
        });
    }

    private void inspeccionar() {
        Path archivo = Path.of(rutaCopia.getText().trim());
        ejecutar("Verificando integridad y compatibilidad...", () -> {
            InformacionCopia informacion = servicio.inspeccionar(archivo);
            return "Integridad: " + informacion.getDetalleIntegridad() + System.lineSeparator()
                    + "Compatibilidad: " + informacion.getDetalleCompatibilidad() + System.lineSeparator()
                    + "Formato: " + informacion.getManifiesto().getFormato()
                    + " v" + informacion.getManifiesto().getVersionFormato() + System.lineSeparator()
                    + "Aplicacion: " + informacion.getManifiesto().getVersionAplicacion() + System.lineSeparator()
                    + "Base origen: " + informacion.getManifiesto().getBaseDatos() + System.lineSeparator()
                    + "SHA-256 del volcado: " + informacion.getManifiesto().getSha256Dump();
        });
    }

    private void restaurar() {
        Path archivo = Path.of(rutaCopia.getText().trim());
        String resumen;
        try {
            InformacionCopia informacion = servicio.inspeccionar(archivo);
            if (!informacion.esValida()) {
                MessageHelper.showValidation(this, informacion.getDetalleCompatibilidad());
                return;
            }
            resumen = informacion.getDetalleCompatibilidad()
                    + System.lineSeparator() + "Base origen: " + informacion.getManifiesto().getBaseDatos();
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, mensaje(ex));
            return;
        }

        // Dos confirmaciones independientes (VAL-99): aceptacion y escritura literal.
        if (!MessageHelper.confirm(this, "La base actual sera sustituida por la copia seleccionada.\n"
                + resumen + "\n\n¿Confirma la restauracion?")) {
            estado.setText("Restauracion cancelada en la primera confirmacion; datos intactos.");
            return;
        }
        String escrito = JOptionPane.showInputDialog(this,
                "Escriba exactamente " + CONFIRMACION_TEXTO + " para autorizar la restauracion:",
                "Segunda confirmacion", JOptionPane.WARNING_MESSAGE);
        if (!CONFIRMACION_TEXTO.equals(escrito)) {
            estado.setText("Restauracion cancelada en la segunda confirmacion; datos intactos.");
            return;
        }

        ejecutar("Restaurando en base de preparacion...", () -> {
            ResultadoRestauracion resultado = servicio.restaurar(archivo, true, true);
            if (!resultado.isExitoso()) {
                throw new IllegalStateException(resultado.getMensaje());
            }
            return resultado.getMensaje() + System.lineSeparator()
                    + "Base verificada: " + resultado.getBaseActiva() + System.lineSeparator()
                    + "Fecha: " + resultado.getFechaHora();
        });
    }

    private void historial() {
        ejecutar("Consultando auditorIA-a...", () -> {
            List<BackupAuditoria> eventos = servicio.historial();
            StringBuilder texto = new StringBuilder("Fecha | Operacion | Resultado | Usuario | Archivo | Motivo\n");
            for (BackupAuditoria evento : eventos) {
                texto.append(evento.getFechaHora()).append(" | ")
                        .append(evento.getOperacion()).append(" | ")
                        .append(evento.getResultado()).append(" | ")
                        .append(evento.getNombreUsuario()).append(" | ")
                        .append(evento.getArchivo()).append(" | ")
                        .append(evento.getMotivoFallo() == null ? "-" : evento.getMotivoFallo())
                        .append('\n');
            }
            return texto.toString();
        });
    }

    private void ejecutar(String mensaje, Callable<String> tarea) {
        if (servicio == null) {
            MessageHelper.showValidation(this, "No hay contexto de aplicacion inicializado.");
            return;
        }
        estado.setText(mensaje);
        establecerBotones(false);
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return tarea.call();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            protected void done() {
                try {
                    String resultado = get();
                    detalle.setText(resultado);
                    estado.setText("Operacion finalizada.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    estado.setText("Operacion interrumpida.");
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause() == null ? ex : ex.getCause();
                    detalle.setText(mensaje(causa));
                    estado.setText("La operacion no se completo.");
                    MessageHelper.showValidation(CopiasSeguridadPanel.this, mensaje(causa));
                } finally {
                    establecerBotones(true);
                }
            }
        };
        worker.execute();
    }

    private void establecerBotones(boolean habilitado) {
        botonCrear.setEnabled(habilitado);
        botonInspeccionar.setEnabled(habilitado);
        botonRestaurar.setEnabled(habilitado);
        botonHistorial.setEnabled(habilitado);
    }

    private static String mensaje(Throwable ex) {
        String texto = ex.getMessage();
        return texto == null || texto.isBlank() ? ex.getClass().getSimpleName() : texto;
    }
}
