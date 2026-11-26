package cu.pican.view.modules;

import cu.pican.controller.app.AppContext;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.controller.service.AnalisisService;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/**
 * Módulo de consulta para el Especialista de Producción (CU-01 a CU-03):
 * pestañas por tabla oficial con filtro por año y búsqueda por texto.
 * Es de solo lectura: el registro y la corrección de las referencias los realiza
 * el administrador desde Administración - Referencias oficiales (CU-18).
 */
public class ReferenciasModulePanel extends JPanel {

    private static final String TODOS = "Todos";

    public ReferenciasModulePanel(AppContext context) {
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(new ModuleHeader("Referencias oficiales (CU-01 a CU-03)",
                "Consulta de solo lectura de las cuatro tablas oficiales PICAN. "
                        + "El administrador registra y corrige estos valores desde Administración "
                        + "\u2192 Referencias oficiales (CU-18)."), BorderLayout.NORTH);

        AnalisisService analisis = context == null ? null : context.getAnalisisService();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UITheme.NORMAL_FONT);
        tabs.addTab("Tabla 1. Infraestructura",
                construirTabIndicadores(analisis, TablaHistorica.INFRAESTRUCTURA));
        tabs.addTab("Tabla 2. Eficiencia productiva",
                construirTabIndicadores(analisis, TablaHistorica.EFICIENCIA_PRODUCTIVA));
        tabs.addTab("Tabla 3. Destinos y proyecciones",
                construirTabIndicadores(analisis, TablaHistorica.DESTINOS_PROYECCIONES));
        tabs.addTab("Tabla 4. Metas estratégicas", construirTabMetas(analisis));
        add(tabs, BorderLayout.CENTER);
    }

    public ReferenciasModulePanel() {
        this(new AppContext());
    }

    // -------------------- Pestañas --------------------

    private JPanel construirTabIndicadores(AnalisisService analisis, TablaHistorica tabla) {
        List<IndicadorHistorico> datos = new ArrayList<>();
        DataTablePanel panelTabla = new DataTablePanel(tabla.getTitulo(),
                new String[]{"Indicador", "Año", "Valor", "Unidad", "Fuente"}, new Object[0][0]);
        panelTabla.getTable().setName("ref.tabla." + tabla.name());
        JLabel contador = new JLabel(" ");
        contador.setName("ref.contador." + tabla.name());
        contador.setFont(UITheme.SMALL_FONT);

        JComboBox<String> filtroAnio = new JComboBox<>();
        filtroAnio.setName("ref.filtroAnio." + tabla.name());
        JTextField busqueda = new JTextField(18);
        busqueda.setName("ref.busqueda." + tabla.name());
        JLabel desglose = tabla == TablaHistorica.DESTINOS_PROYECCIONES ? new JLabel(" ") : null;
        if (desglose != null) {
            desglose.setName("ref.desglose.DESTINOS_PROYECCIONES");
            desglose.setFont(UITheme.SMALL_FONT);
        }

        Runnable aplicarFiltro = () -> {
            String texto = busqueda.getText() == null ? "" : busqueda.getText().trim().toLowerCase();
            Object seleccion = filtroAnio.getSelectedItem();
            Integer anio = seleccion != null && !TODOS.equals(seleccion)
                    ? Integer.valueOf((String) seleccion) : null;
            Object[][] filas = datos.stream()
                    .filter(h -> anio == null || Objects.equals(anio, h.getAnio()))
                    .filter(h -> texto.isEmpty()
                            || h.getIndicador().toLowerCase().contains(texto)
                            || h.getUnidadMedida().toLowerCase().contains(texto)
                            || h.getFuente().toLowerCase().contains(texto))
                    .sorted(Comparator.comparing(IndicadorHistorico::getIndicador)
                            .thenComparing(IndicadorHistorico::getAnio))
                    .map(h -> new Object[]{h.getIndicador(), h.getAnio(), num(h.getValor()),
                            h.getUnidadMedida(), h.getFuente()})
                    .toArray(Object[][]::new);
            reemplazar(panelTabla, filas);
            contador.setText(datos.isEmpty()
                    ? "Sin registros oficiales cargados. El administrador los registra desde "
                            + "Administración - Referencias oficiales."
                    : "Mostrando " + filas.length + " de " + datos.size() + " registros oficiales.");
            if (desglose != null) {
                desglose.setText(notaDesglose(datos, anio));
            }
        };

        busqueda.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                aplicarFiltro.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                aplicarFiltro.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                aplicarFiltro.run();
            }
        });

        if (analisis != null) {
            try {
                datos.addAll(analisis.consultarReferencias(tabla));
            } catch (RuntimeException ex) {
                contador.setText("No disponible: " + ex.getMessage());
            }
        }

        filtroAnio.addItem(TODOS);
        new TreeSet<>(datos.stream().map(IndicadorHistorico::getAnio).toList()).descendingSet()
                .forEach(anio -> filtroAnio.addItem(String.valueOf(anio)));
        filtroAnio.addActionListener(e -> aplicarFiltro.run());

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Año:"));
        filtros.add(filtroAnio);
        filtros.add(new JLabel("Buscar:"));
        busqueda.setToolTipText("Filtra por indicador, unidad o fuente.");
        filtros.add(busqueda);
        filtros.add(contador);

        JPanel norte = new JPanel(new BorderLayout(0, 6));
        norte.setOpaque(false);
        norte.add(filtros, BorderLayout.NORTH);
        JPanel notas = new JPanel(new BorderLayout());
        notas.setOpaque(false);
        notas.add(new JLabel("<html><div style='width:950px;color:#555555'>"
                + tabla.getNota() + "</div></html>"), BorderLayout.NORTH);
        if (desglose != null) {
            notas.add(desglose, BorderLayout.SOUTH);
        }
        norte.add(notas, BorderLayout.SOUTH);

        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setOpaque(false);
        tab.add(norte, BorderLayout.NORTH);
        tab.add(panelTabla, BorderLayout.CENTER);

        aplicarFiltro.run();
        return tab;
    }

    private JPanel construirTabMetas(AnalisisService analisis) {
        List<MetaEstrategica> datos = new ArrayList<>();
        DataTablePanel panelTabla = new DataTablePanel(TablaHistorica.METAS_ESTRATEGICAS.getTitulo(),
                new String[]{"Indicador", "Real 2019", "Estimado 2030", "Incremento %", "Unidad", "Fuente"},
                new Object[0][0]);
        panelTabla.getTable().setName("ref.tabla.METAS_ESTRATEGICAS");
        JLabel contador = new JLabel(" ");
        contador.setName("ref.contador.METAS_ESTRATEGICAS");
        contador.setFont(UITheme.SMALL_FONT);
        JTextField busqueda = new JTextField(18);
        busqueda.setName("ref.busqueda.METAS_ESTRATEGICAS");

        Runnable aplicarFiltro = () -> {
            String texto = busqueda.getText() == null ? "" : busqueda.getText().trim().toLowerCase();
            Object[][] filas = datos.stream()
                    .filter(m -> texto.isEmpty()
                            || m.getIndicador().toLowerCase().contains(texto)
                            || m.getUnidadMedida().toLowerCase().contains(texto)
                            || m.getFuente().toLowerCase().contains(texto))
                    .sorted(Comparator.comparing(MetaEstrategica::getIndicador))
                    .map(m -> new Object[]{m.getIndicador(), num(m.getValorReal2019()), num(m.getValorEstimado2030()),
                            num(m.getIncrementoPorcentual()), m.getUnidadMedida(), m.getFuente()})
                    .toArray(Object[][]::new);
            reemplazar(panelTabla, filas);
            contador.setText(datos.isEmpty()
                    ? "Sin metas oficiales cargadas. El administrador las registra desde "
                            + "Administración - Referencias oficiales."
                    : "Mostrando " + filas.length + " de " + datos.size() + " metas oficiales.");
        };

        busqueda.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                aplicarFiltro.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                aplicarFiltro.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                aplicarFiltro.run();
            }
        });

        if (analisis != null) {
            try {
                datos.addAll(analisis.consultarMetas());
            } catch (RuntimeException ex) {
                contador.setText("No disponible: " + ex.getMessage());
            }
        }

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        filtros.setOpaque(false);
        filtros.add(new JLabel("Buscar:"));
        busqueda.setToolTipText("Filtra por indicador, unidad o fuente.");
        filtros.add(busqueda);
        filtros.add(contador);

        JPanel norte = new JPanel(new BorderLayout(0, 6));
        norte.setOpaque(false);
        norte.add(filtros, BorderLayout.NORTH);
        norte.add(new JLabel("<html><div style='width:950px;color:#555555'>"
                + TablaHistorica.METAS_ESTRATEGICAS.getNota() + "</div></html>"), BorderLayout.SOUTH);

        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setOpaque(false);
        tab.add(norte, BorderLayout.NORTH);
        tab.add(panelTabla, BorderLayout.CENTER);

        aplicarFiltro.run();
        return tab;
    }

    // -------------------- Utilidades --------------------

    private String notaDesglose(List<IndicadorHistorico> datos, Integer anioSeleccionado) {
        List<Integer> anios = anioSeleccionado == null
                ? datos.stream().map(IndicadorHistorico::getAnio).distinct().sorted().toList()
                : List.of(anioSeleccionado);
        List<String> notas = new ArrayList<>();
        for (Integer anio : anios) {
            agregarDiferencia(datos, anio, "Huevos Ponedoras (Comercial)", List.of(
                    "– Balance Nacional", "– Consumo Social (Salud/Educación)",
                    "– Turismo / Mercado MLC"), notas);
            agregarDiferencia(datos, anio, "Carne Avícola (Especializada + Alt.)", List.of(
                    "– Autoabastecimiento Municipal"), notas);
        }
        if (notas.isEmpty()) {
            return " ";
        }
        return "<html><div style='width:950px;color:#555555'><b>No desglosada en la fuente:</b><br>"
                + String.join("<br>", notas) + "</div></html>";
    }

    private void agregarDiferencia(List<IndicadorHistorico> datos, Integer anio, String principal,
                                   List<String> subordinados, List<String> notas) {
        IndicadorHistorico filaPrincipal = buscarIndicador(datos, anio, principal);
        if (filaPrincipal == null) {
            return;
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (String subordinado : subordinados) {
            IndicadorHistorico fila = buscarIndicador(datos, anio, subordinado);
            if (fila == null) {
                return;
            }
            suma = suma.add(fila.getValor());
        }
        BigDecimal diferencia = filaPrincipal.getValor().subtract(suma);
        if (diferencia.compareTo(BigDecimal.ZERO) != 0) {
            notas.add(anio + " · " + html(principal) + ": " + num(diferencia)
                    + " " + html(filaPrincipal.getUnidadMedida()));
        }
    }

    private IndicadorHistorico buscarIndicador(List<IndicadorHistorico> datos, Integer anio, String nombre) {
        for (IndicadorHistorico fila : datos) {
            if (anio.equals(fila.getAnio()) && normalizarRenglon(fila.getIndicador())
                    .equalsIgnoreCase(normalizarRenglon(nombre))) {
                return fila;
            }
        }
        return null;
    }

    private String normalizarRenglon(String valor) {
        return valor == null ? "" : valor.replace('–', '-').replace('—', '-').trim();
    }

    private String html(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
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
