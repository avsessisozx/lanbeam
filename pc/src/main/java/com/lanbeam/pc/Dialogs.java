package com.lanbeam.pc;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Dialogs drawn with the app's own theme. The stock JOptionPane/JButton ignore the dark theme
 * (white-on-white buttons), so every message, question and form in the app goes through here.
 */
final class Dialogs {
    enum Level { INFO, WARN, ERROR, QUESTION }

    private Dialogs() {}

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    // ------------------------------------------------------------------ simple dialogs

    static void message(Component owner, String title, String plainText, Level level) {
        run(owner, title, level, htmlLabel(esc(plainText)), "Tamam", null);
    }

    /** html is trusted markup built by the caller (escape user text with {@link #esc}). */
    static boolean confirmHtml(Component owner, String title, String html, String okText, String cancelText) {
        return run(owner, title, Level.QUESTION, htmlLabel(html), okText, cancelText);
    }

    /** Returns the typed text, or null when cancelled. */
    static String input(Component owner, String title, String prompt) {
        JPanel body = column();
        body.add(htmlLabel(esc(prompt)));
        body.add(Box.createVerticalStrut(10));
        JTextField f = field("");
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(f);
        return run(owner, title, Level.QUESTION, body, "Tamam", "İptal", f) ? f.getText() : null;
    }

    /** Shows body in a themed dialog with OK / Cancel; true when OK was pressed. */
    static boolean form(Component owner, String title, JComponent body, String okText) {
        return run(owner, title, null, body, okText, "İptal");
    }

    // ------------------------------------------------------------------ themed controls

    static JTextField field(String text) {
        JTextField f = new JTextField(text);
        f.setFont(Theme.font(13.5f, Font.PLAIN));
        f.setBackground(Theme.TONAL);
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.TEXT);
        f.setSelectionColor(Theme.PRIMARY);
        f.setSelectedTextColor(Theme.ON_PRIMARY);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.STROKE, 1), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return f;
    }

    static JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(12.5f, Font.BOLD));
        l.setForeground(Theme.DIM);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        return l;
    }

    static JComboBox<String> combo(String... items) {
        final JComboBox<String> c = new JComboBox<>(items);
        c.setFont(Theme.font(13.5f, Font.PLAIN));
        c.setBackground(Theme.TONAL);
        c.setForeground(Theme.TEXT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        c.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = Widgets.g2(g);
                        g2.setColor(Theme.TONAL);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(Theme.TEXT);
                        g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                        int cx = getWidth() / 2, cy = getHeight() / 2;
                        Path2D p = new Path2D.Float();
                        p.moveTo(cx - 5, cy - 2);
                        p.lineTo(cx, cy + 3);
                        p.lineTo(cx + 5, cy - 2);
                        g2.draw(p);
                        g2.dispose();
                    }
                };
                b.setBorder(BorderFactory.createEmptyBorder());
                b.setContentAreaFilled(false);
                b.setFocusable(false);
                b.setPreferredSize(new Dimension(34, 30));
                return b;
            }

            @Override public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
                g.setColor(Theme.TONAL);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        });
        c.setBorder(BorderFactory.createLineBorder(Theme.STROKE, 1));
        c.setRenderer(new ListCellRenderer<String>() {
            private final JLabel l = new JLabel();

            @Override public Component getListCellRendererComponent(JList<? extends String> list, String v, int i, boolean sel, boolean f) {
                l.setOpaque(true);
                l.setText(v);
                l.setFont(Theme.font(13.5f, Font.PLAIN));
                l.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                l.setBackground(sel && i >= 0 ? Theme.PRIMARY : Theme.TONAL);
                l.setForeground(sel && i >= 0 ? Theme.ON_PRIMARY : Theme.TEXT);
                return l;
            }
        });
        Object popup = c.getAccessibleContext().getAccessibleChild(0);
        if (popup instanceof BasicComboPopup) {
            ((BasicComboPopup) popup).setBorder(BorderFactory.createLineBorder(Theme.STROKE, 1));
            ((BasicComboPopup) popup).getList().setBackground(Theme.TONAL);
        }
        return c;
    }

    static JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JLabel htmlLabel(String html) {
        JLabel l = new JLabel("<html><body style='width:340px'>" + html + "</body></html>");
        l.setFont(Theme.font(14f, Font.PLAIN));
        l.setForeground(Theme.TEXT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    // ------------------------------------------------------------------ the dialog itself

    private static boolean run(Component owner, String title, Level level, JComponent body, String okText, String cancelText) {
        return run(owner, title, level, body, okText, cancelText, null);
    }

    private static boolean run(Component owner, String title, Level level, JComponent body, String okText, String cancelText, JComponent focus) {
        Window w = owner == null ? null : javax.swing.SwingUtilities.getWindowAncestor(owner);
        if (w == null && owner instanceof Window) w = (Window) owner;
        final JDialog d = new JDialog(w, title, JDialog.DEFAULT_MODALITY_TYPE);
        final boolean[] ok = new boolean[1];

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));

        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setOpaque(false);
        if (level != null) {
            LevelIcon li = new LevelIcon(level);
            JPanel iconHolder = new JPanel(new BorderLayout());
            iconHolder.setOpaque(false);
            iconHolder.add(li, BorderLayout.NORTH);
            center.add(iconHolder, BorderLayout.WEST);
        }
        center.add(body, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        if (cancelText != null) {
            Widgets.Btn cancel = new Widgets.Btn(cancelText, Widgets.Kind.TONAL);
            cancel.addActionListener(e -> d.dispose());
            buttons.add(cancel);
        }
        final Widgets.Btn okBtn = new Widgets.Btn(okText, Widgets.Kind.PRIMARY);
        okBtn.addActionListener(e -> { ok[0] = true; d.dispose(); });
        buttons.add(okBtn);
        root.add(buttons, BorderLayout.SOUTH);

        d.setContentPane(root);
        d.getRootPane().setDefaultButton(null);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.getRootPane().registerKeyboardAction(e -> { ok[0] = true; d.dispose(); }, KeyStroke.getKeyStroke("ENTER"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setResizable(false);
        d.pack();
        d.setMinimumSize(new Dimension(420, 0));
        d.pack();
        d.setLocationRelativeTo(owner);
        final JComponent f = focus != null ? focus : okBtn;
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) { f.requestFocusInWindow(); }
        });
        d.setVisible(true);
        return ok[0];
    }

    /** Round colored badge with a glyph for the dialog level. */
    private static final class LevelIcon extends JComponent {
        private final Level level;

        LevelIcon(Level level) {
            this.level = level;
            setPreferredSize(new Dimension(44, 44));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Widgets.g2(g);
            Color c;
            String glyph;
            switch (level) {
                case ERROR: c = Theme.DANGER; glyph = "×"; break;
                case WARN: c = warn(); glyph = "!"; break;
                case QUESTION: c = Theme.PRIMARY; glyph = "?"; break;
                default: c = Theme.PRIMARY; glyph = "i";
            }
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Theme.dark ? 55 : 40));
            g2.fill(new Ellipse2D.Float(0, 0, 44, 44));
            g2.setColor(c);
            g2.setFont(Theme.font(24f, Font.BOLD));
            java.awt.FontMetrics fm = g2.getFontMetrics();
            g2.drawString(glyph, (44 - fm.stringWidth(glyph)) / 2, (44 - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    static Color warn() {
        return Theme.dark ? new Color(0xFFB74D) : new Color(0xEF6C00);
    }
}
