package com.lanbeam.pc;

import com.lanbeam.core.NetUtil;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/** One card in the transfers list: title, progress bar, speed and an action button. */
final class TransferRow extends Widgets.Card {
    private final JLabel title = new JLabel();
    private final JLabel percent = new JLabel(" ");
    private final JLabel status = new JLabel("Bekleniyor…");
    private final Widgets.Bar bar = new Widgets.Bar();
    private final Widgets.Btn action = new Widgets.Btn("İptal", Widgets.Kind.TEXT);
    private Runnable onAction;
    private volatile boolean finished;

    TransferRow(String text, Runnable cancel) {
        super(12, 14, false);
        setLayout(new BorderLayout(10, 4));
        title.setText(text);
        title.setFont(Theme.font(13.5f, Font.BOLD));
        title.setForeground(Theme.TEXT);
        percent.setFont(Theme.font(13f, Font.BOLD));
        percent.setForeground(Theme.PRIMARY);
        status.setFont(Theme.font(12f, Font.PLAIN));
        status.setForeground(Theme.DIM);
        onAction = cancel;
        action.setVisible(cancel != null);
        action.addActionListener(e -> { if (onAction != null) onAction.run(); });

        JPanel north = new JPanel(new BorderLayout(8, 0));
        north.setOpaque(false);
        north.add(title, BorderLayout.CENTER);
        north.add(percent, BorderLayout.EAST);
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(north, BorderLayout.NORTH);
        center.add(bar, BorderLayout.CENTER);
        center.add(status, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        add(action, BorderLayout.EAST);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height + 4));
    }

    boolean isFinished() { return finished; }

    void progress(long done, long total, double bps) {
        SwingUtilities.invokeLater(() -> {
            int v = total <= 0 ? 1000 : (int) (done * 1000 / total);
            bar.set(v, Theme.PRIMARY);
            percent.setText(v / 10 + "%");
            String eta = "";
            if (bps > 0 && total > done) {
                long s = (long) ((total - done) / bps);
                eta = String.format("  •  kalan %d:%02d", s / 60, s % 60);
            }
            status.setForeground(Theme.DIM);
            status.setText(NetUtil.formatSize(done) + " / " + NetUtil.formatSize(total) + "  •  " + NetUtil.formatSpeed(bps) + eta);
        });
    }

    void finish(String text, String buttonText, Runnable buttonAction) {
        SwingUtilities.invokeLater(() -> {
            boolean ok = text.startsWith("Tamam") || text.startsWith("Paylaşımda");
            boolean bad = text.startsWith("Hata") || text.startsWith("Gönderilemedi") || text.contains("reddetti")
                    || text.contains("kesildi") || text.startsWith("İptal");
            Color c = bad ? Theme.DANGER : ok ? Theme.SUCCESS : Theme.DIM;
            if (text.startsWith("Tamam")) { bar.set(1000, Theme.SUCCESS); percent.setText("100%"); }
            else if (bad) bar.set(0, Theme.DANGER);
            status.setForeground(c);
            status.setText(text);
            if (ok || bad) finished = true;
            onAction = buttonAction;
            if (buttonText == null) action.setVisible(false);
            else { action.setText(buttonText); action.setVisible(true); }
        });
    }
}
