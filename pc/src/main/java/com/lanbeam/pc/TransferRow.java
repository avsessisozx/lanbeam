package com.lanbeam.pc;

import com.lanbeam.core.NetUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/** One line in the transfers list: title, progress bar, speed and an action button. */
final class TransferRow extends JPanel {
    private final JLabel title = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JButton action = new JButton("İptal");
    private Runnable onAction;

    TransferRow(String text, Runnable cancel) {
        super(new BorderLayout(8, 2));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(0xdddddd)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        title.setText(text);
        bar.setStringPainted(true);
        bar.setString("Bekleniyor…");
        onAction = cancel;
        action.addActionListener(e -> { if (onAction != null) onAction.run(); });
        JPanel center = new JPanel(new BorderLayout(0, 2));
        center.setOpaque(false);
        center.add(title, BorderLayout.NORTH);
        center.add(bar, BorderLayout.CENTER);
        center.add(status, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(action);
        add(right, BorderLayout.EAST);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    void progress(long done, long total, double bps) {
        SwingUtilities.invokeLater(() -> {
            int v = total <= 0 ? 1000 : (int) (done * 1000 / total);
            bar.setValue(v);
            bar.setString(v / 10 + "%");
            String eta = "";
            if (bps > 0 && total > done) {
                long s = (long) ((total - done) / bps);
                eta = String.format("  •  kalan %d:%02d", s / 60, s % 60);
            }
            status.setText(NetUtil.formatSize(done) + " / " + NetUtil.formatSize(total) + "  •  " + NetUtil.formatSpeed(bps) + eta);
        });
    }

    void finish(String text, String buttonText, Runnable buttonAction) {
        SwingUtilities.invokeLater(() -> {
            bar.setString(text);
            if (text.startsWith("Tamam")) bar.setValue(1000);
            onAction = buttonAction;
            if (buttonText == null) action.setVisible(false);
            else action.setText(buttonText);
        });
    }
}
