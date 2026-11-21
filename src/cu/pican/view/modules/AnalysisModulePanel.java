package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.ComparacionMeta;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.Nave;
import cu.pican.model.RegistroAnalisis;
import cu.pican.model.TablaHistorica;
import cu.pican.model.UEB;
import cu.pican.service.AnalisisService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.BarChartPanel;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.MessageHelper;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/**
 * CU-14: Analisis e indicadores. Las referencias historicas oficiales y la informacion operativa
 * se muestran en fuentes separadas e identificadas. Ninguna referencia es modificable.
 */
public class AnalysisModulePanel extends JPanel {

    private final AnalisisService service;

    private final JComboBox<TablaHistorica> historicoTablaCombo =
            new JComboBox<>(new TablaHistorica[]{TablaHistorica.INFRAESTRUCTURA,
                    TablaHistorica.EFICIENCIA_PRODUCTIVA, TablaHistorica.DESTINOS_PROYECCIONES});
    private final JPanel historicoTablaContainer = new JPanel(new BorderLayout());
    private final JPanel historicoChartContainer = new JPanel(new BorderLayout());
    private final JLabel historicoNota = new JLabel();

    private final JTextField huevosDesde = new JTextField(10);
    private final JTextField huevosHasta = new JTextField(10);
    private final JComboBox<String> huevosUeb = new JComboBox<>();
    private final JComboBox<String> huevosNave = new JComboBox<>();
    private final JPanel huevosResultados = new JPanel();
    private final Map<String, java.util.UUID> huevosUebIds = new HashMap<>();
    private final Map<String, java.util.UUID> huevosNaveIds = new HashMap<>();

    private final JTextField carneDesde = new JTextField(10);
    private final JTextField carneHasta = new JTextField(10);
    private final JComboBox<String> carneUeb = new JComboBox<>();
    private final JPanel carneResultados = new JPanel();
    private final Map<String, java.util.UUID> carneUebIds = new HashMap<>();

    private final JTextField metasDesde = new JTextField(10);
    private final JTextField metasHasta = new JTextField(10);
    private final JPanel metasTablaContainer = new JPanel(new BorderLayout());
    private final JPanel metasChartContainer = new JPanel(new BorderLayout());
    private final JPanel metasComparacionContainer = new JPanel(new BorderLayout());
    private final JLabel metasNota = new JLabel(
            "Metas oficiales 2030. NO son cuotas anuales ni por UEB. No se calculan porcentajes de cumplimiento.");

    public AnalysisModulePanel(AppContext context) {
        this.service = context == null ? null : context.getAnalisisService();
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(new javax.swing.border.EmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Análisis e indicadores (CU-14)",
                "Fuentes separadas: referencias históricas oficiales (solo lectura) e información operativa registrada."),
                BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Referencias históricas", buildHistoricoTab());
        tabs.addTab("Producción de huevos", buildHuevosTab());
        tabs.addTab("Producción de carne", buildCarneTab());
        tabs.addTab("Metas estratégicas", buildMetasTab());
        add(tabs, BorderLayout.CENTER);

        if (service != null) {
            llenarComboUEB(huevosUeb, huevosUebIds);
            llenarComboUEB(carneUeb, carneUebIds);
            llenarComboNave(huevosUeb, huevosNave, huevosUebIds, huevosNaveIds);
            actualizarHistorico();
            actualizarMetas();
        }
    }

    /** Constructor de compatibilidad con el panel provisional anterior. */
    public AnalysisModulePanel() {
        this(new AppContext());
    }

    // -------------------- Pestaña 1: referencias históricas oficiales --------------------

    private JPanel buildHistoricoTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 10));
        tab.setOpaque(false);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Tabla oficial:"));
        filtros.add(historicoTablaCombo);
        historicoTablaCombo.addActionListener(e -> actualizarHistorico());
        tab.add(filtros, BorderLayout.NORTH);

        historicoNota.setFont(UITheme.SMALL_FONT);
        historicoNota.setForeground(UITheme.MUTED_TEXT);

        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setOpaque(false);
        centro.add(historicoNota);
        centro.add(historicoTablaContainer);
        centro.add(historicoChartContainer);
        tab.add(new JScrollPane(centro), BorderLayout.CENTER);
        return tab;
    }

    private void actualizarHistorico() {
        try {
            TablaHistorica tabla = (TablaHistorica) historicoTablaCombo.getSelectedItem();
            if (tabla == null) {
                return;
            }
            historicoNota.setText("FUENTE: " + tabla.getTitulo() + ". " + tabla.getNota());
            List<IndicadorHistorico> datos = service.consultarReferencias(tabla);
            List<Integer> anios = datos.stream().map(IndicadorHistorico::getAnio)
                    .distinct().sorted().collect(Collectors.toList());

            String[] cols = new String[anios.size() + 3];
            cols[0] = "Indicador";
            for (int i = 0; i < anios.size(); i++) {
                cols[i + 1] = String.valueOf(anios.get(i));
            }
            cols[cols.length - 2] = "Unidad";
            cols[cols.length - 1] = "Fuente";

            List<Object[]> rows = new ArrayList<>();
            for (IndicadorHistorico dato : datos) {
                Object[] row = new Object[cols.length];
                row[0] = dato.getIndicador();
                for (int i = 0; i < anios.size(); i++) {
                    row[i + 1] = dato.getAnio() == anios.get(i) ? dato.getValor().toPlainString() : "";
                }
                row[cols.length - 2] = dato.getUnidadMedida();
                row[cols.length - 1] = dato.getFuente();
                rows.add(row);
            }

            DataTablePanel tablaDatos = nuevaTabla("Referencia oficial - " + tabla.getTitulo(), cols, rows);
            tablaDatos.getTable().getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int fila = tablaDatos.getTable().getSelectedRow();
                    if (fila >= 0 && fila < datos.size()) {
                        mostrarGraficoHistorico(datos.get(fila));
                    }
                }
            });
            reemplazar(historicoTablaContainer, tablaDatos);
            if (!datos.isEmpty()) {
                mostrarGraficoHistorico(datos.get(0));
            } else {
                reemplazar(historicoChartContainer, new JPanel());
            }
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void mostrarGraficoHistorico(IndicadorHistorico indicador) {
        List<BarChartPanel.Barra> barras = new ArrayList<>();
        for (IndicadorHistorico dato : service.consultarReferenciasPorIndicador(indicador.getIndicador())) {
            barras.add(new BarChartPanel.Barra(String.valueOf(dato.getAnio()), dato.getValor()));
        }
        String titulo = indicador.getIndicador() + " (" + indicador.getUnidadMedida() + ")";
        reemplazar(historicoChartContainer,
                new BarChartPanel(titulo, indicador.getUnidadMedida(), indicador.getFuente(), barras));
    }

    // -------------------- Pestaña 2: producción de huevos --------------------

    private JPanel buildHuevosTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setOpaque(false);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Desde:"));
        filtros.add(huevosDesde);
        filtros.add(new JLabel("Hasta:"));
        filtros.add(huevosHasta);
        filtros.add(new JLabel("UEB:"));
        filtros.add(huevosUeb);
        filtros.add(new JLabel("Nave:"));
        filtros.add(huevosNave);
        huevosUeb.addActionListener(e -> llenarComboNave(huevosUeb, huevosNave, huevosUebIds, huevosNaveIds));
        AppButton consultar = new AppButton("Consultar");
        consultar.addActionListener(e -> consultarHuevos());
        filtros.add(consultar);
        tab.add(filtros, BorderLayout.NORTH);

        huevosResultados.setLayout(new BoxLayout(huevosResultados, BoxLayout.Y_AXIS));
        huevosResultados.setOpaque(false);
        tab.add(new JScrollPane(huevosResultados), BorderLayout.CENTER);
        return tab;
    }

    private void consultarHuevos() {
        try {
            LocalDate desde = fecha(huevosDesde, "desde");
            LocalDate hasta = fecha(huevosHasta, "hasta");
            java.util.UUID idUEB = idSeleccionado(huevosUeb, huevosUebIds);
            java.util.UUID idNave = idSeleccionado(huevosNave, huevosNaveIds);

            List<RegistroAnalisis> registros;
            if (idNave != null) {
                registros = service.consultarHuevosPorNave(idNave, desde, hasta);
            } else if (idUEB != null) {
                registros = service.consultarHuevosPorUEB(idUEB, desde, hasta);
            } else {
                registros = service.consultarHuevosPorPeriodo(desde, hasta);
            }

            List<RegistroAnalisis> consolidados = idUEB == null
                    ? service.consultarConsolidaciones(desde, hasta)
                    : service.consultarConsolidacionesPorUEB(idUEB, desde, hasta);
            List<RegistroAnalisis> asociadas = service.consultarPartesUnidadesAsociadas(desde, hasta);

            huevosResultados.removeAll();
            huevosResultados.add(nuevaTabla("Producción de huevos - OPERATIVO (parte de nave)",
                    new String[]{"Fecha", "Nave / UEB", "Huevos producidos", "Unidad", "Fuente", "Observación"},
                    filas(registros)));
            huevosResultados.add(espacio());
            huevosResultados.add(nuevaTabla("Consolidaciones - OPERATIVO (no se suman a los partes)",
                    new String[]{"Fecha inicio", "UEB", "Cantidad", "Unidad", "Fuente", "Observación"},
                    filas(consolidados)));
            huevosResultados.add(espacio());
            huevosResultados.add(nuevaTabla("Partes de unidades asociadas - sin campos productivos definidos",
                    new String[]{"Fecha", "Referencia", "Cantidad", "Unidad", "Fuente", "Observación"},
                    filas(asociadas)));
            huevosResultados.revalidate();
            huevosResultados.repaint();
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    // -------------------- Pestaña 3: producción de carne --------------------

    private JPanel buildCarneTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setOpaque(false);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Desde:"));
        filtros.add(carneDesde);
        filtros.add(new JLabel("Hasta:"));
        filtros.add(carneHasta);
        filtros.add(new JLabel("UEB:"));
        filtros.add(carneUeb);
        AppButton consultar = new AppButton("Consultar");
        consultar.addActionListener(e -> consultarCarne());
        filtros.add(consultar);
        tab.add(filtros, BorderLayout.NORTH);

        carneResultados.setLayout(new BorderLayout());
        carneResultados.setOpaque(false);
        tab.add(new JScrollPane(carneResultados), BorderLayout.CENTER);
        return tab;
    }

    private void consultarCarne() {
        try {
            LocalDate desde = fecha(carneDesde, "desde");
            LocalDate hasta = fecha(carneHasta, "hasta");
            java.util.UUID idUEB = idSeleccionado(carneUeb, carneUebIds);
            List<RegistroAnalisis> registros = idUEB == null
                    ? service.consultarCarnePorPeriodo(desde, hasta)
                    : service.consultarCarnePorUEB(idUEB, desde, hasta);
            reemplazar(carneResultados, nuevaTabla("Producción de carne - OPERATIVO",
                    new String[]{"Fecha", "UEB", "Cantidad", "Unidad", "Fuente", "Observación"},
                    filas(registros)));
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    // -------------------- Pestaña 4: metas estratégicas --------------------

    private JPanel buildMetasTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setOpaque(false);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Período operativo desde:"));
        filtros.add(metasDesde);
        filtros.add(new JLabel("hasta:"));
        filtros.add(metasHasta);
        AppButton comparar = new AppButton("Actualizar comparación");
        comparar.addActionListener(e -> actualizarMetas());
        filtros.add(comparar);
        tab.add(filtros, BorderLayout.NORTH);

        metasNota.setFont(UITheme.SMALL_FONT);
        metasNota.setForeground(UITheme.MUTED_TEXT);

        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setOpaque(false);
        centro.add(metasNota);
        centro.add(metasTablaContainer);
        centro.add(metasChartContainer);
        centro.add(espacio());
        centro.add(metasComparacionContainer);
        tab.add(new JScrollPane(centro), BorderLayout.CENTER);
        return tab;
    }

    private void actualizarMetas() {
        try {
            List<MetaEstrategica> metas = service.consultarMetas();
            List<Object[]> rows = new ArrayList<>();
            for (MetaEstrategica meta : metas) {
                rows.add(new Object[]{meta.getIndicador(), meta.getValorReal2019().toPlainString(),
                        meta.getValorEstimado2030().toPlainString(),
                        meta.getIncrementoPorcentual().toPlainString() + " %",
                        meta.getUnidadMedida(), meta.getFuente()});
            }
            DataTablePanel tablaMetas = nuevaTabla("Tabla 4. Metas estratégicas - REFERENCIA OFICIAL",
                    new String[]{"Indicador", "Real 2019", "Estimado 2030", "Incremento aprobado", "Unidad", "Fuente"},
                    rows);
            tablaMetas.getTable().getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int fila = tablaMetas.getTable().getSelectedRow();
                    if (fila >= 0 && fila < metas.size()) {
                        mostrarGraficoMeta(metas.get(fila));
                    }
                }
            });
            reemplazar(metasTablaContainer, tablaMetas);
            if (!metas.isEmpty()) {
                mostrarGraficoMeta(metas.get(0));
            }

            LocalDate desde = fecha(metasDesde, "desde");
            LocalDate hasta = fecha(metasHasta, "hasta");
            List<ComparacionMeta> comparaciones = service.compararMetasConProduccionOperativa(desde, hasta);
            List<Object[]> filas = new ArrayList<>();
            for (ComparacionMeta c : comparaciones) {
                filas.add(new Object[]{c.getIndicador(), c.getValorHistorico2019().toPlainString(),
                        c.getValorMeta2030().toPlainString(),
                        c.getProduccionOperativa() == null ? "sin dato"
                                : c.getProduccionOperativa().toPlainString(),
                        c.getUnidadOperativa(), c.getPeriodoOperativo(), c.getAclaracion()});
            }
            reemplazar(metasComparacionContainer, nuevaTabla(
                    "Comparación referencia / meta / producción operativa (sin cumplimiento)",
                    new String[]{"Indicador", "Histórico 2019", "Meta 2030", "Producción operativa",
                            "Unidad operativa", "Período", "Aclaración"},
                    filas));
        } catch (RuntimeException ex) {
            MessageHelper.showValidation(this, ex.getMessage());
        }
    }

    private void mostrarGraficoMeta(MetaEstrategica meta) {
        List<BarChartPanel.Barra> barras = new ArrayList<>();
        barras.add(new BarChartPanel.Barra("Real 2019", meta.getValorReal2019()));
        barras.add(new BarChartPanel.Barra("Meta 2030", meta.getValorEstimado2030()));
        reemplazar(metasChartContainer,
                new BarChartPanel(meta.getIndicador(), meta.getUnidadMedida(), meta.getFuente(), barras));
    }

    // -------------------- Utilidades --------------------

    private void llenarComboUEB(JComboBox<String> combo, Map<String, java.util.UUID> ids) {
        combo.removeAllItems();
        ids.clear();
        combo.addItem("Todas");
        if (service == null) {
            return;
        }
        for (UEB ueb : service.listarUEBs()) {
            String item = (ueb.getCodigo() == null ? "" : ueb.getCodigo() + " - ") + ueb.getNombre();
            combo.addItem(item);
            ids.put(item, ueb.getIdUEB());
        }
    }

    private void llenarComboNave(JComboBox<String> uebCombo, JComboBox<String> naveCombo,
                                 Map<String, java.util.UUID> uebIds, Map<String, java.util.UUID> naveIds) {
        naveCombo.removeAllItems();
        naveIds.clear();
        naveCombo.addItem("Todas");
        java.util.UUID idUEB = idSeleccionado(uebCombo, uebIds);
        if (idUEB == null || service == null) {
            return;
        }
        for (Nave nave : service.listarNaves()) {
            if (idUEB.equals(nave.getIdUEB())) {
                String item = (nave.getCodigo() == null ? "" : nave.getCodigo() + " - ") + nave.getNombre();
                naveCombo.addItem(item);
                naveIds.put(item, nave.getIdNave());
            }
        }
    }

    private LocalDate fecha(JTextField field, String nombre) {
        String texto = field.getText() == null ? "" : field.getText().trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException ex) {
            throw new ValidacionException("La fecha " + nombre + " debe tener formato AAAA-MM-DD.");
        }
    }

    private java.util.UUID idSeleccionado(JComboBox<String> combo, Map<String, java.util.UUID> ids) {
        Object item = combo.getSelectedItem();
        return item == null ? null : ids.get(item.toString());
    }

    private List<Object[]> filas(List<RegistroAnalisis> registros) {
        List<Object[]> out = new ArrayList<>();
        for (RegistroAnalisis r : registros) {
            out.add(new Object[]{
                    r.getFecha() == null ? "-" : r.getFecha().toString(),
                    r.getElemento(),
                    r.getCantidad() == null ? "sin dato" : r.getCantidad().toPlainString(),
                    r.getUnidadMedida(),
                    r.getFuente(),
                    r.getObservacion()
            });
        }
        return out;
    }

    private DataTablePanel nuevaTabla(String titulo, String[] cols, List<Object[]> rows) {
        DataTablePanel tabla = new DataTablePanel(titulo, cols, rows.toArray(new Object[0][]));
        tabla.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setPreferredSize(new Dimension(700, Math.max(90, Math.min(280, 50 + rows.size() * 22))));
        return tabla;
    }

    private JPanel espacio() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(10, 12));
        return p;
    }

    private void reemplazar(JPanel contenedor, JPanel nuevo) {
        contenedor.removeAll();
        contenedor.setLayout(new BorderLayout());
        contenedor.add(nuevo, BorderLayout.CENTER);
        contenedor.revalidate();
        contenedor.repaint();
    }
}