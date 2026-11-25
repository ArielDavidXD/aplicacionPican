package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.Destino;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.UEB;
import cu.pican.model.ValeEntrega;
import cu.pican.service.DestinoService;
import cu.pican.service.ProductoDestinoService;
import cu.pican.service.ProductoService;
import cu.pican.service.UEBService;
import cu.pican.service.ValeEntregaService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Módulo de Entregas y vales (integrado con servicios en memoria).
 * Emite vales con un solo producto por vale e incluye anulación lógica (CU-13) de vales EMITIDO.
 * No implementa recepción, incidencias, inventario, camiones/choferes, cuotas ni persistencia PostgreSQL.
 */
public class DeliveriesModulePanel extends JPanel {

    private static final DateTimeFormatter FECHA_HORA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ValeEntregaService valeEntregaService;
    private final UEBService uebService;
    private final ProductoService productoService;
    private final DestinoService destinoService;
    private final ProductoDestinoService productoDestinoService;

    private final DataTablePanel tablePanel;
    private final List<ValeEntrega> currentRows = new ArrayList<>();

    private final JComboBox<String> estadoFiltroCombo = new JComboBox<>(new String[]{
            "TODOS", ValeEntrega.ESTADO_EMITIDO, ValeEntrega.ESTADO_CERRADO,
            ValeEntrega.ESTADO_RECIBIDO_CON_INCIDENCIA, ValeEntrega.ESTADO_ANULADO
    });

    private final JLabel detalleNumeroLabel = new JLabel("-");
    private final JLabel detalleEstadoLabel = new JLabel("-");
    private final JLabel detalleMotivoLabel = new JLabel("-");
    private final JLabel detalleFechaAnulacionLabel = new JLabel("-");

    private ValeEntrega valeSeleccionado;

    private final JComboBox<String> uebCombo = new JComboBox<>();
    private final JComboBox<String> destinoCombo = new JComboBox<>();
    private final JComboBox<String> productoCombo = new JComboBox<>();
    private final JTextField cantidadField = new JTextField(10);
    private final JTextField unidadField = new JTextField(10);
    private final JLabel numeroLabel = new JLabel("-");
    private final JLabel fechaHoraLabel = new JLabel("-");
    private final JLabel estadoLabel = new JLabel(ValeEntrega.ESTADO_EMITIDO);

    private final Map<String, UUID> uebLabelToId = new HashMap<>();
    private final Map<String, UUID> destinoLabelToId = new HashMap<>();
    private final Map<String, UUID> productoLabelToId = new HashMap<>();

    private boolean updatingCombos = false;

    public DeliveriesModulePanel(AppContext context) {
        this.valeEntregaService = context.getValeEntregaService();
        this.uebService = context.getUebService();
        this.productoService = context.getProductoService();
        this.destinoService = context.getDestinoService();
        this.productoDestinoService = context.getProductoDestinoService();

        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Entregas y vales",
                "Emisión, consulta y anulación lógica de vales de entrega (un producto por vale, datos en memoria). " +
                        "Numeración consecutiva por UEB generada automáticamente (formato provisional, no oficial)."),
                BorderLayout.NORTH);

        String[] cols = {"Número", "Fecha y hora", "UEB", "Destino", "Producto", "Cantidad", "Unidad", "Estado"};
        tablePanel = new DataTablePanel("Vales emitidos", cols, new Object[0][0]);
        tablePanel.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onSeleccionTabla();
        });

        JPanel tablaConFiltro = new JPanel(new BorderLayout(0, 6));
        tablaConFiltro.setOpaque(false);
        tablaConFiltro.add(buildFiltroPanel(), BorderLayout.NORTH);
        tablaConFiltro.add(tablePanel, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(buildFormPanel(), BorderLayout.NORTH);
        center.add(tablaConFiltro, BorderLayout.CENTER);
        center.add(buildDetallePanel(), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        add(buildActionsPanel(), BorderLayout.SOUTH);

        estadoFiltroCombo.addActionListener(e -> refrescarTabla());

        productoCombo.addActionListener(e -> {
            if (!updatingCombos) {
                actualizarDestinosSegunProducto();
                actualizarUnidadDesdeProducto();
            }
        });
        destinoCombo.addActionListener(e -> {
            if (!updatingCombos) {
                actualizarProductosSegunDestino();
            }
        });

        recargarCatalogos();
        refrescarTabla();
        prepararNuevoVale();
    }

    private JPanel buildFiltroPanel() {
        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtro.setOpaque(false);
        filtro.add(new JLabel("Filtrar por estado:"));
        filtro.add(estadoFiltroCombo);
        return filtro;
    }

    private JPanel buildDetallePanel() {
        JPanel detalle = new JPanel(new GridBagLayout());
        detalle.setOpaque(false);
        detalle.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Datos del vale seleccionado"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        detalle.add(new JLabel("Número:"), c);
        c.gridx = 1; detalle.add(detalleNumeroLabel, c);
        c.gridx = 2; detalle.add(new JLabel("Estado:"), c);
        c.gridx = 3; detalle.add(detalleEstadoLabel, c);

        c.gridx = 0; c.gridy = 1;
        detalle.add(new JLabel("Motivo de anulación:"), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        detalle.add(detalleMotivoLabel, c);
        c.gridwidth = 1; c.fill = GridBagConstraints.NONE;

        c.gridx = 0; c.gridy = 2;
        detalle.add(new JLabel("Fecha y hora de anulación:"), c);
        c.gridx = 1; c.gridwidth = 3;
        detalle.add(detalleFechaAnulacionLabel, c);
        return detalle;
    }

    private JPanel buildFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                "Emisión de vale de entrega"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("UEB emisora:"), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(uebCombo, c);

        c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Producto:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(productoCombo, c);

        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(new JLabel("Destino:"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(destinoCombo, c);

        c.fill = GridBagConstraints.NONE; c.weightx = 0;
        c.gridx = 0; c.gridy = 2;
        form.add(new JLabel("Cantidad:"), c);
        c.gridx = 1;
        form.add(cantidadField, c);
        c.gridx = 2;
        form.add(new JLabel("Unidad de medida:"), c);
        c.gridx = 3;
        form.add(unidadField, c);

        c.gridx = 0; c.gridy = 3;
        form.add(new JLabel("Número (generado):"), c);
        c.gridx = 1;
        numeroLabel.setFont(UITheme.NORMAL_FONT);
        form.add(numeroLabel, c);
        c.gridx = 2;
        form.add(new JLabel("Fecha y hora:"), c);
        c.gridx = 3;
        fechaHoraLabel.setFont(UITheme.NORMAL_FONT);
        form.add(fechaHoraLabel, c);

        c.gridx = 0; c.gridy = 4;
        form.add(new JLabel("Estado:"), c);
        c.gridx = 1;
        estadoLabel.setFont(UITheme.NORMAL_FONT);
        form.add(estadoLabel, c);

        c.gridx = 0; c.gridy = 5; c.gridwidth = 4;
        JLabel nota = new JLabel("Solo combinaciones producto-destino autorizadas y activas. Un vale = un producto. " +
                "No se exige camión/chofer ni se controla inventario.");
        nota.setFont(UITheme.SMALL_FONT);
        nota.setForeground(UITheme.MUTED_TEXT);
        form.add(nota, c);

        // tamaños preferidos para combos
        uebCombo.setPreferredSize(new Dimension(320, uebCombo.getPreferredSize().height));
        productoCombo.setPreferredSize(new Dimension(260, productoCombo.getPreferredSize().height));
        destinoCombo.setPreferredSize(new Dimension(260, destinoCombo.getPreferredSize().height));

        return form;
    }

    private JPanel buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);

        AppButton emitirBtn = new AppButton("Emitir vale");
        AppButton anularBtn = new AppButton("Anular vale");
        AppButton reimprimirBtn = new AppButton("Reimprimir vale");
        AppButton limpiarBtn = new AppButton("Limpiar formulario");
        AppButton recargarBtn = new AppButton("Recargar catálogos");
        AppButton actualizarBtn = new AppButton("Actualizar tabla");

        emitirBtn.addActionListener(e -> emitirVale());
        anularBtn.addActionListener(e -> anularVale());
        reimprimirBtn.addActionListener(e -> reimprimirVale());
        limpiarBtn.addActionListener(e -> prepararNuevoVale());
        recargarBtn.addActionListener(e -> {
            recargarCatalogos();
            prepararNuevoVale();
            MessageHelper.showInfo(this, "Catálogos recargados.");
        });
        actualizarBtn.addActionListener(e -> refrescarTabla());

        actions.add(emitirBtn);
        actions.add(anularBtn);
        actions.add(reimprimirBtn);
        actions.add(limpiarBtn);
        actions.add(recargarBtn);
        actions.add(actualizarBtn);
        return actions;
    }

    private void emitirVale() {
        try {
            UUID idUEB = getSelectedId(uebCombo, uebLabelToId);
            UUID idProducto = getSelectedId(productoCombo, productoLabelToId);
            UUID idDestino = getSelectedId(destinoCombo, destinoLabelToId);

            ValeEntrega vale = new ValeEntrega();
            vale.setIdVale(UUID.randomUUID());
            vale.setIdUEB(idUEB);
            vale.setIdProducto(idProducto);
            vale.setIdDestino(idDestino);
            vale.setCantidadDespachada(parseCantidad(cantidadField.getText()));
            vale.setUnidadMedida(trimToNull(unidadField.getText()));
            vale.setFechaHoraEmision(LocalDateTime.now());
            vale.setEstado(ValeEntrega.ESTADO_EMITIDO);

            ValeEntrega emitido = valeEntregaService.emitir(vale);
            numeroLabel.setText(emitido.getNumeroVale());
            fechaHoraLabel.setText(formatFechaHora(emitido.getFechaHoraEmision()));
            estadoLabel.setText(emitido.getEstado() != null ? emitido.getEstado() : ValeEntrega.ESTADO_EMITIDO);
            MessageHelper.showInfo(this, "Vale emitido correctamente: " + emitido.getNumeroVale());
            refrescarTabla();
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo emitir el vale: " + ex.getMessage());
        }
    }

    private void onSeleccionTabla() {
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= currentRows.size()) {
            valeSeleccionado = null;
            limpiarDetalle();
            return;
        }
        valeSeleccionado = currentRows.get(row);
        cargarDetalle(valeSeleccionado);
    }

    private void cargarDetalle(ValeEntrega vale) {
        if (vale == null) {
            limpiarDetalle();
            return;
        }
        detalleNumeroLabel.setText(safe(vale.getNumeroVale()));
        detalleEstadoLabel.setText(safe(vale.getEstado()));
        boolean anulado = ValeEntrega.ESTADO_ANULADO.equals(vale.getEstado());
        detalleMotivoLabel.setText(anulado ? safe(vale.getMotivoAnulacion()) : "-");
        detalleFechaAnulacionLabel.setText(anulado ? formatFechaHora(vale.getFechaHoraAnulacion()) : "-");
    }

    private void limpiarDetalle() {
        detalleNumeroLabel.setText("-");
        detalleEstadoLabel.setText("-");
        detalleMotivoLabel.setText("-");
        detalleFechaAnulacionLabel.setText("-");
    }

    /**
     * Anulación CU-13: la vista solo solicita datos y confirmaciones; todas las reglas se validan en el servicio.
     */
    private void anularVale() {
        try {
            ValeEntrega vale = valeSeleccionado;
            if (vale == null) {
                throw new ValidacionException("Debe seleccionar un vale de la tabla.");
            }

            String motivo = JOptionPane.showInputDialog(this, "Motivo de la anulación:",
                    "Anular vale", JOptionPane.QUESTION_MESSAGE);
            if (motivo == null) {
                return; // el usuario canceló
            }

            int salida = JOptionPane.showConfirmDialog(this,
                    "Confirme explícitamente que el producto NO ha salido físicamente de la UEB.",
                    "Salida física del producto", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (salida != JOptionPane.YES_OPTION) {
                return;
            }

            if (!MessageHelper.confirm(this, "¿Anular el vale " + safe(vale.getNumeroVale())
                    + "?\nMotivo: " + motivo.trim())) {
                return;
            }

            ValeEntrega anulado = valeEntregaService.anular(vale.getIdVale(), motivo, true, null);
            MessageHelper.showInfo(this, "Vale anulado correctamente: " + safe(anulado.getNumeroVale())
                    + ". El registro se conserva con estado ANULADO.");
            refrescarTabla();
            cargarDetalle(anulado);
        } catch (ValidacionException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        } catch (Exception ex) {
            MessageHelper.showValidation(this, "No se pudo anular el vale: " + ex.getMessage());
        }
    }

    /**
     * CU-13: reimpresión del vale seleccionado con la marca DUPLICADO (o ANULADO si el vale está anulado).
     * Muestra una vista previa y permite imprimir la copia. El vale original no se modifica.
     */
    private void reimprimirVale() {
        if (valeSeleccionado == null) {
            MessageHelper.showValidation(this, "Debe seleccionar un vale de la tabla para reimprimir.");
            return;
        }
        ValeEntrega vale = valeSeleccionado;
        boolean anulado = ValeEntrega.ESTADO_ANULADO.equals(vale.getEstado());
        String marca = anulado ? "ANULADO" : "DUPLICADO";

        StringBuilder sb = new StringBuilder();
        sb.append("VALE DE ENTREGA - COPIA ").append(marca).append('\n');
        sb.append("---------------------------------------------\n");
        sb.append("Número: ").append(safe(vale.getNumeroVale())).append('\n');
        sb.append("Fecha y hora de emisión: ").append(formatFechaHora(vale.getFechaHoraEmision())).append('\n');
        sb.append("UEB: ").append(nombreUEB(vale.getIdUEB())).append('\n');
        sb.append("Destino: ").append(nombreDestino(vale.getIdDestino())).append('\n');
        sb.append("Producto: ").append(nombreProducto(vale.getIdProducto())).append('\n');
        sb.append("Cantidad: ").append(vale.getCantidadDespachada() != null
                ? vale.getCantidadDespachada().toPlainString() : "").append('\n');
        sb.append("Unidad de medida: ").append(safe(vale.getUnidadMedida())).append('\n');
        sb.append("Estado: ").append(safe(vale.getEstado())).append('\n');
        if (anulado) {
            sb.append("Motivo de anulación: ").append(safe(vale.getMotivoAnulacion())).append('\n');
            sb.append("Fecha y hora de anulación: ").append(formatFechaHora(vale.getFechaHoraAnulacion())).append('\n');
        }
        sb.append("---------------------------------------------\n");
        sb.append("Copia reimpresa; no sustituye al vale original.");

        mostrarVistaPrevia("Reimpresión del vale " + safe(vale.getNumeroVale()) + " (" + marca + ")",
                sb.toString());
    }

    private void mostrarVistaPrevia(String titulo, String contenido) {
        JTextArea area = new JTextArea(contenido);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JDialog dialogo = new JDialog(SwingUtilities.getWindowAncestor(this), titulo, Dialog.ModalityType.APPLICATION_MODAL);
        dialogo.setLayout(new BorderLayout(0, 8));
        dialogo.add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton imprimirBtn = new JButton("Imprimir");
        JButton cerrarBtn = new JButton("Cerrar");
        imprimirBtn.addActionListener(e -> imprimirTexto(titulo, contenido));
        cerrarBtn.addActionListener(e -> dialogo.dispose());
        botones.add(imprimirBtn);
        botones.add(cerrarBtn);
        dialogo.add(botones, BorderLayout.SOUTH);

        dialogo.setSize(420, 460);
        dialogo.setLocationRelativeTo(this);
        dialogo.setVisible(true);
    }

    private void imprimirTexto(String titulo, String contenido) {
        String[] lineas = contenido.split("\n", -1);
        PrinterJob trabajo = PrinterJob.getPrinterJob();
        trabajo.setJobName(titulo);
        trabajo.setPrintable((Graphics graphics, PageFormat pageFormat, int pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) graphics;
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            int altoLinea = g2.getFontMetrics().getHeight();
            int y = altoLinea;
            for (String linea : lineas) {
                g2.drawString(linea, 0, y);
                y += altoLinea;
                if (y > pageFormat.getImageableHeight()) {
                    break;
                }
            }
            return Printable.PAGE_EXISTS;
        });
        try {
            if (trabajo.printDialog()) {
                trabajo.print();
            }
        } catch (PrinterException ex) {
            MessageHelper.showValidation(this, "No se pudo imprimir: " + ex.getMessage());
        }
    }

    private void prepararNuevoVale() {
        cantidadField.setText("");
        // unidad se mantiene/autocompleta según producto, pero limpiamos selección dependiente de forma controlada
        numeroLabel.setText("Se generará al emitir");
        fechaHoraLabel.setText(formatFechaHora(LocalDateTime.now()));
        estadoLabel.setText(ValeEntrega.ESTADO_EMITIDO);
        // asegurar combos coherentes con relaciones permitidas
        if (!updatingCombos) {
            if (productoCombo.getItemCount() > 0 && productoCombo.getSelectedIndex() < 0) {
                productoCombo.setSelectedIndex(0);
            }
            actualizarDestinosSegunProducto();
            actualizarUnidadDesdeProducto();
        }
        unidadField.setText(unidadField.getText() == null ? "" : unidadField.getText());
    }

    private void recargarCatalogos() {
        updatingCombos = true;
        try {
            UUID prevUeb = getSelectedId(uebCombo, uebLabelToId);
            UUID prevProd = getSelectedId(productoCombo, productoLabelToId);
            UUID prevDest = getSelectedId(destinoCombo, destinoLabelToId);

            uebLabelToId.clear();
            productoLabelToId.clear();
            destinoLabelToId.clear();
            uebCombo.removeAllItems();
            productoCombo.removeAllItems();
            destinoCombo.removeAllItems();

            for (UEB u : uebService.listarTodas()) {
                if (u.isActiva()) {
                    String label = safe(u.getCodigo()) + " - " + safe(u.getNombre());
                    uebLabelToId.put(label, u.getIdUEB());
                    uebCombo.addItem(label);
                }
            }
            for (Producto p : productoService.listarTodos()) {
                if (p.isActiva()) {
                    String label = formatProducto(p);
                    productoLabelToId.put(label, p.getIdProducto());
                    productoCombo.addItem(label);
                }
            }
            // destinos iniciales: todos activos (se restringirán según producto)
            for (Destino d : destinoService.listarTodos()) {
                if (d.isActiva()) {
                    String label = formatDestino(d);
                    destinoLabelToId.put(label, d.getIdDestino());
                    destinoCombo.addItem(label);
                }
            }

            seleccionarEnCombo(uebCombo, uebLabelToId, prevUeb);
            seleccionarEnCombo(productoCombo, productoLabelToId, prevProd);
            seleccionarEnCombo(destinoCombo, destinoLabelToId, prevDest);
        } finally {
            updatingCombos = false;
        }
        // aplicar restricciones de relación a partir de la selección actual
        actualizarDestinosSegunProducto();
        if (getSelectedId(destinoCombo, destinoLabelToId) == null && destinoCombo.getItemCount() == 0) {
            // si el producto no tiene destinos válidos, también limitar productos según destino seleccionado (si lo hubiera)
            actualizarProductosSegunDestino();
        }
        actualizarUnidadDesdeProducto();
    }

    private void actualizarDestinosSegunProducto() {
        UUID idProducto = getSelectedId(productoCombo, productoLabelToId);
        UUID prevDestino = getSelectedId(destinoCombo, destinoLabelToId);
        updatingCombos = true;
        try {
            destinoLabelToId.clear();
            destinoCombo.removeAllItems();
            List<Destino> destinosPermitidos = destinosPermitidosParaProducto(idProducto);
            for (Destino d : destinosPermitidos) {
                String label = formatDestino(d);
                destinoLabelToId.put(label, d.getIdDestino());
                destinoCombo.addItem(label);
            }
            if (prevDestino != null && !seleccionarEnCombo(destinoCombo, destinoLabelToId, prevDestino)) {
                // selección previa ya no válida; se deja la primera opción válida (si existe)
                if (destinoCombo.getItemCount() > 0) destinoCombo.setSelectedIndex(0);
            } else if (destinoCombo.getItemCount() > 0 && destinoCombo.getSelectedIndex() < 0) {
                destinoCombo.setSelectedIndex(0);
            }
        } finally {
            updatingCombos = false;
        }
    }

    private void actualizarProductosSegunDestino() {
        UUID idDestino = getSelectedId(destinoCombo, destinoLabelToId);
        UUID prevProducto = getSelectedId(productoCombo, productoLabelToId);
        updatingCombos = true;
        try {
            productoLabelToId.clear();
            productoCombo.removeAllItems();
            List<Producto> productosPermitidos = productosPermitidosParaDestino(idDestino);
            for (Producto p : productosPermitidos) {
                String label = formatProducto(p);
                productoLabelToId.put(label, p.getIdProducto());
                productoCombo.addItem(label);
            }
            if (prevProducto != null && !seleccionarEnCombo(productoCombo, productoLabelToId, prevProducto)) {
                if (productoCombo.getItemCount() > 0) productoCombo.setSelectedIndex(0);
            } else if (productoCombo.getItemCount() > 0 && productoCombo.getSelectedIndex() < 0) {
                productoCombo.setSelectedIndex(0);
            }
        } finally {
            updatingCombos = false;
        }
        actualizarUnidadDesdeProducto();
        // al cambiar producto permitido, ajustar destinos coherentes
        if (!updatingCombos) {
            actualizarDestinosSegunProducto();
        }
    }

    private List<Destino> destinosPermitidosParaProducto(UUID idProducto) {
        List<Destino> res = new ArrayList<>();
        if (idProducto == null) {
            // sin producto seleccionado: mostrar destinos activos (UI guiará luego)
            for (Destino d : destinoService.listarTodos()) {
                if (d.isActiva()) res.add(d);
            }
            return res;
        }
        for (ProductoDestino rel : productoDestinoService.buscarPorProducto(idProducto)) {
            if (rel.isActiva()) {
                destinoService.buscarPorId(rel.getIdDestino()).ifPresent(d -> {
                    if (d.isActiva()) res.add(d);
                });
            }
        }
        return res;
    }

    private List<Producto> productosPermitidosParaDestino(UUID idDestino) {
        List<Producto> res = new ArrayList<>();
        if (idDestino == null) {
            for (Producto p : productoService.listarTodos()) {
                if (p.isActiva()) res.add(p);
            }
            return res;
        }
        for (ProductoDestino rel : productoDestinoService.buscarPorDestino(idDestino)) {
            if (rel.isActiva()) {
                productoService.buscarPorId(rel.getIdProducto()).ifPresent(p -> {
                    if (p.isActiva()) res.add(p);
                });
            }
        }
        return res;
    }

    private void actualizarUnidadDesdeProducto() {
        UUID idProducto = getSelectedId(productoCombo, productoLabelToId);
        if (idProducto == null) return;
        productoService.buscarPorId(idProducto).ifPresent(p -> {
            if (p.getUnidadMedida() != null && !p.getUnidadMedida().trim().isEmpty()) {
                unidadField.setText(p.getUnidadMedida());
            }
        });
    }

    private void refrescarTabla() {
        currentRows.clear();
        DefaultTableModel model = tablePanel.getTableModel();
        model.setRowCount(0);
        String filtro = (String) estadoFiltroCombo.getSelectedItem();
        List<ValeEntrega> vales = valeEntregaService.listarTodos();
        for (ValeEntrega v : vales) {
            if (filtro != null && !"TODOS".equals(filtro) && !filtro.equals(v.getEstado())) {
                continue;
            }
            currentRows.add(v);
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
        if (valeSeleccionado != null && !currentRows.contains(valeSeleccionado)) {
            valeSeleccionado = null;
            limpiarDetalle();
        }
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

    private UUID getSelectedId(JComboBox<String> combo, Map<String, UUID> map) {
        String sel = (String) combo.getSelectedItem();
        if (sel == null) return null;
        return map.get(sel);
    }

    private boolean seleccionarEnCombo(JComboBox<String> combo, Map<String, UUID> map, UUID id) {
        if (id == null) return false;
        for (Map.Entry<String, UUID> e : map.entrySet()) {
            if (id.equals(e.getValue())) {
                combo.setSelectedItem(e.getKey());
                return true;
            }
        }
        return false;
    }

    private String formatProducto(Producto p) {
        if (p == null) return "(producto)";
        return safe(p.getCodigo()) + " - " + safe(p.getNombre());
    }

    private String formatDestino(Destino d) {
        if (d == null) return "(destino)";
        return safe(d.getCodigo()) + " - " + safe(d.getNombre());
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private BigDecimal parseCantidad(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("La cantidad debe ser un número válido (use punto decimal).");
        }
    }

    private String formatFechaHora(LocalDateTime dt) {
        return dt == null ? "" : dt.format(FECHA_HORA_FMT);
    }
}
