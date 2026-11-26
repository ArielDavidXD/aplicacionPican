package cu.pican.controller.service;

import cu.pican.controller.importacion.ArchivoExcel;
import cu.pican.controller.importacion.ImportacionException;
import cu.pican.controller.importacion.LectorXlsx;
import cu.pican.controller.importacion.ResultadoImportacion;
import cu.pican.controller.importacion.TipoImportacion;
import cu.pican.controller.importacion.ValidadorImportacion;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.Rol;
import cu.pican.model.Usuario;
import cu.pican.model.persistence.TransactionManager;
import cu.pican.model.repository.ImportacionReferenciasRepository;

import java.nio.file.Path;
import java.util.Objects;

/**
 * CU-18. Prepara y confirma importaciones exclusivamente para ADMINISTRADOR.
 *
 * La sesión se comprueba dos veces: al preparar y al confirmar. No hay superusuario;
 * ningún otro rol puede invocar estas operaciones aunque conozca la ruta del archivo.
 */
public class ImportacionService extends ServicioAutorizado {

    private final ImportacionReferenciasRepository destino;
    private final TransactionManager transacciones;

    public ImportacionService(ImportacionReferenciasRepository destino, TransactionManager transacciones) {
        this.destino = Objects.requireNonNull(destino, "destino");
        this.transacciones = Objects.requireNonNull(transacciones, "transacciones");
    }

    /** Lee y valida sin escribir. Los errores estructurales detienen el proceso aquí. */
    public ResultadoImportacion preparar(TipoImportacion tipo, Path archivo) {
        requerirAdministrador();
        ArchivoExcel excel = LectorXlsx.leer(archivo);
        ResultadoImportacion resultado = ValidadorImportacion.validar(tipo, excel, destino);
        resultado.setUsuario(nombreUsuario());
        return resultado;
    }

    /** Guarda únicamente las filas válidas de una vista previa ya autorizada. */
    public ResultadoImportacion confirmar(ResultadoImportacion vistaPrevia) {
        requerirAdministrador();
        if (vistaPrevia == null) {
            throw new ImportacionException("No hay una vista previa confirmable.");
        }
        if (vistaPrevia.isConfirmada()) {
            throw new ImportacionException("Esta vista previa ya fue confirmada.");
        }
        try {
            return transacciones.ejecutar(() -> {
                int escritas = 0;
                for (IndicadorHistorico indicador : vistaPrevia.getIndicadoresValidos()) {
                    if (destino.guardarIndicadorSiAusente(indicador)) {
                        escritas++;
                    } else {
                        vistaPrevia.registrarRepetidaEnConfirmacion();
                    }
                }
                for (MetaEstrategica meta : vistaPrevia.getMetasValidas()) {
                    if (destino.guardarMetaSiAusente(meta)) {
                        escritas++;
                    } else {
                        vistaPrevia.registrarRepetidaEnConfirmacion();
                    }
                }
                vistaPrevia.marcarConfirmada(escritas);
                return vistaPrevia;
            });
        } catch (AutorizacionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ImportacionException("No se pudieron guardar los datos validados.", ex);
        }
    }

    private void requerirAdministrador() {
        autorizacion().requerir(CasoUso.CU_18);
        Usuario usuario = autorizacion().usuarioAutenticado();
        if (usuario == null || !usuario.isActivo() || usuario.getRol() == null
                || !Rol.ADMINISTRADOR.equals(usuario.getRol().getNombre())) {
            throw new AutorizacionException(
                    "CU-18 requiere una sesión activa de ADMINISTRADOR.");
        }
    }

    private String nombreUsuario() {
        Usuario usuario = autorizacion().usuarioAutenticado();
        return usuario == null ? "" : usuario.getNombreUsuario();
    }
}
