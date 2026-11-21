package cu.pican.repository.memory;

import cu.pican.model.ControlRestauracion;
import cu.pican.repository.ControlRestauracionRepository;

/** Control SIN-13 en memoria: suficiente para arneses MEMORY, no es persistencia real. */
public final class ControlRestauracionRepositoryMemory implements ControlRestauracionRepository {

    private volatile ControlRestauracion control;

    @Override
    public synchronized ControlRestauracion obtener(String idInstalacion) {
        if (control == null) {
            control = ControlRestauracion.desbloqueado(idInstalacion);
        }
        return control;
    }

    @Override
    public synchronized void guardar(ControlRestauracion control) {
        this.control = control;
    }
}
