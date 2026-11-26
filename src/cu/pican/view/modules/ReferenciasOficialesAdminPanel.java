package cu.pican.view.modules;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.controller.service.GestionReferenciasService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.UITheme;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * CU-18. Carga y edición administrativa de las referencias oficiales: indicadores
 * históricos (Tablas 1 a 3) y metas estratégicas (Tabla 4). Solo visible para ADMINISTRADOR.
 */
public class ReferenciasOficialesAdminPanel extends JPanel {

    private static final String TODAS = "Todas";

    private final GestionReferenciasService service;

    // Indicadores históricos
    private final DataTablePanel tablaIndicadores = new DataTablePanel("Indicadores históricos oficiales",
            new String[]{"Tabla", "Indicador", "Año", "Valor", "Unidad", "Fuente"}, new Object[0][0]);
    private final List<IndicadorHistorico> indicadores = new ArrayList<>();
    private final JComboBox<String> filtroTabla = new JComboBox<>();
    private final JLabel estadoIndicadores = new JLabel(" ");
    private final JComboBox<TablaHistorica> tablaCombo = new JComboBox<>(new TablaHistorica[]{
            TablaHistorica.INFRAESTRUCTURA, TablaHistorica.EFICIENCIA_PRODUCTIVA,
            TablaHistorica.DESTINOS_PROYECCIONES});
    private final JTextField indicadorField = new JTextField(22);
    private final JTextField anioField = new JTextField(6);
    private final JTextField valorField = new JTextField(10);
    private final JTextField unidadField = new JTextField(10);
    private final JTextField fuenteField = new JTextField(22);
    private String indicadorEditando;

    // Metas estratégicas
    private final DataTablePanel tablaMetas = new DataTablePanel("Metas estratégicas oficiales",
            new String[]{"Indicador", "Real 2019", "Estimado 2030", "Incremento %", "Unidad", "Fuente"},
            new Object[0][0]);
    private final List<MetaEstrategica> metas = new ArrayList<>();
    private final JLabel estadoMetas = new JLabel(" ");
    private final JTextField metaIndicadorField = new JTextField(22);
    private final JTextField metaRealField = new JTextField(10);
    private final JTextField metaEstimadoField = new JTextField(10);
    private final JTextField metaIncrementoField = new JTextField(10);
    private final JTextField metaUnidadField = new JTextField(10);
    private final JTextField metaFuenteField = new JTextField(22);
    private String metaEditando;

    public ReferenciasOficialesAdminPanel(GestionReferenciasService service) {
        this.service = service;
        setLayout(new BorderLayout(0, 8));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        nombreComponentes();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UITheme.NORMAL_FONT);
        tabs.addTab("Indicadores históricos", construirTabIndicadores());
        tabs.addTab("Metas estratégicas", construirTabMetas());
        add(tabs, BorderLayout.CENTER);

        if (service != null) {
            ejecutar(() -> {
                recargarIndicadores();
                recargarMetas();
            });
        }
    }

    // -------------------- Componentes --------------------

    private void nombreComponentes() {
        tablaIndicadores.getTable().setName("admin.ref.tablaIndicadores");
        tablaMetas.getTable().setName("admin.ref.tablaMetas");
        filtroTabla.setName("admin.ref.filtroTabla");
        tablaCombo.setName("admin.ref.tablaCombo");
        indicadorField.setName("admin.ref.campoIndicador");
        anioField.setName("admin.ref.campoAnio");
        valorField.setName("admin.ref.campoValor");
        unidadField.setName("admin.ref.campoUnidad");
        fuenteField.setName("admin.ref.campoFuente");
        metaIndicadorField.setName("admin.ref.campoMetaIndicador");
        metaRealField.setName("admin.ref.campoMetaReal");
        metaEstimadoField.setName("admin.ref.campoMetaEstimado");
        metaIncrementoField.setName("admin.ref.campoMetaIncremento");
        metaUnidadField.setName("admin.ref.campoMetaUnidad");
        metaFuenteField.setName("admin.ref.campoMetaFuente");
    }

    private JPanel construirTabIndicadores() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setOpaque(false);

        filtroTabla.addItem(TODAS);
        for (TablaHistorica tabla : new TablaHistorica[]{TablaHistorica.INFRAESTRUCTURA,
                TablaHistorica.EFICIENCIA_PRODUCTIVA, TablaHistorica.DESTINOS_PROYECCIONES}) {
            filtroTabla.addItem(tabla.getTitulo());
        }
        filtroTabla.addActionListener(e -> ejecutar(this::recargarIndicadores));

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Tabla:"));
        filtros.add(filtroTabla);
        AppButton actualizar = new AppButton("Actualizar lista");
        actualizar.addActionListener(e -> ejecutar(this::recargarIndicadores));
        filtros.add(actualizar);
        estadoIndicadores.setFont(UITheme.SMALL_FONT);
        filtros.add(estadoIndicadores);

        tab.add(filtros, BorderLayout.NORTH);
        tab.add(tablaIndicadores, BorderLayout.CENTER);
        tab.add(construirFormIndicadores(), BorderLayout.SOUTH);

        tablaIndicadores.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaIndicadores.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSeleccionIndicador();
            }
        });
        return tab;
    }

    private JPanel construirFormIndicadores() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                "Nuevo registro / edición del seleccionado (se guarda en la tabla oficial)"));
        form.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Tabla:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; form.add(tablaCombo, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE; form.add(new JLabel("Indicador:"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; form.add(indicadorField, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0; form.add(new JLabel("Año:"), c);
        c.gridx = 1; form.add(anioField, c);
        c.gridx = 2; form.add(new JLabel("Valor:"), c);
        c.gridx = 3; c.weightx = 1; form.add(valorField, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0; form.add(new JLabel("Unidad:"), c);
        c.gridx = 1; form.add(unidadField, c);
        c.gridx = 2; form.add(new JLabel("Fuente:"), c);
        c.gridx = 3; c.weightx = 1; form.add(fuenteField, c);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        botones.setOpaque(false);
        AppButton nuevo = new AppButton("Nuevo registro");
        nuevo.setName("admin.ref.botonNuevoIndicador");
        nuevo.addActionListener(e -> limpiarFormIndicadores());
        AppButton registrar = new AppButton("Registrar");
        registrar.setName("admin.ref.botonRegistrarIndicador");
        registrar.addActionListener(e -> ejecutar(this::registrarIndicador));
        AppButton guardar = new AppButton("Guardar cambios");
        guardar.setName("admin.ref.botonGuardarIndicador");
        guardar.addActionListener(e -> ejecutar(this::guardarIndicador));
        botones.add(nuevo);
        botones.add(registrar);
        botones.add(guardar);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 4; c.weightx = 1; form.add(botones, c);
        return form;
    }

    private JPanel construirTabMetas() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setOpaque(false);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Metas estratégicas (Tabla 4). El incremento se calcula solo si se deja en blanco."));
        AppButton actualizar = new AppButton("Actualizar lista");
        actualizar.addActionListener(e -> ejecutar(this::recargarMetas));
        filtros.add(actualizar);
        estadoMetas.setFont(UITheme.SMALL_FONT);
        filtros.add(estadoMetas);

        tab.add(filtros, BorderLayout.NORTH);
        tab.add(tablaMetas, BorderLayout.CENTER);
        tab.add(construirFormMetas(), BorderLayout.SOUTH);

        tablaMetas.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaMetas.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSeleccionMeta();
            }
        });
        return tab;
    }

    private JPanel construirFormMetas() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                "Nueva meta / edición de la seleccionada"));
        form.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Indicador:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; form.add(metaIndicadorField, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE; form.add(new JLabel("Unidad:"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; form.add(metaUnidadField, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0; form.add(new JLabel("Real 2019:"), c);
        c.gridx = 1; form.add(metaRealField, c);
        c.gridx = 2; form.add(new JLabel("Estimado 2030:"), c);
        c.gridx = 3; c.weightx = 1; form.add(metaEstimadoField, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0; form.add(new JLabel("Incremento % (opcional):"), c);
        c.gridx = 1; form.add(metaIncrementoField, c);
        c.gridx = 2; form.add(new JLabel("Fuente:"), c);
        c.gridx = 3; c.weightx = 1; form.add(metaFuenteField, c);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        botones.setOpaque(false);
        AppButton nuevo = new AppButton("Nueva meta");
        nuevo.setName("admin.ref.botonNuevaMeta");
        nuevo.addActionListener(e -> limpiarFormMetas());
        AppButton registrar = new AppButton("Registrar meta");
        registrar.setName("admin.ref.botonRegistrarMeta");
        registrar.addActionListener(e -> ejecutar(this::registrarMeta));
        AppButton guardar = new AppButton("Guardar cambios");
        guardar.setName("admin.ref.botonGuardarMeta");
        guardar.addActionListener(e -> ejecutar(this::guardarMeta));
        botones.add(nuevo);
        botones.add(registrar);
        botones.add(guardar);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 4; c.weightx = 1; form.add(botones, c);
        return form;
    }

    // -------------------- Indicadores --------------------

    private void recargarIndicadores() {
        if (service == null) {
            return;
        }
        String seleccion = (String) filtroTabla.getSelectedItem();
        indicadores.clear();
        indicadores.addAll(service.listarIndicadores().stream()
                .filter(h -> TODAS.equals(seleccion) || h.getTabla().getTitulo().equals(seleccion))
                .sorted(Comparator.comparing((IndicadorHistorico h) -> h.getTabla().name())
                        .thenComparing(IndicadorHistorico::getIndicador)
                        .thenComparing(IndicadorHistorico::getAnio))
                .toList());
        Object[][] filas = indicadores.stream()
                .map(h -> new Object[]{h.getTabla().getTitulo(), h.getIndicador(), h.getAnio(),
                        num(h.getValor()), h.getUnidadMedida(), h.getFuente()})
                .toArray(Object[][]::new);
        reemplazar(tablaIndicadores, filas);
        if (indicadorEditando != null) {
            int indice = -1;
            for (int i = 0; i < indicadores.size(); i++) {
                if (Objects.equals(indicadorEditando, indicadores.get(i).getId())) {
                    indice = i;
                    break;
                }
            }
            if (indice >= 0) {
                tablaIndicadores.getTable().setRowSelectionInterval(indice, indice);
                return;
            }
        }
        tablaIndicadores.getTable().clearSelection();
        limpiarFormIndicadores();
    }

    private void cargarSeleccionIndicador() {
        int fila = tablaIndicadores.getTable().getSelectedRow();
        if (fila < 0 || fila >= indicadores.size()) {
            return;
        }
        IndicadorHistorico h = indicadores.get(fila);
        indicadorEditando = h.getId();
        tablaCombo.setSelectedItem(h.getTabla());
        indicadorField.setText(h.getIndicador());
        anioField.setText(String.valueOf(h.getAnio()));
        valorField.setText(num(h.getValor()));
        unidadField.setText(h.getUnidadMedida());
        fuenteField.setText(h.getFuente());
        estadoIndicadores.setText("Editando el registro seleccionado. Modifique los campos y pulse Guardar cambios.");
    }

    private void registrarIndicador() {
        IndicadorHistorico nuevo = service.registrarIndicador(
                (TablaHistorica) tablaCombo.getSelectedItem(), indicadorField.getText(),
                anio(anioField), decimal(valorField, "El valor"),
                unidadField.getText(), fuenteField.getText());
        limpiarFormIndicadores();
        recargarIndicadores();
        MessageHelper.showInfo(this, "Registro oficial creado:\n" + nuevo.getIndicador() + " ("
                + nuevo.getAnio() + ") en " + nuevo.getTabla().getTitulo() + ".");
    }

    private void guardarIndicador() {
        if (indicadorEditando == null) {
            throw new ValidacionException("Seleccione un registro de la lista para editarlo.");
        }
        service.actualizarIndicador(indicadorEditando, (TablaHistorica) tablaCombo.getSelectedItem(),
                indicadorField.getText(), anio(anioField), decimal(valorField, "El valor"),
                unidadField.getText(), fuenteField.getText());
        limpiarFormIndicadores();
        recargarIndicadores();
        MessageHelper.showInfo(this, "El registro oficial quedó actualizado.");
    }

    private void limpiarFormIndicadores() {
        indicadorEditando = null;
        tablaCombo.setSelectedIndex(0);
        indicadorField.setText("");
        anioField.setText("");
        valorField.setText("");
        unidadField.setText("");
        fuenteField.setText("");
        estadoIndicadores.setText(" ");
    }

    // -------------------- Metas --------------------

    private void recargarMetas() {
        if (service == null) {
            return;
        }
        metas.clear();
        metas.addAll(service.listarMetas().stream()
                .sorted(Comparator.comparing(MetaEstrategica::getIndicador))
                .toList());
        Object[][] filas = metas.stream()
                .map(m -> new Object[]{m.getIndicador(), num(m.getValorReal2019()), num(m.getValorEstimado2030()),
                        num(m.getIncrementoPorcentual()), m.getUnidadMedida(), m.getFuente()})
                .toArray(Object[][]::new);
        reemplazar(tablaMetas, filas);
        if (metaEditando != null) {
            int indice = -1;
            for (int i = 0; i < metas.size(); i++) {
                if (Objects.equals(metaEditando, metas.get(i).getId())) {
                    indice = i;
                    break;
                }
            }
            if (indice >= 0) {
                tablaMetas.getTable().setRowSelectionInterval(indice, indice);
                return;
            }
        }
        tablaMetas.getTable().clearSelection();
        limpiarFormMetas();
    }

    private void cargarSeleccionMeta() {
        int fila = tablaMetas.getTable().getSelectedRow();
        if (fila < 0 || fila >= metas.size()) {
            return;
        }
        MetaEstrategica m = metas.get(fila);
        metaEditando = m.getId();
        metaIndicadorField.setText(m.getIndicador());
        metaRealField.setText(num(m.getValorReal2019()));
        metaEstimadoField.setText(num(m.getValorEstimado2030()));
        metaIncrementoField.setText(num(m.getIncrementoPorcentual()));
        metaUnidadField.setText(m.getUnidadMedida());
        metaFuenteField.setText(m.getFuente());
        estadoMetas.setText("Editando la meta seleccionada. Modifique los campos y pulse Guardar cambios.");
    }

    private void registrarMeta() {
        MetaEstrategica nueva = service.registrarMeta(metaIndicadorField.getText(),
                decimal(metaRealField, "El valor real 2019"),
                decimal(metaEstimadoField, "El valor estimado 2030"),
                decimalOpcional(metaIncrementoField, "El incremento"),
                metaUnidadField.getText(), metaFuenteField.getText());
        limpiarFormMetas();
        recargarMetas();
        MessageHelper.showInfo(this, "Meta estratégica creada:\n" + nueva.getIndicador() + " (incremento "
                + num(nueva.getIncrementoPorcentual()) + " %).");
    }

    private void guardarMeta() {
        if (metaEditando == null) {
            throw new ValidacionException("Seleccione una meta de la lista para editarla.");
        }
        service.actualizarMeta(metaEditando, metaIndicadorField.getText(),
                decimal(metaRealField, "El valor real 2019"),
                decimal(metaEstimadoField, "El valor estimado 2030"),
                decimalOpcional(metaIncrementoField, "El incremento"),
                metaUnidadField.getText(), metaFuenteField.getText());
        limpiarFormMetas();
        recargarMetas();
        MessageHelper.showInfo(this, "La meta estratégica quedó actualizada.");
    }

    private void limpiarFormMetas() {
        metaEditando = null;
        metaIndicadorField.setText("");
        metaRealField.setText("");
        metaEstimadoField.setText("");
        metaIncrementoField.setText("");
        metaUnidadField.setText("");
        metaFuenteField.setText("");
        estadoMetas.setText(" ");
    }

    // -------------------- Utilidades --------------------

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    private Integer anio(JTextField field) {
        String texto = field.getText() == null ? "" : field.getText().trim();
        if (texto.isEmpty()) {
            throw new ValidacionException("El año es obligatorio.");
        }
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException ex) {
            throw new ValidacionException("El año debe ser un número entero.");
        }
    }

    private BigDecimal decimal(JTextField field, String nombre) {
        String texto = field.getText() == null ? "" : field.getText().trim();
        if (texto.isEmpty()) {
            throw new ValidacionException(nombre + " es obligatorio.");
        }
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException ex) {
            throw new ValidacionException(nombre + " debe ser un número válido.");
        }
    }

    private BigDecimal decimalOpcional(JTextField field, String nombre) {
        String texto = field.getText() == null ? "" : field.getText().trim();
        return texto.isEmpty() ? null : decimal(field, nombre);
    }

    private void reemplazar(DataTablePanel tabla, Object[][] filas) {
        DefaultTableModel model = tabla.getTableModel();
        String[] columnas = new String[model.getColumnCount()];
        for (int i = 0; i < columnas.length; i++) {
            columnas[i] = model.getColumnName(i);
        }
        model.setDataVector(filas, columnas);
    }

    private String num(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        return valor.stripTrailingZeros().toPlainString();
    }
}
