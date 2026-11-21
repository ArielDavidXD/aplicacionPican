package cu.pican.sync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Lectura y escritura segura de paquetes ZIP en memoria (sin extracción al disco). */
public final class SyncArchivo {

    private static final long LIMITE_TOTAL = 20L * 1024 * 1024;
    private static final int LIMITE_ENTRADA = 16 * 1024 * 1024;

    private SyncArchivo() {
    }

    public static String sha256(byte[] contenido) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(contenido);
            StringBuilder texto = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                texto.append(String.format("%02x", b));
            }
            return texto.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new SyncException("SHA-256 no disponible en esta JVM", ex);
        }
    }

    public static boolean hashCoincide(String esperado, byte[] contenido) {
        if (esperado == null || esperado.length() != 64) {
            return false;
        }
        byte[] real = sha256(contenido).getBytes(StandardCharsets.US_ASCII);
        byte[] declarado = esperado.toLowerCase(java.util.Locale.ROOT)
                .getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(real, declarado);
    }

    public static void escribir(Path destino, LinkedHashMap<String, byte[]> entradas) {
        validarRuta(destino);
        long total = 0;
        for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
            validarNombre(entrada.getKey());
            total += entrada.getValue().length;
        }
        if (total > LIMITE_TOTAL) {
            throw new SyncException("El paquete supera el límite de 20 MB.");
        }
        try {
            Path padre = destino.toAbsolutePath().getParent();
            if (padre != null && !Files.isDirectory(padre)) {
                throw new SyncException("El directorio de destino no existe: " + padre);
            }
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destino))) {
                for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                    ZipEntry entry = new ZipEntry(entrada.getKey());
                    zip.putNextEntry(entry);
                    zip.write(entrada.getValue());
                    zip.closeEntry();
                }
            }
        } catch (IOException ex) {
            throw new SyncException("No se pudo escribir el paquete en " + destino, ex);
        }
    }

    public static LinkedHashMap<String, byte[]> leer(Path origen, Set<String> esperados) {
        if (origen == null || !Files.isRegularFile(origen)) {
            throw new SyncException("No existe el archivo de paquete seleccionado.");
        }
        long tamano;
        try {
            tamano = Files.size(origen);
        } catch (IOException ex) {
            throw new SyncException("No se pudo consultar el tamaño del paquete", ex);
        }
        if (tamano == 0 || tamano > LIMITE_TOTAL) {
            throw new SyncException("El paquete está vacío o supera el límite de 20 MB.");
        }
        LinkedHashMap<String, byte[]> contenido = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(origen))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                validarNombre(entry.getName());
                if (!esperados.contains(entry.getName())) {
                    throw new SyncException("Entrada no reconocida en el paquete: " + entry.getName());
                }
                if (contenido.containsKey(entry.getName())) {
                    throw new SyncException("Entrada duplicada en el paquete: " + entry.getName());
                }
                byte[] bytes = leerEntrada(zip, entry.getName());
                total += bytes.length;
                if (total > LIMITE_TOTAL) {
                    throw new SyncException("El paquete descomprimido supera el límite de 20 MB.");
                }
                contenido.put(entry.getName(), bytes);
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new SyncException("No se pudo leer el paquete ZIP", ex);
        }
        if (contenido.isEmpty()) {
            throw new SyncException("El ZIP no contiene archivos reconocibles.");
        }
        return contenido;
    }

    public static String texto(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] leerEntrada(ZipInputStream zip, String nombre) throws IOException {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = zip.read(buffer)) != -1) {
                if (salida.size() + leidos > LIMITE_ENTRADA) {
                    throw new SyncException("La entrada " + nombre + " supera el límite permitido.");
                }
                salida.write(buffer, 0, leidos);
            }
            return salida.toByteArray();
        }
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || nombre.isEmpty() || nombre.length() > 120
                || nombre.contains("..") || nombre.contains("/") || nombre.contains("\\")
                || nombre.startsWith("/") || !Arrays.asList("MANIFEST.json", "REGISTROS.json",
                        "CONFIRMACION.json").contains(nombre)) {
            throw new SyncException("Nombre de archivo interno no permitido: " + nombre);
        }
    }

    private static void validarRuta(Path ruta) {
        try {
            if (ruta == null || ruta.toString().trim().isEmpty()) {
                throw new SyncException("La ruta de destino está vacía.");
            }
            String nombre = ruta.getFileName().toString();
            if (nombre.contains("\0") || nombre.length() > 200) {
                throw new SyncException("Nombre de paquete no válido.");
            }
            // Se fuerza la resolución canónica para detectar rutas incorrectas antes de escribir.
            ruta.toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new SyncException("Ruta de paquete no válida: " + ruta, ex);
        }
    }

}
