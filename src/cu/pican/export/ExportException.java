package cu.pican.export;

/** Error controlado al generar archivos PDF o Excel. */
public class ExportException extends RuntimeException {

    public ExportException(String mensaje) {
        super(mensaje);
    }

    public ExportException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
