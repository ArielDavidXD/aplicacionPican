package cu.pican.controller.importacion;

import cu.pican.controller.app.ReferenciasHistoricasInitializer;
import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Referencia oficial reutilizada por el validador; no calcula ni crea datos nuevos. */
public final class PlantillaOficial {

    private PlantillaOficial() {
    }

    public static Map<String, IndicadorHistorico> indicadores(TablaHistorica tabla) {
        Map<String, IndicadorHistorico> resultado = new LinkedHashMap<>();
        for (IndicadorHistorico fila : ReferenciasHistoricasInitializer.buildIndicadores()) {
            if (fila.getTabla() == tabla) {
                resultado.put(claveIndicador(fila.getIndicador(), fila.getAnio()), fila);
            }
        }
        return resultado;
    }

    public static Map<String, MetaEstrategica> metas() {
        Map<String, MetaEstrategica> resultado = new LinkedHashMap<>();
        for (MetaEstrategica fila : ReferenciasHistoricasInitializer.buildMetas()) {
            resultado.put(claveMeta(fila.getIndicador()), fila);
        }
        return resultado;
    }

    public static String claveIndicador(String indicador, int anio) {
        return indicador + "|" + anio;
    }

    public static String claveMeta(String indicador) {
        return "T4|" + indicador;
    }

    /** Comprueba la fila de encabezado antes de leer datos. Error estructural: detiene sin escribir. */
    public static void validarCabeceras(TipoImportacion tipo, FilaExcel cabecera) {
        if (cabecera == null) {
            throw new ImportacionException("El archivo no contiene una fila de encabezado.");
        }
        List<String> encontradas = sinVaciosFinales(cabecera.getCeldas());
        List<String> esperadas = tipo.getCabeceras();
        if (!esperadas.equals(encontradas)) {
            throw new ImportacionException("Las columnas no coinciden con la plantilla de "
                    + tipo.getEtiqueta() + ". Se esperaban: " + String.join(", ", esperadas)
                    + ". Se encontraron: " + (encontradas.isEmpty() ? "(ninguna)" : String.join(", ", encontradas)) + ".");
        }
    }

    private static List<String> sinVaciosFinales(List<String> celdas) {
        List<String> resultado = new ArrayList<>();
        for (String celda : celdas) {
            resultado.add(celda == null ? "" : celda.trim());
        }
        while (!resultado.isEmpty() && resultado.get(resultado.size() - 1).isEmpty()) {
            resultado.remove(resultado.size() - 1);
        }
        return resultado;
    }
}
