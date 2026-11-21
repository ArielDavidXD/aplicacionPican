package cu.pican.importacion;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;
import cu.pican.repository.ImportacionReferenciasRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Valida filas contra las referencias oficiales ya aprobadas.
 *
 * No transforma valores: solo interpreta la representación decimal con coma o punto.
 * Una fila con la misma clave pero contenido distinto se rechaza sin sobrescribir,
 * tal como exige la regla pendiente de acuerdo con PICAN documentada en el DCUS.
 */
public final class ValidadorImportacion {

    private ValidadorImportacion() {
    }

    public static ResultadoImportacion validar(TipoImportacion tipo, ArchivoExcel archivo,
                                               ImportacionReferenciasRepository destino) {
        if (tipo == null) {
            throw new ImportacionException("Debe seleccionar un tipo de datos.");
        }
        if (archivo == null) {
            throw new ImportacionException("No hay un archivo para validar.");
        }
        if (destino == null) {
            throw new ImportacionException("No hay un destino de datos configurado.");
        }
        List<FilaExcel> filas = archivo.getFilas();
        if (filas.isEmpty()) {
            throw new ImportacionException("El archivo no contiene una fila de encabezado.");
        }

        // Error estructural: se lanza antes de cualquier escritura.
        PlantillaOficial.validarCabeceras(tipo, filas.get(0));

        Map<String, IndicadorHistorico> oficialesIndicador = tipo.esMeta()
                ? Map.of() : PlantillaOficial.indicadores(tipo.getTabla());
        Map<String, MetaEstrategica> oficialesMeta = tipo.esMeta() ? PlantillaOficial.metas() : Map.of();

        Set<String> clavesEnArchivo = new HashSet<>();
        List<ResultadoFila> detalles = new ArrayList<>();
        List<IndicadorHistorico> indicadoresValidos = new ArrayList<>();
        List<MetaEstrategica> metasValidas = new ArrayList<>();

        for (int i = 1; i < filas.size(); i++) {
            FilaExcel fila = filas.get(i);
            if (estaVacia(fila)) {
                detalles.add(new ResultadoFila(fila.getNumero(), EstadoFila.RECHAZADA, "Fila vacía."));
                continue;
            }
            ResultadoFila resultado = tipo.esMeta()
                    ? validarMeta(fila, oficialesMeta, clavesEnArchivo, destino, metasValidas)
                    : validarIndicador(fila, tipo, oficialesIndicador, clavesEnArchivo, destino, indicadoresValidos);
            detalles.add(resultado);
        }

        ResultadoImportacion resultado = new ResultadoImportacion(
                archivo.getNombre(), archivo.getHoja(), tipo, detalles, indicadoresValidos, metasValidas);
        for (ResultadoFila detalle : detalles) {
            resultado.registrar(detalle);
        }
        return resultado;
    }

    private static ResultadoFila validarIndicador(FilaExcel fila, TipoImportacion tipo,
                                                  Map<String, IndicadorHistorico> oficiales,
                                                  Set<String> clavesEnArchivo,
                                                  ImportacionReferenciasRepository destino,
                                                  List<IndicadorHistorico> validos) {
        String indicador = fila.celda(0);
        int anio;
        BigDecimal valor;
        String unidad = fila.celda(3);
        try {
            anio = parseEntero(fila.celda(1));
        } catch (RuntimeException ex) {
            return rechazar(fila, "El año no es un número entero válido.");
        }
        try {
            valor = parseDecimal(fila.celda(2));
        } catch (RuntimeException ex) {
            return rechazar(fila, "El valor no es un número decimal válido.");
        }

        String clave = PlantillaOficial.claveIndicador(indicador, anio);
        IndicadorHistorico esperado = oficiales.get(clave);
        if (esperado == null) {
            return rechazar(fila, "El indicador o el año no pertenecen a " + tipo.getEtiqueta() + ".");
        }
        if (valor.compareTo(esperado.getValor()) != 0) {
            return rechazar(fila, "El valor difiere de la referencia oficial; no se sobrescribe.");
        }
        if (!Objects.equals(unidad, esperado.getUnidadMedida())) {
            return rechazar(fila, "La unidad de medida no coincide con la referencia oficial.");
        }
        if (!clavesEnArchivo.add(clave) || destino.existeIndicador(tipo.getTabla(), indicador, anio)) {
            return new ResultadoFila(fila.getNumero(), EstadoFila.REPETIDA,
                    "Ya existe una fila con la misma clave; se omite y se contabiliza.");
        }
        validos.add(esperado);
        return new ResultadoFila(fila.getNumero(), EstadoFila.VALIDA, "");
    }

    private static ResultadoFila validarMeta(FilaExcel fila, Map<String, MetaEstrategica> oficiales,
                                             Set<String> clavesEnArchivo,
                                             ImportacionReferenciasRepository destino,
                                             List<MetaEstrategica> validos) {
        String indicador = fila.celda(0);
        BigDecimal real;
        BigDecimal estimado;
        BigDecimal incremento;
        try {
            real = parseDecimal(fila.celda(1));
            estimado = parseDecimal(fila.celda(2));
            incremento = parseDecimal(fila.celda(3));
        } catch (RuntimeException ex) {
            return rechazar(fila, "Los valores de la meta no son números decimales válidos.");
        }
        String unidad = fila.celda(4);

        MetaEstrategica esperada = oficiales.get(PlantillaOficial.claveMeta(indicador));
        if (esperada == null) {
            return rechazar(fila, "El indicador no pertenece a la Tabla 4 de metas estratégicas.");
        }
        if (real.compareTo(esperada.getValorReal2019()) != 0
                || estimado.compareTo(esperada.getValorEstimado2030()) != 0
                || incremento.compareTo(esperada.getIncrementoPorcentual()) != 0) {
            return rechazar(fila, "Los valores difieren de la referencia oficial; no se sobrescribe.");
        }
        if (!Objects.equals(unidad, esperada.getUnidadMedida())) {
            return rechazar(fila, "La unidad de medida no coincide con la referencia oficial.");
        }
        String clave = PlantillaOficial.claveMeta(indicador);
        if (!clavesEnArchivo.add(clave) || destino.existeMeta(indicador)) {
            return new ResultadoFila(fila.getNumero(), EstadoFila.REPETIDA,
                    "Ya existe una fila con la misma clave; se omite y se contabiliza.");
        }
        validos.add(esperada);
        return new ResultadoFila(fila.getNumero(), EstadoFila.VALIDA, "");
    }

    private static ResultadoFila rechazar(FilaExcel fila, String motivo) {
        return new ResultadoFila(fila.getNumero(), EstadoFila.RECHAZADA, motivo);
    }

    private static boolean estaVacia(FilaExcel fila) {
        for (String celda : fila.getCeldas()) {
            if (celda != null && !celda.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static int parseEntero(String texto) {
        String limpio = texto.trim();
        if (limpio.isEmpty()) {
            throw new NumberFormatException("vacío");
        }
        BigDecimal numero = new BigDecimal(limpio);
        if (numero.stripTrailingZeros().scale() > 0) {
            throw new NumberFormatException("no entero");
        }
        return numero.intValueExact();
    }

    /** Acepta coma o punto decimal como representación; el valor numérico no se altera. */
    private static BigDecimal parseDecimal(String texto) {
        String limpio = texto.trim().replace(" ", "");
        if (limpio.isEmpty()) {
            throw new NumberFormatException("vacío");
        }
        boolean tieneComa = limpio.contains(",");
        boolean tienePunto = limpio.contains(".");
        if (tieneComa && tienePunto) {
            if (limpio.lastIndexOf(',') > limpio.lastIndexOf('.')) {
                limpio = limpio.replace(".", "").replace(',', '.');
            } else {
                limpio = limpio.replace(",", "");
            }
        } else if (tieneComa) {
            limpio = limpio.replace(',', '.');
        }
        return new BigDecimal(limpio);
    }
}
