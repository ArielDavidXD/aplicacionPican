package cu.pican.view.modules;

import cu.pican.app.AppContext;
import cu.pican.model.Usuario;
import cu.pican.service.CasoUso;
import cu.pican.service.DashboardService;
import cu.pican.view.components.AppButton;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.components.ModuleHeader;
import cu.pican.view.components.UITheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class DashboardModulePanel extends JPanel {

    private static final String[] PERIODOS = {"Últimos 7 días", "Últimos 30 días", "Últimos 90 días", "Todo el historial"};
    private static final DateTimeFormatter ACTUALIZADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AppContext context;
    private final DashboardService servicio;
    private final JPanel tarjetas = new JPanel(new GridLayout(0, 3, 12, 12));
    private final DataTablePanel tabla;
    private final JLabel sesion = new JLabel();
    private final JLabel alcance = new JLabel();
    private final JLabel nota = new JLabel();
    private final JLabel estado = new JLabel();
    private final JComboBox<String> periodo = new JComboBox<>(PERIODOS);
    private final AppButton actualizar = new AppButton("Actualizar");
    private final boolean periodoHabilitado;
    private SwingWorker<DashboardService.Resumen, Void> trabajo;
    private long versionSolicitud;

    public DashboardModulePanel(AppContext context) {
        this.context = context;
        this.servicio = context == null ? null : context.getDashboardService();
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        this.periodoHabilitado = context != null
                && (context.getAutorizacionService().puedeAcceder(CasoUso.CU_05)
                || context.getAutorizacionService().puedeAcceder(CasoUso.CU_14));
        configurarPeriodo();
        configurarSesion();
        JPanel encabezado = new JPanel(new BorderLayout(0, 12));
        encabezado.setOpaque(false);
        encabezado.add(new ModuleHeader("Inicio / Dashboard",
                "Resumen real según el rol y los datos registrados en esta instalación."), BorderLayout.NORTH);
        encabezado.add(construirNorte(), BorderLayout.SOUTH);
        add(encabezado, BorderLayout.NORTH);

        tarjetas.setOpaque(false);
        tabla = new DataTablePanel("Detalle del resumen",
                new String[]{"Indicador", "Valor", "Detalle"}, new Object[0][0]);
        nota.setFont(UITheme.SMALL_FONT);
        nota.setForeground(UITheme.MUTED_TEXT);
        JPanel detalle = new JPanel(new BorderLayout(0, 8));
        detalle.setOpaque(false);
        detalle.add(tabla, BorderLayout.CENTER);
        detalle.add(nota, BorderLayout.SOUTH);

        JPanel centro = new JPanel(new BorderLayout(0, 12));
        centro.setOpaque(false);
        centro.add(tarjetas, BorderLayout.NORTH);
        centro.add(detalle, BorderLayout.CENTER);
        add(centro, BorderLayout.CENTER);

        estado.setFont(UITheme.SMALL_FONT);
        estado.setForeground(UITheme.MUTED_TEXT);
        add(estado, BorderLayout.SOUTH);

        actualizar.addActionListener(e -> cargar());
        periodo.addActionListener(e -> cargar());
        if (servicio == null) {
            actualizar.setEnabled(false);
            periodo.setEnabled(false);
            mostrarError(new IllegalStateException("No hay contexto de aplicación inicializado."));
        } else {
            SwingUtilities.invokeLater(this::cargar);
        }
    }

    /** Constructor heredado para herramientas que no construyen un AppContext. */
    public DashboardModulePanel() {
        this(null);
    }

    private JPanel construirNorte() {
        JPanel izquierda = new JPanel(new GridLayout(2, 1, 0, 3));
        izquierda.setOpaque(false);
        sesion.setFont(UITheme.NORMAL_FONT);
        sesion.setForeground(UITheme.TEXT);
        alcance.setFont(UITheme.SMALL_FONT);
        alcance.setForeground(UITheme.MUTED_TEXT);
        izquierda.add(sesion);
        izquierda.add(alcance);

        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);
        JLabel etiquetaPeriodo = new JLabel("Período:");
        etiquetaPeriodo.setFont(UITheme.SMALL_FONT);
        etiquetaPeriodo.setForeground(UITheme.MUTED_TEXT);
        periodo.setFont(UITheme.SMALL_FONT);
        periodo.setEnabled(periodoHabilitado);
        periodo.setToolTipText(periodoHabilitado
                ? "Filtra la producción y la cobertura por UEB" : "Este resumen no depende de un período");
        derecha.add(etiquetaPeriodo);
        derecha.add(periodo);
        derecha.add(actualizar);

        JPanel controles = new JPanel(new BorderLayout(8, 8));
        controles.setOpaque(false);
        controles.add(izquierda, BorderLayout.CENTER);
        controles.add(derecha, BorderLayout.EAST);
        return controles;
    }

    private void configurarPeriodo() {
        if (context != null && context.getAutorizacionService().puedeAcceder(CasoUso.CU_14)) {
            periodo.setSelectedIndex(1);
        } else if (periodoHabilitado) {
            periodo.setSelectedIndex(0);
        } else {
            periodo.setSelectedIndex(PERIODOS.length - 1);
        }
    }

    private void configurarSesion() {
        if (context == null) {
            sesion.setText("Sesión: sin contexto de aplicación");
            alcance.setText("No se pueden consultar indicadores.");
            return;
        }
        Usuario usuario = context.getAuthService().getUsuarioAutenticado();
        String nombre = usuario == null ? "Sin sesión" : usuario.getNombreCompleto();
        String rol = usuario == null || usuario.getRol() == null
                ? "Sin rol" : usuario.getRol().getNombre();
        String instalacion = context.esCentral() ? "Instalación central" : "UEB local";
        String persistencia = context.esPostgresql() ? "PostgreSQL" : "Memoria (demostración)";
        sesion.setText("Sesión: " + nombre + " · Rol: " + rol + " · " + instalacion + " · " + persistencia);
        alcance.setText(periodoHabilitado
                ? "Producción registrada en el período seleccionado." : "Resumen general de la instalación.");
    }

    private void cargar() {
        if (servicio == null) {
            return;
        }
        if (trabajo != null && !trabajo.isDone()) {
            trabajo.cancel(false);
        }
        long solicitud = ++versionSolicitud;
        int dias = switch (periodo.getSelectedIndex()) {
            case 0 -> 7;
            case 1 -> 30;
            case 2 -> 90;
            default -> 0;
        };
        setCargando(true);
        trabajo = new SwingWorker<>() {
            @Override
            protected DashboardService.Resumen doInBackground() {
                return servicio.resumir(dias);
            }

            @Override
            protected void done() {
                if (solicitud != versionSolicitud) {
                    return;
                }
                setCargando(false);
                try {
                    mostrarResumen(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    mostrarError(ex);
                } catch (ExecutionException ex) {
                    mostrarError(ex.getCause() == null ? ex : ex.getCause());
                } catch (CancellationException ex) {
                    mostrarError(new IllegalStateException("La consulta del Dashboard fue cancelada."));
                }
            }
        };
        trabajo.execute();
    }

    private void setCargando(boolean cargando) {
        actualizar.setEnabled(!cargando && servicio != null);
        periodo.setEnabled(!cargando && periodoHabilitado);
        setCursor(cargando ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        if (cargando) {
            estado.setForeground(UITheme.MUTED_TEXT);
            estado.setText("Cargando datos reales del Dashboard…");
        }
    }

    private void mostrarResumen(DashboardService.Resumen resumen) {
        tarjetas.removeAll();
        tarjetas.setLayout(new GridLayout(0, 3, 12, 12));
        for (DashboardService.Indicador indicador : resumen.indicadores()) {
            tarjetas.add(crearTarjeta(indicador));
        }
        tarjetas.revalidate();
        tarjetas.repaint();

        tabla.setTitulo(resumen.tituloDetalle());
        tabla.getTableModel().setDataVector(
                resumen.detalle().toArray(Object[][]::new), resumen.columnas());
        alcance.setText(resumen.subtitulo());
        nota.setText(notaHtml(resumen.nota()));
        estado.setForeground(UITheme.MUTED_TEXT);
        estado.setText("Actualizado: " + LocalDateTime.now().format(ACTUALIZADO)
                + " · " + resumen.indicadores().size() + " indicadores · "
                + resumen.detalle().size() + " filas de detalle");
        firePropertyChange("resumenActualizado", null, resumen);
    }

    private void mostrarError(Throwable error) {
        Throwable causa = error;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String mensaje = causa.getMessage() == null || causa.getMessage().isBlank()
                ? causa.getClass().getSimpleName() : causa.getMessage();
        tarjetas.removeAll();
        tarjetas.setLayout(new BorderLayout());
        JPanel errorPanel = new JPanel(new BorderLayout(0, 8));
        errorPanel.setOpaque(false);
        errorPanel.setBorder(BorderFactory.createEmptyBorder(24, 12, 24, 12));
        JLabel titulo = new JLabel("No se pudieron cargar los indicadores");
        titulo.setFont(UITheme.SECTION_FONT);
        titulo.setForeground(new Color(166, 45, 45));
        JTextArea descripcion = new JTextArea(mensaje + "\nPulse Actualizar para volver a intentarlo.");
        descripcion.setEditable(false);
        descripcion.setOpaque(false);
        descripcion.setLineWrap(true);
        descripcion.setWrapStyleWord(true);
        descripcion.setFont(UITheme.NORMAL_FONT);
        descripcion.setForeground(UITheme.TEXT);
        errorPanel.add(titulo, BorderLayout.NORTH);
        errorPanel.add(descripcion, BorderLayout.CENTER);
        tarjetas.add(errorPanel, BorderLayout.CENTER);
        tarjetas.revalidate();
        tarjetas.repaint();

        tabla.setTitulo("Detalle del resumen");
        tabla.getTableModel().setDataVector(new Object[0][0],
                new String[]{"Indicador", "Valor", "Detalle"});
        alcance.setText("La consulta no finalizó correctamente.");
        nota.setText(notaHtml("No se muestran datos parciales ni ficticios."));
        estado.setForeground(new Color(166, 45, 45));
        estado.setText("Error al actualizar: " + acortar(mensaje, 240));
        actualizar.setEnabled(servicio != null);
        periodo.setEnabled(periodoHabilitado && servicio != null);
        setCursor(Cursor.getDefaultCursor());
        firePropertyChange("cargaFallida", null, causa);
    }

    private JPanel crearTarjeta(DashboardService.Indicador indicador) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UITheme.SIDEBAR_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JLabel titulo = new JLabel(indicador.titulo());
        titulo.setFont(UITheme.NORMAL_FONT);
        titulo.setForeground(UITheme.TEXT);

        JLabel valor = new JLabel(indicador.valor());
        float tamano = indicador.valor().length() > 18 ? 16f : 22f;
        valor.setFont(UITheme.NORMAL_FONT.deriveFont(Font.BOLD, tamano));
        valor.setForeground(UITheme.PRIMARY_DARK);

        JTextArea detalle = new JTextArea(indicador.detalle());
        detalle.setEditable(false);
        detalle.setOpaque(false);
        detalle.setLineWrap(true);
        detalle.setWrapStyleWord(true);
        detalle.setBorder(null);
        detalle.setFont(UITheme.SMALL_FONT);
        detalle.setForeground(UITheme.MUTED_TEXT);

        panel.add(titulo, BorderLayout.NORTH);
        panel.add(valor, BorderLayout.CENTER);
        panel.add(detalle, BorderLayout.SOUTH);
        return panel;
    }

    private static String notaHtml(String texto) {
        String contenido = texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div style='width:800px'>" + contenido + "</div></html>";
    }

    private static String acortar(String texto, int limite) {
        return texto.length() <= limite ? texto : texto.substring(0, limite - 1) + "…";
    }
}
