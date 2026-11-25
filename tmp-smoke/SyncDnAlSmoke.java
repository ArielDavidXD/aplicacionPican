import cu.pican.app.AppContext;
import cu.pican.model.Destino;
import cu.pican.model.Informe;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.Rol;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.model.ValeEntrega;
import cu.pican.sync.SyncException;
import cu.pican.sync.SyncModelo.ResultadoImportacion;
import cu.pican.sync.SyncService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Arnés de humo para DN/AL con dos bases PostgreSQL separadas. Uso: SyncDnAlSmoke central|ueb|central-vale|ueb-vale|informe */
public final class SyncDnAlSmoke {

    private static final String DIR = "tmp-smoke";

    public static void main(String[] args) throws Exception {
        String fase = args.length > 0 ? args[0] : "central";
        AppContext ctx = new AppContext();
        ctx.getAuthService().login("admin", System.getProperty("pican.admin.password"));
        switch (fase) {
            case "central": central(ctx); break;
            case "ueb": ueb(ctx); break;
            case "central-vale": centralVale(ctx); break;
            case "informe": informe(ctx); break;
            default: uebVale(ctx); break;
        }
    }

    private static void central(AppContext ctx) {
        UEB ueb = new UEB(UUID.randomUUID(), "UEB Prueba Humo", "UEB01", true);
        ctx.getUebService().registrar(ueb);
        ctx.getProductoService().registrar(
                new Producto(UUID.randomUUID(), "HUEVOS", "Huevos", true, "millar"));
        ctx.getDestinoService().registrar(
                new Destino(UUID.randomUUID(), "DEST01", "Destino Prueba", true));
        ctx.getUnidadAsociadaService().registrar(
                new UnidadAsociada(UUID.randomUUID(), "Unidad Prueba", "UA01", true));
        ctx.getUsuarioService().registrar("jefe_ueb01", "Jefe Prueba", "Jefe123!", Rol.JEFE_UEB);
        ctx.getUsuarioService().registrar("estad_central", "Estadística Central", "Estad123!",
                Rol.ESPECIALISTA_ESTADISTICA);

        SyncService sync = ctx.getSyncService();
        UUID idAlta = sync.generarPaqueteAlta(ruta("AVI_AL_UEB01.avipkg"), ueb.getIdUEB(), "jefe_ueb01");
        System.out.println("ALTA GENERADO " + idAlta);
        ctx.getAuthService().login("estad_central", "Estad123!");
        sync.generarBajada(ruta("AVI_DN1_UEB01.avipkg"), ueb.getIdUEB());
        sync.generarBajada(ruta("AVI_DN2_UEB01.avipkg"), ueb.getIdUEB());
        sync.generarBajada(ruta("AVI_DN3_UEB01.avipkg"), ueb.getIdUEB());
        System.out.println("UEB_ID " + ueb.getIdUEB());
        System.out.println("CENTRAL OK");
    }

    private static void ueb(AppContext ctx) {
        SyncService sync = ctx.getSyncService();

        ResultadoImportacion alta = sync.importarPaqueteAlta(ruta("AVI_AL_UEB01.avipkg"));
        System.out.println("ALTA " + alta.estado + " aplicados=" + alta.cuantos("APLICADO")
                + " omitidos=" + alta.cuantos("OMITIDO") + " rechazados=" + alta.cuantos("RECHAZADO"));
        UUID idUeb = ctx.getUebService().listarTodas().isEmpty() ? null
                : ctx.getUebService().listarTodas().get(0).getIdUEB();

        ctx.getAuthService().login("jefe_ueb01", "Jefe123!");
        System.out.println("JEFE LOGIN OK rol="
                + ctx.getAuthService().getUsuarioAutenticado().getRol().getNombre());

        try {
            sync.importarPaqueteAlta(ruta("AVI_AL_UEB01.avipkg"));
            System.out.println("REPLAY ALTA: FALLO (no rechazó)");
        } catch (SyncException ex) {
            System.out.println("REPLAY ALTA: OK (" + ex.getMessage() + ")");
        }

        ResultadoImportacion dn1 = sync.importarBajada(ruta("AVI_DN1_UEB01.avipkg"));
        System.out.println("DN1 " + dn1.estado + " aplicados=" + dn1.cuantos("APLICADO")
                + " omitidos=" + dn1.cuantos("OMITIDO") + " rechazados=" + dn1.cuantos("RECHAZADO"));

        bloquear();
        try {
            sync.generarExportacion(ruta("UP_bloqueado.avipkg"), idUeb);
            System.out.println("SIN13 BLOQUEO: FALLO (generó)");
        } catch (SyncException ex) {
            System.out.println("SIN13 BLOQUEO: OK (" + ex.getMessage() + ")");
        }

        ResultadoImportacion dn2 = sync.importarBajada(ruta("AVI_DN2_UEB01.avipkg"));
        System.out.println("DN2 " + dn2.estado);
        System.out.println("SIN13 DESBLOQUEO: " + (estaBloqueado()
                ? "FALLO (sigue bloqueado)" : "OK (desbloqueado)"));

        configurarVersion("99");
        ResultadoImportacion dn3 = sync.importarBajada(ruta("AVI_DN3_UEB01.avipkg"));
        System.out.println("DN3-VERSION-ANTERIOR " + dn3.estado + " (" + dn3.mensaje + ")");
        configurarVersion("4");
        ResultadoImportacion dn3b = sync.importarBajada(ruta("AVI_DN3_UEB01.avipkg"));
        System.out.println("DN3-REPLAY " + dn3b.estado);

        UUID up = sync.generarExportacion(ruta("UP_jefe.avipkg"), idUeb);
        System.out.println("JEFE UP GENERADO " + up);
        ResultadoImportacion dnJefe = sync.importarBajada(ruta("AVI_DN3_UEB01.avipkg"));
        System.out.println("JEFE DN " + dnJefe.estado);
        System.out.println("UEB OK");
    }

    private static void centralVale(AppContext ctx) {
        UEB ueb = ctx.getUebService().listarTodas().get(0);
        Producto producto = ctx.getProductoService().listarTodos().get(0);
        Destino destino = ctx.getDestinoService().listarTodos().get(0);
        boolean existeRelacion = ctx.getProductoDestinoService().buscarPorProducto(producto.getIdProducto())
                .stream().anyMatch(r -> destino.getIdDestino().equals(r.getIdDestino()));
        if (!existeRelacion) {
            ctx.getProductoDestinoService().registrar(new ProductoDestino(
                    UUID.randomUUID(), producto.getIdProducto(), destino.getIdDestino(), true));
        }
        ctx.getAuthService().login("jefe_ueb01", "Jefe123!");
        ValeEntrega vale = new ValeEntrega();
        vale.setIdUEB(ueb.getIdUEB());
        vale.setIdProducto(producto.getIdProducto());
        vale.setIdDestino(destino.getIdDestino());
        vale.setCantidadDespachada(new BigDecimal("10"));
        vale.setUnidadMedida("millar");
        vale.setFechaHoraEmision(java.time.LocalDateTime.now());
        ValeEntrega emitido = ctx.getValeEntregaService().emitir(vale);
        System.out.println("VALE EMITIDO " + emitido.getNumeroVale());
        ctx.getAuthService().login("estad_central", "Estad123!");
        ctx.getSyncService().generarBajada(ruta("AVI_DN4_UEB01.avipkg"), ueb.getIdUEB());
        System.out.println("DN4 GENERADO");
    }

    private static void informe(AppContext ctx) {
        ctx.getAuthService().login("estad_central", "Estad123!");
        LocalDate hoy = LocalDate.now();
        Informe informe = ctx.getInformeService().crearInforme("INF-SMOKE-001", "Informe mensual de prueba",
                hoy.minusMonths(1), hoy, hoy, "Estadística Central", "Resumen ejecutivo de prueba.");
        ctx.getInformeService().guardarSecciones(informe.getIdInforme(),
                "Producción frente a metas: texto de prueba.",
                "Entregas: texto de prueba.",
                "Infraestructura: texto de prueba.",
                "Calidad de los datos: texto de prueba.",
                "Alertas: texto de prueba.",
                "Causas y medidas: texto de prueba.");
        ctx.getInformeService().enviarARevision(informe.getIdInforme());
        ctx.getInformeService().registrarDecision(informe.getIdInforme(), "Directivo Prueba",
                "APROBADO", hoy, null);
        Map<String, String> secciones = ctx.getInformeService().componerSecciones(informe.getIdInforme());
        System.out.println("INFORME-SECCIONES " + secciones.size() + " -> "
                + (secciones.size() == 8 ? "OK (8 secciones)" : "FALLO"));
        for (Map.Entry<String, String> seccion : secciones.entrySet()) {
            System.out.println("  [" + seccion.getKey() + "] " + seccion.getValue().length() + " caracteres");
        }
        ctx.getInformeService().exportarPdf(ruta("informe_smoke.pdf"), informe.getIdInforme());
        ctx.getInformeService().exportarExcel(ruta("informe_smoke.xlsx"), informe.getIdInforme());
        System.out.println("INFORME-EXPORT OK (" + ruta("informe_smoke.pdf").getFileName() + ", "
                + ruta("informe_smoke.xlsx").getFileName() + ")");
    }

    private static void uebVale(AppContext ctx) {
        UUID idUeb = ctx.getUebService().listarTodas().get(0).getIdUEB();
        fijarValeSecuencia(idUeb, 50L);
        ctx.getAuthService().login("jefe_ueb01", "Jefe123!");
        ResultadoImportacion dn4 = ctx.getSyncService().importarBajada(ruta("AVI_DN4_UEB01.avipkg"));
        System.out.println("DN4 " + dn4.estado + " (" + dn4.mensaje + ")");
        long secuencia = leerValeSecuencia(idUeb);
        System.out.println("VALE-SECUENCIA " + secuencia + " -> "
                + (secuencia == 50L ? "OK (no retrocede)" : "FALLO"));
    }

    private static void fijarValeSecuencia(UUID idUeb, long valor) {
        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement("INSERT INTO vale_secuencia (id_ueb, secuencia) "
                     + "VALUES (?, ?) ON CONFLICT (id_ueb) DO UPDATE SET secuencia = ?")) {
            ps.setObject(1, idUeb);
            ps.setLong(2, valor);
            ps.setLong(3, valor);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static long leerValeSecuencia(UUID idUeb) {
        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT secuencia FROM vale_secuencia WHERE id_ueb = ?")) {
            ps.setObject(1, idUeb);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Path ruta(String nombre) {
        return Paths.get(DIR, nombre).toAbsolutePath();
    }

    private static Connection conexion() throws SQLException {
        String url = "jdbc:postgresql://" + System.getProperty("pican.db.host", "127.0.0.1")
                + ":" + System.getProperty("pican.db.port", "5432")
                + "/" + System.getProperty("pican.db.name", "pican");
        return DriverManager.getConnection(url, System.getProperty("pican.db.user", "pican"),
                System.getProperty("pican.db.password", ""));
    }

    private static void bloquear() {
        ejecutar("INSERT INTO pican_restauracion_control (id_control, bloqueado, motivo, fecha_bloqueo, "
                + "id_auditoria, id_instalacion) VALUES (1, TRUE, 'Prueba SIN-13', CURRENT_TIMESTAMP, NULL, 'UEB01') "
                + "ON CONFLICT (id_control) DO UPDATE SET bloqueado = TRUE, "
                + "motivo = 'Prueba SIN-13', fecha_bloqueo = CURRENT_TIMESTAMP");
    }

    private static boolean estaBloqueado() {
        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT bloqueado FROM pican_restauracion_control WHERE id_control = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void configurarVersion(String valor) {
        ejecutar("INSERT INTO sync_configuracion (clave, valor, actualizado_en) "
                + "VALUES ('version_configuracion', '" + valor + "', CURRENT_TIMESTAMP) "
                + "ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor, "
                + "actualizado_en = CURRENT_TIMESTAMP");
    }

    private static void ejecutar(String sql) {
        try (Connection c = conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
