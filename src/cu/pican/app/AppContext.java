package cu.pican.app;

import cu.pican.backup.PgBackupOperations;
import cu.pican.backup.PostgresBackupOperations;
import cu.pican.backup.VerificadorDestinoSistema;
import cu.pican.persistence.DatabaseConfig;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcTransactionManager;
import cu.pican.persistence.MigrationRunner;
import cu.pican.persistence.NoOpTransactionManager;
import cu.pican.persistence.TransactionManager;
import cu.pican.repository.BackupAuditoriaRepository;
import cu.pican.repository.ConsolidadoUEBRepository;
import cu.pican.repository.ControlRestauracionRepository;
import cu.pican.repository.DecisionDirectivoRepository;
import cu.pican.repository.DestinoRepository;
import cu.pican.repository.EntregaInformeRepository;
import cu.pican.repository.FuenteInformeRepository;
import cu.pican.repository.IncidenciaRepository;
import cu.pican.repository.IndicadorHistoricoRepository;
import cu.pican.repository.ImportacionReferenciasRepository;
import cu.pican.repository.InformeRepository;
import cu.pican.repository.MetaEstrategicaRepository;
import cu.pican.repository.NaveRepository;
import cu.pican.repository.ParteNaveRepository;
import cu.pican.repository.ParteUnidadAsociadaRepository;
import cu.pican.repository.ProduccionCarneRepository;
import cu.pican.repository.ProductoDestinoRepository;
import cu.pican.repository.ProductoRepository;
import cu.pican.repository.RecepcionRepository;
import cu.pican.repository.UEBRepository;
import cu.pican.repository.UnidadAsociadaRepository;
import cu.pican.repository.UsuarioRepository;
import cu.pican.repository.ValeEntregaRepository;
import cu.pican.repository.jdbc.BackupAuditoriaRepositoryPostgres;
import cu.pican.repository.jdbc.ConsolidadoUEBRepositoryPostgres;
import cu.pican.repository.jdbc.ControlRestauracionRepositoryPostgres;
import cu.pican.repository.jdbc.DecisionDirectivoRepositoryPostgres;
import cu.pican.repository.jdbc.DestinoRepositoryPostgres;
import cu.pican.repository.jdbc.EntregaInformeRepositoryPostgres;
import cu.pican.repository.jdbc.FuenteInformeRepositoryPostgres;
import cu.pican.repository.jdbc.IncidenciaRepositoryPostgres;
import cu.pican.repository.jdbc.IndicadorHistoricoRepositoryPostgres;
import cu.pican.repository.jdbc.ImportacionReferenciasRepositoryPostgres;
import cu.pican.repository.jdbc.InformeRepositoryPostgres;
import cu.pican.repository.jdbc.MetaEstrategicaRepositoryPostgres;
import cu.pican.repository.jdbc.NaveRepositoryPostgres;
import cu.pican.repository.jdbc.ParteNaveRepositoryPostgres;
import cu.pican.repository.jdbc.ParteUnidadAsociadaRepositoryPostgres;
import cu.pican.repository.jdbc.ProduccionCarneRepositoryPostgres;
import cu.pican.repository.jdbc.ProductoDestinoRepositoryPostgres;
import cu.pican.repository.jdbc.ProductoRepositoryPostgres;
import cu.pican.repository.jdbc.RecepcionRepositoryPostgres;
import cu.pican.repository.jdbc.UEBRepositoryPostgres;
import cu.pican.repository.jdbc.UnidadAsociadaRepositoryPostgres;
import cu.pican.repository.jdbc.UsuarioRepositoryPostgres;
import cu.pican.repository.jdbc.ValeEntregaRepositoryPostgres;
import cu.pican.repository.memory.BackupAuditoriaRepositoryMemory;
import cu.pican.repository.memory.ConsolidadoUEBRepositoryMemory;
import cu.pican.repository.memory.ControlRestauracionRepositoryMemory;
import cu.pican.repository.memory.DecisionDirectivoRepositoryMemory;
import cu.pican.repository.memory.DestinoRepositoryMemory;
import cu.pican.repository.memory.EntregaInformeRepositoryMemory;
import cu.pican.repository.memory.FuenteInformeRepositoryMemory;
import cu.pican.repository.memory.IncidenciaRepositoryMemory;
import cu.pican.repository.memory.IndicadorHistoricoRepositoryMemory;
import cu.pican.repository.memory.ImportacionReferenciasRepositoryMemory;
import cu.pican.repository.memory.InformeRepositoryMemory;
import cu.pican.repository.memory.MetaEstrategicaRepositoryMemory;
import cu.pican.repository.memory.NaveRepositoryMemory;
import cu.pican.repository.memory.ParteNaveRepositoryMemory;
import cu.pican.repository.memory.ParteUnidadAsociadaRepositoryMemory;
import cu.pican.repository.memory.ProduccionCarneRepositoryMemory;
import cu.pican.repository.memory.ProductoDestinoRepositoryMemory;
import cu.pican.repository.memory.ProductoRepositoryMemory;
import cu.pican.repository.memory.RecepcionRepositoryMemory;
import cu.pican.repository.memory.UEBRepositoryMemory;
import cu.pican.repository.memory.UnidadAsociadaRepositoryMemory;
import cu.pican.repository.memory.UsuarioRepositoryMemory;
import cu.pican.repository.memory.ValeEntregaRepositoryMemory;
import cu.pican.service.AnalisisService;
import cu.pican.service.AutorizacionService;
import cu.pican.service.BackupService;
import cu.pican.service.AuthService;
import cu.pican.service.ConsolidadoUEBService;
import cu.pican.service.DestinoService;
import cu.pican.service.IncidenciaService;
import cu.pican.service.ImportacionService;
import cu.pican.service.InformeService;
import cu.pican.service.NaveService;
import cu.pican.service.PasswordHasher;
import cu.pican.service.ParteNaveService;
import cu.pican.service.ParteUnidadAsociadaService;
import cu.pican.service.ProduccionCarneService;
import cu.pican.service.RecepcionService;
import cu.pican.service.ProductoDestinoService;
import cu.pican.service.ProductoService;
import cu.pican.service.UEBService;
import cu.pican.service.UnidadAsociadaService;
import cu.pican.service.UsuarioService;
import cu.pican.service.ValeEntregaService;
import cu.pican.sync.SyncException;
import cu.pican.sync.SyncService;

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
    private InformeService informeService;
    private ImportacionReferenciasRepository importacionRepository;
    private ImportacionService importacionService;
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
            ReferenciasHistoricasJdbcInitializer.cargar(conexiones);
            this.importacionRepository = new ImportacionReferenciasRepositoryPostgres(conexiones);
            this.indicadorHistoricoMemory = null;
            this.metaEstrategicaMemory = null;
        } else {
            this.conexiones = null;
            this.transacciones = new NoOpTransactionManager();
            construirRepositoriosMemoria();
            this.controlRestauracionRepository = new ControlRestauracionRepositoryMemory();
            this.indicadorHistoricoMemory = (IndicadorHistoricoRepositoryMemory) indicadorHistoricoRepository;
            this.metaEstrategicaMemory = (MetaEstrategicaRepositoryMemory) metaEstrategicaRepository;
            ReferenciasHistoricasInitializer.cargar(indicadorHistoricoMemory, metaEstrategicaMemory);
            this.importacionRepository = new ImportacionReferenciasRepositoryMemory(
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
        parteUnidadAsociadaService = new ParteUnidadAsociadaService(parteUnidadAsociadaRepository, unidadAsociadaRepository);
        produccionCarneService = new ProduccionCarneService(produccionCarneRepository, uebRepository);
        consolidadoUEBService = new ConsolidadoUEBService(consolidadoUEBRepository, uebRepository);
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
        informeService.setAutorizacion(autorizacionService);
        importacionService.setAutorizacion(autorizacionService);
        backupService.setAutorizacion(autorizacionService);
        usuarioService.setAutorizacion(autorizacionService);

        // SIN-13: solo una instalacion LOCAL restaurada bloquea emision y exportacion.
        BooleanSupplier bloqueoTrasRestauracion = () -> DatabaseConfig.MODO_LOCAL.equals(config.getModo())
                && controlRestauracionRepository.obtener(config.getIdInstancia()).isBloqueado();
        valeEntregaService.setBloqueoPostRestauracion(bloqueoTrasRestauracion);
        if (syncService != null) {
            syncService.setBloqueoPostRestauracion(bloqueoTrasRestauracion);
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
        informeService.setAutorizacion(servicio);
        importacionService.setAutorizacion(servicio);
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

    public InformeService getInformeService() {
        return informeService;
    }

    public ImportacionService getImportacionService() {
        return importacionService;
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
