package cu.pican.view.components;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BorderFactory;

/**
 * Grafico de barras sencillo (Java2D, sin dependencias).
 * Solo representa los periodos que efectivamente tienen dato; no completa anios ausentes con ceros.
 * Muestra nombre del indicador, periodos, unidad y fuente.
 */
public class BarChartPanel extends JPanel {

    /** Barra individual: etiqueta del periodo y valor informado. */
    public static final class Barra {
        private final String etiqueta;
        private final BigDecimal valor;

        public Barra(String etiqueta, BigDecimal valor) {
            this.etiqueta = etiqueta;
            this.valor = valor;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        public BigDecimal getValor() {
            return valor;
        }
    }

    private final String titulo;
    private final String unidad;
    private final String fuente;
    private final List<Barra> barras;

    public BarChartPanel(String titulo, String unidad, String fuente, List<Barra> barras) {
        this.titulo = titulo == null ? "" : titulo;
        this.unidad = unidad == null ? "" : unidad;
        this.fuente = fuente == null ? "" : fuente;
        this.barras = barras == null ? new ArrayList<>() : new ArrayList<>(barras);
        setLayout(new BorderLayout());
        setBackground(UITheme.CONTENT_BG);
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UITheme.BORDER), "Gráfico: " + this.titulo));

        JLabel encabezado = new JLabel("Unidad: " + this.unidad + "    |    Fuente: " + this.fuente);
        encabezado.setFont(UITheme.SMALL_FONT);
        encabezado.setForeground(UITheme.MUTED_TEXT);
        add(encabezado, BorderLayout.NORTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (barras.isEmpty()) {
                g2.setColor(UITheme.MUTED_TEXT);
                g2.drawString("No hay valores informados para el indicador seleccionado.", 16, h / 2);
                return;
            }

            BigDecimal max = BigDecimal.ZERO;
            for (Barra barra : barras) {
                if (barra.getValor() != null && barra.getValor().compareTo(max) > 0) {
                    max = barra.getValor();
                }
            }
            if (max.compareTo(BigDecimal.ZERO) == 0) {
                max = BigDecimal.ONE;
            }

            int top = 44;
            int bottom = h - 34;
            int left = 46;
            int right = w - 16;
            int areaH = Math.max(10, bottom - top);
            int areaW = Math.max(10, right - left);

            g2.setColor(new Color(235, 239, 243));
            g2.fillRect(left, top, areaW, areaH);
            g2.setColor(UITheme.MUTED_TEXT);
            g2.drawString(max.toPlainString(), 6, top + 4);
            g2.drawString("0", left - 12, bottom + 4);

            g2.setColor(UITheme.PRIMARY);
            int n = barras.size();
            int slot = areaW / n;
            int barW = Math.max(8, Math.min(42, slot - 12));
            FontMetrics fm = g2.getFontMetrics(UITheme.SMALL_FONT);
            for (int i = 0; i < n; i++) {
                Barra barra = barras.get(i);
                BigDecimal valor = barra.getValor() == null ? BigDecimal.ZERO : barra.getValor();
                int alto = valor.signum() == 0 ? 0
                        : Math.max(2, valor.multiply(BigDecimal.valueOf(areaH)).divide(max, 0, RoundingMode.HALF_UP).intValue());
                int x = left + i * slot + (slot - barW) / 2;
                int y = bottom - alto;
                if (alto > 0) {
                    g2.fillRect(x, y, barW, alto);
                }
                g2.setColor(UITheme.TEXT);
                String valorTxt = barra.getValor() == null ? "sin dato" : barra.getValor().toPlainString();
                g2.drawString(valorTxt, x - 4, Math.max(top + 12, y - 4));
                String etiqueta = barra.getEtiqueta();
                int anchoTxt = fm.stringWidth(etiqueta);
                g2.drawString(etiqueta, x + (barW - anchoTxt) / 2, bottom + 16);
                g2.setColor(UITheme.PRIMARY);
            }

            g2.setColor(UITheme.MUTED_TEXT);
            g2.drawString("Períodos informados únicamente; no se representan años inexistentes.", left, h - 6);
        } finally {
            g2.dispose();
        }
    }
}