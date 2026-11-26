package cu.pican.model.repository.memory;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.model.repository.ImportacionReferenciasRepository;

import java.util.Objects;

/** Implementación en memoria de la escritura controlada de CU-18. */
public class ImportacionReferenciasRepositoryMemory implements ImportacionReferenciasRepository {

    private final IndicadorHistoricoRepositoryMemory indicadores;
    private final MetaEstrategicaRepositoryMemory metas;

    public ImportacionReferenciasRepositoryMemory(IndicadorHistoricoRepositoryMemory indicadores,
                                                  MetaEstrategicaRepositoryMemory metas) {
        this.indicadores = Objects.requireNonNull(indicadores, "indicadores");
        this.metas = Objects.requireNonNull(metas, "metas");
    }

    @Override
    public boolean existeIndicador(TablaHistorica tabla, String indicador, int anio) {
        return indicadores.findByTablaAndAnio(tabla, anio).stream()
                .anyMatch(fila -> Objects.equals(indicador, fila.getIndicador()));
    }

    @Override
    public boolean existeMeta(String indicador) {
        return !metas.findByIndicador(indicador).isEmpty();
    }

    @Override
    public boolean guardarIndicadorSiAusente(IndicadorHistorico indicador) {
        if (indicador == null || existeIndicador(indicador.getTabla(), indicador.getIndicador(), indicador.getAnio())) {
            return false;
        }
        indicadores.guardar(indicador);
        return true;
    }

    @Override
    public boolean guardarMetaSiAusente(MetaEstrategica meta) {
        if (meta == null || existeMeta(meta.getIndicador())) {
            return false;
        }
        metas.guardar(meta);
        return true;
    }
}
