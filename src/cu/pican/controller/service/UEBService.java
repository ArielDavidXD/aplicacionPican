package cu.pican.controller.service;

import cu.pican.model.UEB;
import cu.pican.model.repository.UEBRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Servicio de negocio para UEB (CU-17).
 * Valida UUID, código y nombre obligatorios, código único dentro del catálogo,
 * permite activar/desactivar (baja lógica) y bloquea la eliminación física de UEB
 * con operaciones asociadas. El predicado de uso se inyecta desde AppContext.
 */
public class UEBService extends ServicioAutorizado {

    private final UEBRepository uebRepository;
    private Predicate<UUID> verificadorOperaciones = id -> false;

    public UEBService(UEBRepository uebRepository) {
        this.uebRepository = uebRepository;
    }

    /** Inyectado desde AppContext; por defecto no bloquea para no romper demos aisladas. */
    public void setVerificadorOperaciones(Predicate<UUID> verificadorOperaciones) {
        this.verificadorOperaciones = verificadorOperaciones;
    }

    public UEB registrar(UEB ueb) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(ueb);
        return uebRepository.save(ueb);
    }

    public UEB actualizar(UEB ueb) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(ueb);
        return uebRepository.update(ueb);
    }

    public Optional<UEB> buscarPorId(UUID idUEB) {
        autorizacion().requerirLecturaCatalogo();
        return uebRepository.findById(idUEB);
    }

    public Optional<UEB> buscarPorCodigo(String codigo) {
        autorizacion().requerirLecturaCatalogo();
        return uebRepository.findByCodigo(codigo);
    }

    public List<UEB> listarTodas() {
        autorizacion().requerirLecturaCatalogo();
        return uebRepository.findAll();
    }

    public UEB activar(UUID idUEB) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idUEB, true);
    }

    public UEB desactivar(UUID idUEB) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idUEB, false);
    }

    public void eliminar(UUID idUEB) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireUuid(idUEB, "El identificador UUID de la UEB es obligatorio.");
        uebRepository.findById(idUEB)
                .orElseThrow(() -> new ValidacionException("No existe la UEB indicada."));
        if (verificadorOperaciones.test(idUEB)) {
            throw new ValidacionException(
                    "No se puede eliminar la UEB porque tiene registros operativos asociados. Desactívela en su lugar.");
        }
        uebRepository.deleteById(idUEB);
    }

    private UEB cambiarEstado(UUID idUEB, boolean activa) {
        ValidationUtils.requireUuid(idUEB, "El identificador UUID de la UEB es obligatorio.");
        UEB actual = uebRepository.findById(idUEB)
                .orElseThrow(() -> new ValidacionException("No existe la UEB indicada."));
        UEB copia = copiar(actual);
        copia.setActiva(activa);
        return uebRepository.update(copia);
    }

    private void validar(UEB ueb) {
        ValidationUtils.requireNonNull(ueb, "La UEB es obligatoria.");
        ValidationUtils.requireUuid(ueb.getIdUEB(), "El identificador UUID de la UEB es obligatorio.");
        ValidationUtils.requireNotBlank(ueb.getCodigo(), "El código de la UEB es obligatorio.");
        ValidationUtils.requireNotBlank(ueb.getNombre(), "El nombre de la UEB es obligatorio.");

        Optional<UEB> porCodigo = uebRepository.findByCodigo(ueb.getCodigo());
        if (porCodigo.isPresent() && !porCodigo.get().getIdUEB().equals(ueb.getIdUEB())) {
            throw new ValidacionException("Ya existe una UEB con el código '" + ueb.getCodigo().trim() + "'.");
        }
    }

    private UEB copiar(UEB actual) {
        return new UEB(actual.getIdUEB(), actual.getNombre(), actual.getCodigo(), actual.isActiva());
    }
}
