package cu.pican.model.repository.memory;

import cu.pican.model.BackupAuditoria;
import cu.pican.model.repository.BackupAuditoriaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Auditoría en memoria para el modo de demostración y arneses sin PostgreSQL. */
public final class BackupAuditoriaRepositoryMemory implements BackupAuditoriaRepository {
    private final List<BackupAuditoria> eventos = new CopyOnWriteArrayList<>();

    @Override public void guardar(BackupAuditoria evento) { if (evento != null) eventos.add(evento); }
    @Override public List<BackupAuditoria> listar() { return new ArrayList<>(eventos); }
}
