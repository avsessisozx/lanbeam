package com.lanbeam.pc;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.FontMetrics;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
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

    /** Vector icons drawn in a 16x16 box (no emoji font needed). */
    enum Ico { NONE, FILE_ADD, FOLDER_ADD, FOLDER, LINK, REFRESH, GEAR, REPORT, DOWNLOAD, UPLOAD, SEND, TRASH, COPY, CLOSE, CHECK, SPEED, DEVICES, PLUS, BROOM }

    static class Btn extends JButton {
        private final Kind kind;
        private final Ico icon;

        Btn(String text, Kind kind) { this(text, kind, Ico.NONE); }

        Btn(String text, Kind kind, Ico icon) {
            super(text);
            this.kind = kind;
            this.icon = icon;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.font(kind == Kind.PRIMARY ? 14f : 13f, java.awt.Font.BOLD));
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        }

        private Color fg() {
            if (!isEnabled()) return Theme.DIM;
            switch (kind) {
                case PRIMARY: return Theme.ON_PRIMARY;
                case TONAL: return Theme.ON_TONAL;
                case HEADER: return Color.WHITE;
                default: return Theme.PRIMARY;
            }
        }

        private int hpad() { return kind == Kind.PRIMARY ? 20 : kind == Kind.TEXT ? 10 : 14; }

        private int iconW() { return icon == Ico.NONE ? 0 : 16 + 7; }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int w = hpad() * 2 + iconW() + fm.stringWidth(getText());
            return new Dimension(w, kind == Kind.TEXT ? 32 : kind == Kind.PRIMARY ? 40 : 38);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = g2(g);
            boolean on = isEnabled();
            boolean hot = getModel().isRollover() && on;
            boolean down = getModel().isPressed() && on;
            int w = getWidth(), h = getHeight();
            Color bg = null;
            switch (kind) {
                case PRIMARY: bg = on ? Theme.PRIMARY : Theme.STROKE; break;
                case TONAL: bg = Theme.TONAL; break;
                case HEADER: bg = new Color(255, 255, 255, hot ? 80 : 50); break;
                default: if (hot) bg = Theme.TONAL;
            }
            if (bg != null) {
                if (kind != Kind.HEADER && (hot || down)) bg = down ? darker(bg, 0.86f) : darker(bg, 0.94f);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            }
            if (kind == Kind.TONAL && on) {
                g2.setColor(Theme.STROKE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 14, 14));
            }
            if (isFocusOwner() && kind != Kind.HEADER) {
                g2.setColor(Theme.PRIMARY);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 14, 14));
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(getText());
            int total = iconW() + tw;
            int x = (w - total) / 2;
            g2.setColor(fg());
            if (icon != Ico.NONE) {
                ico(g2, icon, x, (h - 16) / 2f, 16f, fg());
                x += iconW();
            }
            g2.drawString(getText(), x, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }

        private static Color darker(Color c, float f) {
            return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f), c.getAlpha());
        }
    }

    /** Rounded progress bar, value 0..1000. */
    static class Bar extends JComponent {
        private int value;
        private Color color = Theme.PRIMARY;

        Bar() { setPreferredSize(new Dimension(100, 10)); }

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

    // ------------------------------------------------------------------ icon set

    /** Draws icon k inside a box of size s at (x, y); the drawing is authored on a 16x16 grid. */
    static void ico(Graphics2D g0, Ico k, float x, float y, float s, Color c) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.scale(s / 16f, s / 16f);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (k) {
            case FILE_ADD:
                g.draw(new RoundRectangle2D.Float(2.5f, 1f, 10f, 14f, 2.5f, 2.5f));
                g.drawLine(8, 6, 8, 11);
                g.drawLine(5, 8, 11, 8);
                break;
            case FOLDER_ADD:
                folder(g);
                g.drawLine(8, 7, 8, 12);
                g.drawLine(5, 9, 11, 9);
                break;
            case FOLDER:
                folder(g);
                break;
            case LINK:
                g.draw(new RoundRectangle2D.Float(1f, 5f, 8.5f, 6f, 6f, 6f));
                g.draw(new RoundRectangle2D.Float(6.5f, 5f, 8.5f, 6f, 6f, 6f));
                break;
            case REFRESH: {
                g.draw(new Arc2D.Float(2.5f, 2.5f, 11f, 11f, 70, 280, Arc2D.OPEN));
                Path2D a = new Path2D.Float();
                a.moveTo(10.2, 0.8);
                a.lineTo(12.1, 3.6);
                a.lineTo(8.8, 4.3);
                g.draw(a);
                break;
            }
            case GEAR:
                g.draw(new Ellipse2D.Float(3.4f, 3.4f, 9.2f, 9.2f));
                g.draw(new Ellipse2D.Float(6.2f, 6.2f, 3.6f, 3.6f));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    g.drawLine((int) Math.round(8 + 5.6 * Math.cos(a)), (int) Math.round(8 + 5.6 * Math.sin(a)),
                            (int) Math.round(8 + 7.4 * Math.cos(a)), (int) Math.round(8 + 7.4 * Math.sin(a)));
                }
                break;
            case REPORT:
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(3, 9, 3, 13);
                g.drawLine(8, 3, 8, 13);
                g.drawLine(13, 6, 13, 13);
                break;
            case DOWNLOAD:
                g.drawLine(8, 2, 8, 10);
                g.drawLine(4, 6, 8, 10);
                g.drawLine(12, 6, 8, 10);
                g.drawLine(2, 11, 2, 14);
                g.drawLine(2, 14, 14, 14);
                g.drawLine(14, 14, 14, 11);
                break;
            case UPLOAD:
                g.drawLine(8, 10, 8, 2);
                g.drawLine(4, 6, 8, 2);
                g.drawLine(12, 6, 8, 2);
                g.drawLine(2, 11, 2, 14);
                g.drawLine(2, 14, 14, 14);
                g.drawLine(14, 14, 14, 11);
                break;
            case SEND:
                g.drawLine(2, 8, 13, 8);
                g.drawLine(8, 3, 13, 8);
                g.drawLine(8, 13, 13, 8);
                break;
            case TRASH:
                g.drawLine(2, 4, 14, 4);
                g.drawLine(6, 4, 6, 2);
                g.drawLine(6, 2, 10, 2);
                g.drawLine(10, 2, 10, 4);
                g.drawLine(4, 4, 5, 14);
                g.drawLine(5, 14, 11, 14);
                g.drawLine(11, 14, 12, 4);
                break;
            case BROOM:
                g.drawLine(13, 2, 7, 8);
                g.draw(new RoundRectangle2D.Float(2.5f, 8f, 7f, 6f, 2f, 2f));
                g.drawLine(5, 10, 5, 13);
                g.drawLine(7, 10, 7, 13);
                break;
            case COPY:
                g.draw(new RoundRectangle2D.Float(6f, 6f, 8f, 8f, 2f, 2f));
                g.draw(new RoundRectangle2D.Float(2f, 2f, 8f, 8f, 2f, 2f));
                break;
            case CLOSE:
                g.drawLine(4, 4, 12, 12);
                g.drawLine(12, 4, 4, 12);
                break;
            case CHECK:
                g.drawLine(3, 8, 7, 12);
                g.drawLine(7, 12, 13, 4);
                break;
            case SPEED:
                g.draw(new Arc2D.Float(2f, 3f, 12f, 12f, 0, 180, Arc2D.OPEN));
                g.drawLine(8, 9, 11, 5);
                g.drawLine(2, 9, 14, 9);
                break;
            case DEVICES:
                g.draw(new RoundRectangle2D.Float(1f, 3f, 10f, 7f, 1.5f, 1.5f));
                g.drawLine(6, 10, 6, 13);
                g.drawLine(3, 13, 9, 13);
                g.draw(new RoundRectangle2D.Float(11.5f, 6f, 3.5f, 7.5f, 1.2f, 1.2f));
                break;
            case PLUS:
                g.drawLine(8, 3, 8, 13);
                g.drawLine(3, 8, 13, 8);
                break;
            default:
        }
        g.dispose();
    }

    private static void folder(Graphics2D g) {
        Path2D p = new Path2D.Float();
        p.moveTo(2, 4);
        p.lineTo(2, 3.5);
        p.quadTo(2, 2.5, 3, 2.5);
        p.lineTo(6, 2.5);
        p.lineTo(7.5, 4);
        g.draw(p);
        g.draw(new RoundRectangle2D.Float(1.5f, 4f, 13f, 9.5f, 2.5f, 2.5f));
    }

    /** Section heading: tinted round chip with an icon, then the title. */
    static class Section extends JComponent {
        private final String title;
        private final Ico ico;
        private final Color accent;

        Section(String title, Ico ico, Color accent) {
            this.title = title;
            this.ico = ico;
            this.accent = accent;
            setPreferredSize(new Dimension(160, 30));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = g2(g);
            int h = getHeight();
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Theme.dark ? 48 : 34));
            g2.fill(new RoundRectangle2D.Float(0, (h - 28) / 2f, 28, 28, 10, 10));
            ico(g2, ico, 6, (h - 16) / 2f, 16f, accent);
            g2.setFont(Theme.font(15f, java.awt.Font.BOLD));
            g2.setColor(Theme.TEXT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, 38, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Round status badge for finished transfers: check or cross. */
    static class Badge extends JComponent {
        private boolean ok = true;
        private boolean shown;
        private final boolean upload;

        Badge(boolean upload) {
            this.upload = upload;
            setPreferredSize(new Dimension(38, 38));
        }

        void state(boolean show, boolean good) { shown = show; ok = good; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = g2(g);
            Color base = shown ? (ok ? Theme.SUCCESS : Theme.DANGER) : (upload ? Theme.PRIMARY : Theme.SUCCESS);
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Theme.dark ? 50 : 36));
            g2.fill(new Ellipse2D.Float(0, 0, 38, 38));
            ico(g2, shown ? (ok ? Ico.CHECK : Ico.CLOSE) : (upload ? Ico.UPLOAD : Ico.DOWNLOAD), 11, 11, 16f, base);
            g2.dispose();
        }
    }

    /** App mark: gradient tile with two devices linked by a beam. */
    static void logo(Graphics2D g0, float x, float y, float s) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.scale(s / 100f, s / 100f);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1565C0), 100, 100, new Color(0x00BFA5)));
        g.fill(new RoundRectangle2D.Float(0, 0, 100, 100, 26, 26));
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Float(1, 1, 98, 98, 25, 25));
        // beam
        Path2D beam = new Path2D.Float();
        beam.moveTo(27, 66);
        beam.curveTo(40, 38, 60, 62, 73, 34);
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(beam);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(6.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(beam);
        // devices
        g.fill(new Ellipse2D.Float(15, 54, 24, 24));
        g.fill(new Ellipse2D.Float(61, 22, 24, 24));
        g.setColor(new Color(0x0D47A1));
        g.fill(new Ellipse2D.Float(22, 61, 10, 10));
        g.setColor(new Color(0x00897B));
        g.fill(new Ellipse2D.Float(68, 29, 10, 10));
        g.dispose();
    }

    /** Radar-like icon for "no devices found". */
    static void radar(Graphics2D g, int cx, int cy) {
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i <= 3; i++) {
            int r = 9 * i;
            g.setColor(new Color(Theme.PRIMARY.getRed(), Theme.PRIMARY.getGreen(), Theme.PRIMARY.getBlue(), 200 - i * 45));
            g.draw(new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, 30, 120, Arc2D.OPEN));
        }
        g.setColor(Theme.PRIMARY);
        g.fill(new Ellipse2D.Float(cx - 4, cy - 4, 8, 8));
    }

    /** Slim rounded scroll bars that follow the theme (the stock ones are white boxes in dark mode). */
    static void slimScroll(JScrollPane sp) {
        for (final JScrollBar b : new JScrollBar[]{sp.getVerticalScrollBar(), sp.getHorizontalScrollBar()}) {
            b.setOpaque(false);
            b.setPreferredSize(new Dimension(10, 10));
            b.setUI(new BasicScrollBarUI() {
                private JButton none() {
                    JButton z = new JButton();
                    z.setPreferredSize(new Dimension(0, 0));
                    z.setBorder(null);
                    return z;
                }
                @Override protected JButton createDecreaseButton(int o) { return none(); }
                @Override protected JButton createIncreaseButton(int o) { return none(); }
                @Override protected void paintTrack(Graphics g, javax.swing.JComponent c, java.awt.Rectangle r) {}
                @Override protected void paintThumb(Graphics g, javax.swing.JComponent c, java.awt.Rectangle r) {
                    if (r.isEmpty() || !scrollbar.isEnabled()) return;
                    Graphics2D g2 = g2(g);
                    boolean hot = isThumbRollover() || isDragging;
                    Color d = Theme.DIM;
                    g2.setColor(new Color(d.getRed(), d.getGreen(), d.getBlue(), hot ? 190 : 110));
                    g2.fill(new RoundRectangle2D.Float(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8));
                    g2.dispose();
                }
            });
        }
    }
}
