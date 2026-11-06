package net.runelite.client.plugins.microbot.pvppresets;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Zentrale Farben & Komponenten-Fabrik für einen ruhigen, modernen Dark-Look. */
final class Theme {
    private Theme() {}

    static final Color BG       = new Color(0x16, 0x18, 0x1d);
    static final Color CARD     = new Color(0x20, 0x23, 0x2a);
    static final Color CARD_HI  = new Color(0x27, 0x2b, 0x34);
    static final Color BORDER   = new Color(0x2f, 0x34, 0x3e);
    static final Color TEXT     = new Color(0xe6, 0xe8, 0xec);
    static final Color MUTED    = new Color(0x8b, 0x93, 0xa1);
    static final Color ACCENT   = new Color(0x4f, 0x9c, 0xff);
    static final Color GOOD     = new Color(0x3d, 0xd6, 0x8c);
    static final Color DANGER   = new Color(0xff, 0x5f, 0x5f);

    static final Font TITLE = new Font("SansSerif", Font.BOLD, 13);
    static final Font BODY  = new Font("SansSerif", Font.PLAIN, 11);
    static final Font SMALL = new Font("SansSerif", Font.PLAIN, 10);

    static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    static JButton button(String text, Color accent) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = getModel().isPressed() ? accent.darker() : getModel().isRollover() ? accent : CARD_HI;
                g2.setColor(base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(getModel().isRollover() ? Color.WHITE : TEXT);
                g2.setFont(BODY);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(70, 24));
        return b;
    }

    /** "ICE_BARRAGE" -> "Ice Barrage", "NONE" -> "—". */
    static String pretty(Object v) {
        String s = String.valueOf(v);
        if ("NONE".equals(s)) return "—";
        if (!s.equals(s.toUpperCase())) return s;
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> c = new JComboBox<>(items);
        c.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                return super.getListCellRendererComponent(l, pretty(v), i, sel, foc);
            }
        });
        // feste, schmale Breite – sonst sprengt der längste Eintrag das 225px-Panel
        c.setPrototypeDisplayValue(null);
        c.setPreferredSize(new Dimension(110, 22));
        c.setMinimumSize(new Dimension(60, 22));
        c.setFont(BODY);
        c.setBackground(CARD_HI);
        c.setForeground(TEXT);
        c.setFocusable(false);
        return c;
    }

    /** Scroll-Inhalt, der immer genau so breit ist wie das Panel (kein horizontales Scrollen). */
    static final class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Abgerundete Karte. */
    static JPanel card() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }
}
