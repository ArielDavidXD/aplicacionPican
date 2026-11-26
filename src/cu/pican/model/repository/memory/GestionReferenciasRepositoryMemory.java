package cu.pican.model.repository.memory;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.model.repository.GestionReferenciasRepository;

import java.util.Objects;

/** Implementación en memoria de la escritura CU-17 sobre las referencias oficiales. */
public class GestionReferenciasRepositoryMemory implements GestionReferenciasRepository {

    private final IndicadorHistoricoRepositoryMemory indicadores;
    private final MetaEstrategicaRepositoryMemory metas;

    public GestionReferenciasRepositoryMemory(IndicadorHistoricoRepositoryMemory indicadores,
                                              MetaEstrategicaRepositoryMemory metas) {
        this.indicadores = Objects.requireNonNull(indicadores, "indicadores");
        this.metas = Objects.requireNonNull(metas, "metas");
    }

    @Override
    public boolean existeIndicador(TablaHistorica tabla, String indicador, int anio, String idExcluido) {
        return indicadores.findByTablaAndAnio(tabla, anio).stream()
                .anyMatch(fila -> Objects.equals(indicador, fila.getIndicador())
                        && !Objects.equals(idExcluido, fila.getId()));
    }

    @Override
    public boolean existeMeta(String indicador, String idExcluido) {
        return metas.findByIndicador(indicador).stream()
                .anyMatch(fila -> !Objects.equals(idExcluido, fila.getId()));
    }

    @Override
    public IndicadorHistorico guardarIndicador(IndicadorHistorico indicador) {
        Objects.requireNonNull(indicador, "indicador");
        indicadores.guardar(indicador);
        return indicador;
    }

    @Override
    public MetaEstrategica guardarMeta(MetaEstrategica meta) {
        Objects.requireNonNull(meta, "meta");
        metas.guardar(meta);
        return meta;
    }
}
