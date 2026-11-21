package cu.pican.sync;

/** Fallo de protocolo, validación, autorización o persistencia del módulo de sincronización. */
public class SyncException extends RuntimeException {

    public SyncException(String mensaje) {
        super(mensaje);
    }

    public SyncException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
