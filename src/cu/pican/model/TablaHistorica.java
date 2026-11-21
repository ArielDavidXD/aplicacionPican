package cu.pican.model;

/**
 * Identifica las cuatro tablas de información histórica oficial de PICAN.
 * No son datos operativos y no deben modificarse desde el módulo de análisis.
 */
public enum TablaHistorica {
    INFRAESTRUCTURA("Tabla 1. Infraestructura (2015-2019)",
            "Referencia histórica oficial. No se modifica desde el sistema."),
    EFICIENCIA_PRODUCTIVA("Tabla 2. Eficiencia productiva (2015, 2017 y 2019)",
            "Referencia histórica oficial. Períodos informados: 2015, 2017 y 2019."),
    DESTINOS_PROYECCIONES("Tabla 3. Destinos y proyecciones (2021, 2025 y 2030)",
            "Referencia histórica y de planificación. Los subtotales y renglones NO son una descomposición "
                    + "matemática completa: no se inventan destinos para las diferencias."),
    METAS_ESTRATEGICAS("Tabla 4. Metas estratégicas",
            "Metas estratégicas aprobadas. NO constituyen cuotas anuales ni por UEB.");

    private final String titulo;
    private final String nota;

    TablaHistorica(String titulo, String nota) {
        this.titulo = titulo;
        this.nota = nota;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getNota() {
        return nota;
    }
}
