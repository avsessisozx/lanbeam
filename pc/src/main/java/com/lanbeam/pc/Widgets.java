package com.lanbeam.pc;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/** Small custom-painted building blocks: cards, buttons, progress bar and icons. */
final class Widgets {
    private Widgets() {}

    static Graphics2D g2(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    /** Rounded white (or dark) panel with a thin border. */
    static class Card extends JPanel {
        private final int arc;
        private final boolean header;

        Card(int pad) { this(pad, 18, false); }

        Card(int pad, int arc, boolean header) {
            this.arc = arc;
            this.header = header;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = g2(gr);
            int w = getWidth(), h = getHeight();
            if (header) {
                g.setPaint(new GradientPaint(0, 0, Theme.HEADER1, w, h, Theme.HEADER2));
                g.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            } else {
                g.setColor(Theme.CARD);
                g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc));
                g.setColor(Theme.STROKE);
                g.setStroke(new BasicStroke(1f));
                g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 2, h - 2, arc, arc));
            }
            g.dispose();
        }
    }

    enum Kind { PRIMARY, TONAL, TEXT, HEADER }

    static class Btn extends JButton {
        private final Kind kind;

        Btn(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.font(kind == Kind.PRIMARY ? 14f : 13f, java.awt.Font.BOLD));
            int v = kind == Kind.PRIMARY ? 11 : kind == Kind.TEXT ? 6 : 8;
            int hpad = kind == Kind.PRIMARY ? 22 : kind == Kind.TEXT ? 8 : 14;
            setBorder(BorderFactory.createEmptyBorder(v, hpad, v, hpad));
            setForeground(fg());
        }

        private Color fg() {
            switch (kind) {
                case PRIMARY: return Theme.ON_PRIMARY;
                case TONAL: return Theme.ON_TONAL;
                case HEADER: return Color.WHITE;
                default: return Theme.PRIMARY;
            }
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, kind == Kind.TEXT ? 30 : 36));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = g2(g);
            boolean hot = getModel().isRollover() && isEnabled();
            boolean down = getModel().isPressed() && isEnabled();
            Color bg = null;
            switch (kind) {
                case PRIMARY: bg = Theme.PRIMARY; break;
                case TONAL: bg = Theme.TONAL; break;
                case HEADER: bg = new Color(255, 255, 255, hot ? 70 : 45); break;
                default: if (hot) bg = Theme.TONAL;
            }
            if (bg != null) {
                if (kind != Kind.HEADER && (hot || down)) bg = down ? darker(bg, 0.86f) : darker(bg, 0.94f);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            }
            g2.dispose();
            setForeground(isEnabled() ? fg() : Theme.DIM);
            super.paintComponent(g);
        }

        private static Color darker(Color c, float f) {
            return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f), c.getAlpha());
        }
    }

    /** Rounded progress bar, value 0..1000. */
    static class Bar extends JComponent {
        private int value;
        private Color color = Theme.PRIMARY;

        Bar() { setPreferredSize(new Dimension(100, 8)); }

        void set(int v, Color c) { value = Math.max(0, Math.min(1000, v)); color = c; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = g2(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(Theme.TRACK);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            int fw = Math.max(value == 0 ? 0 : h, w * value / 1000);
            if (fw > 0) {
                g2.setColor(color);
                g2.fill(new RoundRectangle2D.Float(0, 0, fw, h, h, h));
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ icons (vector, no emoji fonts needed)

    /** Round tonal avatar holding a monitor or a phone. */
    static void deviceIcon(Graphics2D g, int x, int y, int d, boolean pc, boolean selected) {
        g.setColor(selected ? Theme.PRIMARY : Theme.TONAL);
        g.fill(new Ellipse2D.Float(x, y, d, d));
        g.setColor(selected ? Theme.ON_PRIMARY : Theme.ON_TONAL);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float cx = x + d / 2f, cy = y + d / 2f;
        if (pc) {
            g.draw(new RoundRectangle2D.Float(cx - 9, cy - 8, 18, 12, 3, 3));
            g.drawLine((int) cx, (int) cy + 4, (int) cx, (int) cy + 8);
            g.drawLine((int) cx - 5, (int) cy + 8, (int) cx + 5, (int) cy + 8);
        } else {
            g.draw(new RoundRectangle2D.Float(cx - 5, cy - 9, 10, 18, 3, 3));
            g.drawLine((int) cx - 1, (int) cy + 6, (int) cx + 1, (int) cy + 6);
        }
    }

    static void fileIcon(Graphics2D g, int x, int y, boolean folder, boolean selected) {
        g.setColor(selected ? Theme.PRIMARY : Theme.ON_TONAL);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (folder) {
            g.draw(new RoundRectangle2D.Float(x, y + 3, 20, 14, 3, 3));
            g.drawLine(x + 1, y + 3, x + 6, y);
            g.drawLine(x + 6, y, x + 9, y + 3);
        } else {
            g.draw(new RoundRectangle2D.Float(x + 3, y, 14, 18, 3, 3));
            g.drawLine(x + 6, y + 7, x + 14, y + 7);
            g.drawLine(x + 6, y + 11, x + 14, y + 11);
        }
    }

    /** Big "drop here" icon: tray with a down arrow. */
    static void dropIcon(Graphics2D g, int cx, int cy) {
        g.setColor(Theme.PRIMARY);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx, cy - 16, cx, cy + 6);
        g.drawLine(cx - 9, cy - 3, cx, cy + 6);
        g.drawLine(cx + 9, cy - 3, cx, cy + 6);
        g.drawLine(cx - 16, cy + 8, cx - 16, cy + 16);
        g.drawLine(cx - 16, cy + 16, cx + 16, cy + 16);
        g.drawLine(cx + 16, cy + 16, cx + 16, cy + 8);
    }
}
