package cu.pican.service;

import cu.pican.model.Nave;
import cu.pican.model.UEB;
import cu.pican.repository.NaveRepository;
import cu.pican.repository.UEBRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Servicio de negocio para Nave (CU-17).
 * Valida UUID, código y nombre obligatorios, código único, UEB existente y activa,
 * activación/desactivación y bloqueo de baja física cuando existen partes de producción.
 * La edición normal conserva la UEB asociada; el cambio se realiza sólo con cambiarUEB(...).
 */
public class NaveService extends ServicioAutorizado {

    private final NaveRepository naveRepository;
    private final UEBRepository uebRepository;
    private Predicate<UUID> verificadorOperaciones = id -> false;

    public NaveService(NaveRepository naveRepository, UEBRepository uebRepository) {
        this.naveRepository = naveRepository;
        this.uebRepository = uebRepository;
    }

    /** Inyectado desde AppContext; por defecto no bloquea para no romper demos aisladas. */
    public void setVerificadorOperaciones(Predicate<UUID> verificadorOperaciones) {
        this.verificadorOperaciones = verificadorOperaciones;
    }

    public Nave registrar(Nave nave) {
        autorizacion().requerir(CasoUso.CU_17);
        validar(nave, true);
        return naveRepository.save(nave);
    }

    /**
     * Actualiza los datos de la nave sin modificar su UEB. Para cambiar la UEB
     * debe invocarse explícitamente {@link #cambiarUEB(UUID, UUID)}.
     */
    public Nave actualizar(Nave nave) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireNonNull(nave, "La nave es obligatoria.");
        ValidationUtils.requireUuid(nave.getIdNave(), "El identificador UUID de la nave es obligatorio.");
        Nave actual = naveRepository.findById(nave.getIdNave())
                .orElseThrow(() -> new ValidacionException("No existe la nave indicada."));
        Nave aGuardar = copiar(nave);
        aGuardar.setIdUEB(actual.getIdUEB());
        validar(aGuardar, false);
        return naveRepository.update(aGuardar);
    }

    /** Cambio explícito y validado de la UEB asociada a una nave. */
    public Nave cambiarUEB(UUID idNave, UUID idUEB) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireUuid(idNave, "El identificador UUID de la nave es obligatorio.");
        Nave actual = naveRepository.findById(idNave)
                .orElseThrow(() -> new ValidacionException("No existe la nave indicada."));
        validarUEBAsociada(idUEB, true);
        Nave copia = copiar(actual);
        copia.setIdUEB(idUEB);
        return naveRepository.update(copia);
    }

    public Optional<Nave> buscarPorId(UUID idNave) {
        autorizacion().requerirLecturaCatalogo();
        return naveRepository.findById(idNave);
    }

    public Optional<Nave> buscarPorCodigo(String codigo) {
        autorizacion().requerirLecturaCatalogo();
        return naveRepository.findByCodigo(codigo);
    }

    public List<Nave> listarTodas() {
        autorizacion().requerirLecturaCatalogo();
        return naveRepository.findAll();
    }

    public List<Nave> buscarPorUEB(UUID idUEB) {
        autorizacion().requerirLecturaCatalogo();
        return naveRepository.findByUEB(idUEB);
    }

    public Nave activar(UUID idNave) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idNave, true);
    }

    public Nave desactivar(UUID idNave) {
        autorizacion().requerir(CasoUso.CU_17);
        return cambiarEstado(idNave, false);
    }

    public void eliminar(UUID idNave) {
        autorizacion().requerir(CasoUso.CU_17);
        ValidationUtils.requireUuid(idNave, "El identificador UUID de la nave es obligatorio.");
        naveRepository.findById(idNave)
                .orElseThrow(() -> new ValidacionException("No existe la nave indicada."));
        if (verificadorOperaciones.test(idNave)) {
            throw new ValidacionException(
                    "No se puede eliminar la nave porque tiene partes de producción asociados. Desactívela en su lugar.");
        }
        naveRepository.deleteById(idNave);
    }

    private Nave cambiarEstado(UUID idNave, boolean activa) {
        ValidationUtils.requireUuid(idNave, "El identificador UUID de la nave es obligatorio.");
        Nave actual = naveRepository.findById(idNave)
                .orElseThrow(() -> new ValidacionException("No existe la nave indicada."));
        Nave copia = copiar(actual);
        copia.setActiva(activa);
        return naveRepository.update(copia);
    }

    private void validar(Nave nave, boolean exigirUebActiva) {
        ValidationUtils.requireNonNull(nave, "La nave es obligatoria.");
        ValidationUtils.requireUuid(nave.getIdNave(), "El identificador UUID de la nave es obligatorio.");
        ValidationUtils.requireNotBlank(nave.getCodigo(), "El código de la nave es obligatorio.");
        ValidationUtils.requireNotBlank(nave.getNombre(), "El nombre de la nave es obligatorio.");
        validarUEBAsociada(nave.getIdUEB(), exigirUebActiva);

        Optional<Nave> porCodigo = naveRepository.findByCodigo(nave.getCodigo());
        if (porCodigo.isPresent() && !porCodigo.get().getIdNave().equals(nave.getIdNave())) {
            throw new ValidacionException("Ya existe una nave con el código '" + nave.getCodigo().trim() + "'.");
        }
    }

    private void validarUEBAsociada(UUID idUEB, boolean exigirActiva) {
        ValidationUtils.requireUuid(idUEB, "La UEB asociada a la nave es obligatoria.");
        UEB ueb = uebRepository.findById(idUEB)
                .orElseThrow(() -> new ValidacionException("La UEB asociada a la nave no existe."));
        if (exigirActiva && !ueb.isActiva()) {
            throw new ValidacionException("No se puede asociar la nave a una UEB desactivada.");
        }
    }

    private Nave copiar(Nave actual) {
        return new Nave(actual.getIdNave(), actual.getIdUEB(), actual.getNombre(),
                actual.getCodigo(), actual.isActiva());
    }
}
