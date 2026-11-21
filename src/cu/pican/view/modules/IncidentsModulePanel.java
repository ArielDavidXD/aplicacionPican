package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.Incidencia;
import cu.pican.model.Recepcion;
import cu.pican.model.ValeEntrega;
import cu.pican.service.IncidenciaService;
import cu.pican.service.RecepcionService;
import cu.pican.service.ValeEntregaService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gestión de incidencias de entrega (CU-12), integrada con servicios compartidos.
 * Completa las incidencias mínimas creadas por CU-11, permite incidencias adicionales
 * para problemas distintos y cierra incidencias. No implementa CU-13 ni sincronización.
 */
public class IncidentsModulePanel extends JPanel {

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IncidenciaService incidenciaService;
    private final ValeEntregaService valeEntregaService;
    private final RecepcionService recepcionService;

    private final DataTablePanel tablePanel;
    private final List<Incidencia> rows = new ArrayList<>();

    private final JComboBox<String> filtroEstadoCombo = new JComboBox<>(new String[]{"TODOS", Incidencia.ESTADO_ABIERTA, Incidencia.ESTADO_CERRADA});
    private final JComboBox<String> tipoCombo = new JComboBox<>(new String[]{
            Incidencia.TIPO_FALTANTE, Incidencia.TIPO_SOBRANTE, Incidencia.TIPO_CALIDAD,
            Incidencia.TIPO_ROTURA, Incidencia.TIPO_OTRO
    });
    private final JTextField cantidadField = new JTextField(10);
    private final JTextArea descripcionArea = new JTextArea(2, 24);
    private final JTextArea medidaArea = new JTextArea(2, 24);
    private final JTextField fechaCompromisoField = new JTextField(16);
    private final JTextField fechaCierreField = new JTextField(16);
    private final JTextArea resultadoArea = new JTextArea(2, 24);

    private final JLabel valeNumeroLabel = new JLabel("-");
    private final JLabel valeEstadoLabel = new JLabel("-");
    private final JLabel recepcionLabel = new JLabel("-");
    private final JLabel observacionesOriginalesLabel = new JLabel("-");
    private final JLabel otrasAbiertasLabel = new JLabel("-");

    private Incidencia seleccionada;

    public IncidentsModulePanel(AppContext context) {
        this.incidenciaService = context.getIncidenciaService();
        this.valeEntregaService = context.getValeEntregaService();
        this.recepcionService = context.getRecepcionService();

        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(new ModuleHeader("Incidencias de entrega",
                "CU-12: completar, dar seguimiento y cerrar diferencias detectadas en una recepción NO_CONFORME. "
                        + "Se reutiliza la incidencia generada por CU-11."), BorderLayout.NORTH);

        String[] cols = {"Número vale", "Tipo", "Cant. afectada", "Descripción", "Medida acordada", "Estado", "Fecha cierre", "Resultado"};
        tablePanel = new DataTablePanel("Incidencias", cols, new Object[0][0]);
        tablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onSeleccion();
        });

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(buildFiltrosPanel(), BorderLayout.NORTH);
        center.add(tablePanel, BorderLayout.CENTER);
        center.add(buildDetallePanel(), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        add(buildActionsPanel(), BorderLayout.SOUTH);

        filtroEstadoCombo.addActionListener(e -> refrescarTabla());
        refrescarTabla();
        limpiarFormulario();
    }

    private JPanel buildFiltrosPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setOpaque(false);
        p.add(new JLabel("Filtrar por estado:"));
        p.add(filtroEstadoCombo);
        p.add(new JLabel("(También puede filtrar por número de vale desde el campo de detalle al seleccionar una incidencia.)"));
        return p;
    }

    private JPanel buildDetallePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Detalle y gestión de la incidencia seleccionada"));

        JPanel datos = new JPanel(new GridBagLayout());
        datos.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;

        int y = 0;
        addDato(datos, c, y++, "Número vale:", valeNumeroLabel, "Estado vale:", valeEstadoLabel);
        addDato(datos, c, y++, "Recepción:", recepcionLabel, "Otras incidencias abiertas:", otrasAbiertasLabel);
        c.gridwidth = 4; c.gridx = 0; c.gridy = y++; c.fill = GridBagConstraints.HORIZONTAL;
        observacionesOriginalesLabel.setFont(UITheme.SMALL_FONT);
        datos.add(observacionesOriginalesLabel, c);

        c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        c.gridx = 0; c.gridy = y;
        datos.add(new JLabel("Tipo:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        datos.add(tipoCombo, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE;
        datos.add(new JLabel("Cantidad afectada:"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL;
        datos.add(cantidadField, c);
        y++;

        c.gridx = 0; c.gridy = y; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.NORTHWEST;
        datos.add(new JLabel("Descripción:"), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.BOTH;
        descripcionArea.setLineWrap(true); descripcionArea.setWrapStyleWord(true);
        datos.add(new JScrollPane(descripcionArea), c);
        y++;

        c.gridx = 0; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        datos.add(new JLabel("Medida acordada:"), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.BOTH;
        medidaArea.setLineWrap(true); medidaArea.setWrapStyleWord(true);
        datos.add(new JScrollPane(medidaArea), c);
        y++;

        c.gridx = 0; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        datos.add(new JLabel("Fecha compromiso:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        fechaCompromisoField.setToolTipText("Opcional. Formato: yyyy-MM-dd HH:mm:ss");
        datos.add(fechaCompromisoField, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE;
        datos.add(new JLabel("Fecha cierre:"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL;
        fechaCierreField.setToolTipText("Obligatoria para cerrar. Formato: yyyy-MM-dd HH:mm:ss");
        datos.add(fechaCierreField, c);
        y++;

        c.gridx = 0; c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.NORTHWEST;
        datos.add(new JLabel("Resultado solución:"), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.BOTH;
        resultadoArea.setLineWrap(true); resultadoArea.setWrapStyleWord(true);
        datos.add(new JScrollPane(resultadoArea), c);

        panel.add(datos, BorderLayout.CENTER);
        JLabel nota = new JLabel("La medida acordada se registra como texto (no existe catálogo oficial aprobado). "
                + "La reposición como acción CU-10 queda pendiente hasta definir su identificación oficial.");
        nota.setFont(UITheme.SMALL_FONT);
        nota.setForeground(UITheme.MUTED_TEXT);
        panel.add(nota, BorderLayout.SOUTH);
        return panel;
    }

    private void addDato(JPanel panel, GridBagConstraints c, int y, String l1, JLabel v1, String l2, JLabel v2) {
        c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = y; panel.add(new JLabel(l1), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; v1.setFont(UITheme.NORMAL_FONT); panel.add(v1, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE; panel.add(new JLabel(l2), c);
        c.gridx = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; v2.setFont(UITheme.NORMAL_FONT); panel.add(v2, c);
    }

    private JPanel buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);
        AppButton guardarBtn = new AppButton("Guardar datos de incidencia");
        AppButton cerrarBtn = new AppButton("Confirmar cierre");
        AppButton otraBtn = new AppButton("Registrar otra incidencia para esta recepción");
        AppButton actualizarBtn = new AppButton("Actualizar lista");
        AppButton limpiarBtn = new AppButton("Limpiar formulario");

        guardarBtn.addActionListener(e -> guardar());
        cerrarBtn.addActionListener(e -> cerrar());
        otraBtn.addActionListener(e -> registrarOtra());
        actualizarBtn.addActionListener(e -> refrescarTabla());
        limpiarBtn.addActionListener(e -> limpiarFormulario());

        actions.add(guardarBtn); actions.add(cerrarBtn); actions.add(otraBtn);
        actions.add(actualizarBtn); actions.add(limpiarBtn);
        return actions;
    }

    private void onSeleccion() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= rows.size()) {
            seleccionada = null;
            limpiarFormulario();
            return;
        }
        seleccionada = rows.get(row);
        cargarSeleccion();
    }

    private void cargarSeleccion() {
        if (seleccionada == null) return;
        ValeEntrega vale = valeEntregaService.buscarPorId(seleccionada.getIdVale()).orElse(null);
        Recepcion recepcion = recepcionService.buscarPorId(seleccionada.getIdRecepcion()).orElse(null);
        valeNumeroLabel.setText(vale == null ? "(no encontrado)" : safe(vale.getNumeroVale()));
        valeEstadoLabel.setText(vale == null ? "-" : safe(vale.getEstado()));
        recepcionLabel.setText(recepcion == null ? "(no encontrada)" : safe(recepcion.getIdRecepcion().toString()));
        observacionesOriginalesLabel.setText("Observaciones originales de la recepción: " + safe(seleccionada.getObservaciones()));
        long abiertas = incidenciaService.buscarPorVale(seleccionada.getIdVale()).stream()
                .filter(i -> Incidencia.ESTADO_ABIERTA.equals(i.getEstado())).count();
        otrasAbiertasLabel.setText(String.valueOf(abiertas));

        tipoCombo.setSelectedItem(seleccionada.getTipo() != null ? seleccionada.getTipo() : Incidencia.TIPO_OTRO);
        cantidadField.setText(seleccionada.getCantidadAfectada() != null ? seleccionada.getCantidadAfectada().toPlainString() : "");
        descripcionArea.setText(safe(seleccionada.getDescripcion()));
        medidaArea.setText(safe(seleccionada.getMedidaAcordada()));
        fechaCompromisoField.setText(formatFecha(seleccionada.getFechaCompromiso()));
        fechaCierreField.setText(formatFecha(seleccionada.getFechaCierre()));
        resultadoArea.setText(safe(seleccionada.getResultadoSolucion()));
    }

    private void guardar() {
        try {
            if (seleccionada == null) throw new ValidacionException("Debe seleccionar una incidencia.");
            Incidencia actualizada = incidenciaService.completar(
                    seleccionada.getIdIncidencia(),
                    (String) tipoCombo.getSelectedItem(),
                    parseCantidad(cantidadField.getText()),
                    descripcionArea.getText(),
                    medidaArea.getText(),
                    parseFechaOpcional(fechaCompromisoField.getText()));
            MessageHelper.showInfo(this, "Incidencia guardada correctamente.");
            refrescarTabla();
            seleccionada = actualizada;
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo guardar la incidencia: " + ex.getMessage());
        }
    }

    private void cerrar() {
        try {
            if (seleccionada == null) throw new ValidacionException("Debe seleccionar una incidencia.");
            Incidencia cerrada = incidenciaService.cerrar(
                    seleccionada.getIdIncidencia(),
                    parseFecha(fechaCierreField.getText()),
                    resultadoArea.getText());
            ValeEntrega vale = valeEntregaService.buscarPorId(cerrada.getIdVale()).orElse(null);
            String msg = "Incidencia cerrada correctamente. Estado del vale: "
                    + (vale == null ? "(desconocido)" : safe(vale.getEstado()));
            if (vale != null && ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA.equals(vale.getEstado())) {
                msg += ". El vale continúa con otras incidencias abiertas.";
            }
            MessageHelper.showInfo(this, msg);
            refrescarTabla();
            limpiarFormulario();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo cerrar la incidencia: " + ex.getMessage());
        }
    }

    private void registrarOtra() {
        try {
            if (seleccionada == null) throw new ValidacionException("Debe seleccionar una incidencia para usar su recepción de origen.");
            String obs = JOptionPane.showInputDialog(this,
                    "Descripción breve del problema adicional (se conservará como observaciones):",
                    "Registrar otra incidencia", JOptionPane.QUESTION_MESSAGE);
            if (obs == null) return;
            incidenciaService.registrarOtraParaLaMismaRecepcion(seleccionada.getIdRecepcion(), obs);
            MessageHelper.showInfo(this, "Incidencia adicional registrada. Complétela y guarde sus datos.");
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la incidencia adicional: " + ex.getMessage());
        }
    }

    private void refrescarTabla() {
        rows.clear();
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        String filtro = (String) filtroEstadoCombo.getSelectedItem();
        for (Incidencia i : incidenciaService.listarTodas()) {
            if (!"TODOS".equals(filtro) && !filtro.equals(i.getEstado())) continue;
            rows.add(i);
            ValeEntrega vale = valeEntregaService.buscarPorId(i.getIdVale()).orElse(null);
            model.addRow(new Object[]{
                    vale == null ? "(no encontrado)" : safe(vale.getNumeroVale()),
                    safe(i.getTipo()),
                    i.getCantidadAfectada() != null ? i.getCantidadAfectada().toPlainString() : "",
                    safe(i.getDescripcion()),
                    safe(i.getMedidaAcordada()),
                    safe(i.getEstado()),
                    formatFecha(i.getFechaCierre()),
                    safe(i.getResultadoSolucion())
            });
        }
    }

    private void limpiarFormulario() {
        seleccionada = null;
        tablePanel.getTable().clearSelection();
        tipoCombo.setSelectedItem(Incidencia.TIPO_OTRO);
        cantidadField.setText("");
        descripcionArea.setText("");
        medidaArea.setText("");
        fechaCompromisoField.setText("");
        fechaCierreField.setText("");
        resultadoArea.setText("");
        valeNumeroLabel.setText("-"); valeEstadoLabel.setText("-"); recepcionLabel.setText("-");
        observacionesOriginalesLabel.setText("-"); otrasAbiertasLabel.setText("-");
    }

    private BigDecimal parseCantidad(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try { return new BigDecimal(text.trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("La cantidad afectada debe ser un número válido."); }
    }

    private LocalDateTime parseFecha(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try { return LocalDateTime.parse(text.trim(), FECHA_FMT); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("La fecha debe tener el formato yyyy-MM-dd HH:mm:ss."); }
    }

    private LocalDateTime parseFechaOpcional(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        return parseFecha(text);
    }

    private String formatFecha(LocalDateTime dt) { return dt == null ? "" : dt.format(FECHA_FMT); }
    private String safe(String s) { return s == null ? "" : s; }
}
