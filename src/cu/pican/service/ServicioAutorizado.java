package cu.pican.service;

/**
 * Base de los servicios de negocio que consultan la autorización central.
 *
 * Cada servicio conserva la autoridad de su propio contexto. En la aplicación real
 * {@code AppContext} inyecta la instancia asociada a su sesión; en demos aisladas se
 * captura la política activa en el momento de construir el servicio. Así varios
 * contextos pueden coexistir en el mismo proceso sin compartir accidentalmente la
 * sesión de otro. Si no hay política instalada, las operaciones fallan cerradas.
 */
abstract class ServicioAutorizado {

    private AutorizacionService autorizacion = AutorizacionService.activa();

    protected final AutorizacionService autorizacion() {
        return autorizacion;
    }

    /** Inyectado desde AppContext; no se acepta una autoridad nula. */
    public final void setAutorizacion(AutorizacionService autorizacion) {
        if (autorizacion != null) {
            this.autorizacion = autorizacion;
        }
    }
}
