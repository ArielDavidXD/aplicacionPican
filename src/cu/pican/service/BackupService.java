package cu.pican.service;

import cu.pican.backup.BackupArchivo;
import cu.pican.backup.BackupException;
import cu.pican.backup.EstadoEsquema;
import cu.pican.backup.InformacionCopia;
import cu.pican.backup.ManifiestoCopia;
import cu.pican.backup.PgBackupOperations;
import cu.pican.backup.ResultadoCopia;
import cu.pican.backup.ResultadoRestauracion;
import cu.pican.backup.ValidadorCopia;
import cu.pican.backup.VerificadorDestino;
import cu.pican.backup.VersionAplicacion;
import cu.pican.model.BackupAuditoria;
import cu.pican.model.ControlRestauracion;
import cu.pican.model.Rol;
import cu.pican.model.Usuario;
import cu.pican.persistence.DatabaseConfig;
import cu.pican.persistence.JdbcConnectionManager;
import cu.pican.persistence.JdbcSupport;
import cu.pican.repository.BackupAuditoriaRepository;
import cu.pican.repository.ControlRestauracionRepository;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CU-19: copias reales de PostgreSQL, solo ADMINISTRADOR, con recuperación segura.
 * Ningún éxito se publica antes de verificar la base preparada y la definitiva.
 */
public final class BackupService extends ServicioAutorizado {
    private static final DateTimeFormatter SELLOS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final long ESPACIO_MINIMO = 32L * 1024 * 1024;
    private static final String LOCAL = "LOCAL";

    private final DatabaseConfig config;
    private final JdbcConnectionManager conexiones;
    private final PgBackupOperations operaciones;
    private final VerificadorDestino verificador;
    private final BackupAuditoriaRepository auditoria;
    private final ControlRestauracionRepository control;

    public BackupService(DatabaseConfig config, JdbcConnectionManager conexiones,
                         PgBackupOperations operaciones, VerificadorDestino verificador,
                         BackupAuditoriaRepository auditoria, ControlRestauracionRepository control) {
        this.config = Objects.requireNonNull(config, "config");
        this.conexiones = conexiones;
        this.operaciones = operaciones;
        this.verificador = Objects.requireNonNull(verificador, "verificador");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.control = Objects.requireNonNull(control, "control");
    }

    /** Generación manual: permisos, espacio, volcado real, manifiesto, hash, publicación y auditoría (VAL-98). */
    public ResultadoCopia crearCopia(Path directorioDestino) {
        requerirAdministrador();
        LocalDateTime fecha = LocalDateTime.now();
        Path volcadoTemporal = null;
        Path zipTemporal = null;
        try {
            validarDestino(directorioDestino);
            if (operaciones == null || conexiones == null) {
                throw new BackupException("Las copias reales requieren PostgreSQL; el modo MEMORY no las soporta.");
            }
            String base = operaciones.baseActiva();
            volcadoTemporal = Files.createTempFile("pican-cu19-", ".dump");
            operaciones.volcar(volcadoTemporal, base);
            long disponible = verificador.espacioDisponibleBytes(directorioDestino);
            long necesario = Files.size(volcadoTemporal) * 3 + 1024L * 1024L;
            if (disponible < necesario) {
                throw new BackupException("Espacio insuficiente en la ubicación de destino para publicar la copia.");
            }
            byte[] dump = Files.readAllBytes(volcadoTemporal);
            String shaDump = BackupArchivo.hash(dump);
            ManifiestoCopia manifiesto = new ManifiestoCopia(
                    VersionAplicacion.actual(), versionEsquemaActual(),
                    EstadoEsquema.migracionesAplicadas(conexiones), fecha.toString(), base,
                    config.getIdInstancia(), config.getModo(), nombreUsuario(), dump.length, shaDump);
            zipTemporal = Files.createTempFile(directorioDestino, "pican-backup-", ".tmp");
            BackupArchivo.escribir(zipTemporal, dump, manifiesto.aJson());
            ValidadorCopia.inspeccionar(zipTemporal, config, conexiones);
            Path destinoFinal = resolverDestinoFinal(directorioDestino, base, fecha);
            publicar(zipTemporal, destinoFinal);
            zipTemporal = null;
            ResultadoCopia resultado = new ResultadoCopia(true, destinoFinal, fecha,
                    Files.size(destinoFinal), shaDump, "Copia creada y verificada correctamente.");
            auditar("CREACION", destinoFinal.toString(), "EXITOSO", null,
                    resultado.getTamanoBytes(), shaDump, null);
            return resultado;
        } catch (AutorizacionException ex) {
            throw ex;
        } catch (RuntimeException | IOException ex) {
            String mensaje = mensajeSeguro(ex);
            auditar("CREACION", directorioDestino == null ? "" : directorioDestino.toString(),
                    "FALLIDO", mensaje, null, null, null);
            return ResultadoCopia.fallido(mensaje);
        } finally {
            borrar(volcadoTemporal);
            borrar(zipTemporal);
        }
    }

    public InformacionCopia inspeccionar(Path archivo) {
        requerirAdministrador();
        return ValidadorCopia.inspeccionar(archivo, config, conexiones);
    }

    /** Restauración segura con dos confirmaciones independientes (VAL-99). */
    public ResultadoRestauracion restaurar(Path archivo, boolean primeraConfirmacion, boolean segundaConfirmacion) {
        requerirAdministrador();
        InformacionCopia informacion;
        try {
            informacion = ValidadorCopia.inspeccionar(archivo, config, conexiones);
        } catch (RuntimeException ex) {
            String mensaje = mensajeSeguro(ex);
            auditar("RESTAURACION", archivo == null ? "" : archivo.toString(), "FALLIDO", mensaje, null, null, null);
            return ResultadoRestauracion.fallido(mensaje, null);
        }
        if (!informacion.esValida()) {
            auditar("RESTAURACION", archivo.toString(), "FALLIDO",
                    informacion.getDetalleCompatibilidad(), null, null, null);
            return ResultadoRestauracion.fallido(informacion.getDetalleCompatibilidad(), informacion);
        }
        if (!primeraConfirmacion || !segundaConfirmacion) {
            auditar("RESTAURACION", archivo.toString(), "CANCELADO",
                    "Confirmación cancelada; datos actuales intactos.", null, null, null);
            return ResultadoRestauracion.cancelado(informacion);
        }
        requerirAdministrador();
        if (operaciones == null || conexiones == null) {
            String mensaje = "La restauración real requiere PostgreSQL; el modo MEMORY no la soporta.";
            auditar("RESTAURACION", archivo.toString(), "FALLIDO", mensaje, null, null, null);
            return ResultadoRestauracion.fallido(mensaje, informacion);
        }

        String sello = LocalDateTime.now().format(SELLOS);
        String activa = null;
        String preparacion = "pican_" + sello + "_prep_test";
        String seguridad = "pican_" + sello + "_seg_test";
        String antigua = "pican_" + sello + "_ant_test";
        String fallida = "pican_" + sello + "_fall_test";
        boolean intercambiado = false;
        Path volcadoExtraido = null;
        try {
            activa = operaciones.baseActiva();
            LinkedHashMap<String, byte[]> entradas = BackupArchivo.leer(archivo);
            volcadoExtraido = Files.createTempFile("pican-cu19-restore-", ".dump");
            Files.write(volcadoExtraido, entradas.get(BackupArchivo.DUMP));

            operaciones.permitirConexiones(activa, false);
            operaciones.terminarConexiones(activa);
            operaciones.crearBase(preparacion);
            operaciones.restaurar(volcadoExtraido, preparacion);
            verificarEstructura(preparacion, informacion, false);
            operaciones.crearBaseDesdePlantilla(seguridad, activa);

            operaciones.renombrarBase(activa, antigua);
            intercambiado = true;
            operaciones.renombrarBase(preparacion, activa);
            operaciones.permitirConexiones(activa, true);
            operaciones.permitirConexiones(antigua, true);
            cerrarConexionDelHilo();
            operaciones.verificarConexion(activa);
            operaciones.aplicarMigracionesPendientes();
            verificarEstructura(activa, informacion, true);

            UUID idAuditoria = UUID.randomUUID();
            if (esUEB()) {
                control.guardar(new ControlRestauracion(true,
                        "Restauración CU-19: bloqueo SIN-13 hasta importar la bajada completa del central.",
                        LocalDateTime.now(), idAuditoria, config.getIdInstancia()));
            }
            auditar("RESTAURACION", archivo.toString(), "EXITOSO", null,
                    informacion.getTamanoArchivo(), informacion.getManifiesto().getSha256Dump(), idAuditoria);
            borrarSilencioso(seguridad);
            borrarSilencioso(antigua);
            return new ResultadoRestauracion(true, false, "Restauración completada y verificada correctamente.",
                    informacion, LocalDateTime.now(), activa);
        } catch (RuntimeException | IOException ex) {
            String mensaje = "No se pudo completar la restauración; los datos actuales fueron conservados. "
                    + mensajeSeguro(ex);
            revertir(activa, preparacion, antigua, fallida, intercambiado);
            auditar("RESTAURACION", archivo.toString(), "FALLIDO", mensaje, null, null, null);
            return ResultadoRestauracion.fallido(mensaje, informacion);
        } finally {
            borrar(volcadoExtraido);
            cerrarConexionDelHilo();
        }
    }

    private void validarDestino(Path directorio) {
        if (!verificador.esEscribible(directorio)) {
            throw new BackupException("La ubicación seleccionada no existe o no permite escribir (VAL-98).");
        }
        if (verificador.espacioDisponibleBytes(directorio) < ESPACIO_MINIMO) {
            throw new BackupException("La ubicación seleccionada no dispone de espacio suficiente (VAL-98).");
        }
    }

    private Path resolverDestinoFinal(Path directorio, String base, LocalDateTime fecha) {
        String nombre = "pican-backup-" + base + "-" + fecha.format(SELLOS) + ".zip";
        Path candidato = directorio.resolve(nombre);
        int sufijo = 1;
        while (Files.exists(candidato)) {
            candidato = directorio.resolve("pican-backup-" + base + "-" + fecha.format(SELLOS)
                    + "-" + sufijo + ".zip");
            sufijo++;
        }
        return candidato;
    }

    private void publicar(Path temporal, Path destino) throws IOException {
        try {
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporal, destino);
        }
    }

    private void verificarEstructura(String base, InformacionCopia informacion, boolean postMigracion) {
        operaciones.verificarConexion(base);
        if (!operaciones.existeTabla(base, "pican_schema_migrations")
                || !operaciones.existeTabla(base, "ueb")
                || !operaciones.existeTabla(base, "usuario")
                || !operaciones.existeTabla(base, "sync_paquete")) {
            throw new BackupException("La base " + base + " no conserva la estructura mínima del sistema.");
        }
        if (operaciones.contarMigracionesAplicadas(base) < informacion.getManifiesto().getMigraciones().size()) {
            throw new BackupException("La base " + base + " no conserva el historial de migraciones esperado.");
        }
        if (postMigracion && (!operaciones.existeTabla(base, "pican_backup_auditoria")
                || !operaciones.existeTabla(base, "pican_restauracion_control"))) {
            throw new BackupException("No se pudieron aplicar las migraciones de auditoría CU-19.");
        }
    }

    private void revertir(String activa, String preparacion, String antigua, String fallida, boolean intercambiado) {
        try {
            if (intercambiado && activa != null && antigua != null) {
                operaciones.permitirConexiones(antigua, true);
                operaciones.permitirConexiones(activa, false);
                operaciones.terminarConexiones(activa);
                operaciones.renombrarBase(activa, fallida);
                operaciones.renombrarBase(antigua, activa);
                operaciones.permitirConexiones(activa, true);
                operaciones.permitirConexiones(fallida, true);
            } else if (activa != null) {
                operaciones.permitirConexiones(activa, true);
            }
            if (!intercambiado && preparacion != null) borrarSilencioso(preparacion);
            cerrarConexionDelHilo();
            if (activa != null) operaciones.verificarConexion(activa);
        } catch (RuntimeException ex) {
            // Se conserva el error original; la reversión también se intentó.
        }
    }

    private void borrarSilencioso(String base) {
        if (base == null) return;
        try { operaciones.eliminarBase(base); }
        catch (RuntimeException ignorado) { }
    }

    private void cerrarConexionDelHilo() {
        Connection activa = JdbcSupport.conexionActiva();
        if (activa != null) {
            JdbcSupport.desenlazar();
            try { activa.close(); } catch (SQLException ignorado) { }
        }
    }

    private boolean esUEB() { return LOCAL.equalsIgnoreCase(config.getModo()); }
    private String versionEsquemaActual() { return EstadoEsquema.unirVersiones(EstadoEsquema.migracionesLocales()); }

    private void auditar(String operacion, String archivo, String resultado, String motivo,
                         Long tamano, String sha, UUID idForzado) {
        Usuario usuario = autorizacion().usuarioAutenticado();
        BackupAuditoria evento = new BackupAuditoria(
                idForzado == null ? UUID.randomUUID() : idForzado,
                usuario == null ? null : usuario.getIdUsuario(),
                usuario == null ? "" : usuario.getNombreUsuario(),
                operacion, sanitizar(archivo), LocalDateTime.now(), resultado, sanitizar(motivo),
                tamano, sha, VersionAplicacion.actual(), versionEsquemaActual(), config.getIdInstancia());
        auditoria.guardar(evento);
    }

    private String mensajeSeguro(Exception ex) {
        return sanitizar(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }

    private String sanitizar(String mensaje) {
        String resultado = mensaje == null ? "" : mensaje;
        String contrasena = config.getContrasena();
        if (contrasena != null && !contrasena.isBlank()) resultado = resultado.replace(contrasena, "***");
        return resultado.replaceAll("(?i)PGPASSWORD=[^\\s]+", "PGPASSWORD=***");
    }

    private void borrar(Path archivo) {
        if (archivo == null) return;
        try { Files.deleteIfExists(archivo); } catch (IOException ignorado) { }
    }

    private String nombreUsuario() {
        Usuario usuario = autorizacion().usuarioAutenticado();
        return usuario == null ? "" : usuario.getNombreUsuario();
    }

    private void requerirAdministrador() {
        autorizacion().requerir(CasoUso.CU_19);
        Usuario usuario = autorizacion().usuarioAutenticado();
        if (usuario == null || !usuario.isActivo() || usuario.getRol() == null
                || !Rol.ADMINISTRADOR.equals(usuario.getRol().getNombre())) {
            throw new AutorizacionException("CU-19 requiere una sesión activa de ADMINISTRADOR.");
        }
    }

    public List<BackupAuditoria> historial() {
        requerirAdministrador();
        return auditoria.listar();
    }
}
