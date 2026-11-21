package cu.pican.view.components;

import java.awt.Color;
import java.awt.Font;

/**
 * Constantes visuales reutilizables para mantener consistencia en la interfaz.
 */
public final class UITheme {
    private UITheme() {}

    public static final Color PRIMARY = new Color(31, 78, 121);
    public static final Color PRIMARY_DARK = new Color(24, 60, 93);
    public static final Color SIDEBAR_BG = new Color(245, 247, 250);
    public static final Color CONTENT_BG = Color.WHITE;
    public static final Color BORDER = new Color(220, 225, 230);
    public static final Color TEXT = new Color(33, 37, 41);
    public static final Color MUTED_TEXT = new Color(90, 98, 107);

    public static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 20);
    public static final Font SECTION_FONT = new Font("SansSerif", Font.BOLD, 16);
    public static final Font NORMAL_FONT = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 12);
}
