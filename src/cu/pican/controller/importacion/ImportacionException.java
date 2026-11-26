package cu.pican.controller.importacion;

/** Rechazo controlado de la importación CU-18. */
public class ImportacionException extends RuntimeException {

    public ImportacionException(String mensaje) {
        super(mensaje);
    }

    public ImportacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
