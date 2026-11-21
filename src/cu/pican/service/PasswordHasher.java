package cu.pican.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Derivación de contraseñas para CU-16.
 * Utiliza PBKDF2WithHmacSHA256 con salt aleatorio por contraseña; nunca guarda texto plano.
 * El valor almacenado es autocontenable:
 * {@code PBKDF2-SHA256$<iteraciones>$<saltBase64>$<derivacionBase64>}.
 */
public final class PasswordHasher {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIJO = "PBKDF2-SHA256";
    private static final int ITERACIONES = 120_000;
    private static final int BYTES_SALT = 16;
    private static final int BITS_CLAVE = 256;
    private static final int BYTES_CONTRASENA_TEMPORAL = 18;

    private final SecureRandom aleatorio = new SecureRandom();

    /** Genera un hash codificado con salt aleatorio. */
    public String hash(String contrasena) {
        ValidationUtils.requireNotBlank(contrasena, "La contraseña es obligatoria.");
        byte[] salt = new byte[BYTES_SALT];
        aleatorio.nextBytes(salt);
        byte[] derivacion = derivar(contrasena, salt, ITERACIONES);
        return PREFIJO + "$" + ITERACIONES + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derivacion);
    }

    /** Comprueba una contraseña contra el valor codificado almacenado. */
    public boolean verificar(String contrasena, String hashGuardado) {
        if (contrasena == null || hashGuardado == null) {
            return false;
        }
        try {
            String[] partes = hashGuardado.split("\\$", -1);
            if (partes.length != 4 || !PREFIJO.equals(partes[0])) {
                return false;
            }
            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones <= 0) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] obtenido = derivar(contrasena, salt, iteraciones);
            return MessageDigest.isEqual(esperado, obtenido);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return false;
        }
    }

    /** Indica si el valor parece un hash generado por esta clase (utilizado por las validaciones). */
    public boolean estaCodificado(String valor) {
        return valor != null && valor.startsWith(PREFIJO + "$");
    }

    /**
     * Contraseña temporal de alta entropía para la cuenta inicial o para cuentas creadas por el administrador.
     * Se entrega por un canal controlado y no se escribe en el código fuente.
     */
    public String generarContrasenaTemporal() {
        byte[] bytes = new byte[BYTES_CONTRASENA_TEMPORAL];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] derivar(String contrasena, byte[] salt, int iteraciones) {
        char[] caracteres = contrasena.toCharArray();
        PBEKeySpec especificacion = new PBEKeySpec(caracteres, salt, iteraciones, BITS_CLAVE);
        try {
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance(ALGORITMO);
            return fabrica.generateSecret(especificacion).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("No es posible derivar la contraseña con PBKDF2.", ex);
        } finally {
            especificacion.clearPassword();
            java.util.Arrays.fill(caracteres, '\0');
        }
    }
}
