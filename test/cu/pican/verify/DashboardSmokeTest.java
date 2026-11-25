package cu.pican.verify;

import cu.pican.app.AppContext;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Destino;
import cu.pican.model.Informe;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.Recepcion;
import cu.pican.model.Rol;
import cu.pican.model.UEB;
import cu.pican.model.Usuario;
import cu.pican.model.ValeEntrega;
import cu.pican.service.AutorizacionException;
import cu.pican.service.DashboardService;
import cu.pican.service.ValidacionException;
import cu.pican.view.components.DataTablePanel;
import cu.pican.view.modules.DashboardModulePanel;

import java.lang.reflect.InvocationTargetException;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class DashboardSmokeTest {

    private static final String CLAVE_ADMIN = "DashboardAdmin123!";
    private static final String CLAVE_JEFE = "DashboardJefe123!";
    private static final String CLAVE_ESTAD = "DashboardEstad123!";
    private static final String CLAVE_PROD = "DashboardProd123!";
    private static final String PREFIJO = "DASH-";
    private static final LocalDate HOY = LocalDate.now();
    private static final List<Resultado> RESULTADOS = new ArrayList<>();

    private AppContext contexto;
    private UUID idUeb1;
    private UUID idUeb2;
    private UUID idNave1;
    private UUID idNave2;
    private UUID idProducto;
    private UUID idDestino;
    private UUID idUsuarioInactivo;

    public static void main(String[] args) {
        int salida;
        try {
            salida = new DashboardSmokeTest().ejecutar();
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            salida = 2;
        }
        System.exit(salida);
    }

    private int ejecutar() throws Exception {
        configurarContexto();
        contexto = new AppContext();
        verificar("DASH-01", "Rechazo sin sesión y período negativo", this::verificarAutorizacion);
        entrarAdmin();
        crearCuentas();
        crearCatalogos();

        verificar("DASH-02", "Jefe: estado vacío sin inventar producción", () -> {
            entrar("dash_jefe", CLAVE_JEFE);
            DashboardService.Resumen resumen = contexto.getDashboardService().resumir(7);
            indicador(resumen, 0, "Sin datos");
            indicador(resumen, 1, "Sin datos");
            indicador(resumen, 2, "0");
            indicador(resumen, 3, "0");
            indicador(resumen, 4, "0");
            comprobar(resumen.detalle().isEmpty(), "El estado vacío debió no tener pendientes");
        });

        verificar("DASH-03", "Jefe: métricas reales, borradores y filtro por período", this::verificarJefe);
        verificar("DASH-04", "Estadística: totales, cobertura UEB e informe en revisión", this::verificarEstadistica);
        verificar("DASH-05", "Producción: referencias históricas y metas oficiales", this::verificarProduccion);
        verificar("DASH-06", "Administrador: cuentas, catálogos y copias", this::verificarAdministrador);
        verificar("DASH-07", "Vista Swing: carga asíncrona, filtro, título y datos reales", this::verificarVista);
        verificar("DASH-08", "Vista sin contexto: error controlado y sin excepción", this::verificarVistaSinContexto);

        long fallos = RESULTADOS.stream().filter(r -> !"APROBADA".equals(r.estado)).count();
        System.out.println("[RESUMEN DASHBOARD] " + (RESULTADOS.size() - fallos) + "/"
                + RESULTADOS.size() + " pruebas aprobadas; backend="
                + (contexto.esPostgresql() ? "POSTGRESQL" : "MEMORY"));
        return fallos == 0 ? 0 : 1;
    }

    private void configurarContexto() {
        System.setProperty("pican.admin.password", CLAVE_ADMIN);
        String url = System.getProperty("pican.test.url");
        if (url == null || url.isBlank()) {
            System.setProperty("pican.persistence", "memory");
            return;
        }
        String base = url.split("\\?", 2)[0];
        base = base.substring(base.lastIndexOf('/') + 1);
        if (!base.toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalArgumentException("La base de prueba debe ser exclusiva: " + base);
        }
        System.setProperty("pican.persistence", "postgresql");
        System.setProperty("pican.db.url", url);
        System.setProperty("pican.db.user", requerir("pican.test.user", "Falta el usuario de prueba"));
        System.setProperty("pican.db.password", System.getProperty("pican.test.password", "trust_local"));
        System.setProperty("pican.db.name", base);
        System.setProperty("pican.db.migrate", "true");
        System.setProperty("pican.migrations.dir", System.getProperty("pican.test.migrations", "sql/migrations"));
    }

    private void verificarAutorizacion() {
        try {
            contexto.getDashboardService().resumir(0);
            throw new AssertionError("El Dashboard admitió una consulta sin sesión");
        } catch (AutorizacionException esperada) {
            comprobar(esperada.getMessage().contains("iniciar sesión"), "Mensaje de sesión inesperado");
        }
        try {
            contexto.getDashboardService().resumir(-1);
            throw new AssertionError("El Dashboard admitió un período negativo");
        } catch (ValidacionException esperada) {
            comprobar(esperada.getMessage().contains("negativo"), "Mensaje de período inesperado");
        }
    }

    private void crearCuentas() {
        contexto.getUsuarioService().registrar("dash_jefe", "Jefe de prueba", CLAVE_JEFE, Rol.JEFE_UEB);
        contexto.getUsuarioService().registrar("dash_estad", "Estadística de prueba", CLAVE_ESTAD,
                Rol.ESPECIALISTA_ESTADISTICA);
        contexto.getUsuarioService().registrar("dash_prod", "Producción de prueba", CLAVE_PROD,
                Rol.ESPECIALISTA_PRODUCCION);
        Usuario inactivo = contexto.getUsuarioService().registrar("dash_inactivo", "Cuenta inactiva de prueba",
                "DashboardInactivo123!", Rol.JEFE_UEB);
        idUsuarioInactivo = inactivo.getIdUsuario();
        contexto.getUsuarioService().desactivar(idUsuarioInactivo);
    }

    private void crearCatalogos() {
        idUeb1 = UUID.randomUUID();
        idUeb2 = UUID.randomUUID();
        idNave1 = UUID.randomUUID();
        idNave2 = UUID.randomUUID();
        idProducto = UUID.randomUUID();
        idDestino = UUID.randomUUID();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        contexto.getUebService().registrar(new UEB(idUeb1, "UEB Dashboard 1", PREFIJO + "U1-" + sufijo, true));
        contexto.getUebService().registrar(new UEB(idUeb2, "UEB Dashboard 2", PREFIJO + "U2-" + sufijo, true));
        contexto.getUebService().registrar(new UEB(UUID.randomUUID(), "UEB inactiva", PREFIJO + "U3-" + sufijo, false));
        contexto.getNaveService().registrar(new Nave(idNave1, idUeb1, "Nave Dashboard 1", PREFIJO + "N1-" + sufijo, true));
        contexto.getNaveService().registrar(new Nave(idNave2, idUeb2, "Nave Dashboard 2", PREFIJO + "N2-" + sufijo, true));
        contexto.getNaveService().registrar(new Nave(UUID.randomUUID(), idUeb2, "Nave inactiva", PREFIJO + "N3-" + sufijo, false));
        contexto.getProductoService().registrar(new Producto(idProducto, PREFIJO + "PROD-" + sufijo,
                "Producto Dashboard", true, "kg"));
        contexto.getProductoService().registrar(new Producto(UUID.randomUUID(), PREFIJO + "PROD2-" + sufijo,
                "Producto inactivo", false, "kg"));
        contexto.getDestinoService().registrar(new Destino(idDestino, PREFIJO + "DEST-" + sufijo,
                "Destino Dashboard", true));
        contexto.getProductoDestinoService().registrar(new ProductoDestino(
                UUID.randomUUID(), idProducto, idDestino, true));
    }

    private void verificarJefe() {
        entrar("dash_jefe", CLAVE_JEFE);
        ParteNave actual = parte(idNave1, HOY, 1000);
        ParteNave anterior = parte(idNave1, HOY.minusDays(20), 500);
        ParteNave borrador = parte(idNave2, HOY.minusDays(2), 777);
        contexto.getParteNaveService().registrar(actual);
        contexto.getParteNaveService().registrar(anterior);
        contexto.getParteNaveService().guardarBorrador(borrador);

        contexto.getProduccionCarneService().registrar(carne(idUeb1, HOY, "2.500"));
        contexto.getProduccionCarneService().registrar(carne(idUeb1, HOY.minusDays(20), "9.000"));

        ValeEntrega pendiente = contexto.getValeEntregaService().emitir(vale(HOY.atTime(9, 0)));
        ValeEntrega conIncidencia = contexto.getValeEntregaService().emitir(vale(HOY.minusDays(20).atTime(9, 0)));
        Recepcion recepcion = new Recepcion();
        recepcion.setIdRecepcion(UUID.randomUUID());
        recepcion.setIdVale(conIncidencia.getIdVale());
        recepcion.setFechaHoraRecepcion(HOY.minusDays(19).atTime(10, 0));
        recepcion.setCantidadRecibida(new BigDecimal("8.000"));
        recepcion.setUnidadMedida("kg");
        recepcion.setResultado(Recepcion.RESULTADO_NO_CONFORME);
        recepcion.setObservaciones("Faltante detectado en prueba del Dashboard");
        contexto.getRecepcionService().registrar(recepcion);

        DashboardService.Resumen semana = contexto.getDashboardService().resumir(7);
        comprobar("Resumen operativo de la UEB".equals(semana.titulo()), "Título de Jefe incorrecto");
        indicador(semana, 0, "1000 U");
        indicador(semana, 1, "2.5 t");
        indicador(semana, 2, "1");
        indicador(semana, 3, "1");
        indicador(semana, 4, "1");
        indicador(semana, 5, "1");
        comprobar(semana.detalle().size() == 3, "Se esperaban tres pendientes operativos");
        comprobar(semana.detalle().stream().anyMatch(f -> "Parte en borrador".equals(f[0])),
                "Falta el parte en borrador");
        comprobar(semana.detalle().stream().anyMatch(f -> "Vale sin recepción".equals(f[0])),
                "Falta el vale pendiente");
        comprobar(semana.detalle().stream().anyMatch(f -> "Incidencia abierta".equals(f[0])),
                "Falta la incidencia abierta");

        DashboardService.Resumen historial = contexto.getDashboardService().resumir(0);
        indicador(historial, 0, "1500 U");
        indicador(historial, 1, "11.5 t");
        indicador(historial, 5, "2");
        comprobar(historial.nota().contains("REGISTRADOS"), "La nota no explica el criterio de producción oficial");
        comprobar(!contexto.getValeEntregaService().buscarPorId(pendiente.getIdVale()).orElseThrow().getEstado()
                .equals(ValeEntrega.ESTADO_CERRADO), "El vale pendiente cambió de estado");
    }

    private void verificarEstadistica() {
        entrar("dash_jefe", CLAVE_JEFE);
        ConsolidadoUEB consolidado = new ConsolidadoUEB();
        consolidado.setIdConsolidado(UUID.randomUUID());
        consolidado.setIdUEB(idUeb1);
        consolidado.setFechaInicio(HOY.minusDays(1));
        consolidado.setFechaFin(HOY.plusDays(1));
        consolidado.setEstado("REGISTRADO");
        contexto.getConsolidadoUEBService().registrar(consolidado);

        entrar("dash_estad", CLAVE_ESTAD);
        Informe informe = contexto.getInformeService().crearInforme(
                PREFIJO + "INF-" + UUID.randomUUID().toString().substring(0, 8),
                "Informe para Dashboard", HOY.minusDays(10), HOY, HOY,
                "Estadística de prueba", "Resumen de prueba");
        contexto.getInformeService().guardarSecciones(informe.getIdInforme(),
                "Producción.", "Entregas.", "Infraestructura.", "Calidad.", "Alertas.", "Causas y medidas.");
        contexto.getInformeService().enviarARevision(informe.getIdInforme());

        DashboardService.Resumen mes = contexto.getDashboardService().resumir(30);
        comprobar("Resumen estadístico de producción".equals(mes.titulo()), "Título de Estadística incorrecto");
        indicador(mes, 0, "1500 U");
        indicador(mes, 1, "11.5 t");
        indicador(mes, 2, "2");
        indicador(mes, 3, "1");
        indicador(mes, 4, "1");
        indicador(mes, 5, "1");
        comprobar(mes.detalle().size() == 2, "La cobertura debió incluir dos UEB activas");
        comprobar(mes.detalle().stream().anyMatch(f -> f[0].toString().contains("UEB Dashboard 1")
                && "Con reporte".equals(f[5])), "La UEB 1 debió tener reporte");
        comprobar(mes.detalle().stream().anyMatch(f -> f[0].toString().contains("UEB Dashboard 2")
                && "Sin reporte".equals(f[5])), "La UEB 2 no debió contar su borrador como reporte");

        DashboardService.Resumen semana = contexto.getDashboardService().resumir(7);
        indicador(semana, 0, "1000 U");
        indicador(semana, 1, "2.5 t");
        indicador(semana, 3, "1");
        comprobar(semana.nota().contains("no añaden consolidaciones"), "La nota no advierte el posible doble conteo");
    }

    private void verificarProduccion() {
        entrar("dash_prod", CLAVE_PROD);
        DashboardService.Resumen resumen = contexto.getDashboardService().resumir(0);
        comprobar("Resumen de referencias oficiales".equals(resumen.titulo()), "Título de Producción incorrecto");
        indicador(resumen, 0, "67");
        indicador(resumen, 1, "2");
        comprobar(!"Sin datos".equals(resumen.indicadores().get(3).valor()), "No hay año histórico real");
        indicador(resumen, 4, "2");
        indicador(resumen, 5, "2");
        comprobar(resumen.detalle().size() == 69, "Se esperaban 67 referencias y dos metas");
        comprobar(resumen.detalle().stream().anyMatch(f -> "Meta 2030".equals(f[0])),
                "Faltan las metas estratégicas");
    }

    private void verificarAdministrador() {
        entrarAdmin();
        DashboardService.Resumen resumen = contexto.getDashboardService().resumir(0);
        comprobar("Resumen administrativo".equals(resumen.titulo()), "Título de Administrador incorrecto");
        indicador(resumen, 0, "4");
        indicador(resumen, 1, "1");
        indicador(resumen, 2, "2");
        indicador(resumen, 3, "2");
        indicador(resumen, 4, "1");
        indicador(resumen, 5, "Sin copias");
        comprobar(resumen.detalle().size() == 5, "Se esperaban cinco cuentas");
        comprobar(resumen.detalle().stream().anyMatch(f -> "dash_inactivo".equals(f[0])
                && "Inactivo".equals(f[3])), "Falta la cuenta inactiva");
        comprobar(resumen.nota().contains("CREACION"), "La nota de copias no es clara");
        comprobar(!contexto.getUsuarioService().buscarPorId(idUsuarioInactivo).orElseThrow().isActivo(),
                "La cuenta inactiva sigue activa");
    }

    private void verificarVista() throws Exception {
        entrar("dash_jefe", CLAVE_JEFE);
        AtomicReference<DashboardModulePanel> referencia = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> referencia.set(new DashboardModulePanel(contexto)));
        DashboardModulePanel panel = referencia.get();
        esperarCarga(panel, () -> { });
        SwingUtilities.invokeAndWait(() -> {
            comprobar(panel.getComponentCount() > 0, "El panel no construyó componentes");
            DataTablePanel tabla = buscar(panel, DataTablePanel.class);
            comprobar(tabla != null, "El Dashboard no contiene tabla");
            comprobar("Pendientes operativos".equals(tabla.getTitulo()), "El título de la tabla no se actualizó");
            comprobar(tabla.getTableModel().getRowCount() == 3, "La tabla no muestra los tres pendientes");
            comprobarTablaVisible(panel);
            comprobar(!textoVisible(panel).toLowerCase(Locale.ROOT).contains("ficticio"),
                    "La vista aún contiene texto ficticio");
            guardarImagen(panel, "dashboard_jefe.png");
        });

        esperarCarga(panel, () -> edt(() -> {
            JComboBox<?> periodo = buscar(panel, JComboBox.class);
            periodo.setSelectedIndex(3);
        }));
        SwingUtilities.invokeAndWait(() -> {
            DataTablePanel tabla = buscar(panel, DataTablePanel.class);
            comprobar(tabla.getTableModel().getValueAt(0, 0) != null, "La tabla perdió sus datos al filtrar");
        });
        esperarCarga(panel, () -> edt(() -> {
            JComboBox<?> periodo = buscar(panel, JComboBox.class);
            periodo.setSelectedIndex(1);
        }));
        SwingUtilities.invokeAndWait(() -> {
            DataTablePanel tabla = buscar(panel, DataTablePanel.class);
            comprobar(tabla.getTableModel().getRowCount() == 3, "El filtro de 30 días alteró los pendientes globales");
            guardarImagen(panel, "dashboard_jefe_30d.png");
        });

        entrar("dash_estad", CLAVE_ESTAD);
        AtomicReference<DashboardModulePanel> referenciaEstadistica = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> referenciaEstadistica.set(new DashboardModulePanel(contexto)));
        DashboardModulePanel estadistica = referenciaEstadistica.get();
        esperarCarga(estadistica, () -> { });
        SwingUtilities.invokeAndWait(() -> {
            DataTablePanel cobertura = buscar(estadistica, DataTablePanel.class);
            comprobar("Cobertura de producción por UEB".equals(cobertura.getTitulo()),
                    "La vista de Estadística no usa su tabla específica");
            comprobar(cobertura.getTableModel().getRowCount() == 2, "La cobertura UEB no se renderizó");
            comprobarTablaVisible(estadistica);
            comprobar(buscar(estadistica, JComboBox.class).isEnabled(), "Estadística debe poder filtrar período");
            guardarImagen(estadistica, "dashboard_estadistica.png");
        });

        entrarAdmin();
        AtomicReference<DashboardModulePanel> referenciaAdmin = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> referenciaAdmin.set(new DashboardModulePanel(contexto)));
        DashboardModulePanel admin = referenciaAdmin.get();
        esperarCarga(admin, () -> { });
        SwingUtilities.invokeAndWait(() -> {
            comprobar(!buscar(admin, JComboBox.class).isEnabled(), "Administrador no debe filtrar un resumen atemporal");
            comprobar("Cuentas de usuario".equals(buscar(admin, DataTablePanel.class).getTitulo()),
                    "La vista administrativa no usa su tabla específica");
            comprobarTablaVisible(admin);
            guardarImagen(admin, "dashboard_administrador.png");
        });
    }

    private void verificarVistaSinContexto() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DashboardModulePanel panel = new DashboardModulePanel();
            String texto = textoVisible(panel).toLowerCase(Locale.ROOT);
            comprobar(texto.contains("no hay contexto"), "El estado sin contexto no es visible");
            comprobar(texto.contains("no se pudieron cargar"), "El error sin contexto no es visible");
            comprobar(buscar(panel, DataTablePanel.class).getTableModel().getRowCount() == 0,
                    "El error no limpió la tabla");
        });
    }

    private static void edt(Runnable accion) {
        try {
            SwingUtilities.invokeAndWait(accion);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Operación Swing interrumpida", ex);
        } catch (InvocationTargetException ex) {
            Throwable causa = ex.getCause();
            if (causa instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (causa instanceof Error error) {
                throw error;
            }
            throw new AssertionError("Falló una operación Swing", causa);
        }
    }

    private void esperarCarga(DashboardModulePanel panel, Runnable accion) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DashboardService.Resumen> obtenido = new AtomicReference<>();
        java.beans.PropertyChangeListener listener = evento -> {
            obtenido.set((DashboardService.Resumen) evento.getNewValue());
            latch.countDown();
        };
        panel.addPropertyChangeListener("resumenActualizado", listener);
        try {
            accion.run();
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("El Dashboard no terminó su carga en 15 segundos");
            }
            comprobar(obtenido.get() != null, "La carga no publicó un resumen real");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Carga interrumpida", ex);
        } finally {
            panel.removePropertyChangeListener("resumenActualizado", listener);
        }
    }

    private static void comprobarTablaVisible(DashboardModulePanel panel) {
        panel.setSize(1180, 720);
        distribuir(panel);
        DataTablePanel tabla = buscar(panel, DataTablePanel.class);
        comprobar(tabla.getWidth() > 0 && tabla.getHeight() > 120,
                "La tabla quedó fuera del área visible: " + tabla.getBounds());
        comprobar(tabla.getY() + tabla.getHeight() < panel.getHeight(),
                "La tabla se solapa con el estado del Dashboard");
    }

    private static void guardarImagen(DashboardModulePanel panel, String nombre) {
        try {
            Files.createDirectories(Path.of("tmp-smoke"));
            panel.setSize(1180, 720);
            distribuir(panel);
            BufferedImage imagen = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D grafico = imagen.createGraphics();
            try {
                panel.paint(grafico);
            } finally {
                grafico.dispose();
            }
            Path salida = Path.of("tmp-smoke", nombre);
            ImageIO.write(imagen, "png", salida.toFile());
            System.out.println("[UI] " + salida.toAbsolutePath());
        } catch (IOException ex) {
            throw new AssertionError("No se pudo guardar la captura del Dashboard", ex);
        }
    }

    private static void distribuir(Component componente) {
        if (componente instanceof Container contenedor) {
            contenedor.doLayout();
            for (Component hijo : contenedor.getComponents()) {
                distribuir(hijo);
            }
        }
    }

    private static <T> T buscar(Component componente, Class<T> tipo) {
        if (tipo.isInstance(componente)) {
            return tipo.cast(componente);
        }
        if (componente instanceof Container contenedor) {
            for (Component hijo : contenedor.getComponents()) {
                T encontrado = buscar(hijo, tipo);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    private static String textoVisible(Component componente) {
        StringBuilder texto = new StringBuilder();
        if (componente instanceof JLabel etiqueta) {
            texto.append(etiqueta.getText()).append('\n');
        } else if (componente instanceof JTextArea area) {
            texto.append(area.getText()).append('\n');
        }
        if (componente instanceof Container contenedor) {
            for (Component hijo : contenedor.getComponents()) {
                texto.append(textoVisible(hijo));
            }
        }
        return texto.toString();
    }

    private ParteNave parte(UUID idNave, LocalDate fecha, int huevos) {
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

    private ProduccionCarne carne(UUID idUeb, LocalDate fecha, String toneladas) {
        ProduccionCarne carne = new ProduccionCarne();
        carne.setIdProduccion(UUID.randomUUID());
        carne.setIdUEB(idUeb);
        carne.setFechaProduccion(fecha);
        carne.setCantidadToneladas(new BigDecimal(toneladas));
        return carne;
    }

    private ValeEntrega vale(LocalDateTime fechaHora) {
        ValeEntrega vale = new ValeEntrega();
        vale.setIdVale(UUID.randomUUID());
        vale.setIdUEB(idUeb1);
        vale.setIdDestino(idDestino);
        vale.setIdProducto(idProducto);
        vale.setCantidadDespachada(new BigDecimal("10.000"));
        vale.setUnidadMedida("kg");
        vale.setFechaHoraEmision(fechaHora);
        return vale;
    }

    private void entrarAdmin() {
        entrar("admin", CLAVE_ADMIN);
    }

    private void entrar(String usuario, String clave) {
        contexto.getAuthService().login(usuario, clave);
    }

    private static void indicador(DashboardService.Resumen resumen, int indice, String esperado) {
        DashboardService.Indicador indicador = resumen.indicadores().get(indice);
        comprobar(esperado.equals(indicador.valor()), "Indicador '" + indicador.titulo()
                + "': esperado " + esperado + ", obtenido " + indicador.valor());
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    private static String requerir(String propiedad, String mensaje) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor;
    }

    private void verificar(String id, String descripcion, Prueba prueba) {
        try {
            prueba.ejecutar();
            RESULTADOS.add(new Resultado(id, descripcion, "APROBADA", "Resultado esperado"));
            System.out.println("[APROBADA] " + id + " -> " + descripcion);
        } catch (Throwable fallo) {
            String detalle = fallo.getClass().getSimpleName() + ": " + fallo.getMessage();
            RESULTADOS.add(new Resultado(id, descripcion, "FALLIDA", detalle));
            System.out.println("[FALLIDA] " + id + " -> " + detalle);
            fallo.printStackTrace(System.out);
        }
    }

    @FunctionalInterface
    private interface Prueba {
        void ejecutar() throws Exception;
    }

    private record Resultado(String id, String descripcion, String estado, String detalle) {
    }
}
