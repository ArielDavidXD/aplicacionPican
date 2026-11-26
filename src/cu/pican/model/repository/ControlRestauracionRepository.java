package cu.pican.model.repository;

import cu.pican.model.ControlRestauracion;

/** Estado persistente del bloqueo SIN-13 posterior a una restauración. */
public interface ControlRestauracionRepository {
    ControlRestauracion obtener(String idInstalacion);
    void guardar(ControlRestauracion control);
}
