package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.Rol;
import cu.pican.model.UEB;
import cu.pican.model.Usuario;
import cu.pican.sync.SyncException;
import cu.pican.sync.SyncModelo.ResultadoImportacion;
import cu.pican.sync.SyncService;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Intercambio manual de paquetes por archivo. La interfaz solo invoca al servicio:
 * la autorización, el formato, la integridad y la persistencia se deciden en SyncService.
 */
public class SyncModulePanel extends JPanel {

    private final AppContext context;
    private final String rol;
    private final DataTablePanel historyTable;
    private final DataTablePanel conflictsTable;

    public SyncModulePanel(AppContext context) {
        this.context = context;
        this.rol = rolDeLaSesion();
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(new javax.swing.border.EmptyBorder(16, 16, 16, 16));
        add(new ModuleHeader("Sincronización mediante USB",
                "Intercambio manual de archivos ZIP. Sin Internet, sin conexión permanente y sin resolución automática de conflictos."),
                BorderLayout.NORTH);

        String[] columnasHistorial = {"Paquete", "Tipo", "Origen", "Destino", "Registros", "Estado", "Procesado"};
        String[] columnasConflictos = {"Tabla", "Registro", "Versión recibida", "Versión local", "Detectado"};
        historyTable = new DataTablePanel("Historial de paquetes", columnasHistorial, new Object[0][0]);
        conflictsTable = new DataTablePanel("Conflictos pendientes de decisión", columnasConflictos, new Object[0][0]);
        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Historial", historyTable);
        pestanas.addTab("Conflictos", conflictsTable);
        add(pestanas, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);
        if (Rol.JEFE_UEB.equals(rol)) {
            actions.add(new AppButton("Preparar paquete", e -> prepararPaquete()));
            actions.add(new AppButton("Importar confirmación", e -> importarConfirmacion()));
            actions.add(new AppButton("Importar bajada (DN)", e -> importarBajada()));
        }
        if (Rol.ESPECIALISTA_ESTADISTICA.equals(rol)) {
            actions.add(new AppButton("Validar paquete", e -> validarPaquete()));
            actions.add(new AppButton("Importar paquete", e -> importarPaquete()));
            actions.add(new AppButton("Generar confirmación", e -> generarConfirmacion()));
            actions.add(new AppButton("Generar bajada (DN)", e -> generarBajada()));
        }
        if (Rol.ADMINISTRADOR.equals(rol)) {
            if (context != null && context.esCentral()) {
                actions.add(new AppButton("Generar alta (AL)", e -> generarAlta()));
            } else {
                actions.add(new AppButton("Importar alta (AL)", e -> importarAlta()));
            }
        }
        actions.add(new AppButton("Actualizar historial", e -> actualizarHistorial()));
        add(actions, BorderLayout.SOUTH);

        actualizarHistorial();
    }

    /** Constructor heredado para herramientas que no construyen un AppContext. */
    public SyncModulePanel() {
        this(null);
    }

    private String rolDeLaSesion() {
        if (context == null || context.getAuthService().getUsuarioAutenticado() == null
                || context.getAuthService().getUsuarioAutenticado().getRol() == null) {
            return null;
        }
        return context.getAuthService().getUsuarioAutenticado().getRol().getNombre();
    }

    private SyncService servicio() {
        if (context == null) {
            throw new SyncException("No hay contexto de aplicación inicializado.");
        }
        return context.getSyncService();
    }

    private void prepararPaquete() {
        ejecutar(() -> {
            List<UEB> uebs = context.getUebService().listarTodas();
            if (uebs.isEmpty()) {
                MessageHelper.showValidation(this, "No hay UEB registradas para identificar el origen del paquete.");
                return;
            }
            String[] opciones = uebs.stream().map(u -> u.getNombre() + " (" + u.getIdUEB() + ")")
                    .toArray(String[]::new);
            String elegida = (String) JOptionPane.showInputDialog(this,
                    "Seleccione la UEB de origen del paquete:", "Preparar paquete",
                    JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[0]);
            if (elegida == null) {
                return;
            }
            int inicio = elegida.lastIndexOf('(') + 1;
            UUID idUeb = UUID.fromString(elegida.substring(inicio, elegida.length() - 1));
            File destino = seleccionarArchivo(true, "pican-paquete-ueb.zip");
            if (destino == null) {
                return;
            }
            UUID idPaquete = servicio().generarExportacion(destino.toPath(), idUeb);
            MessageHelper.showInfo(this, "Paquete generado correctamente.\nIdentificador: " + idPaquete
                    + "\nArchivo: " + destino.getAbsolutePath()
                    + "\nCopie el archivo a la memoria USB y entréguelo en la Dirección de Producción.");
            actualizarHistorial();
        });
    }

    private void validarPaquete() {
        ejecutar(() -> {
            File archivo = seleccionarArchivo(false, "pican-paquete-ueb.zip");
            if (archivo == null) {
                return;
            }
            SyncService.PaqueteLeido paquete = servicio().validarPaquete(archivo.toPath());
            String origen = paquete.manifestacion.get("idUebOrigen") == null
                    ? "central" : String.valueOf(paquete.manifestacion.get("idUebOrigen"));
            MessageHelper.showInfo(this, "Paquete válido: " + paquete.idPaquete()
                    + "\nTipo: " + paquete.tipo
                    + "\nOrigen: " + origen
                    + "\nDestino: " + paquete.idDestino()
                    + "\nRegistros: " + paquete.cantidad()
                    + "\nIntegridad SHA-256 verificada (no autentica al emisor).");
        });
    }

    private void importarPaquete() {
        ejecutar(() -> {
            File archivo = seleccionarArchivo(false, "pican-paquete-ueb.zip");
            if (archivo == null) {
                return;
            }
            ResultadoImportacion resultado = servicio().importarPaquete(archivo.toPath(), "CENTRAL");
            MessageHelper.showInfo(this, "Importación " + resultado.estado + ".\n" + resultado.mensaje
                    + "\nLos conflictos quedaron registrados; no se resolvieron automáticamente.");
            actualizarHistorial();
        });
    }

    private void generarConfirmacion() {
        ejecutar(() -> {
            List<Map<String, Object>> historial = servicio().consultarHistorial();
            List<String> opciones = new ArrayList<>();
            for (Map<String, Object> paquete : historial) {
                String tipo = String.valueOf(paquete.get("tipo"));
                if (SyncService.EXPORTACION.equals(tipo)) {
                    opciones.add(String.valueOf(paquete.get("id_paquete")) + " · "
                            + String.valueOf(paquete.get("estado")));
                }
            }
            if (opciones.isEmpty()) {
                MessageHelper.showValidation(this, "No hay paquetes recibidos para confirmar.");
                return;
            }
            String elegido = (String) JOptionPane.showInputDialog(this,
                    "Seleccione el paquete recibido:", "Generar confirmación",
                    JOptionPane.QUESTION_MESSAGE, null, opciones.toArray(), opciones.get(0));
            if (elegido == null) {
                return;
            }
            UUID idPaquete = UUID.fromString(elegido.substring(0, elegido.indexOf(' ')));
            File destino = seleccionarArchivo(true, "pican-confirmacion.zip");
            if (destino == null) {
                return;
            }
            servicio().generarConfirmacion(destino.toPath(), idPaquete);
            MessageHelper.showInfo(this, "Confirmación generada en " + destino.getAbsolutePath()
                    + ".\nDevuélvala a la UEB de origen por el mismo medio manual.");
            actualizarHistorial();
        });
    }

    private void importarConfirmacion() {
        ejecutar(() -> {
            File archivo = seleccionarArchivo(false, "pican-confirmacion.zip");
            if (archivo == null) {
                return;
            }
            ResultadoImportacion resultado = servicio().importarConfirmacion(archivo.toPath());
            MessageHelper.showInfo(this, "Confirmación importada: " + resultado.estado + ".\n" + resultado.mensaje);
            actualizarHistorial();
        });
    }

    private void generarBajada() {
        ejecutar(() -> {
            List<UEB> uebs = context.getUebService().listarTodas();
            if (uebs.isEmpty()) {
                MessageHelper.showValidation(this, "No hay UEB registradas para preparar la bajada.");
                return;
            }
            String[] opciones = uebs.stream().map(u -> u.getNombre() + " (" + u.getIdUEB() + ")")
                    .toArray(String[]::new);
            String elegida = (String) JOptionPane.showInputDialog(this,
                    "Seleccione la UEB de destino de la bajada:", "Generar bajada (DN)",
                    JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[0]);
            if (elegida == null) {
                return;
            }
            int inicio = elegida.lastIndexOf('(') + 1;
            UUID idUeb = UUID.fromString(elegida.substring(inicio, elegida.length() - 1));
            File destino = seleccionarArchivo(true, servicio().nombreSugeridoBajada(idUeb));
            if (destino == null) {
                return;
            }
            UUID idPaquete = servicio().generarBajada(destino.toPath(), idUeb);
            MessageHelper.showInfo(this, "Bajada generada correctamente.\nIdentificador: " + idPaquete
                    + "\nArchivo: " + destino.getAbsolutePath()
                    + "\nEntréguela a la UEB por el medio manual acordado.");
            actualizarHistorial();
        });
    }

    private void importarBajada() {
        ejecutar(() -> {
            File archivo = seleccionarArchivo(false, "AVI_DN_paquete.avipkg");
            if (archivo == null) {
                return;
            }
            ResultadoImportacion resultado = servicio().importarBajada(archivo.toPath());
            MessageHelper.showInfo(this, "Bajada importada: " + resultado.estado + ".\n" + resultado.mensaje);
            actualizarHistorial();
        });
    }

    private void generarAlta() {
        ejecutar(() -> {
            List<UEB> uebs = context.getUebService().listarTodas();
            if (uebs.isEmpty()) {
                MessageHelper.showValidation(this, "No hay UEB registradas para dar de alta.");
                return;
            }
            String[] opciones = uebs.stream().map(u -> u.getNombre() + " (" + u.getIdUEB() + ")")
                    .toArray(String[]::new);
            String elegida = (String) JOptionPane.showInputDialog(this,
                    "Seleccione la UEB que se dará de alta:", "Generar alta (AL)",
                    JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[0]);
            if (elegida == null) {
                return;
            }
            int inicio = elegida.lastIndexOf('(') + 1;
            UUID idUeb = UUID.fromString(elegida.substring(inicio, elegida.length() - 1));
            List<Usuario> jefes = new ArrayList<>();
            for (Usuario usuario : context.getUsuarioService().listarTodos()) {
                if (usuario.isActivo() && usuario.getRol() != null
                        && Rol.JEFE_UEB.equals(usuario.getRol().getNombre())) {
                    jefes.add(usuario);
                }
            }
            if (jefes.isEmpty()) {
                MessageHelper.showValidation(this,
                        "No hay ningún Jefe de UEB activo. Regístrelo primero para poder dar el alta.");
                return;
            }
            String[] nombres = jefes.stream().map(u -> u.getNombreCompleto()
                    + " (" + u.getNombreUsuario() + ")").toArray(String[]::new);
            String jefeElegido = (String) JOptionPane.showInputDialog(this,
                    "Seleccione la cuenta inicial del Jefe de UEB:", "Generar alta (AL)",
                    JOptionPane.QUESTION_MESSAGE, null, nombres, nombres[0]);
            if (jefeElegido == null) {
                return;
            }
            int paren = jefeElegido.lastIndexOf('(') + 1;
            String nombreUsuario = jefeElegido.substring(paren, jefeElegido.length() - 1);
            File destino = seleccionarArchivo(true, servicio().nombreSugeridoAlta(idUeb));
            if (destino == null) {
                return;
            }
            UUID idPaquete = servicio().generarPaqueteAlta(destino.toPath(), idUeb, nombreUsuario);
            MessageHelper.showInfo(this, "Alta generada correctamente.\nIdentificador: " + idPaquete
                    + "\nArchivo: " + destino.getAbsolutePath()
                    + "\nEl identificador de activación solo podrá aplicarse una vez.");
            actualizarHistorial();
        });
    }

    private void importarAlta() {
        ejecutar(() -> {
            File archivo = seleccionarArchivo(false, "AVI_AL_paquete.avipkg");
            if (archivo == null) {
                return;
            }
            ResultadoImportacion resultado = servicio().importarPaqueteAlta(archivo.toPath());
            MessageHelper.showInfo(this, "Alta importada: " + resultado.estado + ".\n" + resultado.mensaje);
            actualizarHistorial();
        });
    }

    private void actualizarHistorial() {
        ejecutar(() -> {
            if (context == null || context.esMemoria()) {
                historyTable.getTableModel().setRowCount(0);
                conflictsTable.getTableModel().setRowCount(0);
                return;
            }
            List<Map<String, Object>> paquetes = servicio().consultarHistorial();
            Object[][] filas = new Object[paquetes.size()][7];
            for (int i = 0; i < paquetes.size(); i++) {
                Map<String, Object> paquete = paquetes.get(i);
                filas[i] = new Object[]{
                        paquete.get("id_paquete"), paquete.get("tipo"), paquete.get("id_ueb_origen"),
                        paquete.get("id_destino"), paquete.get("cantidad_registros"),
                        paquete.get("estado"), paquete.get("procesado_en")};
            }
            historyTable.getTableModel().setDataVector(filas, new String[]{
                    "Paquete", "Tipo", "Origen", "Destino", "Registros", "Estado", "Procesado"});

            List<Map<String, Object>> conflictos = servicio().consultarConflictos();
            Object[][] filasConflicto = new Object[conflictos.size()][5];
            for (int i = 0; i < conflictos.size(); i++) {
                Map<String, Object> conflicto = conflictos.get(i);
                filasConflicto[i] = new Object[]{
                        conflicto.get("tabla"), conflicto.get("id_registro"),
                        conflicto.get("version_recibida"), conflicto.get("version_local"),
                        conflicto.get("detectado_en")};
            }
            conflictsTable.getTableModel().setDataVector(filasConflicto, new String[]{
                    "Tabla", "Registro", "Versión recibida", "Versión local", "Detectado"});
        });
    }

    private File seleccionarArchivo(boolean guardar, String nombreSugerido) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle(guardar ? "Ubicación de destino (memoria USB)" : "Seleccione el paquete de la memoria USB");
        selector.setSelectedFile(new File(nombreSugerido));
        int resultado = guardar ? selector.showSaveDialog(this) : selector.showOpenDialog(this);
        return resultado == JFileChooser.APPROVE_OPTION ? selector.getSelectedFile() : null;
    }

    private void ejecutar(Runnable operacion) {
        try {
            operacion.run();
        } catch (SyncException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, "No se pudo completar la operación: " + ex.getMessage());
        }
    }
}
