package cu.pican.verify;

import cu.pican.controller.app.AppContext;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.controller.service.AutorizacionException;
import cu.pican.controller.service.GestionReferenciasService;
import cu.pican.controller.service.ValidacionException;
import cu.pican.view.modules.ReferenciasModulePanel;
import cu.pican.view.modules.ReferenciasOficialesAdminPanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Verificación de la gestión de referencias oficiales (CU-02, CU-03 y CU-18) y de su
 * consulta: carga administrativa, edición normativa de Producción, rechazo por rol,
 * validaciones de las tablas oficiales y paneles de consulta/administración.
 *
 * Uso:
 *   java ... cu.pican.verify.ReferenciasGestionTest                 (MEMORY)
 *   java ... cu.pican.verify.ReferenciasGestionTest postgresql      (requiere -Dpican.test.url/user/password)
 */
public final class ReferenciasGestionTest {

    private static final String CLAVE_ADMIN = "RefAdmin123!";
    private static final String CLAVE_PROD = "RefProd123!";

    private static AppContext contexto;
    private static final String SUFIJO = UUID.randomUUID().toString().substring(0, 8);

    public static void main(String[] args) throws Exception {
        boolean postgres = args.length > 0 && "postgresql".equalsIgnoreCase(args[0]);
        System.setProperty("pican.persistence", postgres ? "postgresql" : "memory");
        System.setProperty("pican.admin.password", CLAVE_ADMIN);
        if (postgres) {
            String url = requerir("pican.test.url", "Falta -Dpican.test.url");
            String usuario = requerir("pican.test.user", "Falta -Dpican.test.user");
            String base = url.split("\\?", 2)[0];
            base = base.substring(base.lastIndexOf('/') + 1);
            if (!base.toLowerCase(Locale.ROOT).contains("test")) {
                throw new IllegalArgumentException("Base no exclusiva de pruebas: " + base);
            }
            System.setProperty("pican.db.url", url);
            System.setProperty("pican.db.user", usuario);
            System.setProperty("pican.db.password",
                    System.getProperty("pican.test.password", ""));
            System.setProperty("pican.db.name", base);
            System.setProperty("pican.db.migrate", "true");
            System.setProperty("pican.migrations.dir",
                    System.getProperty("pican.test.migrations", "sql/migrations"));
        }

        contexto = new AppContext();
        try {
            verificarServicios();
            verificarPanelConsulta();
            verificarPanelAdmin();
            verificarReglasOficiales();
            System.out.println("[RESUMEN] Gestión de referencias verificada en modo "
                    + (postgres ? "POSTGRESQL" : "MEMORIA")
                    + ": el administrador carga por CU-18, Producción gestiona infraestructura/metas "
                    + "y ambos paneles funcionan.");
            System.exit(0);
        } finally {
            contexto.getAuthService().logout();
        }
    }

    // -------------------- Servicio (CU-18 y roles) --------------------

    private static void verificarServicios() {
        contexto.getAuthService().login("admin", CLAVE_ADMIN);
        contexto.getUsuarioService().registrar("ref_prod", "Especialista de producción",
                CLAVE_PROD, "ESPECIALISTA_DE_PRODUCCION");

        GestionReferenciasService gestion = contexto.getGestionReferenciasService();
        int inicialIndicadores = gestion.listarIndicadores().size();
        int inicialMetas = gestion.listarMetas().size();
        comprobar(inicialIndicadores == 0 && inicialMetas == 0,
                "Las tablas oficiales deben arrancar vacías para que las cargue el administrador: "
                        + inicialIndicadores + " indicadores, " + inicialMetas + " metas");
        System.out.println("[OK] Las referencias oficiales arrancan vacías (las registra el administrador, CU-18).");

        String nombreIndicador = "Indicador de prueba " + SUFIJO;
        IndicadorHistorico nuevo = gestion.registrarIndicador(TablaHistorica.EFICIENCIA_PRODUCTIVA,
                nombreIndicador, 2019, new BigDecimal("12.5"), "%", "Prueba CU-18");
        comprobar(gestion.listarIndicadores().size() == inicialIndicadores + 1,
                "El nuevo indicador no se guardó");
        System.out.println("[OK] Alta de indicador oficial: " + nombreIndicador + " (2019, 12.5 %).");

        esperarValidacion(() -> gestion.registrarIndicador(TablaHistorica.EFICIENCIA_PRODUCTIVA,
                nombreIndicador, 2019, new BigDecimal("99"), "%", "Duplicado"), "clave natural duplicada");

        IndicadorHistorico editado = gestion.actualizarIndicador(nuevo.getId(),
                TablaHistorica.EFICIENCIA_PRODUCTIVA, nombreIndicador, 2019,
                new BigDecimal("13.5"), "%", "Prueba CU-18 editada");
        comprobar(new BigDecimal("13.5").compareTo(editado.getValor()) == 0,
                "La edición no cambió el valor");
        comprobar("Prueba CU-18 editada".equals(editado.getFuente()), "La edición no cambió la fuente");
        System.out.println("[OK] Edición de indicador oficial: valor 13.5 y fuente actualizada.");

        esperarValidacion(() -> gestion.registrarIndicador(TablaHistorica.EFICIENCIA_PRODUCTIVA,
                "Negativo", 2019, new BigDecimal("-1"), "U", "F"), "valor negativo");
        esperarValidacion(() -> gestion.registrarIndicador(TablaHistorica.METAS_ESTRATEGICAS,
                "Fuera de tablas", 2019, BigDecimal.ONE, "U", "F"), "tabla de metas como indicador");
        esperarValidacion(() -> gestion.actualizarIndicador("no-existe",
                TablaHistorica.EFICIENCIA_PRODUCTIVA, "X", 2019, BigDecimal.ONE, "U", "F"),
                "identificador inexistente");

        String nombreMeta = "Meta de prueba " + SUFIJO;
        MetaEstrategica metaNueva = gestion.registrarMeta(nombreMeta, new BigDecimal("100"),
                new BigDecimal("150"), null, "t", "Prueba CU-18");
        comprobar(new BigDecimal("50.00").compareTo(metaNueva.getIncrementoPorcentual()) == 0,
                "El incremento automático no se calculó: " + metaNueva.getIncrementoPorcentual());
        comprobar(gestion.listarMetas().size() == inicialMetas + 1, "La meta nueva no se guardó");
        System.out.println("[OK] Alta de meta con incremento automático: 100 \u2192 150 = +50.00 %.");

        esperarValidacion(() -> gestion.registrarMeta(nombreMeta, new BigDecimal("100"),
                new BigDecimal("150"), null, "t", "Duplicada"), "meta duplicada");

        MetaEstrategica metaEditada = gestion.actualizarMeta(metaNueva.getId(), nombreMeta,
                new BigDecimal("100"), new BigDecimal("160"), new BigDecimal("60.00"), "t", "F editada");
        comprobar(new BigDecimal("60.00").compareTo(metaEditada.getIncrementoPorcentual()) == 0,
                "La edición de la meta no calculó VAL-12");
        esperarValidacion(() -> gestion.actualizarMeta(metaNueva.getId(), nombreMeta,
                new BigDecimal("100"), new BigDecimal("160"), new BigDecimal("55.5"), "t", "F incompatible"),
                "incremento manual incompatible con VAL-12");
        System.out.println("[OK] Edición de meta: incremento validado contra VAL-12 (+60.00 %).");

        esperarValidacion(() -> gestion.registrarMeta("Meta con real cero " + SUFIJO, BigDecimal.ZERO,
                new BigDecimal("10"), null, "t", "F"), "incremento automático con real 2019 en cero");

        // Producción consulta todas las referencias y gestiona infraestructura/metas;
        // la carga de eficiencia y destinos queda reservada al administrador por CU-18.
        contexto.getAuthService().logout();
        contexto.getAuthService().login("ref_prod", CLAVE_PROD);
        comprobar(!gestion.listarIndicadores().isEmpty(), "Producción no puede consultar indicadores");
        esperarAutorizacion(() -> gestion.registrarIndicador(TablaHistorica.EFICIENCIA_PRODUCTIVA,
                "Intento indebido", 2019, BigDecimal.ONE, "U", "F"), "alta CU-18 con especialista");
        comprobar(contexto.getAnalisisService().consultarTodasLasReferencias().stream()
                        .anyMatch(h -> nombreIndicador.equals(h.getIndicador())
                                && new BigDecimal("13.5").compareTo(h.getValor()) == 0),
                "El especialista no ve el indicador editado en la consulta");
        comprobar(contexto.getAnalisisService().consultarMetas().stream()
                        .anyMatch(m -> nombreMeta.equals(m.getIndicador())
                                && new BigDecimal("60.00").compareTo(m.getIncrementoPorcentual()) == 0),
                "El especialista no ve la meta editada en la consulta");
        System.out.println("[OK] Roles: Producción consulta referencias y no carga eficiencia/destinos (CU-18).");
    }

    // -------------------- Panel de consulta (CU-01 a CU-03) --------------------

    private static void verificarPanelConsulta() throws Exception {
        ReferenciasModulePanel panel = edt(() -> new ReferenciasModulePanel(contexto));
        edt(() -> {
            List<JTable> tablas = tablas(panel);
            JTable infra = porNombre(tablas, "ref.tabla.INFRAESTRUCTURA");
            JTable eficiencia = porNombre(tablas, "ref.tabla.EFICIENCIA_PRODUCTIVA");
            JTable destinos = porNombre(tablas, "ref.tabla.DESTINOS_PROYECCIONES");
            JTable metas = porNombre(tablas, "ref.tabla.METAS_ESTRATEGICAS");
            comprobar(infra.getRowCount() == 0, "Tabla 1 debe arrancar vacía; hay " + infra.getRowCount());
            comprobar(eficiencia.getRowCount() == 1,
                    "Tabla 2 debe listar solo el indicador registrado en CU-18; hay " + eficiencia.getRowCount());
            comprobar(destinos.getRowCount() == 0, "Tabla 3 debe arrancar vacía; hay " + destinos.getRowCount());
            comprobar(metas.getRowCount() == 1,
                    "Tabla 4 debe listar solo la meta registrada en CU-18; hay " + metas.getRowCount());

            JLabel contadorInfra = (JLabel) porNombreComponente(panel, "ref.contador.INFRAESTRUCTURA");
            comprobar(contadorInfra.getText().contains("Sin registros oficiales"),
                    "Falta el aviso de tabla vacía: " + contadorInfra.getText());

            JComboBox<?> filtroAnio = (JComboBox<?>) porNombreComponente(panel,
                    "ref.filtroAnio.EFICIENCIA_PRODUCTIVA");
            filtroAnio.setSelectedItem("2019");
            comprobar(eficiencia.getRowCount() == 1, "El filtro por año 2019 debe dejar 1 fila; hay "
                    + eficiencia.getRowCount());
            filtroAnio.setSelectedItem("Todos");

            JTextField busqueda = (JTextField) porNombreComponente(panel,
                    "ref.busqueda.EFICIENCIA_PRODUCTIVA");
            busqueda.setText("Indicador de prueba");
            comprobar(eficiencia.getRowCount() == 1, "La búsqueda por nombre debe dejar 1 fila; hay "
                    + eficiencia.getRowCount());
            return null;
        });
        pintar(panel, "referencias-consulta.png");
        System.out.println("[OK] Panel de consulta: 4 pestañas, filtro por año y búsqueda funcionan.");
    }

    // -------------------- Panel de administración (CU-18) --------------------

    private static void verificarPanelAdmin() throws Exception {
        contexto.getAuthService().logout();
        contexto.getAuthService().login("admin", CLAVE_ADMIN);

        ReferenciasOficialesAdminPanel panel = edt(() ->
                new ReferenciasOficialesAdminPanel(contexto.getGestionReferenciasService()));
        edt(() -> {
            List<JTable> tablas = tablas(panel);
            JTable tablaInd = porNombre(tablas, "admin.ref.tablaIndicadores");
            JTable tablaMetas = porNombre(tablas, "admin.ref.tablaMetas");
            comprobar(tablaInd.getRowCount() == 1, "El panel admin debe listar 1 indicador (el de la prueba); hay "
                    + tablaInd.getRowCount());
            comprobar(tablaMetas.getRowCount() == 1, "El panel admin debe listar 1 meta (la de la prueba); hay "
                    + tablaMetas.getRowCount());
            return null;
        });

        // Alta desde la interfaz.
        String nombreUi = "Indicador UI " + SUFIJO;
        edt(() -> {
            ((JComboBox<?>) porNombreComponente(panel, "admin.ref.tablaCombo"))
                    .setSelectedItem(TablaHistorica.INFRAESTRUCTURA);
            ((JTextField) porNombreComponente(panel, "admin.ref.campoIndicador")).setText(nombreUi);
            ((JTextField) porNombreComponente(panel, "admin.ref.campoAnio")).setText("2019");
            ((JTextField) porNombreComponente(panel, "admin.ref.campoValor")).setText("9");
            ((JTextField) porNombreComponente(panel, "admin.ref.campoUnidad")).setText("U");
            ((JTextField) porNombreComponente(panel, "admin.ref.campoFuente")).setText("Prueba UI");
            boton(panel, "admin.ref.botonRegistrarIndicador").doClick();
            return null;
        });
        comprobar(contexto.getGestionReferenciasService().listarIndicadores().stream()
                        .anyMatch(h -> nombreUi.equals(h.getIndicador())
                                && new BigDecimal("9").compareTo(h.getValor()) == 0),
                "El alta desde la interfaz no se persistió");
        System.out.println("[OK] Alta desde la interfaz admin: " + nombreUi + " (2019, 9 U).");

        // Edición desde la interfaz: seleccionar la fila y cambiar el valor.
        edt(() -> {
            JTable tablaInd = porNombre(tablas(panel), "admin.ref.tablaIndicadores");
            comprobar(tablaInd.getRowCount() == 2, "Tras el alta deben verse 2 indicadores; hay "
                    + tablaInd.getRowCount());
            int fila = -1;
            for (int i = 0; i < tablaInd.getRowCount(); i++) {
                if (nombreUi.equals(tablaInd.getValueAt(i, 1))) {
                    fila = i;
                    break;
                }
            }
            comprobar(fila >= 0, "La fila recién creada no aparece en la tabla");
            tablaInd.setRowSelectionInterval(fila, fila);
            JTextField valor = (JTextField) porNombreComponente(panel, "admin.ref.campoValor");
            comprobar("9".equals(valor.getText()),
                    "Seleccionar la fila no cargó el formulario: " + valor.getText());
            valor.setText("10");
            boton(panel, "admin.ref.botonGuardarIndicador").doClick();
            return null;
        });
        comprobar(contexto.getGestionReferenciasService().listarIndicadores().stream()
                        .anyMatch(h -> nombreUi.equals(h.getIndicador())
                                && new BigDecimal("10").compareTo(h.getValor()) == 0),
                "La edición desde la interfaz no se persistió");
        comprobar(contexto.getGestionReferenciasService().listarIndicadores().size() == 2,
                "La edición no debe duplicar registros");
        System.out.println("[OK] Edición desde la interfaz admin: valor 10 sin duplicados.");

        // Alta de meta desde la interfaz con incremento automático.
        String nombreMetaUi = "Meta UI " + SUFIJO;
        edt(() -> {
            ((JTextField) porNombreComponente(panel, "admin.ref.campoMetaIndicador")).setText(nombreMetaUi);
            ((JTextField) porNombreComponente(panel, "admin.ref.campoMetaReal")).setText("200");
            ((JTextField) porNombreComponente(panel, "admin.ref.campoMetaEstimado")).setText("300");
            ((JTextField) porNombreComponente(panel, "admin.ref.campoMetaUnidad")).setText("t");
            ((JTextField) porNombreComponente(panel, "admin.ref.campoMetaFuente")).setText("Prueba UI");
            boton(panel, "admin.ref.botonRegistrarMeta").doClick();
            return null;
        });
        MetaEstrategica metaUi = contexto.getGestionReferenciasService().listarMetas().stream()
                .filter(m -> nombreMetaUi.equals(m.getIndicador()))
                .findFirst().orElseThrow(() -> new AssertionError("La meta creada desde la interfaz no existe"));
        comprobar(new BigDecimal("50.00").compareTo(metaUi.getIncrementoPorcentual()) == 0,
                "El incremento automático de la meta UI no es 50.00: " + metaUi.getIncrementoPorcentual());
        System.out.println("[OK] Alta de meta desde la interfaz admin con incremento automático +50.00 %.");

        pintar(panel, "referencias-admin.png");
        System.out.println("[OK] Panel admin: listas, formularios, alta y edición verificados.");
    }

    private static void verificarReglasOficiales() throws Exception {
        GestionReferenciasService gestion = contexto.getGestionReferenciasService();
        contexto.getAuthService().logout();
        contexto.getAuthService().login("ref_prod", CLAVE_PROD);

        gestion.registrarIndicador(TablaHistorica.INFRAESTRUCTURA, "Cantidad de Naves",
                2018, new BigDecimal("100"), "U", "VAL-17");
        esperarValidacion(() -> gestion.registrarIndicador(TablaHistorica.INFRAESTRUCTURA,
                "Naves para Ponedoras", 2018, new BigDecimal("101"), "U", "VAL-17"),
                "VAL-17: ponedoras superan el total de naves");
        gestion.registrarIndicador(TablaHistorica.INFRAESTRUCTURA, "Naves para Ponedoras",
                2018, new BigDecimal("100"), "U", "VAL-17");
        esperarValidacion(() -> gestion.registrarIndicador(TablaHistorica.INFRAESTRUCTURA,
                "Tractores", 2018, new BigDecimal("1.5"), "U", "Enteros"),
                "infraestructura decimal");
        System.out.println("[OK] VAL-17 y valores enteros de infraestructura.");

        MetaEstrategica huevos = gestion.registrarMeta("Producción de Huevos VAL-12 " + SUFIJO,
                new BigDecimal("211.9"), new BigDecimal("241.4"), null, "", "Tabla 4");
        MetaEstrategica carne = gestion.registrarMeta("Producción de Carne de Ave VAL-12 " + SUFIJO,
                new BigDecimal("1208.8"), new BigDecimal("10051.9"), null, "", "Tabla 4");
        comprobar(new BigDecimal("13.92").compareTo(huevos.getIncrementoPorcentual()) == 0,
                "VAL-12 de huevos no es +13.92 %: " + huevos.getIncrementoPorcentual());
        comprobar(new BigDecimal("731.56").compareTo(carne.getIncrementoPorcentual()) == 0,
                "VAL-12 de carne no es +731.56 %: " + carne.getIncrementoPorcentual());
        comprobar("millón".equals(huevos.getUnidadMedida()), "La unidad oficial de huevos no es millón");
        esperarValidacion(() -> gestion.registrarMeta("Meta incompatible " + SUFIJO,
                new BigDecimal("211.9"), new BigDecimal("241.4"), new BigDecimal("12.22"),
                "millón", "Porcentaje antiguo"), "porcentaje antiguo incompatible con VAL-12");
        System.out.println("[OK] VAL-12: +13.92 % y +731.56 %; porcentajes antiguos rechazados.");

        esperarAutorizacion(() -> gestion.registrarIndicador(TablaHistorica.DESTINOS_PROYECCIONES,
                "Destino indebido " + SUFIJO, 2021, BigDecimal.ONE, "MM/U", "F"),
                "Producción carga destinos sin CU-18");
        contexto.getAuthService().logout();
        contexto.getAuthService().login("admin", CLAVE_ADMIN);
        gestion.registrarIndicador(TablaHistorica.DESTINOS_PROYECCIONES,
                "Huevos Ponedoras (Comercial)", 2021, new BigDecimal("153.6"), "MM/U", "Tabla 3");
        gestion.registrarIndicador(TablaHistorica.DESTINOS_PROYECCIONES,
                "– Balance Nacional", 2021, new BigDecimal("119.4"), "MM/U", "Tabla 3");
        gestion.registrarIndicador(TablaHistorica.DESTINOS_PROYECCIONES,
                "– Consumo Social (Salud/Educación)", 2021, new BigDecimal("9.6"), "MM/U", "Tabla 3");
        gestion.registrarIndicador(TablaHistorica.DESTINOS_PROYECCIONES,
                "– Turismo / Mercado MLC", 2021, new BigDecimal("21.4"), "MM/U", "Tabla 3");
        esperarValidacion(() -> gestion.registrarIndicador(TablaHistorica.EFICIENCIA_PRODUCTIVA,
                "Año no oficial " + SUFIJO, 2020, BigDecimal.ONE, "%", "F"),
                "año fuera del período oficial");
        System.out.println("[OK] CU-18 carga destinos y se rechazan años fuera de la tabla oficial.");

        contexto.getAuthService().logout();
        contexto.getAuthService().login("ref_prod", CLAVE_PROD);
        ReferenciasModulePanel panel = edt(() -> new ReferenciasModulePanel(contexto));
        edt(() -> {
            JTable destinos = porNombre(tablas(panel), "ref.tabla.DESTINOS_PROYECCIONES");
            comprobar(destinos.getRowCount() == 4, "La consulta de destinos no muestra los cuatro renglones");
            JLabel desglose = (JLabel) porNombreComponente(panel, "ref.desglose.DESTINOS_PROYECCIONES");
            comprobar(desglose.getText().contains("No desglosada en la fuente")
                            && desglose.getText().contains("3.2"),
                    "No se muestra la diferencia 153.6 - 150.4 = 3.2: " + desglose.getText());
            return null;
        });
        System.out.println("[OK] Tabla 3 conserva la diferencia como «No desglosada en la fuente».");
    }

    // -------------------- Utilidades --------------------

    private static void esperarValidacion(Runnable accion, String motivo) {
        try {
            accion.run();
            throw new AssertionError("Se esperaba ValidacionException: " + motivo);
        } catch (ValidacionException ex) {
            System.out.println("[OK] Validación esperada (" + motivo + "): " + ex.getMessage());
        }
    }

    private static void esperarAutorizacion(Runnable accion, String motivo) {
        try {
            accion.run();
            throw new AssertionError("Se esperaba AutorizacionException: " + motivo);
        } catch (AutorizacionException ex) {
            System.out.println("[OK] Autorización esperada (" + motivo + "): " + ex.getMessage());
        }
    }

    private static JTable porNombre(List<JTable> tablas, String nombre) {
        return tablas.stream()
                .filter(t -> nombre.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la tabla " + nombre));
    }

    private static Component porNombreComponente(Container raiz, String nombre) {
        Component encontrado = buscarComponente(raiz, nombre);
        if (encontrado == null) {
            throw new AssertionError("No se encontró el componente " + nombre);
        }
        return encontrado;
    }

    private static Component buscarComponente(Container raiz, String nombre) {
        for (Component componente : raiz.getComponents()) {
            if (nombre.equals(componente.getName())) {
                return componente;
            }
            if (componente instanceof Container) {
                Component encontrado = buscarComponente((Container) componente, nombre);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    private static JButton boton(Container raiz, String nombre) {
        return (JButton) porNombreComponente(raiz, nombre);
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

    private static void pintar(Container panel, String nombre) throws Exception {
        edt(() -> {
            panel.setSize(1200, 720);
            distribuir(panel);
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
        Path ruta = carpeta.resolve(nombre);
        ImageIO.write(imagen, "png", ruta.toFile());
        System.out.println("[UI] " + ruta.toAbsolutePath());
    }

    private static void distribuir(Container contenedor) {
        contenedor.doLayout();
        for (Component componente : contenedor.getComponents()) {
            if (componente instanceof Container) {
                distribuir((Container) componente);
            }
        }
    }

    private static String requerir(String propiedad, String mensaje) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor;
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
