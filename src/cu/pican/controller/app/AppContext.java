package cu.pican.controller.app;

import cu.pican.controller.backup.PgBackupOperations;
import cu.pican.controller.backup.PostgresBackupOperations;
import cu.pican.controller.backup.VerificadorDestinoSistema;
import cu.pican.model.ControlRestauracion;
import cu.pican.model.persistence.DatabaseConfig;
import cu.pican.model.persistence.JdbcConnectionManager;
import cu.pican.model.persistence.JdbcTransactionManager;
import cu.pican.model.persistence.MigrationRunner;
import cu.pican.model.persistence.NoOpTransactionManager;
import cu.pican.model.persistence.TransactionManager;
import cu.pican.model.repository.AclaracionOperativaRepository;
import cu.pican.model.repository.BackupAuditoriaRepository;
import cu.pican.model.repository.ConsolidadoUEBRepository;
import cu.pican.model.repository.ControlRestauracionRepository;
import cu.pican.model.repository.DecisionDirectivoRepository;
import cu.pican.model.repository.DestinoRepository;
import cu.pican.model.repository.EntregaInformeRepository;
import cu.pican.model.repository.FuenteInformeRepository;
import cu.pican.model.repository.GestionReferenciasRepository;
import cu.pican.model.repository.IncidenciaRepository;
import cu.pican.model.repository.IndicadorHistoricoRepository;
import cu.pican.model.repository.ImportacionReferenciasRepository;
import cu.pican.model.repository.InformeRepository;
import cu.pican.model.repository.MetaEstrategicaRepository;
import cu.pican.model.repository.NaveRepository;
import cu.pican.model.repository.ParteNaveRepository;
import cu.pican.model.repository.ParteUnidadAsociadaRepository;
import cu.pican.model.repository.ProduccionCarneRepository;
import cu.pican.model.repository.ProductoDestinoRepository;
import cu.pican.model.repository.ProductoRepository;
import cu.pican.model.repository.RecepcionRepository;
import cu.pican.model.repository.UEBRepository;
import cu.pican.model.repository.UnidadAsociadaRepository;
import cu.pican.model.repository.UsuarioRepository;
import cu.pican.model.repository.ValeEntregaRepository;
import cu.pican.model.repository.jdbc.AclaracionOperativaRepositoryPostgres;
import cu.pican.model.repository.jdbc.BackupAuditoriaRepositoryPostgres;
import cu.pican.model.repository.jdbc.ConsolidadoUEBRepositoryPostgres;
import cu.pican.model.repository.jdbc.ControlRestauracionRepositoryPostgres;
import cu.pican.model.repository.jdbc.DecisionDirectivoRepositoryPostgres;
import cu.pican.model.repository.jdbc.DestinoRepositoryPostgres;
import cu.pican.model.repository.jdbc.EntregaInformeRepositoryPostgres;
import cu.pican.model.repository.jdbc.FuenteInformeRepositoryPostgres;
import cu.pican.model.repository.jdbc.GestionReferenciasRepositoryPostgres;
import cu.pican.model.repository.jdbc.IncidenciaRepositoryPostgres;
import cu.pican.model.repository.jdbc.IndicadorHistoricoRepositoryPostgres;
import cu.pican.model.repository.jdbc.ImportacionReferenciasRepositoryPostgres;
import cu.pican.model.repository.jdbc.InformeRepositoryPostgres;
import cu.pican.model.repository.jdbc.MetaEstrategicaRepositoryPostgres;
import cu.pican.model.repository.jdbc.NaveRepositoryPostgres;
import cu.pican.model.repository.jdbc.ParteNaveRepositoryPostgres;
import cu.pican.model.repository.jdbc.ParteUnidadAsociadaRepositoryPostgres;
import cu.pican.model.repository.jdbc.ProduccionCarneRepositoryPostgres;
import cu.pican.model.repository.jdbc.ProductoDestinoRepositoryPostgres;
import cu.pican.model.repository.jdbc.ProductoRepositoryPostgres;
import cu.pican.model.repository.jdbc.RecepcionRepositoryPostgres;
import cu.pican.model.repository.jdbc.UEBRepositoryPostgres;
import cu.pican.model.repository.jdbc.UnidadAsociadaRepositoryPostgres;
import cu.pican.model.repository.jdbc.UsuarioRepositoryPostgres;
import cu.pican.model.repository.jdbc.ValeEntregaRepositoryPostgres;
import cu.pican.model.repository.memory.AclaracionOperativaRepositoryMemory;
import cu.pican.model.repository.memory.BackupAuditoriaRepositoryMemory;
import cu.pican.model.repository.memory.ConsolidadoUEBRepositoryMemory;
import cu.pican.model.repository.memory.ControlRestauracionRepositoryMemory;
import cu.pican.model.repository.memory.DecisionDirectivoRepositoryMemory;
import cu.pican.model.repository.memory.DestinoRepositoryMemory;
import cu.pican.model.repository.memory.EntregaInformeRepositoryMemory;
import cu.pican.model.repository.memory.FuenteInformeRepositoryMemory;
import cu.pican.model.repository.memory.GestionReferenciasRepositoryMemory;
import cu.pican.model.repository.memory.IncidenciaRepositoryMemory;
import cu.pican.model.repository.memory.IndicadorHistoricoRepositoryMemory;
import cu.pican.model.repository.memory.ImportacionReferenciasRepositoryMemory;
import cu.pican.model.repository.memory.InformeRepositoryMemory;
import cu.pican.model.repository.memory.MetaEstrategicaRepositoryMemory;
import cu.pican.model.repository.memory.NaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteNaveRepositoryMemory;
import cu.pican.model.repository.memory.ParteUnidadAsociadaRepositoryMemory;
import cu.pican.model.repository.memory.ProduccionCarneRepositoryMemory;
import cu.pican.model.repository.memory.ProductoDestinoRepositoryMemory;
import cu.pican.model.repository.memory.ProductoRepositoryMemory;
import cu.pican.model.repository.memory.RecepcionRepositoryMemory;
import cu.pican.model.repository.memory.UEBRepositoryMemory;
import cu.pican.model.repository.memory.UnidadAsociadaRepositoryMemory;
import cu.pican.model.repository.memory.UsuarioRepositoryMemory;
import cu.pican.model.repository.memory.ValeEntregaRepositoryMemory;
import cu.pican.controller.service.AnalisisService;
import cu.pican.controller.service.AutorizacionService;
import cu.pican.controller.service.BackupService;
import cu.pican.controller.service.AuthService;
import cu.pican.controller.service.ConsolidadoUEBService;
import cu.pican.controller.service.DashboardService;
import cu.pican.controller.service.DestinoService;
import cu.pican.controller.service.GestionReferenciasService;
import cu.pican.controller.service.IncidenciaService;
import cu.pican.controller.service.ImportacionService;
import cu.pican.controller.service.InformeService;
import cu.pican.controller.service.NaveService;
import cu.pican.controller.service.PasswordHasher;
import cu.pican.controller.service.ParteNaveService;
import cu.pican.controller.service.ParteUnidadAsociadaService;
import cu.pican.controller.service.ProduccionCarneService;
import cu.pican.controller.service.RecepcionService;
import cu.pican.controller.service.ProductoDestinoService;
import cu.pican.controller.service.ProductoService;
import cu.pican.controller.service.UEBService;
import cu.pican.controller.service.UnidadAsociadaService;
import cu.pican.controller.service.UsuarioService;
import cu.pican.controller.service.ValeEntregaService;
import cu.pican.controller.sync.SyncException;
import cu.pican.controller.sync.SyncService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Contenedor central de dependencias de la aplicación.
 *
 * Construye los 20 repositorios y los servicios existentes en uno de dos modos:
 * memoria (comportamiento histórico de demostración) o PostgreSQL (persistencia real).
 * En modo PostgreSQL se comprueba la conexión, se ejecutan las migraciones pendientes
 * y se comparten el gestor de conexiones y el gestor transaccional.
 */
public class AppContext {

    private final DatabaseConfig config;
    private final boolean postgresql;
    private final JdbcConnectionManager conexiones;
    private final TransactionManager transacciones;

    private UEBRepository uebRepository;
    private NaveRepository naveRepository;
    private UnidadAsociadaRepository unidadAsociadaRepository;
    private ParteNaveRepository parteNaveRepository;
    private ParteUnidadAsociadaRepository parteUnidadAsociadaRepository;
    private ProduccionCarneRepository produccionCarneRepository;
    private ConsolidadoUEBRepository consolidadoUEBRepository;
    private AclaracionOperativaRepository aclaracionOperativaRepository;
    private UsuarioRepository usuarioRepository;
    private ProductoRepository productoRepository;
    private DestinoRepository destinoRepository;
    private ProductoDestinoRepository productoDestinoRepository;
    private ValeEntregaRepository valeEntregaRepository;
    private RecepcionRepository recepcionRepository;
    private IncidenciaRepository incidenciaRepository;
    private IndicadorHistoricoRepository indicadorHistoricoRepository;
    private MetaEstrategicaRepository metaEstrategicaRepository;
    private InformeRepository informeRepository;
    private FuenteInformeRepository fuenteInformeRepository;
    private DecisionDirectivoRepository decisionDirectivoRepository;
    private EntregaInformeRepository entregaInformeRepository;
    private ControlRestauracionRepository controlRestauracionRepository;
    private BackupService backupService;

    // Solo se usan en modo memoria para cargar las tablas históricas oficiales.
    private final IndicadorHistoricoRepositoryMemory indicadorHistoricoMemory;
    private final MetaEstrategicaRepositoryMemory metaEstrategicaMemory;

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final UsuarioService usuarioService;
    private final AuthService authService;
    private final AutorizacionService autorizacionService;

    private UEBService uebService;
    private NaveService naveService;
    private UnidadAsociadaService unidadAsociadaService;
    private ParteNaveService parteNaveService;
    private ParteUnidadAsociadaService parteUnidadAsociadaService;
    private ProduccionCarneService produccionCarneService;
    private ConsolidadoUEBService consolidadoUEBService;
    private ProductoService productoService;
    private DestinoService destinoService;
    private ProductoDestinoService productoDestinoService;
    private ValeEntregaService valeEntregaService;
    private RecepcionService recepcionService;
    private IncidenciaService incidenciaService;
    private AnalisisService analisisService;
    private DashboardService dashboardService;
    private InformeService informeService;
    private ImportacionReferenciasRepository importacionRepository;
    private ImportacionService importacionService;
    private GestionReferenciasRepository gestionReferenciasRepository;
    private GestionReferenciasService gestionReferenciasService;
    private SyncService syncService;

    public AppContext() {
        this.config = DatabaseConfig.cargar();
        this.postgresql = config.esPostgresql();

        if (postgresql) {
            this.conexiones = new JdbcConnectionManager(config);
            this.conexiones.verificarConexion();
            if (config.isMigrarAlIniciar()) {
                new MigrationRunner(conexiones, MigrationRunner.directorioPorDefecto()).ejecutar();
            }
            this.transacciones = new JdbcTransactionManager(conexiones);
            construirRepositoriosPostgres();
            this.controlRestauracionRepository = new ControlRestauracionRepositoryPostgres(conexiones);
            this.importacionRepository = new ImportacionReferenciasRepositoryPostgres(conexiones);
            this.gestionReferenciasRepository = new GestionReferenciasRepositoryPostgres(conexiones);
            this.indicadorHistoricoMemory = null;
            this.metaEstrategicaMemory = null;
        } else {
            this.conexiones = null;
            this.transacciones = new NoOpTransactionManager();
            construirRepositoriosMemoria();
            this.controlRestauracionRepository = new ControlRestauracionRepositoryMemory();
            this.indicadorHistoricoMemory = (IndicadorHistoricoRepositoryMemory) indicadorHistoricoRepository;
            this.metaEstrategicaMemory = (MetaEstrategicaRepositoryMemory) metaEstrategicaRepository;
            // Las tablas de referencia oficiales arrancan vacías: el administrador las carga
            // desde Administración - Referencias oficiales (CU-17) o por importación (CU-18).
            this.importacionRepository = new ImportacionReferenciasRepositoryMemory(
                    indicadorHistoricoMemory, metaEstrategicaMemory);
            this.gestionReferenciasRepository = new GestionReferenciasRepositoryMemory(
                    indicadorHistoricoMemory, metaEstrategicaMemory);
        }

        this.authService = new AuthService(usuarioRepository, passwordHasher);
        this.autorizacionService = new AutorizacionService(authService);
        AutorizacionService.instalar(this.autorizacionService);
        this.usuarioService = new UsuarioService(usuarioRepository, passwordHasher, transacciones);
        this.syncService = postgresql ? new SyncService(transacciones, conexiones, authService) : null;
        construirServicios();
        BackupAuditoriaRepository auditoriaBackups = postgresql
                ? new BackupAuditoriaRepositoryPostgres(conexiones)
                : new BackupAuditoriaRepositoryMemory();
        PgBackupOperations operacionesBackup = postgresql
                ? new PostgresBackupOperations(config, conexiones)
                : null;
        this.backupService = new BackupService(config, conexiones, operacionesBackup,
                new VerificadorDestinoSistema(), auditoriaBackups, controlRestauracionRepository);
        this.dashboardService = new DashboardService(analisisService, parteNaveService,
                produccionCarneService, consolidadoUEBService, valeEntregaService, incidenciaService,
                informeService, usuarioService, uebService, naveService, productoService,
                destinoService, backupService);
        cablearDependencias();

        // Cuenta administrativa inicial solo si no existe. En PostgreSQL exige contraseña
        // configurada; en MEMORY se mantiene la generación temporal de demostración.
        UsuarioInicialInitializer.inicializar(usuarioService, !postgresql);
    }

    private void construirRepositoriosMemoria() {
        uebRepository = new UEBRepositoryMemory();
        naveRepository = new NaveRepositoryMemory();
        unidadAsociadaRepository = new UnidadAsociadaRepositoryMemory();
        parteNaveRepository = new ParteNaveRepositoryMemory();
        parteUnidadAsociadaRepository = new ParteUnidadAsociadaRepositoryMemory();
        produccionCarneRepository = new ProduccionCarneRepositoryMemory();
        consolidadoUEBRepository = new ConsolidadoUEBRepositoryMemory();
        aclaracionOperativaRepository = new AclaracionOperativaRepositoryMemory();
        productoRepository = new ProductoRepositoryMemory();
        destinoRepository = new DestinoRepositoryMemory();
        productoDestinoRepository = new ProductoDestinoRepositoryMemory();
        valeEntregaRepository = new ValeEntregaRepositoryMemory();
        recepcionRepository = new RecepcionRepositoryMemory();
        incidenciaRepository = new IncidenciaRepositoryMemory();
        indicadorHistoricoRepository = new IndicadorHistoricoRepositoryMemory();
        metaEstrategicaRepository = new MetaEstrategicaRepositoryMemory();
        informeRepository = new InformeRepositoryMemory();
        fuenteInformeRepository = new FuenteInformeRepositoryMemory();
        decisionDirectivoRepository = new DecisionDirectivoRepositoryMemory();
        entregaInformeRepository = new EntregaInformeRepositoryMemory();
        // CU-16: el predicado impide borrar físicamente cuentas ya vinculadas a operaciones.
        usuarioRepository = new UsuarioRepositoryMemory(this::usuarioTieneOperaciones);
    }

    private void construirRepositoriosPostgres() {
        uebRepository = new UEBRepositoryPostgres(conexiones);
        naveRepository = new NaveRepositoryPostgres(conexiones);
        unidadAsociadaRepository = new UnidadAsociadaRepositoryPostgres(conexiones);
        parteNaveRepository = new ParteNaveRepositoryPostgres(conexiones);
        parteUnidadAsociadaRepository = new ParteUnidadAsociadaRepositoryPostgres(conexiones);
        produccionCarneRepository = new ProduccionCarneRepositoryPostgres(conexiones);
        consolidadoUEBRepository = new ConsolidadoUEBRepositoryPostgres(conexiones);
        aclaracionOperativaRepository = new AclaracionOperativaRepositoryPostgres(conexiones);
        productoRepository = new ProductoRepositoryPostgres(conexiones);
        destinoRepository = new DestinoRepositoryPostgres(conexiones);
        productoDestinoRepository = new ProductoDestinoRepositoryPostgres(conexiones);
        valeEntregaRepository = new ValeEntregaRepositoryPostgres(conexiones);
        recepcionRepository = new RecepcionRepositoryPostgres(conexiones);
        incidenciaRepository = new IncidenciaRepositoryPostgres(conexiones);
        indicadorHistoricoRepository = new IndicadorHistoricoRepositoryPostgres(conexiones);
        metaEstrategicaRepository = new MetaEstrategicaRepositoryPostgres(conexiones);
        informeRepository = new InformeRepositoryPostgres(conexiones);
        fuenteInformeRepository = new FuenteInformeRepositoryPostgres(conexiones);
        decisionDirectivoRepository = new DecisionDirectivoRepositoryPostgres(conexiones);
        entregaInformeRepository = new EntregaInformeRepositoryPostgres(conexiones);
        usuarioRepository = new UsuarioRepositoryPostgres(conexiones);
    }

    private void construirServicios() {
        uebService = new UEBService(uebRepository);
        naveService = new NaveService(naveRepository, uebRepository);
        unidadAsociadaService = new UnidadAsociadaService(unidadAsociadaRepository);
        parteNaveService = new ParteNaveService(parteNaveRepository, naveRepository, usuarioRepository);
        parteUnidadAsociadaService = new ParteUnidadAsociadaService(
                parteUnidadAsociadaRepository, unidadAsociadaRepository, uebRepository);
        produccionCarneService = new ProduccionCarneService(produccionCarneRepository, uebRepository);
        consolidadoUEBService = new ConsolidadoUEBService(
                transacciones, consolidadoUEBRepository, uebRepository, naveRepository,
                parteNaveRepository, parteUnidadAsociadaRepository, unidadAsociadaRepository,
                produccionCarneRepository, aclaracionOperativaRepository);
        productoService = new ProductoService(productoRepository);
        destinoService = new DestinoService(destinoRepository);
        productoDestinoService = new ProductoDestinoService(
                productoDestinoRepository, productoRepository, destinoRepository);
        valeEntregaService = new ValeEntregaService(
                valeEntregaRepository, uebService, productoService, destinoService,
                productoDestinoService, transacciones);
        recepcionService = new RecepcionService(recepcionRepository, valeEntregaService, null, transacciones);
        incidenciaService = new IncidenciaService(
                incidenciaRepository, recepcionService, valeEntregaService, transacciones);
        analisisService = new AnalisisService(
                indicadorHistoricoRepository, metaEstrategicaRepository, parteNaveService, naveService,
                uebService, produccionCarneService, consolidadoUEBService, parteUnidadAsociadaService);
        informeService = new InformeService(
                informeRepository, fuenteInformeRepository, decisionDirectivoRepository,
                entregaInformeRepository, analisisService, transacciones);
        importacionService = new ImportacionService(importacionRepository, transacciones);
        gestionReferenciasService = new GestionReferenciasService(
                gestionReferenciasRepository, indicadorHistoricoRepository, metaEstrategicaRepository);
    }

    private void cablearDependencias() {
        // Autoridad de este contexto: cada servicio consulta su propia instancia, nunca la global.
        uebService.setAutorizacion(autorizacionService);
        naveService.setAutorizacion(autorizacionService);
        unidadAsociadaService.setAutorizacion(autorizacionService);
        parteNaveService.setAutorizacion(autorizacionService);
        parteUnidadAsociadaService.setAutorizacion(autorizacionService);
        produccionCarneService.setAutorizacion(autorizacionService);
        consolidadoUEBService.setAutorizacion(autorizacionService);
        productoService.setAutorizacion(autorizacionService);
        destinoService.setAutorizacion(autorizacionService);
        productoDestinoService.setAutorizacion(autorizacionService);
        valeEntregaService.setAutorizacion(autorizacionService);
        recepcionService.setAutorizacion(autorizacionService);
        incidenciaService.setAutorizacion(autorizacionService);
        analisisService.setAutorizacion(autorizacionService);
        dashboardService.setAutorizacion(autorizacionService);
        informeService.setAutorizacion(autorizacionService);
        importacionService.setAutorizacion(autorizacionService);
        gestionReferenciasService.setAutorizacion(autorizacionService);
        backupService.setAutorizacion(autorizacionService);
        usuarioService.setAutorizacion(autorizacionService);

        // SIN-13: solo una instalacion LOCAL restaurada bloquea emision y exportacion.
        BooleanSupplier bloqueoTrasRestauracion = () -> DatabaseConfig.MODO_LOCAL.equals(config.getModo())
                && controlRestauracionRepository.obtener(config.getIdInstancia()).isBloqueado();
        valeEntregaService.setBloqueoPostRestauracion(bloqueoTrasRestauracion);
        if (syncService != null) {
            syncService.setBloqueoPostRestauracion(bloqueoTrasRestauracion);
            // DN/AL: la central los genera; las UEB los importan. Una bajada completa
            // desbloquea la instalación LOCAL restaurada (SIN-13).
            syncService.setEsCentral(esCentral());
            syncService.setDesbloqueoPostRestauracion(() -> {
                if (DatabaseConfig.MODO_LOCAL.equals(config.getModo())) {
                    controlRestauracionRepository.guardar(
                            ControlRestauracion.desbloqueado(config.getIdInstancia()));
                }
            });
        }

        // CU-11 usa incidencias y CU-12 consulta recepciones: se resuelve la dependencia circular en un único lugar.
        recepcionService.setIncidenciaService(incidenciaService);
        // CU-13 necesita comprobar que el vale no tenga recepción asociada.
        valeEntregaService.setRecepcionRepository(recepcionRepository);
        // CU-17: baja lógica y protección de catálogos usados por operaciones.
        uebService.setVerificadorOperaciones(this::uebTieneOperaciones);
        naveService.setVerificadorOperaciones(this::naveTienePartes);
        unidadAsociadaService.setVerificadorOperaciones(this::unidadTienePartes);
        productoService.setVerificadorOperaciones(this::productoTieneOperaciones);
        destinoService.setVerificadorOperaciones(this::destinoTieneOperaciones);
        productoDestinoService.setValeEntregaRepository(valeEntregaRepository);

        // CU-16: una sola sesión compartida; los servicios existentes solo consultan el usuario autenticado.
        authService.setUsuarioService(usuarioService);
        usuarioService.setAuthService(authService);
        parteNaveService.setProveedorSesion(authService);
        parteUnidadAsociadaService.setProveedorSesion(authService);
        produccionCarneService.setProveedorSesion(authService);
        consolidadoUEBService.setProveedorSesion(authService);
        valeEntregaService.setProveedorSesion(authService);
        recepcionService.setProveedorSesion(authService);
        incidenciaService.setProveedorSesion(authService);
        informeService.setProveedorSesion(authService);
    }

    /** Comprueba todas las operaciones que ya registran un usuario responsable. */
    private boolean usuarioTieneOperaciones(UUID idUsuario) {
        if (idUsuario == null) {
            return false;
        }
        if (parteNaveRepository.findAll().stream().anyMatch(o -> idUsuario.equals(o.getIdUsuario()))) return true;
        if (parteUnidadAsociadaRepository.findAll().stream().anyMatch(o -> idUsuario.equals(o.getIdUsuario()))) return true;
        if (produccionCarneRepository.findAll().stream().anyMatch(o -> idUsuario.equals(o.getIdUsuario()))) return true;
        if (consolidadoUEBRepository.findAll().stream().anyMatch(o -> idUsuario.equals(o.getIdUsuario()))) return true;
        if (valeEntregaRepository.findAll().stream().anyMatch(o -> idUsuario.equals(o.getIdUsuario())
                || idUsuario.equals(o.getIdUsuarioAnulacion()))) return true;
        if (recepcionRepository.findAll().stream().anyMatch(o -> idUsuario.equals(o.getIdUsuario()))) return true;
        if (incidenciaRepository.findAll().stream().anyMatch(o -> idUsuario.equals(o.getIdUsuario()))) return true;
        return informeRepository.findAll().stream().anyMatch(o -> idUsuario.equals(o.getIdUsuario()));
    }

    private boolean uebTieneOperaciones(UUID idUEB) {
        if (idUEB == null) return false;
        if (naveRepository.findAll().stream().anyMatch(o -> idUEB.equals(o.getIdUEB()))) return true;
        if (produccionCarneRepository.findAll().stream().anyMatch(o -> idUEB.equals(o.getIdUEB()))) return true;
        if (consolidadoUEBRepository.findAll().stream().anyMatch(o -> idUEB.equals(o.getIdUEB()))) return true;
        return valeEntregaRepository.findAll().stream().anyMatch(o -> idUEB.equals(o.getIdUEB()));
    }

    private boolean naveTienePartes(UUID idNave) {
        if (idNave == null) return false;
        return parteNaveRepository.findAll().stream().anyMatch(o -> idNave.equals(o.getIdNave()));
    }

    private boolean unidadTienePartes(UUID idUnidad) {
        if (idUnidad == null) return false;
        return parteUnidadAsociadaRepository.findAll().stream().anyMatch(o -> idUnidad.equals(o.getIdUnidad()));
    }

    private boolean productoTieneOperaciones(UUID idProducto) {
        if (idProducto == null) return false;
        if (valeEntregaRepository.findAll().stream().anyMatch(o -> idProducto.equals(o.getIdProducto()))) return true;
        return productoDestinoRepository.findAll().stream().anyMatch(o -> idProducto.equals(o.getIdProducto()));
    }

    private boolean destinoTieneOperaciones(UUID idDestino) {
        if (idDestino == null) return false;
        if (valeEntregaRepository.findAll().stream().anyMatch(o -> idDestino.equals(o.getIdDestino()))) return true;
        return productoDestinoRepository.findAll().stream().anyMatch(o -> idDestino.equals(o.getIdDestino()));
    }

    public boolean esPostgresql() {
        return postgresql;
    }

    public boolean esMemoria() {
        return !postgresql;
    }

    /** True cuando PICAN_DB_MODE=CENTRAL: esta instalación genera bajadas (DN) y altas (AL). */
    public boolean esCentral() {
        return DatabaseConfig.MODO_CENTRAL.equals(config.getModo());
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public TransactionManager getTransactionManager() {
        return transacciones;
    }

    public JdbcConnectionManager getConexiones() {
        return conexiones;
    }

    /** Mapa diagnóstico: permite verificar el tipo concreto de cada uno de los 20 repositorios. */
    public Map<String, Object> getRepositorios() {
        Map<String, Object> repositorios = new LinkedHashMap<>();
        repositorios.put("UEBRepository", uebRepository);
        repositorios.put("NaveRepository", naveRepository);
        repositorios.put("UnidadAsociadaRepository", unidadAsociadaRepository);
        repositorios.put("ParteNaveRepository", parteNaveRepository);
        repositorios.put("ParteUnidadAsociadaRepository", parteUnidadAsociadaRepository);
        repositorios.put("ProduccionCarneRepository", produccionCarneRepository);
        repositorios.put("ConsolidadoUEBRepository", consolidadoUEBRepository);
        repositorios.put("UsuarioRepository", usuarioRepository);
        repositorios.put("ProductoRepository", productoRepository);
        repositorios.put("DestinoRepository", destinoRepository);
        repositorios.put("ProductoDestinoRepository", productoDestinoRepository);
        repositorios.put("ValeEntregaRepository", valeEntregaRepository);
        repositorios.put("RecepcionRepository", recepcionRepository);
        repositorios.put("IncidenciaRepository", incidenciaRepository);
        repositorios.put("IndicadorHistoricoRepository", indicadorHistoricoRepository);
        repositorios.put("MetaEstrategicaRepository", metaEstrategicaRepository);
        repositorios.put("InformeRepository", informeRepository);
        repositorios.put("FuenteInformeRepository", fuenteInformeRepository);
        repositorios.put("DecisionDirectivoRepository", decisionDirectivoRepository);
        repositorios.put("EntregaInformeRepository", entregaInformeRepository);
        return Collections.unmodifiableMap(repositorios);
    }

    public UEBService getUebService() {
        return uebService;
    }

    public NaveService getNaveService() {
        return naveService;
    }

    public UnidadAsociadaService getUnidadAsociadaService() {
        return unidadAsociadaService;
    }

    public ParteNaveService getParteNaveService() {
        return parteNaveService;
    }

    public ParteUnidadAsociadaService getParteUnidadAsociadaService() {
        return parteUnidadAsociadaService;
    }

    public ProduccionCarneService getProduccionCarneService() {
        return produccionCarneService;
    }

    public ConsolidadoUEBService getConsolidadoUEBService() {
        return consolidadoUEBService;
    }

    public UsuarioRepository getUsuarioRepository() {
        return usuarioRepository;
    }

    public UsuarioService getUsuarioService() {
        return usuarioService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public AutorizacionService getAutorizacionService() {
        return autorizacionService;
    }

    /**
     * Sustituye temporalmente la autoridad de los servicios de este contexto.
     * Se usa solo en arranques de demostración y arneses históricos; la aplicación
     * real no la invoca. Debe restaurarse la autoridad original al terminar.
     */
    public void usarAutorizacion(AutorizacionService servicio) {
        if (servicio == null) {
            return;
        }
        uebService.setAutorizacion(servicio);
        naveService.setAutorizacion(servicio);
        unidadAsociadaService.setAutorizacion(servicio);
        parteNaveService.setAutorizacion(servicio);
        parteUnidadAsociadaService.setAutorizacion(servicio);
        produccionCarneService.setAutorizacion(servicio);
        consolidadoUEBService.setAutorizacion(servicio);
        productoService.setAutorizacion(servicio);
        destinoService.setAutorizacion(servicio);
        productoDestinoService.setAutorizacion(servicio);
        valeEntregaService.setAutorizacion(servicio);
        recepcionService.setAutorizacion(servicio);
        incidenciaService.setAutorizacion(servicio);
        analisisService.setAutorizacion(servicio);
        dashboardService.setAutorizacion(servicio);
        informeService.setAutorizacion(servicio);
        importacionService.setAutorizacion(servicio);
        gestionReferenciasService.setAutorizacion(servicio);
        backupService.setAutorizacion(servicio);
        usuarioService.setAutorizacion(servicio);
        AutorizacionService.instalar(servicio);
    }

    public ProductoService getProductoService() {
        return productoService;
    }

    public DestinoService getDestinoService() {
        return destinoService;
    }

    public ProductoDestinoService getProductoDestinoService() {
        return productoDestinoService;
    }

    public ValeEntregaService getValeEntregaService() {
        return valeEntregaService;
    }

    public RecepcionService getRecepcionService() {
        return recepcionService;
    }

    public IncidenciaService getIncidenciaService() {
        return incidenciaService;
    }

    public AnalisisService getAnalisisService() {
        return analisisService;
    }

    public DashboardService getDashboardService() {
        return dashboardService;
    }

    public InformeService getInformeService() {
        return informeService;
    }

    public ImportacionService getImportacionService() {
        return importacionService;
    }

    public GestionReferenciasService getGestionReferenciasService() {
        return gestionReferenciasService;
    }

    public BackupService getBackupService() {
        return backupService;
    }

    /**
     * El intercambio de paquetes requiere un historial persistente; en MEMORY se rechaza
     * explícitamente en lugar de simular una sincronización que no existiría.
     */
    public SyncService getSyncService() {
        if (syncService == null) {
            throw new SyncException("La sincronización por archivos requiere PostgreSQL; el modo MEMORY no la soporta.");
        }
        return syncService;
    }
}
