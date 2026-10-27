package com.lanbeam.pc;

import com.lanbeam.core.Diagnostics;
import com.lanbeam.core.NetUtil;
import com.lanbeam.core.Peer;
import com.lanbeam.core.SpeedTest;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** "Bağlantı / Rapor": collects PC facts, runs the speed test, shows a report the user can copy or open as a GitHub issue. */
final class ReportDialog {
    private ReportDialog() {}

    /** Fills Diagnostics with what Java and Windows tell us about this PC and its network adapters. */
    static void collect(File saveDir) {
        Diagnostics.info("İşletim sistemi", System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "") + " (" + System.getProperty("os.arch", "") + ")");
        Diagnostics.info("Bellek (Java üst sınırı)", NetUtil.formatSize(Runtime.getRuntime().maxMemory()));
        StringBuilder ad = new StringBuilder();
        for (NetUtil.LocalAddress a : NetUtil.localAddresses()) {
            if (ad.length() > 0) ad.append("; ");
            ad.append(a.iface.replaceAll("\\s+", " ").trim());
        }
        Diagnostics.info("Ağ arayüzleri", ad.length() == 0 ? "yok" : ad.toString());
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            String speeds = run("powershell", "-NoProfile", "-Command",
                    "Get-NetAdapter | Where-Object Status -eq 'Up' | ForEach-Object { $_.InterfaceDescription + ' = ' + $_.LinkSpeed } ");
            if (!speeds.isEmpty()) Diagnostics.info("Bağdaştırıcı hızları (Windows)", speeds.replace("\n", "; "));
            String wl = run("netsh", "wlan", "show", "interfaces");
            StringBuilder w = new StringBuilder();
            for (String line : wl.split("\n")) {
                String l = line.trim().toLowerCase();
                if (l.startsWith("radio type") || l.startsWith("band") || l.startsWith("channel") || l.startsWith("receive rate")
                        || l.startsWith("transmit rate") || l.startsWith("signal") || l.startsWith("radyo") || l.startsWith("bant")
                        || l.startsWith("kanal") || l.startsWith("alma hızı") || l.startsWith("iletim hızı") || l.startsWith("sinyal")) {
                    if (w.length() > 0) w.append("; ");
                    w.append(line.trim());
                }
            }
            Diagnostics.info("Wi-Fi (Windows)", w.length() == 0 ? "Wi-Fi bağlantısı yok (kablolu olabilir)" : w.toString());
        }
    }

    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"))) {
                String s;
                while ((s = r.readLine()) != null) if (!s.trim().isEmpty()) sb.append(s).append('\n');
            }
            if (!p.waitFor(8, TimeUnit.SECONDS)) p.destroyForcibly();
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    static void show(Frame owner, Peer selected, File saveDir) {
        final JDialog d = new JDialog(owner, "Bağlantı / Rapor / Geri bildirim", true);
        final JTextArea area = new JTextArea(26, 82);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBackground(Theme.CARD);
        area.setForeground(Theme.TEXT);
        area.setText("Toplanıyor…");
        final Runnable refresh = () -> new Thread(() -> {
            collect(saveDir);
            final String text = Diagnostics.report("PC", "Windows/Java");
            SwingUtilities.invokeLater(() -> { area.setText(text); area.setCaretPosition(0); });
        }, "lanbeam-report").start();
        refresh.run();

        JButton speed = new JButton("Hız testi" + (selected == null ? " (önce bir cihaz seçin)" : ""));
        speed.setEnabled(selected != null);
        speed.addActionListener(e -> {
            speed.setEnabled(false);
            speed.setText("Ölçülüyor…");
            new Thread(() -> {
                String msg;
                try {
                    SpeedTest.Result r = SpeedTest.run(selected.host, selected.port, SpeedTest.DEFAULT_BYTES);
                    String par = SpeedTest.runParallel(selected.host, selected.port, 32L * 1024 * 1024);
                    double disk = SpeedTest.diskWrite(saveDir, 128L * 1024 * 1024);
                    msg = "Ağ: " + r.summary() + "\n\nParalel bağlantılar:\n" + par + "\n\nDepolama yazma (kayıt klasörü): " + NetUtil.formatSpeed(disk);
                } catch (Exception ex) {
                    Diagnostics.log("hız testi hatası: " + ex);
                    msg = "Ölçüm yapılamadı: " + ex.getMessage();
                }
                final String m = msg;
                SwingUtilities.invokeLater(() -> {
                    speed.setText("Hız testi");
                    speed.setEnabled(true);
                    JOptionPane.showMessageDialog(d, m, "Hız testi", JOptionPane.INFORMATION_MESSAGE);
                    refresh.run();
                });
            }, "lanbeam-speedtest").start();
        });
        JButton copy = new JButton("Kopyala");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(area.getText()), null);
            copy.setText("Kopyalandı ✓");
        });
        JButton gh = new JButton("GitHub'da bildir");
        gh.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(area.getText()), null);
            JOptionPane.showMessageDialog(d, "Rapor panoya kopyalandı. Açılan sayfada yapıştırın (Ctrl+V).", "LanBeam", JOptionPane.INFORMATION_MESSAGE);
            try { Desktop.getDesktop().browse(new URI("https://github.com/avsessisozx/lanbeam/issues/new")); } catch (Exception ignored) {}
        });
        JButton close = new JButton("Kapat");
        close.addActionListener(e -> d.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(speed);
        buttons.add(copy);
        buttons.add(gh);
        buttons.add(close);
        d.getContentPane().setLayout(new BorderLayout());
        d.getContentPane().add(new JScrollPane(area), BorderLayout.CENTER);
        d.getContentPane().add(buttons, BorderLayout.SOUTH);
        d.pack();
        d.setMinimumSize(new Dimension(640, 420));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
