package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.DecisionDirectivo;
import cu.pican.model.DestinatarioInstitucional;
import cu.pican.model.EntregaInforme;
import cu.pican.model.FuenteInforme;
import cu.pican.model.Informe;
import cu.pican.model.OpcionFuente;
import cu.pican.model.TipoFuenteInforme;
import cu.pican.service.AnalisisService;
import cu.pican.service.InformeService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/** CU-15: elaboración, consulta, decisión y entrega de informes estadísticos. */
public class ReportManagementPanel extends JPanel {

    private static final String TODOS = "TODOS";

    private final InformeService service;
    private final AnalisisService analisisService;

    private final JComboBox<String> filtroEstado = new JComboBox<>(
            new String[]{TODOS, Informe.ESTADO_ELABORADO, Informe.ESTADO_DEVUELTO,
                    Informe.ESTADO_APROBADO, Informe.ESTADO_ENTREGADO});
    private final DataTablePanel tablaInformes = new DataTablePanel("Informes registrados",
            new String[]{"ID", "Número", "Título", "Período", "Elaboración", "Especialista", "Estado", "Rev.", "Vers."},
            new Object[0][0]);
    private final List<Informe> informes = new ArrayList<>();

    private final JLabel estadoLabel = new JLabel("-");
    private final JLabel revisionLabel = new JLabel("-");
    private final JLabel numeroLabel = new JLabel("-");
    private final JLabel periodoLabel = new JLabel("-");
    private final JLabel especialistaLabel = new JLabel("-");
    private final JLabel resumenAreaLabel = new JLabel("Resumen del informe");
    private final JTextArea resumenArea = new JTextArea(5, 40);

    private final JTextField numeroField = new JTextField(12);
    private final JTextField tituloField = new JTextField(22);
    private final JTextField desdeField = new JTextField(12);
    private final JTextField hastaField = new JTextField(12);
    private final JTextField elaboracionField = new JTextField(12);
    private final JTextField especialistaField = new JTextField(20);
    private final JTextArea contenidoArea = new JTextArea(4, 38);
    private final JPanel formulario = new JPanel(new GridBagLayout());

    private final DataTablePanel tablaFuentes = new DataTablePanel("Fuentes utilizadas",
            new String[]{"Tipo", "Registro origen", "Descripción", "Procedencia", "Detalle"}, new Object[0][0]);
    private final DataTablePanel tablaDecisiones = new DataTablePanel("Historial de decisiones",
            new String[]{"Nro.", "Directivo", "Decisión", "Fecha", "Observación", "Registro"}, new Object[0][0]);
    private final DataTablePanel tablaEntregas = new DataTablePanel("Entregas registradas",
            new String[]{"Destinatario", "Fecha", "Responsable", "Observaciones", "Registro"}, new Object[0][0]);

    private final JTextField fuenteDesdeField = new JTextField(12);
    private final JTextField fuenteHastaField = new JTextField(12);
    private final JComboBox<OpcionFuente> fuentesCombo = new JComboBox<>();

    private final JTextField nombreDirectivoField = new JTextField(18);
    private final JComboBox<String> decisionCombo = new JComboBox<>(
            new String[]{DecisionDirectivo.APROBADO, DecisionDirectivo.DEVUELTO});
    private final JTextField fechaDecisionField = new JTextField(12);
    private final JTextArea observacionDecisionArea = new JTextArea(2, 32);

    private final JComboBox<String> destinatarioCombo = new JComboBox<>(
            new String[]{DestinatarioInstitucional.GEGAN.name(), DestinatarioInstitucional.MINAG.name()});
    private final JTextField fechaEntregaField = new JTextField(12);
    private final JTextField responsableEntregaField = new JTextField(18);
    private final JTextArea observacionesEntregaArea = new JTextArea(2, 32);

    private Informe seleccionado;

    public ReportManagementPanel(AppContext context) {
        this.service = context == null ? null : context.getInformeService();
        this.analisisService = context == null ? null : context.getAnalisisService();

        setLayout(new BorderLayout(0, 10));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(new ModuleHeader("Elaboración y entrega de informes (CU-15)",
                "El especialista registra informes, fuentes, decisiones del directivo y entregas a GEGAN/MINAG. "
                        + "El directivo no inicia sesión en el sistema."), BorderLayout.NORTH);

        tablaInformes.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaInformes.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSeleccion();
            }
        });

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Filtrar por estado:"));
        filtros.add(filtroEstado);
        AppButton refrescar = new AppButton("Actualizar lista");
        refrescar.addActionListener(e -> ejecutar(this::refrescar));
        filtros.add(refrescar);

        JPanel lista = new JPanel(new BorderLayout(0, 6));
        lista.setOpaque(false);
        lista.add(filtros, BorderLayout.NORTH);
        lista.add(tablaInformes, BorderLayout.CENTER);

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.setOpaque(false);
        centro.add(construirFormulario(), BorderLayout.NORTH);
        centro.add(lista, BorderLayout.CENTER);
        centro.add(construirDetalle(), BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);
        add(construirAcciones(), BorderLayout.SOUTH);

        filtroEstado.addActionListener(e -> ejecutar(this::refrescar));
        limpiarFormulario();
        refrescar();
    }

    private JPanel construirFormulario() {
        formulario.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER), "Nuevo informe / datos editables"));
        int fila = 0;
        fila = addCampo(formulario, fila, "Número/código (opcional):", numeroField);
        fila = addCampo(formulario, fila, "Título:", tituloField);
        fila = addCampo(formulario, fila, "Período desde (AAAA-MM-DD):", desdeField);
        fila = addCampo(formulario, fila, "Período hasta (AAAA-MM-DD):", hastaField);
        fila = addCampo(formulario, fila, "Fecha de elaboración:", elaboracionField);
        fila = addCampo(formulario, fila, "Especialista responsable:", especialistaField);
        fila = addCampo(formulario, fila, "Contenido o resumen:", new JScrollPane(contenidoArea));
        return formulario;
    }

    private JTabbedPane construirDetalle() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setPreferredSize(new Dimension(900, 320));
        tabs.addTab("Contenido y estado", construirContenidoTab());
        tabs.addTab("Fuentes", construirFuentesTab());
        tabs.addTab("Decisiones", construirDecisionesTab());
        tabs.addTab("Entregas", construirEntregasTab());
        return tabs;
    }

    private JPanel construirContenidoTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 6));
        panel.setOpaque(false);
        JPanel datos = new JPanel(new GridBagLayout());
        datos.setOpaque(false);
        int f = 0;
        f = addCampo(datos, f, "Estado actual:", estadoLabel);
        f = addCampo(datos, f, "Revisión / versión:", revisionLabel);
        f = addCampo(datos, f, "Número/código:", numeroLabel);
        f = addCampo(datos, f, "Período analizado:", periodoLabel);
        f = addCampo(datos, f, "Especialista:", especialistaLabel);
        JPanel norte = new JPanel(new BorderLayout());
        norte.setOpaque(false);
        norte.add(datos, BorderLayout.NORTH);
        panel.add(norte, BorderLayout.NORTH);

        JPanel resumen = new JPanel(new BorderLayout(4, 4));
        resumen.setOpaque(false);
        resumen.add(resumenAreaLabel, BorderLayout.NORTH);
        resumenArea.setEditable(false);
        resumenArea.setLineWrap(true);
        resumenArea.setWrapStyleWord(true);
        resumen.add(new JScrollPane(resumenArea), BorderLayout.CENTER);
        panel.add(resumen, BorderLayout.CENTER);
        return panel;
    }

    private JPanel construirFuentesTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.add(tablaFuentes, BorderLayout.CENTER);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.setOpaque(false);
        form.add(new JLabel("Período fuentes desde:"));
        form.add(fuenteDesdeField);
        form.add(new JLabel("hasta:"));
        form.add(fuenteHastaField);
        AppButton cargar = new AppButton("Cargar opciones desde análisis");
        cargar.addActionListener(e -> ejecutar(this::cargarOpcionesFuentes));
        form.add(cargar);
        form.add(fuentesCombo);
        AppButton agregar = new AppButton("Agregar fuente seleccionada");
        agregar.addActionListener(e -> ejecutar(this::agregarFuente));
        form.add(agregar);
        panel.add(form, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel construirDecisionesTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.add(tablaDecisiones, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER), "Registrar decisión recibida del directivo"));
        int f = 0;
        f = addCampo(form, f, "Nombre del directivo:", nombreDirectivoField);
        f = addCampo(form, f, "Decisión:", decisionCombo);
        f = addCampo(form, f, "Fecha de la decisión (AAAA-MM-DD):", fechaDecisionField);
        f = addCampo(form, f, "Observación (obligatoria si DEVUELTO):", new JScrollPane(observacionDecisionArea));
        AppButton registrar = new AppButton("Registrar decisión");
        registrar.addActionListener(e -> ejecutar(this::registrarDecision));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = f;
        c.anchor = GridBagConstraints.WEST;
        form.add(registrar, c);
        panel.add(form, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel construirEntregasTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.add(tablaEntregas, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER), "Registrar entrega a GEGAN o MINAG"));
        int f = 0;
        f = addCampo(form, f, "Destinatario:", destinatarioCombo);
        f = addCampo(form, f, "Fecha de entrega (AAAA-MM-DD):", fechaEntregaField);
        f = addCampo(form, f, "Responsable que registra:", responsableEntregaField);
        f = addCampo(form, f, "Observaciones (opcionales):", new JScrollPane(observacionesEntregaArea));
        AppButton registrar = new AppButton("Registrar entrega");
        registrar.addActionListener(e -> ejecutar(this::registrarEntrega));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = f;
        c.anchor = GridBagConstraints.WEST;
        form.add(registrar, c);
        panel.add(form, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel construirAcciones() {
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.setOpaque(false);
        AppButton nuevo = new AppButton("Nuevo informe");
        nuevo.addActionListener(e -> {
            limpiarFormulario();
            formulario.setVisible(true);
            revalidate();
            repaint();
        });
        AppButton guardar = new AppButton("Guardar cambios de contenido");
        guardar.addActionListener(e -> ejecutar(this::guardarContenido));
        AppButton revision = new AppButton("Crear nueva revisión");
        revision.addActionListener(e -> ejecutar(this::crearRevision));
        AppButton ocultar = new AppButton("Ocultar formulario");
        ocultar.addActionListener(e -> {
            formulario.setVisible(false);
            revalidate();
            repaint();
        });
        acciones.add(nuevo);
        acciones.add(guardar);
        acciones.add(revision);
        acciones.add(ocultar);
        return acciones;
    }

    // -------------------- Acciones --------------------

    private void refrescar() {
        if (service == null) {
            return;
        }
        String filtro = (String) filtroEstado.getSelectedItem();
        informes.clear();
        if (TODOS.equals(filtro)) {
            informes.addAll(service.listarInformes());
        } else {
            informes.addAll(service.filtrarPorEstado(filtro));
        }
        Object[][] rows = new Object[informes.size()][9];
        for (int i = 0; i < informes.size(); i++) {
            Informe inf = informes.get(i);
            rows[i] = new Object[]{
                    corto(inf.getIdInforme()),
                    nulo(inf.getNumeroInforme()),
                    inf.getTitulo(),
                    inf.getPeriodoAnalizado(),
                    inf.getFechaElaboracion(),
                    inf.getEspecialistaResponsable(),
                    inf.getEstado(),
                    inf.getNumeroRevision(),
                    inf.getVersion()
            };
        }
        reemplazar(tablaInformes, rows);
        if (!informes.isEmpty()) {
            tablaInformes.getTable().setRowSelectionInterval(0, 0);
        } else {
            onSeleccion();
        }
    }

    private void onSeleccion() {
        int fila = tablaInformes.getTable().getSelectedRow();
        seleccionado = fila >= 0 && fila < informes.size() ? informes.get(fila) : null;
        if (seleccionado == null || service == null) {
            estadoLabel.setText("-");
            revisionLabel.setText("-");
            numeroLabel.setText("-");
            periodoLabel.setText("-");
            especialistaLabel.setText("-");
            resumenArea.setText("");
            reemplazar(tablaFuentes, new Object[0][0]);
            reemplazar(tablaDecisiones, new Object[0][0]);
            reemplazar(tablaEntregas, new Object[0][0]);
            return;
        }
        estadoLabel.setText(seleccionado.getEstado());
        revisionLabel.setText("Revisión " + seleccionado.getNumeroRevision() + " / versión " + seleccionado.getVersion());
        numeroLabel.setText(nulo(seleccionado.getNumeroInforme()));
        periodoLabel.setText(seleccionado.getPeriodoAnalizado());
        especialistaLabel.setText(seleccionado.getEspecialistaResponsable());
        resumenArea.setText(seleccionado.getContenidoResumen());

        List<Object[]> filasFuentes = new ArrayList<>();
        for (FuenteInforme f : service.consultarFuentes(seleccionado.getIdInforme())) {
            filasFuentes.add(new Object[]{f.getTipo(), f.getIdRegistroOrigen(), f.getDescripcion(),
                    f.getFuente(), nulo(f.getDetalleConsulta())});
        }
        reemplazar(tablaFuentes, filasFuentes.toArray(new Object[0][]));

        List<Object[]> filasDecisiones = new ArrayList<>();
        for (DecisionDirectivo d : service.consultarHistorialDecisiones(seleccionado.getIdInforme())) {
            filasDecisiones.add(new Object[]{d.getNumeroDecision(), d.getNombreDirectivo(), d.getDecision(),
                    d.getFechaDecision(), nulo(d.getObservacion()), d.getFechaRegistro()});
        }
        reemplazar(tablaDecisiones, filasDecisiones.toArray(new Object[0][]));

        List<Object[]> filasEntregas = new ArrayList<>();
        for (EntregaInforme e : service.consultarEntregas(seleccionado.getIdInforme())) {
            filasEntregas.add(new Object[]{e.getDestinatario(), e.getFechaEntrega(), e.getResponsableRegistro(),
                    nulo(e.getObservaciones()), e.getFechaRegistro()});
        }
        reemplazar(tablaEntregas, filasEntregas.toArray(new Object[0][]));
    }

    private void crearInforme() {
        service.crearInforme(numeroField.getText(), tituloField.getText(),
                fecha(desdeField, "desde"), fecha(hastaField, "hasta"),
                fecha(elaboracionField, "elaboración"), especialistaField.getText(), contenidoArea.getText());
        formulario.setVisible(false);
        refrescar();
        MessageHelper.showInfo(this, "El informe quedó registrado en estado ELABORADO.");
    }

    private void guardarContenido() {
        requerirSeleccion();
        service.actualizarContenido(seleccionado.getIdInforme(), tituloField.getText(),
                fecha(desdeField, "desde"), fecha(hastaField, "hasta"),
                fecha(elaboracionField, "elaboración"), especialistaField.getText(), contenidoArea.getText());
        refrescar();
        MessageHelper.showInfo(this, "El contenido del informe se actualizó.");
    }

    private void crearRevision() {
        requerirSeleccion();
        Informe revision = service.crearRevision(seleccionado.getIdInforme(), tituloField.getText(),
                fecha(desdeField, "desde"), fecha(hastaField, "hasta"),
                fecha(elaboracionField, "elaboración"), especialistaField.getText(), contenidoArea.getText());
        refrescar();
        MessageHelper.showInfo(this, "Se creó la revisión " + revision.getNumeroRevision()
                + ". El informe anterior se conserva sin cambios.");
    }

    private void cargarOpcionesFuentes() {
        fuentesCombo.removeAllItems();
        for (OpcionFuente op : service.consultarFuentesDisponibles(
                fecha(fuenteDesdeField, "desde fuentes"), fecha(fuenteHastaField, "hasta fuentes"))) {
            fuentesCombo.addItem(op);
        }
        if (fuentesCombo.getItemCount() == 0) {
            MessageHelper.showInfo(this, "No hay fuentes disponibles para el período indicado.");
        }
    }

    private void agregarFuente() {
        requerirSeleccion();
        OpcionFuente op = (OpcionFuente) fuentesCombo.getSelectedItem();
        if (op == null) {
            throw new ValidacionException("Seleccione una fuente desde el análisis antes de agregarla.");
        }
        service.agregarFuente(seleccionado.getIdInforme(), op.getTipo(), op.getIdRegistroOrigen(),
                op.getDescripcion(), op.getFuente(), op.getDetalleConsulta());
        onSeleccion();
        MessageHelper.showInfo(this, "La fuente quedó identificada en el informe.");
    }

    private void registrarDecision() {
        requerirSeleccion();
        service.registrarDecision(seleccionado.getIdInforme(), nombreDirectivoField.getText(),
                (String) decisionCombo.getSelectedItem(), fecha(fechaDecisionField, "decisión"),
                observacionDecisionArea.getText());
        refrescar();
        MessageHelper.showInfo(this, "La decisión quedó registrada en el historial.");
    }

    private void registrarEntrega() {
        requerirSeleccion();
        service.registrarEntrega(seleccionado.getIdInforme(), (String) destinatarioCombo.getSelectedItem(),
                fecha(fechaEntregaField, "entrega"), responsableEntregaField.getText(),
                observacionesEntregaArea.getText());
        refrescar();
        MessageHelper.showInfo(this, "La entrega quedó registrada.");
    }

    // -------------------- Utilidades --------------------

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    private void requerirSeleccion() {
        if (seleccionado == null) {
            throw new ValidacionException("Seleccione un informe de la lista.");
        }
    }

    private LocalDate fecha(JTextField field, String nombre) {
        String texto = field.getText() == null ? "" : field.getText().trim();
        if (texto.isEmpty()) {
            throw new ValidacionException("La fecha de " + nombre + " es obligatoria (AAAA-MM-DD).");
        }
        try {
            return LocalDate.parse(texto);
        } catch (RuntimeException ex) {
            throw new ValidacionException("La fecha de " + nombre + " debe tener formato AAAA-MM-DD.");
        }
    }

    private int addCampo(JPanel panel, int fila, String etiqueta, JComponent campo) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = fila;
        panel.add(new JLabel(etiqueta), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(campo, c);
        return fila + 1;
    }

    private void reemplazar(DataTablePanel tabla, Object[][] rows) {
        javax.swing.table.DefaultTableModel model = tabla.getTableModel();
        String[] columnas = new String[model.getColumnCount()];
        for (int i = 0; i < columnas.length; i++) {
            columnas[i] = model.getColumnName(i);
        }
        model.setDataVector(rows, columnas);
    }

    private void limpiarFormulario() {
        numeroField.setText("");
        tituloField.setText("");
        desdeField.setText("");
        hastaField.setText("");
        elaboracionField.setText(LocalDate.now().toString());
        especialistaField.setText("");
        contenidoArea.setText("");
    }

    private String corto(UUID id) {
        if (id == null) {
            return "-";
        }
        String s = id.toString();
        return s.length() <= 8 ? s : s.substring(0, 8);
    }

    private String nulo(String texto) {
        return texto == null || texto.trim().isEmpty() ? "-" : texto;
    }
}