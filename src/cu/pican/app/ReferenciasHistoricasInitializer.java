package cu.pican.app;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.model.TablaHistorica;
import cu.pican.repository.memory.IndicadorHistoricoRepositoryMemory;
import cu.pican.repository.memory.MetaEstrategicaRepositoryMemory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Carga controlada de las cuatro tablas históricas oficiales del PICAN.
 * Los valores son exactamente los proporcionados como referencia; no se calculan ni se inventan datos.
 * La carga es idempotente: repetir la inicialización no duplica registros.
 */
public final class ReferenciasHistoricasInitializer {

    public static final String FUENTE_HISTORICA = "Información histórica oficial PICAN";
    public static final String FUENTE_META = "Meta estratégica oficial PICAN (no cuota anual)";

    private ReferenciasHistoricasInitializer() {
    }

    public static void cargar(IndicadorHistoricoRepositoryMemory indicadores,
                              MetaEstrategicaRepositoryMemory metas) {
        indicadores.cargarSiVacio(buildIndicadores());
        metas.cargarSiVacio(buildMetas());
    }

    /** Filas oficiales ya aprobadas. Expuestas al importador CU-18 como referencia, sin cálculo nuevo. */
    public static List<IndicadorHistorico> buildIndicadores() {
        List<IndicadorHistorico> out = new ArrayList<>();

        // Tabla 1. Infraestructura (2015-2019)
        addFila(out, "T1", TablaHistorica.INFRAESTRUCTURA, "Cantidad Naves", "naves",
                new int[]{2015, 2016, 2017, 2018, 2019},
                new String[]{"221", "221", "221", "221", "222"});
        addFila(out, "T1", TablaHistorica.INFRAESTRUCTURA, "Naves Ponedoras", "naves",
                new int[]{2015, 2016, 2017, 2018, 2019},
                new String[]{"104", "104", "104", "104", "105"});
        addFila(out, "T1", TablaHistorica.INFRAESTRUCTURA, "Equipos Bombeo", "equipos",
                new int[]{2015, 2016, 2017, 2018, 2019},
                new String[]{"31", "31", "31", "31", "31"});
        addFila(out, "T1", TablaHistorica.INFRAESTRUCTURA, "Tractores", "tractores",
                new int[]{2015, 2016, 2017, 2018, 2019},
                new String[]{"41", "42", "43", "43", "44"});
        addFila(out, "T1", TablaHistorica.INFRAESTRUCTURA, "Camiones", "camiones",
                new int[]{2015, 2016, 2017, 2018, 2019},
                new String[]{"56", "58", "58", "58", "58"});

        // Tabla 2. Eficiencia productiva (2015, 2017 y 2019)
        int[] aniosEficiencia = {2015, 2017, 2019};
        addFila(out, "T2", TablaHistorica.EFICIENCIA_PRODUCTIVA, "Aprovechamiento Capacidades (%)", "%",
                aniosEficiencia, new String[]{"68.6", "69.5", "67.9"});
        addFila(out, "T2", TablaHistorica.EFICIENCIA_PRODUCTIVA, "Producción Total Huevos (MM/U)", "MM/U",
                aniosEficiencia, new String[]{"197.4", "198.4", "211.9"});
        addFila(out, "T2", TablaHistorica.EFICIENCIA_PRODUCTIVA, "Gallinas Promedio (Miles)", "miles",
                aniosEficiencia, new String[]{"583.4", "591.2", "615.9"});
        addFila(out, "T2", TablaHistorica.EFICIENCIA_PRODUCTIVA, "Productividad Huevo/Ave (U)", "U",
                aniosEficiencia, new String[]{"251.28", "246.37", "259.62"});
        addFila(out, "T2", TablaHistorica.EFICIENCIA_PRODUCTIVA, "Conversión Pienso (Kg/dc)", "Kg/dc",
                aniosEficiencia, new String[]{"1.61", "1.61", "1.54"});
        addFila(out, "T2", TablaHistorica.EFICIENCIA_PRODUCTIVA, "Viabilidad Masa (%)", "%",
                aniosEficiencia, new String[]{"71.52", "62.32", "74.44"});
        addFila(out, "T2", TablaHistorica.EFICIENCIA_PRODUCTIVA, "Producción Total Carne (t)", "t",
                aniosEficiencia, new String[]{"832.2", "1086.0", "1208.8"});

        // Tabla 3. Destinos y proyecciones (2021, 2025 y 2030)
        int[] aniosProyeccion = {2021, 2025, 2030};
        addFila(out, "T3", TablaHistorica.DESTINOS_PROYECCIONES, "Huevos ponedoras comerciales: Total", "MM/U",
                aniosProyeccion, new String[]{"153.6", "173.3", "182.2"});
        addFila(out, "T3", TablaHistorica.DESTINOS_PROYECCIONES,
                "Huevos ponedoras comerciales: Balance Nacional", "MM/U",
                aniosProyeccion, new String[]{"119.4", "139.1", "148.0"});
        addFila(out, "T3", TablaHistorica.DESTINOS_PROYECCIONES,
                "Huevos ponedoras comerciales: Consumo Social Salud/Educación", "MM/U",
                aniosProyeccion, new String[]{"9.6", "9.6", "9.6"});
        addFila(out, "T3", TablaHistorica.DESTINOS_PROYECCIONES,
                "Huevos ponedoras comerciales: Turismo/Mercado MLC", "MM/U",
                aniosProyeccion, new String[]{"21.4", "21.4", "21.4"});
        addFila(out, "T3", TablaHistorica.DESTINOS_PROYECCIONES, "Huevos Marrones Alternativa (MM/U)", "MM/U",
                aniosProyeccion, new String[]{"30.4", "34.2", "37.0"});
        addFila(out, "T3", TablaHistorica.DESTINOS_PROYECCIONES,
                "Carne Avícola Especializada + Alternativa (t)", "t",
                aniosProyeccion, new String[]{"1052.7", "4901.7", "10051.9"});
        addFila(out, "T3", TablaHistorica.DESTINOS_PROYECCIONES, "Autoabastecimiento Municipal (t)", "t",
                aniosProyeccion, new String[]{"1002.7", "4801.7", "9862.0"});

        return out;
    }

    private static void addFila(List<IndicadorHistorico> out, String prefijo, TablaHistorica tabla,
                                String indicador, String unidad, int[] anios, String[] valores) {
        for (int i = 0; i < anios.length; i++) {
            out.add(new IndicadorHistorico(
                    prefijo + "-" + anios[i] + "-" + i + "-" + indicador.hashCode(),
                    tabla, indicador, anios[i], new BigDecimal(valores[i]), unidad, FUENTE_HISTORICA));
        }
    }

    /** Metas oficiales ya aprobadas. Expuestas al importador CU-18 como referencia, sin cálculo nuevo. */
    public static List<MetaEstrategica> buildMetas() {
        List<MetaEstrategica> out = new ArrayList<>();
        out.add(new MetaEstrategica("T4-HUEVOS", "Huevos (millones)",
                new BigDecimal("211.9"), new BigDecimal("241.4"), new BigDecimal("13.92"),
                "millones", FUENTE_META));
        out.add(new MetaEstrategica("T4-CARNE", "Carne (toneladas)",
                new BigDecimal("1208.8"), new BigDecimal("10051.9"), new BigDecimal("731.56"),
                "t", FUENTE_META));
        return out;
    }
}
