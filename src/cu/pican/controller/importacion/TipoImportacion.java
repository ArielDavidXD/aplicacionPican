package cu.pican.controller.importacion;

import cu.pican.model.TablaHistorica;

import java.util.List;

/**
 * Únicos tipos de datos definidos documentalmente para CU-18.
 *
 * Las cabeceras no son un diseño nuevo: son los campos ya existentes en las entidades
 * {@code IndicadorHistorico} y {@code MetaEstrategica}. No se añaden columnas ni claves.
 */
public enum TipoImportacion {

    TABLA_1("Tabla 1. Infraestructura (2015-2019)", TablaHistorica.INFRAESTRUCTURA,
            "indicador", "anio", "valor", "unidad_medida"),
    TABLA_2("Tabla 2. Eficiencia productiva (2015, 2017 y 2019)", TablaHistorica.EFICIENCIA_PRODUCTIVA,
            "indicador", "anio", "valor", "unidad_medida"),
    TABLA_3("Tabla 3. Destinos y proyecciones (2021, 2025 y 2030)", TablaHistorica.DESTINOS_PROYECCIONES,
            "indicador", "anio", "valor", "unidad_medida"),
    TABLA_4("Tabla 4. Metas estratégicas", null,
            "indicador", "valor_real_2019", "valor_estimado_2030", "incremento_porcentual", "unidad_medida");

    private final String etiqueta;
    private final TablaHistorica tabla;
    private final List<String> cabeceras;

    TipoImportacion(String etiqueta, TablaHistorica tabla, String... cabeceras) {
        this.etiqueta = etiqueta;
        this.tabla = tabla;
        this.cabeceras = List.of(cabeceras);
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public TablaHistorica getTabla() {
        return tabla;
    }

    public List<String> getCabeceras() {
        return cabeceras;
    }

    public boolean esMeta() {
        return tabla == null;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
