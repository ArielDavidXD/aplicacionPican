package cu.pican.view.modules;

import cu.pican.controller.app.AppContext;
import cu.pican.model.Destino;
import cu.pican.model.Producto;
import cu.pican.model.Recepcion;
import cu.pican.model.UEB;
import cu.pican.model.ValeEntrega;
import cu.pican.controller.service.DestinoService;
import cu.pican.controller.service.ProductoService;
import cu.pican.controller.service.RecepcionService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.ValeEntregaService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
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
import java.util.stream.Collectors;

/**
 * Módulo de Recepción de vales (CU-11, integrado con servicios en memoria).
 * Permite consultar vales EMITIDO pendientes y registrar su recepción conforme/no conforme.
 * No implementa gestión completa de incidencias (CU-12), anulación, inventario, etc.
 */
public class ReceptionModulePanel extends JPanel {

    private static final DateTimeFormatter FECHA_HORA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RecepcionService recepcionService;
    private final ValeEntregaService valeEntregaService;
    private final UEBService uebService;
    private final DestinoService destinoService;
    private final ProductoService productoService;

    private final DataTablePanel pendientesTablePanel;
    private final List<ValeEntrega> pendientesRows = new ArrayList<>();

    private final JLabel numeroLabel = new JLabel("-");
    private final JLabel uebLabel = new JLabel("-");
    private final JLabel destinoLabel = new JLabel("-");
    private final JLabel productoLabel = new JLabel("-");
    private final JLabel cantidadDespachadaLabel = new JLabel("-");
    private final JLabel unidadDespachadaLabel = new JLabel("-");
    private final JLabel estadoValeLabel = new JLabel("-");

    private final JTextField cantidadRecibidaField = new JTextField(12);
    private final JComboBox<String> resultadoCombo = new JComboBox<>(new String[]{
            Recepcion.RESULTADO_CONFORME, Recepcion.RESULTADO_NO_CONFORME
    });
    private final JTextField fechaHoraField = new JTextField(16);
    private final JTextArea observacionesArea = new JTextArea(3, 30);

    private ValeEntrega valeSeleccionado;

    public ReceptionModulePanel(AppContext context) {
        this.recepcionService = context.getRecepcionService();
        this.valeEntregaService = context.getValeEntregaService();
        this.uebService = context.getUebService();
        this.destinoService = context.getDestinoService();
        this.productoService = context.getProductoService();

        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Recepción de entregas",
                "Registro de recepción de vales emitidos (CU-11). " +
                        "La recepción conserva el vale original y registra lo realmente recibido. Datos en memoria."),
                BorderLayout.NORTH);

        String[] cols = {"Número", "Fecha emisión", "UEB", "Destino", "Producto", "Cant. despachada", "Unidad", "Estado"};
        pendientesTablePanel = new DataTablePanel("Vales pendientes de recepción (EMITIDO)", cols, new Object[0][0]);
        pendientesTablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pendientesTablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSeleccionVale();
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(pendientesTablePanel, BorderLayout.CENTER);
        center.add(buildRegistroPanel(), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        add(buildActionsPanel(), BorderLayout.SOUTH);

        resultadoCombo.addActionListener(e -> actualizarAyudaObservaciones());
        actualizarAyudaObservaciones();
        refrescarPendientes();
        limpiarRegistro(false);
    }

    private JPanel buildRegistroPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Registro de recepción del vale seleccionado"));

        JPanel datos = new JPanel(new GridBagLayout());
        datos.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;

        int y = 0;
        addDato(datos, c, y++, "Número:", numeroLabel, "Estado vale:", estadoValeLabel);
        addDato(datos, c, y++, "UEB:", uebLabel, "Destino:", destinoLabel);
        addDato(datos, c, y++, "Producto:", productoLabel, null, null);
        addDato(datos, c, y++, "Cantidad despachada:", cantidadDespachadaLabel, "Unidad despachada:", unidadDespachadaLabel);

        c.gridx = 0; c.gridy = y; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        datos.add(new JLabel("Cantidad recibida:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        datos.add(cantidadRecibidaField, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        datos.add(new JLabel("Resultado:"), c);
        c.gridx = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        datos.add(resultadoCombo, c);
        y++;

        c.gridx = 0; c.gridy = y; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        datos.add(new JLabel("Fecha y hora recepción:"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        fechaHoraField.setToolTipText("Formato: yyyy-MM-dd HH:mm:ss");
        datos.add(fechaHoraField, c);
        y++;

        c.gridx = 0; c.gridy = y; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.NORTHWEST;
        datos.add(new JLabel("Observaciones:"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.BOTH;
        observacionesArea.setLineWrap(true);
        observacionesArea.setWrapStyleWord(true);
        JScrollPane obsScroll = new JScrollPane(observacionesArea);
        obsScroll.setPreferredSize(new Dimension(400, 60));
        datos.add(obsScroll, c);

        panel.add(datos, BorderLayout.CENTER);

        JLabel nota = new JLabel("Conforme: cantidad recibida igual a despachada. No conforme: requiere observaciones (el vale pasará a RECIBIDO_CON_INCIDENCIA; gestionable desde CU-12).");
        nota.setFont(UITheme.SMALL_FONT);
        nota.setForeground(UITheme.MUTED_TEXT);
        nota.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        panel.add(nota, BorderLayout.SOUTH);

        return panel;
    }

    private void addDato(JPanel panel, GridBagConstraints c, int y,
                         String l1, JLabel v1, String l2, JLabel v2) {
        c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = y;
        panel.add(new JLabel(l1), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        v1.setFont(UITheme.NORMAL_FONT);
        panel.add(v1, c);
        if (l2 != null && v2 != null) {
            c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            panel.add(new JLabel(l2), c);
            c.gridx = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            v2.setFont(UITheme.NORMAL_FONT);
            panel.add(v2, c);
        }
    }

    private JPanel buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);

        AppButton confirmarBtn = new AppButton("Confirmar recepción");
        AppButton limpiarBtn = new AppButton("Limpiar registro");
        AppButton actualizarBtn = new AppButton("Actualizar pendientes");

        confirmarBtn.addActionListener(e -> confirmarRecepcion());
        limpiarBtn.addActionListener(e -> limpiarRegistro(true));
        actualizarBtn.addActionListener(e -> {
            refrescarPendientes();
            limpiarRegistro(false);
        });

        actions.add(confirmarBtn);
        actions.add(limpiarBtn);
        actions.add(actualizarBtn);
        return actions;
    }

    private void onSeleccionVale() {
        int row = pendientesTablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= pendientesRows.size()) {
            valeSeleccionado = null;
            mostrarVale(null);
            return;
        }
        valeSeleccionado = pendientesRows.get(row);
        mostrarVale(valeSeleccionado);
    }

    private void mostrarVale(ValeEntrega vale) {
        if (vale == null) {
            numeroLabel.setText("-");
            estadoValeLabel.setText("-");
            uebLabel.setText("-");
            destinoLabel.setText("-");
            productoLabel.setText("-");
            cantidadDespachadaLabel.setText("-");
            unidadDespachadaLabel.setText("-");
            return;
        }
        numeroLabel.setText(safe(vale.getNumeroVale()));
        estadoValeLabel.setText(safe(vale.getEstado()));
        uebLabel.setText(nombreUEB(vale.getIdUEB()));
        destinoLabel.setText(nombreDestino(vale.getIdDestino()));
        productoLabel.setText(nombreProducto(vale.getIdProducto()));
        cantidadDespachadaLabel.setText(vale.getCantidadDespachada() != null ? vale.getCantidadDespachada().toPlainString() : "");
        unidadDespachadaLabel.setText(safe(vale.getUnidadMedida()));
        // precargar cantidad recibida sugerida con lo despachado (usuario puede ajustar)
        if (vale.getCantidadDespachada() != null && (cantidadRecibidaField.getText() == null || cantidadRecibidaField.getText().trim().isEmpty())) {
            cantidadRecibidaField.setText(vale.getCantidadDespachada().toPlainString());
        }
    }

    private void confirmarRecepcion() {
        try {
            if (valeSeleccionado == null) {
                throw new ValidacionException("Debe seleccionar un vale pendiente de recepción.");
            }
            // validar estado actual antes de intentar
            ValeEntrega actual = valeEntregaService.buscarPorId(valeSeleccionado.getIdVale())
                    .orElseThrow(() -> new ValidacionException("El vale seleccionado no existe."));
            if (!ValeEntrega.ESTADO_EMITIDO.equals(actual.getEstado())) {
                throw new ValidacionException("Solo se pueden recibir vales en estado EMITIDO.");
            }
            if (recepcionService.buscarPorVale(actual.getIdVale()).isPresent()) {
                throw new ValidacionException("El vale ya tiene una recepción registrada.");
            }

            Recepcion r = new Recepcion();
            r.setIdRecepcion(UUID.randomUUID());
            r.setIdVale(actual.getIdVale());
            r.setCantidadRecibida(parseCantidad(cantidadRecibidaField.getText()));
            r.setUnidadMedida(actual.getUnidadMedida()); // debe corresponder con la del vale
            String resultado = (String) resultadoCombo.getSelectedItem();
            r.setResultado(resultado);
            r.setObservaciones(trimToNull(observacionesArea.getText()));
            r.setFechaHoraRecepcion(parseFechaHora(fechaHoraField.getText()));

            Recepcion guardada = recepcionService.registrar(r);
            ValeEntrega valePost = valeEntregaService.buscarPorId(actual.getIdVale()).orElse(actual);
            MessageHelper.showInfo(this, "Recepción registrada correctamente. Nuevo estado del vale: " + safe(valePost.getEstado()));
            refrescarPendientes();
            limpiarRegistro(false);
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo registrar la recepción: " + ex.getMessage());
        }
    }

    private void refrescarPendientes() {
        pendientesRows.clear();
        DefaultTableModel model = pendientesTablePanel.getTableModel();
        model.setRowCount(0);
        List<ValeEntrega> emitidos = valeEntregaService.listarTodos().stream()
                .filter(v -> ValeEntrega.ESTADO_EMITIDO.equals(v.getEstado()))
                .collect(Collectors.toList());
        for (ValeEntrega v : emitidos) {
            pendientesRows.add(v);
            model.addRow(new Object[]{
                    safe(v.getNumeroVale()),
                    formatFechaHora(v.getFechaHoraEmision()),
                    nombreUEB(v.getIdUEB()),
                    nombreDestino(v.getIdDestino()),
                    nombreProducto(v.getIdProducto()),
                    v.getCantidadDespachada() != null ? v.getCantidadDespachada().toPlainString() : "",
                    safe(v.getUnidadMedida()),
                    safe(v.getEstado())
            });
        }
    }

    private void limpiarRegistro(boolean limpiarSeleccionCampos) {
        if (limpiarSeleccionCampos) {
            cantidadRecibidaField.setText("");
            observacionesArea.setText("");
        } else {
            if (valeSeleccionado == null) {
                cantidadRecibidaField.setText("");
                observacionesArea.setText("");
            }
        }
        resultadoCombo.setSelectedItem(Recepcion.RESULTADO_CONFORME);
        fechaHoraField.setText(formatFechaHora(LocalDateTime.now()));
        if (valeSeleccionado == null) {
            pendientesTablePanel.getTable().clearSelection();
            mostrarVale(null);
        } else {
            // si el vale ya no está pendiente, deseleccionar
            boolean sigue = pendientesRows.stream().anyMatch(v -> v.getIdVale().equals(valeSeleccionado.getIdVale()));
            if (!sigue) {
                valeSeleccionado = null;
                pendientesTablePanel.getTable().clearSelection();
                mostrarVale(null);
                cantidadRecibidaField.setText("");
                observacionesArea.setText("");
            }
        }
        actualizarAyudaObservaciones();
    }

    private void actualizarAyudaObservaciones() {
        Object sel = resultadoCombo.getSelectedItem();
        boolean noConforme = Recepcion.RESULTADO_NO_CONFORME.equals(sel);
        observacionesArea.setToolTipText(noConforme
                ? "Obligatorio para recepción no conforme: explique la diferencia o problema."
                : "Opcional para recepción conforme.");
    }

    private String nombreUEB(UUID id) {
        if (id == null) return "";
        return uebService.buscarPorId(id).map(UEB::getNombre).orElse("(UEB no encontrada)");
    }

    private String nombreDestino(UUID id) {
        if (id == null) return "";
        return destinoService.buscarPorId(id).map(Destino::getNombre).orElse("(Destino no encontrado)");
    }

    private String nombreProducto(UUID id) {
        if (id == null) return "";
        return productoService.buscarPorId(id).map(Producto::getNombre).orElse("(Producto no encontrado)");
    }

    private BigDecimal parseCantidad(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("La cantidad recibida debe ser un número válido (use punto decimal).");
        }
    }

    private LocalDateTime parseFechaHora(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(text.trim(), FECHA_HORA_FMT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("La fecha y hora deben tener el formato yyyy-MM-dd HH:mm:ss.");
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String formatFechaHora(LocalDateTime dt) {
        return dt == null ? "" : dt.format(FECHA_HORA_FMT);
    }
}
