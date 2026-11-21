package cu.pican.importacion;

import cu.pican.model.IndicadorHistorico;
import cu.pican.model.MetaEstrategica;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resultado de la vista previa y de la confirmación de CU-18. */
public final class ResultadoImportacion {

    private final String nombreArchivo;
    private final String hoja;
    private final TipoImportacion tipo;
    private final LocalDateTime preparadoEn;
    private final List<ResultadoFila> detalles;
    private final List<IndicadorHistorico> indicadoresValidos;
    private final List<MetaEstrategica> metasValidas;

    private String usuario;
    private int filasLeidas;
    private int validas;
    private int rechazadas;
    private int repetidas;
    private int escritas;
    private boolean confirmada;

    public ResultadoImportacion(String nombreArchivo, String hoja, TipoImportacion tipo,
                                List<ResultadoFila> detalles, List<IndicadorHistorico> indicadoresValidos,
                                List<MetaEstrategica> metasValidas) {
        this.nombreArchivo = nombreArchivo;
        this.hoja = hoja;
        this.tipo = tipo;
        this.preparadoEn = LocalDateTime.now();
        this.detalles = Collections.unmodifiableList(new ArrayList<>(detalles));
        this.indicadoresValidos = Collections.unmodifiableList(new ArrayList<>(indicadoresValidos));
        this.metasValidas = Collections.unmodifiableList(new ArrayList<>(metasValidas));
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public String getHoja() {
        return hoja;
    }

    public TipoImportacion getTipo() {
        return tipo;
    }

    public LocalDateTime getPreparadoEn() {
        return preparadoEn;
    }

    public String getUsuario() {
        return usuario;
    }

    public List<ResultadoFila> getDetalles() {
        return detalles;
    }

    public List<IndicadorHistorico> getIndicadoresValidos() {
        return indicadoresValidos;
    }

    public List<MetaEstrategica> getMetasValidas() {
        return metasValidas;
    }

    public int getFilasLeidas() {
        return filasLeidas;
    }

    public int getValidas() {
        return validas;
    }

    public int getRechazadas() {
        return rechazadas;
    }

    public int getRepetidas() {
        return repetidas;
    }

    public int getEscritas() {
        return escritas;
    }

    public boolean isConfirmada() {
        return confirmada;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public void registrar(ResultadoFila fila) {
        filasLeidas++;
        switch (fila.getEstado()) {
            case VALIDA -> validas++;
            case RECHAZADA -> rechazadas++;
            case REPETIDA -> repetidas++;
        }
    }

    public void registrarRepetidaEnConfirmacion() {
        if (validas > 0) {
            validas--;
            repetidas++;
        }
    }

    public void marcarConfirmada(int escritas) {
        this.confirmada = true;
        this.escritas = escritas;
    }

    public String resumen() {
        return "Archivo: " + nombreArchivo + " · Tipo: " + tipo.getEtiqueta()
                + " · Válidas: " + validas + " · Rechazadas: " + rechazadas
                + " · Repetidas: " + repetidas
                + (confirmada ? " · Cargadas: " + escritas : " · Vista previa (sin guardar)");
    }
}
