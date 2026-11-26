package cu.pican.verify;

import cu.pican.controller.app.AppContext;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.UEB;
import cu.pican.controller.service.ConsolidadoUEBService;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.modules.ConsolidationModulePanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * Verificación de la pantalla de consolidación (CU-07).
 *
 * Comprueba que el panel carga consolidaciones reales, lista los partes del período,
 * excluye del total los borradores y muestra un resumen con los totales de huevos y carne.
 */
public final class ConsolidacionPantallaTest {

    private static final String CLAVE_ADMIN = "ConsolAdmin123!";
    private static final String CLAVE_JEFE = "ConsolJefe123!";
    private static final LocalDate HOY = LocalDate.now();

    public static void main(String[] args) throws Exception {
        configurar(args);
        AppContext contexto = new AppContext();
        UUID idUeb = UUID.randomUUID();
        UUID idNave = UUID.randomUUID();
        UUID idNaveBorrador = UUID.randomUUID();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        contexto.getAuthService().login("admin", CLAVE_ADMIN);
        contexto.getUebService().registrar(new UEB(idUeb, "UEB consolidación", "CONS-U-" + sufijo, true));
        contexto.getNaveService().registrar(new Nave(idNave, idUeb, "Nave consolidación", "CONS-N-" + sufijo, true));
        contexto.getNaveService().registrar(new Nave(idNaveBorrador, idUeb, "Nave borrador", "CONS-B-" + sufijo, true));
        contexto.getUsuarioService().registrar("cons_jefe", "Jefe de consolidación", CLAVE_JEFE, "JEFE_DE_UEB");
        contexto.getAuthService().logout();

        contexto.getAuthService().login("cons_jefe", CLAVE_JEFE);
        ParteNave registrado = parte(idNave, HOY, 1000);
        ParteNave borrador = parte(idNaveBorrador, HOY, 777);
        contexto.getParteNaveService().registrar(registrado);
        contexto.getParteNaveService().guardarBorrador(borrador);

        ProduccionCarne carne = new ProduccionCarne();
        carne.setIdProduccion(UUID.randomUUID());
        carne.setIdUEB(idUeb);
        carne.setFechaProduccion(HOY);
        carne.setCantidadToneladas(new BigDecimal("2.500"));
        contexto.getProduccionCarneService().registrar(carne);

        ConsolidadoUEB consolidado = new ConsolidadoUEB();
        consolidado.setIdConsolidado(UUID.randomUUID());
        consolidado.setIdUEB(idUeb);
        consolidado.setFechaInicio(HOY.minusDays(1));
        consolidado.setFechaFin(HOY.plusDays(1));
        consolidado.setEstado("REGISTRADO");
        consolidado.setMotivoParcial("Hay un borrador pendiente de oficializar");
        ConsolidadoUEBService servicioConsolidado = contexto.getConsolidadoUEBService();
        servicioConsolidado.registrar(consolidado);

        ConsolidationModulePanel panel = edt(() -> new ConsolidationModulePanel(contexto));
        edt(() -> {
            List<JTable> tablas = tablas(panel);
            comprobar(!tablas.isEmpty(), "La pantalla no tiene tablas");
            JTable consolidados = tablas.stream()
                    .filter(t -> t.getRowCount() == 1)
                    .findFirst().orElseThrow(() -> new AssertionError("No aparece la consolidación registrada"));
            consolidados.setRowSelectionInterval(0, 0);
            return null;
        });

        edt(() -> {
            List<JTable> tablas = tablas(panel);
            JTable huevos = tablaConFilasYColumnas(tablas, 1, 9);
            JTable tablaCarne = tablaConFilasYColumnas(tablas, 1, 4);
            comprobar("Huevos".equals(huevos.getColumnName(2)),
                    "Falta la columna Huevos en los partes incluidos");
            comprobar(String.valueOf(huevos.getValueAt(0, 2)).equals("1000"),
                    "El detalle no conserva solo el parte registrado");
            comprobar(String.valueOf(tablaCarne.getValueAt(0, 2)).equals("2.500"),
                    "La carne no se listó con su cantidad");
            return null;
        });

        String resumen = edt(() -> {
            for (JLabel etiqueta : etiquetas(panel)) {
                String texto = etiqueta.getText();
                if (texto != null && texto.contains("Huevos:")) {
                    return texto;
                }
            }
            throw new AssertionError("No se encontró el resumen de totales del período");
        });
        comprobar(resumen.contains("1000 U"), "El total de huevos no excluye el borrador: " + resumen);
        comprobar(resumen.contains("Excluidos: nave 1"), "No se informan los borradores excluidos: " + resumen);
        comprobar(resumen.contains("2.5 t"), "Falta el total de carne: " + resumen);

        edt(() -> {
            panel.setSize(1180, 720);
            distribuir(panel);
            List<DataTablePanel> tablasDetalle = new ArrayList<>();
            buscar(panel, DataTablePanel.class, tablasDetalle);
            comprobar(tablasDetalle.size() == 5, "Se esperaban cinco tablas; hay " + tablasDetalle.size());
            for (DataTablePanel t : tablasDetalle) {
                comprobar(t.getHeight() > 0, "Una tabla quedó sin altura visible");
            }
            return null;
        });

        Path carpeta = Paths.get("tmp-smoke");
        Files.createDirectories(carpeta);
        BufferedImage imagen = edt(() -> {
            BufferedImage captura = new BufferedImage(panel.getWidth(), panel.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            panel.paint(captura.getGraphics());
            return captura;
        });
        Path ruta = carpeta.resolve("consolidacion.png");
        ImageIO.write(imagen, "png", ruta.toFile());
        System.out.println("[UI] " + ruta.toAbsolutePath());

        contexto.getAuthService().logout();
        System.out.println("[RESUMEN] Pantalla de consolidación verificada: totales reales, "
                + "borradores excluidos y tablas visibles.");
        System.exit(0);
    }

    private static void configurar(String[] args) {
        System.setProperty("pican.admin.password", CLAVE_ADMIN);
        boolean postgres = args.length > 0 && "postgresql".equalsIgnoreCase(args[0]);
        if (!postgres) {
            System.setProperty("pican.persistence", "memory");
            return;
        }
        String url = requerir("pican.test.url", "Falta -Dpican.test.url");
        String usuario = requerir("pican.test.user", "Falta -Dpican.test.user");
        String clave = requerir("pican.test.password", "Falta -Dpican.test.password");
        String base = url.split("\\?", 2)[0];
        base = base.substring(base.lastIndexOf('/') + 1);
        if (!base.toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalArgumentException("Base no exclusiva de pruebas: " + base);
        }
        System.setProperty("pican.persistence", "postgresql");
        System.setProperty("pican.db.url", url);
        System.setProperty("pican.db.user", usuario);
        System.setProperty("pican.db.password", clave);
        System.setProperty("pican.db.name", base);
        System.setProperty("pican.db.migrate", "true");
    }

    private static String requerir(String propiedad, String mensaje) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor;
    }

    private static ParteNave parte(UUID idNave, LocalDate fecha, int huevos) {
        ParteNave parte = new ParteNave();
        parte.setIdParte(UUID.randomUUID());
        parte.setIdNave(idNave);
        parte.setFechaProduccion(fecha);
        parte.setGallinasVivas(1000);
        parte.setGallinasMuertas(0);
        parte.setGallinasEnfermas(0);
        parte.setHuevosProducidos(huevos);
        parte.setHuevosRotos(0);
        parte.setPiensoKg(new BigDecimal("0.00"));
        return parte;
    }

    private static JTable tablaConFilas(List<JTable> tablas, int filas) {
        return tablas.stream()
                .filter(t -> t.getRowCount() == filas)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay tabla con " + filas + " filas"));
    }

    private static JTable tablaConFilasYColumnas(List<JTable> tablas, int filas, int columnas) {
        return tablas.stream()
                .filter(t -> t.getRowCount() == filas && t.getColumnCount() == columnas)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay tabla con " + filas + " filas y "
                        + columnas + " columnas"));
    }

    private static List<JTable> tablas(Container raiz) {
        List<JTable> resultado = new ArrayList<>();
        buscarTablas(raiz, resultado);
        return resultado;
    }

    private static void buscarTablas(Container contenedor, List<JTable> resultado) {
        for (Component componente : contenedor.getComponents()) {
            if (componente instanceof JTable) {
                resultado.add((JTable) componente);
            } else if (componente instanceof Container) {
                buscarTablas((Container) componente, resultado);
            }
        }
    }

    private static List<JLabel> etiquetas(Container raiz) {
        List<JLabel> resultado = new ArrayList<>();
        for (Component componente : raiz.getComponents()) {
            if (componente instanceof JLabel) {
                resultado.add((JLabel) componente);
            } else if (componente instanceof Container) {
                resultado.addAll(etiquetas((Container) componente));
            }
        }
        return resultado;
    }

    private static <T extends Component> void buscar(Container contenedor, Class<T> tipo, List<T> resultado) {
        for (Component componente : contenedor.getComponents()) {
            if (tipo.isInstance(componente)) {
                resultado.add(tipo.cast(componente));
            }
            if (componente instanceof Container) {
                buscar((Container) componente, tipo, resultado);
            }
        }
    }

    private static void distribuir(Container contenedor) {
        contenedor.doLayout();
        for (Component componente : contenedor.getComponents()) {
            if (componente instanceof Container) {
                distribuir((Container) componente);
            }
        }
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    @FunctionalInterface
    private interface EnEdt<T> {
        T ejecutar() throws Exception;
    }

    private static <T> T edt(EnEdt<T> tarea) throws Exception {
        final Object[] caja = new Object[2];
        SwingUtilities.invokeAndWait(() -> {
            try {
                caja[0] = tarea.ejecutar();
            } catch (Exception ex) {
                caja[1] = ex;
            }
        });
        if (caja[1] != null) {
            throw (Exception) caja[1];
        }
        @SuppressWarnings("unchecked")
        T resultado = (T) caja[0];
        return resultado;
    }
}
