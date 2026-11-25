package cu.pican.verify;

import cu.pican.app.AppContext;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Destino;
import cu.pican.model.Incidencia;
import cu.pican.model.Informe;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.Recepcion;
import cu.pican.model.TipoFuenteInforme;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.model.ValeEntrega;
import cu.pican.service.ValidacionException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Etapa 4.15 · Fase 1 · Validación integral con PostgreSQL real.
 *
 * No repite los 145 checks JDBC ni las pruebas transaccionales de la Etapa 4.14.
 * Ejercita la integración AppContext -> servicios -> repositorios JDBC ->
 * transacciones -> PostgreSQL sobre una base exclusiva de pruebas.
 */
public final class ValidacionIntegralPostgresTest {

    private static final String PREFIJO = "INT415-";
    private static final String CLAVE_ADMIN = "ClaveIntegral416Admin!";
    private static final String CLAVE_JEFE = "ClaveIntegral416Jefe!";
    private static final String CLAVE_ESTAD = "ClaveIntegral416Estad!";
    private final List<Resultado> resultados = new ArrayList<>();
    private AppContext contexto;
    private String usuarioAdmin;
    private String usuarioJefe;
    private String usuarioEstad;
    private UUID idUeb;
    private UUID idNave;
    private UUID idProducto;
    private UUID idDestino;
    private UUID idVale;
    private UUID idInforme;

    public static void main(String[] args) {
        int salida;
        try {
            salida = new ValidacionIntegralPostgresTest().ejecutarTodo();
        } catch (Throwable fallo) {
            System.out.println("[FATAL] " + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            fallo.printStackTrace(System.out);
            salida = 2;
        }
        System.exit(salida);
    }

    private int ejecutarTodo() {
        String url = requerir("pican.test.url", "Falta -Dpican.test.url");
        String usuario = requerir("pican.test.user", "Falta -Dpican.test.user");
        String base = url.split("\\?", 2)[0];
        base = base.substring(base.lastIndexOf('/') + 1);
        if (!base.toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalArgumentException("Base no exclusiva de pruebas: " + base);
        }

        System.setProperty("pican.persistence", "postgresql");
        System.setProperty("pican.db.url", url);
        System.setProperty("pican.db.user", usuario);
        System.setProperty("pican.db.password", System.getProperty("pican.test.password", "trust_local"));
        System.setProperty("pican.db.name", base);
        System.setProperty("pican.db.migrate", "true");
        System.setProperty("pican.migrations.dir", System.getProperty("pican.test.migrations", "sql/migrations"));
        // Etapa 4.16: las altas de cuentas exigen sesión administrativa previa.
        System.setProperty("pican.admin.password", CLAVE_ADMIN);

        contexto = new AppContext();
        contexto.getAuthService().login("admin", CLAVE_ADMIN);
        verificar("INT-01", "Arranque POSTGRES y migraciones aplicadas", () -> {
            check(contexto.esPostgresql(), "AppContext no inició en PostgreSQL");
            check(contexto.getConexiones() != null, "Sin JdbcConnectionManager");
            int migraciones = contarMigraciones();
            check(migraciones >= 1, "No hay migraciones registradas en pican_schema_migrations");
        });

        verificar("INT-02", "Inicio de sesión con cuenta creada en la prueba", () -> {
            usuarioAdmin = "int415_admin_" + UUID.randomUUID().toString().substring(0, 8);
            usuarioJefe = "int415_jefe_" + UUID.randomUUID().toString().substring(0, 8);
            usuarioEstad = "int415_estad_" + UUID.randomUUID().toString().substring(0, 8);
            contexto.getUsuarioService().registrar(usuarioAdmin, "Administrador integral 4.15",
                    CLAVE_ADMIN, "ADMINISTRADOR");
            contexto.getUsuarioService().registrar(usuarioJefe, "Jefe integral 4.15",
                    CLAVE_JEFE, "JEFE_DE_UEB");
            contexto.getUsuarioService().registrar(usuarioEstad, "Estadística integral 4.15",
                    CLAVE_ESTAD, "ESPECIALISTA_DE_ESTADISTICA");
            entrar(usuarioAdmin, CLAVE_ADMIN);
            check(contexto.getAuthService().estaAutenticado(), "No quedó autenticada la sesión");
            check("ADMINISTRADOR".equals(contexto.getAuthService().getUsuarioAutenticado().getRol().getNombre()),
                    "El rol de la sesión no es ADMINISTRADOR");
        });

        verificar("INT-03", "Gestión de UEB", () -> {
            idUeb = UUID.randomUUID();
            contexto.getUebService().registrar(new UEB(idUeb, "UEB integral", codigo("UEB"), true));
            check(contexto.getUebService().buscarPorId(idUeb).isPresent(), "UEB no persistida");
        });

        verificar("INT-04", "Gestión de naves", () -> {
            idNave = UUID.randomUUID();
            contexto.getNaveService().registrar(new Nave(idNave, idUeb, "Nave integral", codigo("NAV"), true));
            check(contexto.getNaveService().buscarPorId(idNave).isPresent(), "Nave no persistida");
            check(contexto.getNaveService().buscarPorUEB(idUeb).stream()
                    .anyMatch(n -> idNave.equals(n.getIdNave())), "Nave no relacionada con su UEB");
        });

        verificar("INT-05", "Gestión de unidades asociadas", () -> {
            UUID idUnidad = UUID.randomUUID();
            contexto.getUnidadAsociadaService().registrar(new UnidadAsociada(
                    idUnidad, "Unidad integral", codigo("UA"), true));
            check(contexto.getUnidadAsociadaService().buscarPorId(idUnidad).isPresent(), "Unidad no persistida");
        });

        verificar("INT-06", "Gestión de productos", () -> {
            idProducto = UUID.randomUUID();
            contexto.getProductoService().registrar(new Producto(
                    idProducto, codigo("PROD"), "Producto integral", true, "kg"));
            check(contexto.getProductoService().buscarPorId(idProducto).isPresent(), "Producto no persistido");
        });

        verificar("INT-07", "Gestión de destinos", () -> {
            idDestino = UUID.randomUUID();
            contexto.getDestinoService().registrar(new Destino(
                    idDestino, codigo("DEST"), "Destino integral", true));
            check(contexto.getDestinoService().buscarPorId(idDestino).isPresent(), "Destino no persistido");
        });

        verificar("INT-08", "Relaciones producto-destino", () -> {
            UUID idRelacion = UUID.randomUUID();
            contexto.getProductoDestinoService().registrar(new ProductoDestino(
                    idRelacion, idProducto, idDestino, true));
            check(contexto.getProductoDestinoService().buscarPorId(idRelacion).isPresent(), "Relación no persistida");
            check(contexto.getProductoDestinoService().buscarPorProducto(idProducto).stream()
                    .anyMatch(r -> idRelacion.equals(r.getIdRelacion())), "Relación no consultable por producto");
        });

        verificar("INT-09", "Registro de producción de huevos y consulta analítica", () -> {
            entrar(usuarioJefe, CLAVE_JEFE);
            ParteNave parte = new ParteNave();
            parte.setIdParte(UUID.randomUUID());
            parte.setIdNave(idNave);
            parte.setFechaProduccion(LocalDate.of(2026, 3, 10));
            parte.setGallinasVivas(120);
            parte.setGallinasMuertas(2);
            parte.setGallinasEnfermas(3);
            parte.setHuevosProducidos(321);
            parte.setHuevosRotos(4);
            parte.setPiensoKg(new BigDecimal("45.75"));
            parte.setObservaciones("Validación integral 4.15");
            contexto.getParteNaveService().registrar(parte);
            entrar(usuarioEstad, CLAVE_ESTAD);
            check(contexto.getAnalisisService()
                            .consultarHuevosPorPeriodo(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 11))
                            .stream().anyMatch(r -> r.getCantidad() != null
                                    && r.getCantidad().compareTo(new BigDecimal("321")) == 0),
                    "El análisis no devolvió la producción de huevos persistida");
        });

        verificar("INT-10", "Registro de producción de carne", () -> {
            entrar(usuarioJefe, CLAVE_JEFE);
            ProduccionCarne carne = new ProduccionCarne();
            carne.setIdProduccion(UUID.randomUUID());
            carne.setIdUEB(idUeb);
            carne.setFechaProduccion(LocalDate.of(2026, 3, 12));
            carne.setCantidadToneladas(new BigDecimal("9.876"));
            contexto.getProduccionCarneService().registrar(carne);
            entrar(usuarioEstad, CLAVE_ESTAD);
            check(contexto.getAnalisisService()
                            .consultarCarnePorPeriodo(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 13))
                            .stream().anyMatch(r -> r.getCantidad() != null
                                    && r.getCantidad().compareTo(new BigDecimal("9.876")) == 0),
                    "El análisis no conservó la precisión decimal de la carne");
        });

        verificar("INT-11", "Consolidación de la UEB", () -> {
            entrar(usuarioJefe, CLAVE_JEFE);
            ConsolidadoUEB consolidado = new ConsolidadoUEB();
            consolidado.setIdConsolidado(UUID.randomUUID());
            consolidado.setIdUEB(idUeb);
            consolidado.setFechaInicio(LocalDate.of(2026, 3, 1));
            consolidado.setFechaFin(LocalDate.of(2026, 3, 31));
            consolidado.setEstado("REGISTRADO");
            contexto.getConsolidadoUEBService().registrar(consolidado);
            entrar(usuarioEstad, CLAVE_ESTAD);
            check(!contexto.getAnalisisService()
                            .consultarConsolidaciones(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).isEmpty(),
                    "No se consultó la consolidación persistida");
        });

        verificar("INT-12", "Emisión de vales de entrega", () -> {
            entrar(usuarioJefe, CLAVE_JEFE);
            ValeEntrega vale = nuevoVale(idUeb, idDestino, idProducto, "8.500", "kg",
                    LocalDateTime.of(2026, 3, 13, 9, 0));
            ValeEntrega emitido = contexto.getValeEntregaService().emitir(vale);
            idVale = emitido.getIdVale();
            check(idVale != null && emitido.getNumeroVale() != null, "Vale sin UUID o número");
            check(ValeEntrega.ESTADO_EMITIDO.equals(emitido.getEstado()), "Estado inicial incorrecto");
        });

        verificar("INT-13", "Recepciones y transición del vale", () -> {
            entrar(usuarioJefe, CLAVE_JEFE);
            Recepcion recepcion = new Recepcion();
            recepcion.setIdRecepcion(UUID.randomUUID());
            recepcion.setIdVale(idVale);
            recepcion.setCantidadRecibida(new BigDecimal("8.500"));
            recepcion.setUnidadMedida("kg");
            recepcion.setResultado(Recepcion.RESULTADO_CONFORME);
            recepcion.setFechaHoraRecepcion(LocalDateTime.of(2026, 3, 14, 10, 0));
            contexto.getRecepcionService().registrar(recepcion);
            check(contexto.getRecepcionService().buscarPorVale(idVale).isPresent(), "Recepción no persistida");
            check(ValeEntrega.ESTADO_CERRADO.equals(
                    contexto.getValeEntregaService().buscarPorId(idVale).orElseThrow().getEstado()),
                    "El vale no pasó a CERRADO tras recepción conforme");
        });

        verificar("INT-14", "Incidencias desde recepción no conforme", () -> {
            entrar(usuarioJefe, CLAVE_JEFE);
            ValeEntrega vale = nuevoVale(idUeb, idDestino, idProducto, "3.000", "kg",
                    LocalDateTime.of(2026, 3, 15, 9, 0));
            ValeEntrega emitido = contexto.getValeEntregaService().emitir(vale);
            Recepcion recepcion = new Recepcion();
            recepcion.setIdRecepcion(UUID.randomUUID());
            recepcion.setIdVale(emitido.getIdVale());
            recepcion.setCantidadRecibida(new BigDecimal("2.500"));
            recepcion.setUnidadMedida("kg");
            recepcion.setResultado(Recepcion.RESULTADO_NO_CONFORME);
            recepcion.setObservaciones("Faltante parcial en validación integral");
            recepcion.setFechaHoraRecepcion(LocalDateTime.of(2026, 3, 16, 10, 0));
            contexto.getRecepcionService().registrar(recepcion);
            List<Incidencia> incidencias = contexto.getIncidenciaService().buscarPorVale(emitido.getIdVale());
            check(!incidencias.isEmpty(), "No se generó la incidencia automática");
            Incidencia incidencia = contexto.getIncidenciaService().completar(
                    incidencias.get(0).getIdIncidencia(), "FALTANTE", new BigDecimal("0.500"),
                    "Faltante detectado", "Reposición", LocalDateTime.of(2026, 3, 20, 10, 0));
            check(incidencia != null, "No se pudo completar la incidencia");
        });

        verificar("INT-15", "Anulación de vales", () -> {
            entrar(usuarioJefe, CLAVE_JEFE);
            ValeEntrega vale = nuevoVale(idUeb, idDestino, idProducto, "1.500", "kg",
                    LocalDateTime.of(2026, 3, 17, 9, 0));
            ValeEntrega emitido = contexto.getValeEntregaService().emitir(vale);
            ValeEntrega anulado = contexto.getValeEntregaService().anular(
                    emitido.getIdVale(), "Error de registro en validación integral", true, null);
            check(ValeEntrega.ESTADO_ANULADO.equals(anulado.getEstado()), "El vale no quedó ANULADO");
            check(anulado.getMotivoAnulacion() != null, "No se conservó el motivo de anulación");
        });

        verificar("INT-16", "Análisis e indicadores", () -> {
            entrar(usuarioEstad, CLAVE_ESTAD);
            check(!contexto.getAnalisisService().consultarTodasLasReferencias().isEmpty(),
                    "No hay referencias históricas oficiales");
            check(!contexto.getAnalisisService().consultarMetas().isEmpty(), "No hay metas estratégicas");
            check(!contexto.getAnalisisService()
                            .compararMetasConProduccionOperativa(
                                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).isEmpty(),
                    "La comparación de metas con producción operativa devolvió vacío");
        });

        verificar("INT-17", "Informes, fuentes, decisión y entrega", () -> {
            entrar(usuarioEstad, CLAVE_ESTAD);
            Informe informe = contexto.getInformeService().crearInforme(
                    codigo("INF"), "Informe integral 4.15",
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    LocalDate.of(2026, 4, 2), "Especialista integral", "Resumen integral");
            idInforme = informe.getIdInforme();
            check(Informe.ESTADO_BORRADOR.equals(informe.getEstado()), "El informe no nació BORRADOR");
            contexto.getInformeService().agregarFuente(idInforme, TipoFuenteInforme.REFERENCIA_HISTORICA,
                    "INT-" + idInforme, "Fuente integral", "Información de la prueba", null);
            check(contexto.getInformeService().consultarFuentes(idInforme).size() == 1, "Fuente no persistida");
            contexto.getInformeService().guardarSecciones(idInforme, "Producción frente a metas.",
                    "Entregas.", "Infraestructura.", "Calidad de los datos.", "Alertas.", "Causas y medidas.");
            contexto.getInformeService().enviarARevision(idInforme);
            check(Informe.ESTADO_EN_REVISION.equals(
                    contexto.getInformeService().buscarPorId(idInforme).getEstado()), "Informe no EN_REVISION");
            contexto.getInformeService().registrarDecision(idInforme, "Directivo integral", "APROBADO",
                    LocalDate.of(2026, 4, 3), null);
            check(Informe.ESTADO_APROBADO.equals(
                    contexto.getInformeService().buscarPorId(idInforme).getEstado()), "Informe no APROBADO");
            contexto.getInformeService().registrarEntrega(idInforme, "GEGAN",
                    LocalDate.of(2026, 4, 4), "Responsable integral", "Entrega integral");
            check(contexto.getInformeService().consultarEntregas(idInforme).size() == 1, "Entrega no persistida");
        });

        verificar("INT-18", "Gestión de usuarios y catálogo de roles", () -> {
            entrar(usuarioAdmin, CLAVE_ADMIN);
            check(contexto.getUsuarioService().rolesDisponibles().size() == 4, "No hay 4 roles predefinidos");
            String nombre = "int415_vista_" + UUID.randomUUID().toString().substring(0, 8);
            contexto.getUsuarioService().registrar(nombre, "Usuario de consulta", "ClaveConsulta415!",
                    "ESPECIALISTA_DE_ESTADISTICA");
            check(contexto.getUsuarioService().buscarPorNombreUsuario(nombre).isPresent(), "Usuario no persistido");
            check(contexto.getUsuarioService().existeAdministrador(), "No existe administrador");
        });

        verificar("INT-19", "Persistencia tras reconstruir AppContext (reinicio simulado)", () -> {
            AppContext reiniciado = new AppContext();
            check(reiniciado.esPostgresql(), "El contexto reconstruido no es PostgreSQL");
            entrarEn(reiniciado, "admin", CLAVE_ADMIN);
            check(reiniciado.getUebService().buscarPorId(idUeb).isPresent(), "La UEB no persistió");
            entrarEn(reiniciado, usuarioEstad, CLAVE_ESTAD);
            check(reiniciado.getInformeService().buscarPorId(idInforme) != null, "El informe no persistió");
            entrarEn(reiniciado, usuarioJefe, CLAVE_JEFE);
            check(reiniciado.getValeEntregaService().buscarPorId(idVale).isPresent(), "El vale no persistió");
        });

        verificar("INT-20", "UUID, fechas, precisión decimal y baja lógica (activa=false)", () -> {
            entrar(usuarioAdmin, CLAVE_ADMIN);
            UUID idNaveBaja = UUID.randomUUID();
            contexto.getNaveService().registrar(new Nave(
                    idNaveBaja, idUeb, "Nave para baja lógica", codigo("BAJA"), true));
            contexto.getNaveService().desactivar(idNaveBaja);
            Nave baja = contexto.getNaveService().buscarPorId(idNaveBaja).orElseThrow();
            check(!baja.isActiva(), "La baja lógica no se aplicó");
            check(metadatosNaveBaja(idNaveBaja), "Versión, fechas o baja lógica no conservadas en la fila SQL");
            entrar(usuarioJefe, CLAVE_JEFE);
            check(contexto.getValeEntregaService().buscarPorId(idVale).orElseThrow()
                            .getCantidadDespachada().compareTo(new BigDecimal("8.500")) == 0,
                    "La precisión decimal del vale cambió");
        });

        long fallos = resultados.stream().filter(r -> !"APROBADA".equals(r.estado)).count();
        imprimirInforme();
        escribirInforme();
        System.out.println("[RESUMEN] " + (resultados.size() - fallos) + "/" + resultados.size()
                + " comprobaciones aprobadas");
        return fallos == 0 ? 0 : 1;
    }

    private int contarMigraciones() throws Exception {
        Connection conexion = contexto.getConexiones().obtenerConexion();
        boolean cerrar = conexion != cu.pican.persistence.JdbcSupport.conexionActiva();
        try (Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM pican_schema_migrations WHERE exito = TRUE")) {
            rs.next();
            return rs.getInt(1);
        } finally {
            if (cerrar) {
                conexion.close();
            }
        }
    }

    private boolean metadatosNaveBaja(UUID idNaveBaja) throws Exception {
        Connection conexion = contexto.getConexiones().obtenerConexion();
        boolean cerrar = conexion != cu.pican.persistence.JdbcSupport.conexionActiva();
        String sql = "SELECT version, creado_en, actualizado_en, activa, eliminado_logico FROM nave WHERE id_nave = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setObject(1, idNaveBaja.toString(), java.sql.Types.OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return rs.getLong("version") >= 1
                        && rs.getTimestamp("creado_en") != null
                        && rs.getTimestamp("actualizado_en") != null
                        && !rs.getBoolean("activa");
            }
        } finally {
            if (cerrar) {
                conexion.close();
            }
        }
    }

    private static ValeEntrega nuevoVale(UUID idUeb, UUID idDestino, UUID idProducto,
                                         String cantidad, String unidad, LocalDateTime emision) {
        ValeEntrega vale = new ValeEntrega();
        vale.setIdVale(UUID.randomUUID());
        vale.setIdUEB(idUeb);
        vale.setIdDestino(idDestino);
        vale.setIdProducto(idProducto);
        vale.setCantidadDespachada(new BigDecimal(cantidad));
        vale.setUnidadMedida(unidad);
        vale.setFechaHoraEmision(emision);
        return vale;
    }

    private static String codigo(String prefijo) {
        return PREFIJO + prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void verificar(String id, String esperado, Prueba prueba) {
        String obtenido;
        String estado;
        try {
            prueba.ejecutar();
            estado = "APROBADA";
            obtenido = "Se cumplió el resultado esperado";
        } catch (Throwable fallo) {
            estado = "FALLIDA";
            obtenido = fallo.getClass().getSimpleName() + ": " + fallo.getMessage();
        }
        resultados.add(new Resultado(id, esperado, obtenido, estado));
        System.out.println("[" + estado + "] " + id + " -> " + obtenido);
    }

    private static void check(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    private void entrar(String usuario, String clave) {
        entrarEn(contexto, usuario, clave);
    }

    private static void entrarEn(AppContext contexto, String usuario, String clave) {
        contexto.getAuthService().logout();
        contexto.getAuthService().login(usuario, clave);
    }

    private static String requerir(String clave, String mensaje) {
        String valor = System.getProperty(clave);
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor;
    }

    private void imprimirInforme() {
        System.out.println("=== VALIDACIÓN INTEGRAL POSTGRESQL · ETAPA 4.15 · FASE 1 ===");
        for (Resultado r : resultados) {
            System.out.println(r.estado + " | " + r.id + " | " + r.esperado + " | " + r.obtenido);
        }
    }

    private void escribirInforme() {
        StringBuilder texto = new StringBuilder();
        texto.append("VALIDACIÓN INTEGRAL POSTGRESQL · ETAPA 4.15 · FASE 1\n\n");
        for (Resultado r : resultados) {
            texto.append(r.estado).append(" | ").append(r.id).append(" | ")
                    .append(r.esperado).append(" | ").append(r.obtenido).append('\n');
        }
        try {
            Path directorio = Paths.get("test-results");
            Files.createDirectories(directorio);
            Files.write(directorio.resolve("RESULTADOS_INTEGRAL_4.15.txt"),
                    texto.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            System.out.println("[AVISO] No se pudo escribir el informe: " + ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface Prueba {
        void ejecutar() throws Exception;
    }

    private static final class Resultado {
        private final String id;
        private final String esperado;
        private final String obtenido;
        private final String estado;

        private Resultado(String id, String esperado, String obtenido, String estado) {
            this.id = id;
            this.esperado = esperado;
            this.obtenido = obtenido;
            this.estado = estado;
        }
    }
}
