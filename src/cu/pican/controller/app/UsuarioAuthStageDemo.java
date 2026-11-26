package cu.pican.controller.app;

import cu.pican.model.ConsolidadoUEB;
import cu.pican.model.Destino;
import cu.pican.model.Incidencia;
import cu.pican.model.Informe;
import cu.pican.model.Nave;
import cu.pican.model.ParteNave;
import cu.pican.model.ParteUnidadAsociada;
import cu.pican.model.Producto;
import cu.pican.model.ProductoDestino;
import cu.pican.model.ProduccionCarne;
import cu.pican.model.Recepcion;
import cu.pican.model.Rol;
import cu.pican.model.UEB;
import cu.pican.model.UnidadAsociada;
import cu.pican.model.Usuario;
import cu.pican.model.ValeEntrega;
import cu.pican.model.repository.UsuarioRepository;
import cu.pican.model.repository.memory.UsuarioRepositoryMemory;
import cu.pican.controller.service.AuthService;
import cu.pican.controller.service.PasswordHasher;
import cu.pican.controller.service.UsuarioService;
import cu.pican.controller.service.ValidacionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Verificación automatizada de la Etapa 4.12 (CU-16: usuarios y autenticación).
 * Usa repositorios aislados para las reglas propias y un AppContext real para la integración.
 */
public class UsuarioAuthStageDemo {

    public static void main(String[] args) {
        UsuarioRepository usuarioRepo = new UsuarioRepositoryMemory();
        PasswordHasher hasher = new PasswordHasher();
        UsuarioService usuarios = new UsuarioService(usuarioRepo, hasher);
        AuthService auth = new AuthService(usuarioRepo, hasher);
        auth.setUsuarioService(usuarios);
        usuarios.setAuthService(auth);

        System.out.println("1) Registro correcto de un usuario");
        Usuario u1 = usuarios.registrar("analista1", "Ana Analista", "ClaveSegura123", Rol.ESPECIALISTA_ESTADISTICA);
        check(u1.getIdUsuario() != null, "Identificador UUID asignado");
        check(u1.isActivo() && u1.getFechaCreacion() != null, "Cuenta activa con fecha de creación");
        check(Rol.ESPECIALISTA_ESTADISTICA.equals(u1.getRol().getNombre()), "Rol asignado");
        check(u1.isCambioContrasenaRequerido(), "Contraseña inicial exige cambio en primer acceso");

        System.out.println("2) Rechazo de nombre de usuario duplicado");
        expectValidation(() -> usuarios.registrar("ANALISTA1", "Otro Analista", "OtraClave123", Rol.JEFE_UEB),
                "nombre duplicado (sin distinción de mayúsculas)");

        System.out.println("3) Rechazo de datos obligatorios vacíos");
        int antesInvalidos = usuarios.listarTodos().size();
        expectValidation(() -> usuarios.registrar("", "Nombre", "ClaveSegura123", Rol.JEFE_UEB), "nombre de usuario vacío");
        expectValidation(() -> usuarios.registrar("usuario2", "  ", "ClaveSegura123", Rol.JEFE_UEB), "nombre completo vacío");
        expectValidation(() -> usuarios.registrar("usuario3", "Nombre", "", Rol.JEFE_UEB), "contraseña vacía");
        expectValidation(() -> usuarios.registrar("usuario4", "Nombre", "abc", Rol.JEFE_UEB), "contraseña demasiado corta");
        expectValidation(() -> usuarios.registrar("usuario5", "Nombre", "ClaveSegura123", "DIRECTIVO"), "rol no previsto");
        check(usuarios.listarTodos().size() == antesInvalidos, "Ninguna cuenta inválida fue registrada");

        System.out.println("4) Inicio de sesión con credenciales correctas");
        auth.login("analista1", "ClaveSegura123");
        check(auth.estaAutenticado(), "Sesión iniciada");
        check(u1.getIdUsuario().equals(auth.getIdUsuarioAutenticado()), "Usuario autenticado correcto");
        check("Ana Analista".equals(auth.getUsuarioSesion().getDisplayName()), "Datos de sesión para la interfaz");

        System.out.println("5) Rechazo de contraseña incorrecta");
        auth.logout();
        expectValidation(() -> auth.login("analista1", "ClaveIncorrecta"), "contraseña incorrecta");
        expectValidation(() -> auth.login("usuarioInexistente", "ClaveSegura123"), "usuario inexistente");
        check(!auth.estaAutenticado(), "No se abre sesión con credenciales inválidas");

        System.out.println("6) Rechazo de acceso de un usuario inactivo");
        Usuario adminDemo = usuarios.registrar("adminDemo", "Administrador Demo", "AdminClave123", Rol.ADMINISTRADOR);
        usuarios.desactivar(u1.getIdUsuario());
        expectValidationQueContiene(() -> auth.login("analista1", "ClaveSegura123"),
                "usuario inactivo", "desactivado");
        expectValidation(() -> usuarios.desactivar(adminDemo.getIdUsuario()),
                "no se permite desactivar la última cuenta administrativa");

        System.out.println("7) Cierre de sesión correcto");
        auth.login("adminDemo", "AdminClave123");
        check(auth.estaAutenticado(), "Sesión previa al cierre");
        auth.logout();
        check(!auth.estaAutenticado() && auth.getUsuarioAutenticado() == null
                && auth.getIdUsuarioAutenticado() == null, "Sesión totalmente cerrada");

        System.out.println("8) Usuario autenticado disponible desde AppContext");
        String propiedadAnterior = System.getProperty("pican.admin.password");
        System.setProperty("pican.admin.password", "AdminConfigurado123");
        AppContext context;
        try {
            context = new AppContext();
        } finally {
            if (propiedadAnterior == null) {
                System.clearProperty("pican.admin.password");
            } else {
                System.setProperty("pican.admin.password", propiedadAnterior);
            }
        }
        Usuario operador = context.getUsuarioService().registrar(
                "operadorCtx", "Operador Contexto", "ClaveContexto123", Rol.JEFE_UEB);
        context.getAuthService().login("operadorCtx", "ClaveContexto123");
        check(context.getAuthService() == context.getAuthService(), "AuthService único en el contexto");
        check(context.getUsuarioService() == context.getUsuarioService(), "UsuarioService único en el contexto");
        check(operador.getIdUsuario().equals(context.getAuthService().getIdUsuarioAutenticado()),
                "Sesión disponible desde el contexto compartido");

        System.out.println("9) Contraseñas nunca almacenadas en texto plano");
        Usuario persistido = usuarioRepo.findById(u1.getIdUsuario()).orElseThrow();
        check(!persistido.getPasswordHash().contains("ClaveSegura123"), "La contraseña no aparece en el repositorio");
        check(hasher.estaCodificado(persistido.getPasswordHash()), "Hash PBKDF2 codificado con parámetros y salt");
        check(hasher.verificar("ClaveSegura123", persistido.getPasswordHash()), "La contraseña correcta verifica el hash");
        check(!hasher.verificar("OtraClave123", persistido.getPasswordHash()), "Una contraseña distinta no verifica");

        System.out.println("10) Operaciones existentes registran al usuario autenticado");
        verificarIntegracionOperaciones(context, operador.getIdUsuario());

        System.out.println("11) Inicialización del administrador inicial es idempotente");
        long administradoresAntes = usuarios.listarTodos().stream().filter(UsuarioAuthStageDemo::esAdministrador).count();
        int totalAntes = usuarios.listarTodos().size();
        UsuarioInicialInitializer.inicializar(usuarios);
        UsuarioInicialInitializer.inicializar(usuarios);
        check(usuarios.listarTodos().size() == totalAntes, "No se crean cuentas al repetir la inicialización");
        check(usuarios.listarTodos().stream().filter(UsuarioAuthStageDemo::esAdministrador).count() == administradoresAntes,
                "No se duplica la cuenta administrativa");

        System.out.println("\n==> Verificación automatizada de Etapa 4.12 (CU-16) finalizada correctamente.");
        System.out.println("Nota: sin PostgreSQL, sin USB, sin cuenta para el directivo y sin cambios en CU-08/CU-09.");
    }

    private static void verificarIntegracionOperaciones(AppContext context, UUID idUsuario) {
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);

        UEB ueb = new UEB(UUID.randomUUID(), "UEB Integración CU-16", "CU16", true);
        context.getUebService().registrar(ueb);
        Nave nave = new Nave(UUID.randomUUID(), ueb.getIdUEB(), "Nave Integración", "NCU16", true);
        context.getNaveService().registrar(nave);

        ParteNave parte = new ParteNave();
        parte.setIdParte(UUID.randomUUID());
        parte.setIdNave(nave.getIdNave());
        parte.setFechaProduccion(desde.plusDays(1));
        parte.setGallinasVivas(0);
        parte.setGallinasMuertas(0);
        parte.setGallinasEnfermas(0);
        parte.setHuevosProducidos(120);
        parte.setHuevosRotos(0);
        check(idUsuario.equals(context.getParteNaveService().registrar(parte).getIdUsuario()),
                "Parte de nave con usuario autenticado");

        UnidadAsociada unidad = new UnidadAsociada(UUID.randomUUID(), "Unidad Integración", "UCU16", true);
        context.getUnidadAsociadaService().registrar(unidad);
        ParteUnidadAsociada parteAsociada = new ParteUnidadAsociada();
        parteAsociada.setIdParteAsociado(UUID.randomUUID());
        parteAsociada.setIdUnidad(unidad.getIdUnidad());
        parteAsociada.setFechaProduccion(desde.plusDays(2));
        check(idUsuario.equals(context.getParteUnidadAsociadaService().registrar(parteAsociada).getIdUsuario()),
                "Parte de unidad asociada con usuario autenticado");

        ProduccionCarne carne = new ProduccionCarne();
        carne.setIdProduccion(UUID.randomUUID());
        carne.setIdUEB(ueb.getIdUEB());
        carne.setFechaProduccion(desde.plusDays(3));
        carne.setCantidadToneladas(new BigDecimal("10.5"));
        check(idUsuario.equals(context.getProduccionCarneService().registrar(carne).getIdUsuario()),
                "Producción de carne con usuario autenticado");

        ConsolidadoUEB consolidado = new ConsolidadoUEB();
        consolidado.setIdConsolidado(UUID.randomUUID());
        consolidado.setIdUEB(ueb.getIdUEB());
        consolidado.setFechaInicio(desde);
        consolidado.setFechaFin(hasta);
        consolidado.setEstado("REGISTRADO");
        check(idUsuario.equals(context.getConsolidadoUEBService().registrar(consolidado).getIdUsuario()),
                "Consolidado con usuario autenticado");

        Producto producto = new Producto(UUID.randomUUID(), "P-CU16", "Producto CU-16", true, "kg");
        Destino destino = new Destino(UUID.randomUUID(), "D-CU16", "Destino CU-16", true);
        context.getProductoService().registrar(producto);
        context.getDestinoService().registrar(destino);
        context.getProductoDestinoService().registrar(
                new ProductoDestino(UUID.randomUUID(), producto.getIdProducto(), destino.getIdDestino(), true));

        ValeEntrega vale = new ValeEntrega();
        vale.setIdVale(UUID.randomUUID());
        vale.setIdUEB(ueb.getIdUEB());
        vale.setIdProducto(producto.getIdProducto());
        vale.setIdDestino(destino.getIdDestino());
        vale.setCantidadDespachada(new BigDecimal("20"));
        vale.setUnidadMedida("kg");
        vale.setFechaHoraEmision(LocalDateTime.of(2026, 1, 10, 9, 0));
        ValeEntrega emitido = context.getValeEntregaService().emitir(vale);
        check(idUsuario.equals(emitido.getIdUsuario()), "Vale emitido con usuario autenticado");

        Recepcion recepcion = new Recepcion();
        recepcion.setIdVale(emitido.getIdVale());
        recepcion.setFechaHoraRecepcion(LocalDateTime.of(2026, 1, 11, 9, 0));
        recepcion.setCantidadRecibida(new BigDecimal("18"));
        recepcion.setUnidadMedida("kg");
        recepcion.setResultado(Recepcion.RESULTADO_NO_CONFORME);
        recepcion.setObservaciones("Faltan dos kilogramos en la entrega.");
        Recepcion guardada = context.getRecepcionService().registrar(recepcion);
        check(idUsuario.equals(guardada.getIdUsuario()), "Recepción con usuario autenticado");

        List<Incidencia> incidencias = context.getIncidenciaService().buscarPorVale(emitido.getIdVale());
        check(!incidencias.isEmpty() && idUsuario.equals(incidencias.get(0).getIdUsuario()),
                "Incidencia generada conserva el usuario de la recepción");

        Informe informe = context.getInformeService().crearInforme(null, "Informe integración CU-16",
                desde, hasta, hasta, "Especialista de estadística", "Resumen de integración");
        check(idUsuario.equals(informe.getIdUsuario()), "Informe elaborado con usuario autenticado");
    }

    private static boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() != null && Rol.ADMINISTRADOR.equals(usuario.getRol().getNombre());
    }

    private static void expectValidation(Runnable accion, String caso) {
        try {
            accion.run();
            throw new IllegalStateException("Se esperaba rechazo: " + caso);
        } catch (ValidacionException ex) {
            System.out.println("  OK - Rechazado (" + caso + "): " + ex.getMessage());
        }
    }

    private static void expectValidationQueContiene(Runnable accion, String caso, String texto) {
        try {
            accion.run();
            throw new IllegalStateException("Se esperaba rechazo: " + caso);
        } catch (ValidacionException ex) {
            if (!ex.getMessage().toLowerCase().contains(texto.toLowerCase())) {
                throw new IllegalStateException("Mensaje inesperado para " + caso + ": " + ex.getMessage());
            }
            System.out.println("  OK - Rechazado (" + caso + "): " + ex.getMessage());
        }
    }

    private static void check(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new IllegalStateException("Fallo: " + mensaje);
        }
        System.out.println("  OK - " + mensaje);
    }
}
