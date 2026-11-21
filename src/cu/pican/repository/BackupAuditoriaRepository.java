package cu.pican.repository;

import cu.pican.model.BackupAuditoria;
import java.util.List;

/** Historial de operaciones CU-19. Solo BackupService puede escribir en él. */
public interface BackupAuditoriaRepository {
    void guardar(BackupAuditoria evento);
    List<BackupAuditoria> listar();
}
