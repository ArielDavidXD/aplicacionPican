package cu.pican.verify;

import cu.pican.controller.app.AppContext;
import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Informe;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.TablaHistorica;
import cu.pican.model.TipoFuenteInforme;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.controller.service.ValidacionException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fase 4 · Etapa 4.14 · Pruebas funcionales sobre PostgreSQL real.
 *
 * Utiliza AppContext (modo POSTGRES) para ejercitar producción, análisis e informes
 * a través de los repositorios JDBC. Requiere una base exclusiva de pruebas.
 */
public final class FuncionalPostgresTest {

    private static final String PREFIJO = "FUNTEST-";
    private static final String CLAVE_ADMIN = "Func416Admin!";
    private static final String CLAVE_JEFE = "Func416Jefe!";
    private static final String CLAVE_ESTAD = "Func416Estad!";
    private static final String CLAVE_PROD = "Func416Prod!";

    private final List<Resultado> resultados = new ArrayList<>();
    private AppContext contexto;
    private UUID idUeb;
    private UUID idNave;
    private UUID idInforme;
    private String usuarioJefe;
    private String usuarioEstad;
    private String usuarioProd;

    public static void main(String[] args) {
        int salida;
        try {
            salida = new FuncionalPostgresTest().ejecutarTodo();
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

        System.setProperty("pican.admin.password", CLAVE_ADMIN);
        contexto = new AppContext();
        check(contexto.esPostgresql(), "AppContext no está en modo PostgreSQL");

        // Sesión administrativa para catalogar y crear las cuentas por rol (CU-16/CU-17).
        contexto.getAuthService().login("admin", CLAVE_ADMIN);
        String sufijoUsuarios = UUID.randomUUID().toString().substring(0, 8);
        usuarioJefe = "funtest_jefe_" + sufijoUsuarios;
        usuarioEstad = "funtest_estad_" + sufijoUsuarios;
        usuarioProd = "funtest_prod_" + sufijoUsuarios;
        contexto.getUsuarioService().registrar(usuarioJefe, "Jefe funcional", CLAVE_JEFE, "JEFE_DE_UEB");
        contexto.getUsuarioService().registrar(usuarioEstad, "Estadística funcional", CLAVE_ESTAD,
                "ESPECIALISTA_DE_ESTADISTICA");
        contexto.getUsuarioService().registrar(usuarioProd, "Producción funcional", CLAVE_PROD,
                "ESPECIALISTA_DE_PRODUCCION");

        verificar("FUN-01", "UUID, códigos y baja lógica de UEB/nave/unidad con JDBC", this::fun01Catalogos);
        verificar("FUN-02", "Parte de nave se consulta en análisis con su cantidad exacta", this::fun02Huevos);
        verificar("FUN-03", "Producción de carne y consolidado se consultan por período", this::fun03CarneConsolidado);
        verificar("FUN-04", "Referencias históricas y metas oficiales disponibles solo para lectura", this::fun04Referencias);
        verificar("FUN-05", "Informe: fuente, decisión y entrega con transiciones de estado", this::fun05Informe);
        verificar("FUN-06", "Regla: no se puede entregar un informe no aprobado", this::fun06ReglaEntrega);
        verificar("FUN-07", "Revisión posterior conserva el informe anterior y su decisión", this::fun07Revision);
        verificar("FUN-08", "Persistencia del informe tras reconstruir AppContext", this::fun08Persistencia);

        long fallos = resultados.stream().filter(r -> !"APROBADA".equals(r.estado)).count();
        imprimirInforme();
        escribirInforme();
        System.out.println("[RESUMEN] " + (resultados.size() - fallos) + "/" + resultados.size()
                + " pruebas aprobadas");
        return fallos == 0 ? 0 : 1;
    }

    private void fun01Catalogos() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        idUeb = UUID.randomUUID();
        idNave = UUID.randomUUID();
        UUID idUnidad = UUID.randomUUID();
        contexto.getUebService().registrar(new UEB(idUeb, "UEB funcional", PREFIJO + "UEB-" + sufijo, true));
        contexto.getNaveService().registrar(new Nave(idNave, idUeb, "Nave funcional", PREFIJO + "NAV-" + sufijo, true));
        contexto.getUnidadAsociadaService().registrar(new UnidadAsociada(
                idUnidad, "Unidad funcional", PREFIJO + "UA-" + sufijo, true));

        check(contexto.getUebService().buscarPorId(idUeb).isPresent(), "UEB no persistida");
        check(contexto.getNaveService().buscarPorId(idNave).isPresent(), "Nave no persistida");
        check(contexto.getUnidadAsociadaService().buscarPorId(idUnidad).isPresent(), "Unidad no persistida");

        UUID idNaveBaja = UUID.randomUUID();
        contexto.getNaveService().registrar(new Nave(
                idNaveBaja, idUeb, "Nave para baja", PREFIJO + "BAJA-" + sufijo, true));
        contexto.getNaveService().desactivar(idNaveBaja);
        check(contexto.getNaveService().buscarPorId(idNaveBaja).isPresent(), "La baja lógica eliminó la nave");
        check(!contexto.getNaveService().buscarPorId(idNaveBaja).orElseThrow().isActiva(),
                "La nave debió quedar inactiva");
    }

    private void fun02Huevos() {
        entrar(usuarioJefe, CLAVE_JEFE);
        LocalDate fecha = LocalDate.of(2026, 2, 10);
        ParteNave parte = new ParteNave();
        parte.setIdParte(UUID.randomUUID());
        parte.setIdNave(idNave);
        parte.setFechaProduccion(fecha);
        parte.setGallinasVivas(100);
        parte.setGallinasMuertas(1);
        parte.setGallinasEnfermas(2);
        parte.setHuevosProducidos(250);
        parte.setHuevosRotos(3);
        parte.setPiensoKg(new BigDecimal("12.50"));
        parte.setObservaciones("Prueba funcional con JDBC");
        parte.setCreadoEn(LocalDateTime.now());
        parte.setVersion(1L);
        contexto.getParteNaveService().registrar(parte);

        entrar(usuarioEstad, CLAVE_ESTAD);
        boolean encontrado = contexto.getAnalisisService()
                .consultarHuevosPorPeriodo(fecha.minusDays(1), fecha.plusDays(1)).stream()
                .anyMatch(r -> r.getCantidad() != null
                        && r.getCantidad().compareTo(new BigDecimal("250")) == 0);
        check(encontrado, "El análisis no devolvió los 250 huevos persistidos por JDBC");
    }

    private void fun03CarneConsolidado() {
        entrar(usuarioJefe, CLAVE_JEFE);
        LocalDate fecha = LocalDate.of(2026, 2, 15);
        ProduccionCarne carne = new ProduccionCarne();
        carne.setIdProduccion(UUID.randomUUID());
        carne.setIdUEB(idUeb);
        carne.setFechaProduccion(fecha);
        carne.setCantidadToneladas(new BigDecimal("12.345"));
        contexto.getProduccionCarneService().registrar(carne);

        ConsolidadoUEB consolidado = new ConsolidadoUEB();
        consolidado.setIdConsolidado(UUID.randomUUID());
        consolidado.setIdUEB(idUeb);
        consolidado.setFechaInicio(LocalDate.of(2026, 2, 1));
        consolidado.setFechaFin(LocalDate.of(2026, 2, 28));
        consolidado.setEstado("REGISTRADO");
        contexto.getConsolidadoUEBService().registrar(consolidado);

        entrar(usuarioEstad, CLAVE_ESTAD);
        boolean carneOk = contexto.getAnalisisService()
                .consultarCarnePorPeriodo(fecha.minusDays(1), fecha.plusDays(1)).stream()
                .anyMatch(r -> r.getCantidad() != null
                        && r.getCantidad().compareTo(new BigDecimal("12.345")) == 0);
        check(carneOk, "No se conservó la precisión decimal de la producción de carne");
        check(!contexto.getAnalisisService()
                        .consultarConsolidaciones(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).isEmpty(),
                "No se consultó el consolidado persistido");
    }

    private void fun04Referencias() {
        // Las tablas oficiales arrancan vacías: el administrador las carga por CU-18
        // y los especialistas de producción las consultan (CU-01 a CU-03).
        entrar("admin", CLAVE_ADMIN);
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        contexto.getGestionReferenciasService().registrarIndicador(TablaHistorica.INFRAESTRUCTURA,
                "Referencia FUN " + sufijo, 2019, new BigDecimal("1"), "U", "Prueba FUN-04");
        contexto.getGestionReferenciasService().registrarMeta("Meta FUN " + sufijo,
                new BigDecimal("10"), new BigDecimal("20"), null, "t", "Prueba FUN-04");

        entrar(usuarioProd, CLAVE_PROD);
        check(contexto.getAnalisisService().consultarTodasLasReferencias().stream()
                        .anyMatch(h -> ("Referencia FUN " + sufijo).equals(h.getIndicador())),
                "La consulta oficial no muestra el indicador registrado por el administrador");
        check(contexto.getAnalisisService().consultarMetas().stream()
                        .anyMatch(m -> ("Meta FUN " + sufijo).equals(m.getIndicador())),
                "La consulta oficial no muestra la meta registrada por el administrador");
    }

    private void fun05Informe() {
        entrar(usuarioEstad, CLAVE_ESTAD);
        Informe informe = contexto.getInformeService().crearInforme(
                PREFIJO + "INF-" + UUID.randomUUID().toString().substring(0, 8),
                "Informe funcional Etapa 4.14",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 5), "Especialista de prueba",
                "Resumen funcional con PostgreSQL");
        idInforme = informe.getIdInforme();
        check(Informe.ESTADO_BORRADOR.equals(contexto.getInformeService().buscarPorId(idInforme).getEstado()),
                "El informe debió nacer BORRADOR");

        contexto.getInformeService().agregarFuente(
                idInforme, TipoFuenteInforme.REFERENCIA_HISTORICA, "T1",
                "Fuente de prueba JDBC", "Tabla 1", "Consulta funcional Etapa 4.14");
        check(contexto.getInformeService().consultarFuentes(idInforme).size() == 1,
                "No se persistió la fuente del informe");

        contexto.getInformeService().guardarSecciones(idInforme,
                "Sección de producción frente a metas.", "Sección de entregas.",
                "Sección de infraestructura.", "Sección de calidad de datos.",
                "Sección de alertas.", "Sección de causas y medidas.");
        contexto.getInformeService().enviarARevision(idInforme);
        check(Informe.ESTADO_EN_REVISION.equals(contexto.getInformeService().buscarPorId(idInforme).getEstado()),
                "El informe completo debió pasar a EN_REVISION");

        contexto.getInformeService().registrarDecision(
                idInforme, "Directivo de prueba", "APROBADO", LocalDate.of(2026, 2, 6), null);
        check(Informe.ESTADO_APROBADO.equals(contexto.getInformeService().buscarPorId(idInforme).getEstado()),
                "El informe debió quedar APROBADO");
        check(contexto.getInformeService().consultarHistorialDecisiones(idInforme).size() == 1,
                "No se persistió la decisión");

        contexto.getInformeService().registrarEntrega(
                idInforme, "GEGAN", LocalDate.of(2026, 2, 7), "Responsable prueba", "Entrega funcional");
        check(Informe.ESTADO_ENTREGADO.equals(contexto.getInformeService().buscarPorId(idInforme).getEstado()),
                "El informe debió quedar ENTREGADO");
        check(contexto.getInformeService().consultarEntregas(idInforme).size() == 1,
                "No se persistió la entrega");
    }

    private void fun06ReglaEntrega() {
        Informe informe = contexto.getInformeService().crearInforme(
                PREFIJO + "REG-" + UUID.randomUUID().toString().substring(0, 8),
                "Informe sin aprobar", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 5), "Especialista de prueba", "Resumen");
        try {
            contexto.getInformeService().registrarEntrega(
                    informe.getIdInforme(), "MINAG", LocalDate.of(2026, 2, 7), "Responsable", null);
            throw new AssertionError("Se esperaba rechazo de entrega anticipada");
        } catch (ValidacionException esperada) {
            check(esperada.getMessage().contains("APROBADO"), "Mensaje inesperado: " + esperada.getMessage());
        }
    }

    private void fun07Revision() {
        Informe revision = contexto.getInformeService().crearRevision(
                idInforme, "Revisión funcional", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 10), "Especialista de prueba", "Revisión tras entrega");
        check(revision.getNumeroRevision() == 2, "La revisión debió ser la número 2");
        check(contexto.getInformeService().consultarRevisiones(idInforme).size() >= 1,
                "No se registró la relación de revisión");
        check(Informe.ESTADO_ENTREGADO.equals(contexto.getInformeService().buscarPorId(idInforme).getEstado()),
                "El informe anterior debió conservar su estado ENTREGADO");
    }

    private void fun08Persistencia() {
        AppContext reiniciado = new AppContext();
        check(reiniciado.esPostgresql(), "El contexto reiniciado no es PostgreSQL");
        reiniciado.getAuthService().login(usuarioEstad, CLAVE_ESTAD);
        check(reiniciado.getInformeService().buscarPorId(idInforme) != null,
                "El informe no persistió tras reconstruir el contexto");
    }

    private void entrar(String usuario, String clave) {
        contexto.getAuthService().login(usuario, clave);
    }

    // -------------------------------------------------------------------------

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

    private static String requerir(String clave, String mensaje) {
        String valor = System.getProperty(clave);
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor;
    }

    private void imprimirInforme() {
        System.out.println("=== INFORME FUNCIONAL POSTGRESQL (Fase 4) ===");
        for (Resultado r : resultados) {
            System.out.println(r.estado + " | " + r.id + " | " + r.esperado + " | " + r.obtenido);
        }
    }

    private void escribirInforme() {
        StringBuilder texto = new StringBuilder();
        texto.append("INFORME FUNCIONAL POSTGRESQL · ETAPA 4.14 · FASE 4\n\n");
        for (Resultado r : resultados) {
            texto.append(r.estado).append(" | ").append(r.id).append('\n')
                    .append("  Esperado: ").append(r.esperado).append('\n')
                    .append("  Obtenido: ").append(r.obtenido).append('\n');
        }
        try {
            Path ruta = Paths.get("test-results", "RESULTADOS_FUNCIONAL.txt");
            Files.createDirectories(ruta.getParent());
            Files.write(ruta, texto.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("[INFORME] " + ruta.toAbsolutePath());
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
