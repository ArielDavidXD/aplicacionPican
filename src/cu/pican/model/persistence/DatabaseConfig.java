package cu.pican.model.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuración centralizada de persistencia de PICAN.
 *
 * Prioridad (de mayor a menor):
 * 1) propiedades de sistema (-Dpican.db.url=...),
 * 2) variables de entorno (PICAN_DB_URL, ...),
 * 3) archivo externo config/pican.properties (o PICAN_CONFIG_FILE),
 * 4) valores por defecto seguros: persistencia en memoria.
 *
 * En PostgreSQL se puede definir PICAN_DB_URL o, alternativamente,
 * PICAN_DB_HOST, PICAN_DB_PORT y PICAN_DB_NAME. La contraseña nunca se registra en logs.
 *
 * El archivo de configuración real está excluido del control de versiones.
 * Nunca se guarda una contraseña en el código fuente.
 */
public final class DatabaseConfig {

    public static final String MEMORIA = "memory";
    public static final String POSTGRESQL = "postgresql";
    public static final String MODO_LOCAL = "LOCAL";
    public static final String MODO_CENTRAL = "CENTRAL";

    private static final String ARCHIVO_POR_DEFECTO = "config/pican.properties";

    private final String persistencia;
    private final String url;
    private final String host;
    private final int puerto;
    private final String baseDatos;
    private final String usuario;
    private final String contrasena;
    private final String idInstancia;
    private final String modo;
    private final boolean migrarAlIniciar;
    private final String archivoOrigen;

    private DatabaseConfig(String persistencia, String url, String host, int puerto, String baseDatos,
                           String usuario, String contrasena, String idInstancia, String modo,
                           boolean migrarAlIniciar, String archivoOrigen) {
        this.persistencia = persistencia;
        this.url = url;
        this.host = host;
        this.puerto = puerto;
        this.baseDatos = baseDatos;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.idInstancia = idInstancia;
        this.modo = modo;
        this.migrarAlIniciar = migrarAlIniciar;
        this.archivoOrigen = archivoOrigen;
    }

    public static DatabaseConfig cargar() {
        Properties propiedades = new Properties();
        String archivo = resolverArchivoConfiguracion();
        Path ruta = Paths.get(archivo);
        String origen = null;
        if (Files.isRegularFile(ruta)) {
            try (InputStream entrada = Files.newInputStream(ruta)) {
                propiedades.load(entrada);
                origen = ruta.toString();
            } catch (IOException ex) {
                throw new PersistenciaException("No se pudo leer el archivo de configuración " + archivo, ex);
            }
        }

        String persistencia = resolver(propiedades, "PICAN_PERSISTENCE", "pican.persistence", MEMORIA);
        if (persistencia == null || persistencia.trim().isEmpty()) {
            persistencia = MEMORIA;
        }
        persistencia = persistencia.trim().toLowerCase();
        if (!MEMORIA.equals(persistencia) && !POSTGRESQL.equals(persistencia)) {
            throw new PersistenciaException("pican.persistence debe ser 'memory' o 'postgresql'; se recibió: " + persistencia);
        }

        String url = resolver(propiedades, "PICAN_DB_URL", "pican.db.url", null);
        String host = resolver(propiedades, "PICAN_DB_HOST", "pican.db.host", "127.0.0.1");
        String puertoTexto = resolver(propiedades, "PICAN_DB_PORT", "pican.db.port", "5432");
        String baseDatos = resolver(propiedades, "PICAN_DB_NAME", "pican.db.name", "pican");
        int puerto;
        try {
            puerto = Integer.parseInt(puertoTexto.trim());
            if (puerto < 1 || puerto > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            throw new PersistenciaException("PICAN_DB_PORT debe ser un puerto válido (1-65535); se recibió: " + puertoTexto, ex);
        }
        if (esVacio(url)) {
            url = "jdbc:postgresql://" + host.trim() + ":" + puerto + "/" + baseDatos.trim();
        }
        String usuario = resolver(propiedades, "PICAN_DB_USER", "pican.db.user", null);
        String contrasena = resolver(propiedades, "PICAN_DB_PASSWORD", "pican.db.password", null);
        String idInstancia = resolver(propiedades, "PICAN_INSTANCE_ID", "pican.db.instance", null);
        String modo = resolver(propiedades, "PICAN_DB_MODE", "pican.db.mode", MODO_LOCAL);
        String migrar = resolver(propiedades, "PICAN_DB_MIGRATE_ON_START", "pican.db.migrate", "true");

        boolean migrarBool = migrar == null || migrar.trim().isEmpty()
                || Boolean.parseBoolean(migrar.trim());
        DatabaseConfig config = new DatabaseConfig(persistencia, url, host.trim(), puerto, baseDatos.trim(),
                usuario, contrasena, idInstancia,
                modo == null || modo.trim().isEmpty() ? MODO_LOCAL : modo.trim().toUpperCase(),
                migrarBool, origen);

        if (config.esPostgresql()) {
            config.validar();
        }
        return config;
    }

    /** Localiza el archivo externo tanto desde la raíz del proyecto como desde su carpeta padre. */
    private static String resolverArchivoConfiguracion() {
        String configurado = valor("PICAN_CONFIG_FILE", "pican.config.file", null);
        if (!esVacio(configurado)) {
            return configurado;
        }
        String[] candidatos = {
                "config/pican.properties",
                ARCHIVO_POR_DEFECTO,
                "../aplicacionPican/config/pican.properties"
        };
        for (String candidato : candidatos) {
            if (Files.isRegularFile(Paths.get(candidato))) {
                return candidato;
            }
        }
        return ARCHIVO_POR_DEFECTO;
    }

    private static String resolver(Properties propiedades, String variableEntorno,
                                   String clavePropiedad, String valorPorDefecto) {
        String valor = System.getProperty(clavePropiedad);
        if (esVacio(valor)) {
            valor = System.getenv(variableEntorno);
        }
        if (esVacio(valor)) {
            valor = propiedades.getProperty(clavePropiedad);
        }
        if (esVacio(valor)) {
            valor = valorPorDefecto;
        }
        return valor;
    }

    private static String valor(String variableEntorno, String clavePropiedad, String valorPorDefecto) {
        return resolver(new Properties(), variableEntorno, clavePropiedad, valorPorDefecto);
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    private void validar() {
        if (esVacio(url) || esVacio(usuario) || contrasena == null) {
            throw new PersistenciaException(
                    "PostgreSQL está seleccionado pero faltan datos de conexión. Defina PICAN_DB_URL, "
                            + "PICAN_DB_USER y PICAN_DB_PASSWORD (o pican.db.* en el archivo de configuración).");
        }
        if (!url.trim().toLowerCase().startsWith("jdbc:postgresql:")) {
            throw new PersistenciaException("La URL JDBC debe comenzar con jdbc:postgresql://: " + url);
        }
        if (!MODO_LOCAL.equals(modo) && !MODO_CENTRAL.equals(modo)) {
            throw new PersistenciaException("PICAN_DB_MODE debe ser LOCAL o CENTRAL; se recibió: " + modo);
        }
    }

    public boolean esPostgresql() {
        return POSTGRESQL.equals(persistencia);
    }

    public boolean esMemoria() {
        return MEMORIA.equals(persistencia);
    }

    public String getPersistencia() {
        return persistencia;
    }

    public String getUrl() {
        return url;
    }

    public String getHost() {
        return host;
    }

    public int getPuerto() {
        return puerto;
    }

    public String getBaseDatos() {
        return baseDatos;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getContrasena() {
        return contrasena;
    }

    public String getIdInstancia() {
        return idInstancia == null ? modo : idInstancia;
    }

    public String getModo() {
        return modo;
    }

    public boolean isMigrarAlIniciar() {
        return migrarAlIniciar;
    }

    public String getArchivoOrigen() {
        return archivoOrigen;
    }

    /** URL de administración para crear la base dedicada de demostración. */
    public String getUrlMantenimiento() {
        String limpia = url.trim();
        int ultimaBarra = limpia.lastIndexOf('/');
        if (ultimaBarra < 0) {
            throw new PersistenciaException("No se pudo derivar la base de mantenimiento desde " + url);
        }
        String prefijo = limpia.substring(0, ultimaBarra + 1);
        String sufijo = limpia.substring(ultimaBarra + 1);
        int signoInterrogacion = sufijo.indexOf('?');
        if (signoInterrogacion >= 0) {
            sufijo = sufijo.substring(0, signoInterrogacion);
        }
        if (sufijo.isEmpty() || "postgres".equalsIgnoreCase(sufijo)) {
            return limpia;
        }
        return prefijo + "postgres";
    }

    @Override
    public String toString() {
        return "DatabaseConfig{" +
                "persistencia='" + persistencia + '\'' +
                ", url='" + url + '\'' +
                ", usuario='" + usuario + '\'' +
                ", idInstancia='" + getIdInstancia() + '\'' +
                ", modo='" + modo + '\'' +
                ", migrarAlIniciar=" + migrarAlIniciar +
                '}';
    }
}
