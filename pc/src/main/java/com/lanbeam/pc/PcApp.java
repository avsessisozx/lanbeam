package com.lanbeam.pc;

import com.lanbeam.core.FileSource;
import com.lanbeam.core.LanBeamNode;
import com.lanbeam.core.LocalFileSource;
import com.lanbeam.core.NetUtil;
import com.lanbeam.core.Offer;
import com.lanbeam.core.Peer;
import com.lanbeam.core.Receiver;
import com.lanbeam.core.Share;
import com.lanbeam.core.ShareLink;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

public final class PcApp implements LanBeamNode.Listener {
    private final Preferences prefs = Preferences.userNodeForPackage(PcApp.class);
    private LanBeamNode node;

    private final JFrame frame = new JFrame("LanBeam");
    private final DefaultListModel<Peer> peerModel = new DefaultListModel<>();
    private final JList<Peer> peerList = new JList<>(peerModel);
    private final Map<String, Peer> manualPeers = new LinkedHashMap<>();
    private final DefaultListModel<File> fileModel = new DefaultListModel<>();
    private final JList<File> fileList = new JList<>(fileModel);
    private final JLabel fileSummary = new JLabel(" ");
    private final JLabel selfLabel = new JLabel();
    private final JPanel transfers = new JPanel();
    private final Map<String, TransferRow> sendRows = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new PcApp().start());
    }

    private String deviceId() {
        String id = prefs.get("id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.put("id", id);
        }
        return id;
    }

    private String deviceName() {
        String def = System.getenv("COMPUTERNAME");
        if (def == null) def = "PC";
        return prefs.get("name", def);
    }

    private File saveDir() {
        File def = new File(System.getProperty("user.home"), "Downloads" + File.separator + "LanBeam");
        return new File(prefs.get("saveDir", def.getAbsolutePath()));
    }

    private void start() {
        buildUi();
        startNode();
    }

    private void startNode() {
        if (node != null) node.stop();
        node = new LanBeamNode(deviceId(), deviceName(), System.getProperty("os.name", "pc").toLowerCase().contains("win") ? "windows" : "pc", this);
        try {
            node.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Sunucu başlatılamadı (8080-8089 portları dolu olabilir):\n" + e, "LanBeam", JOptionPane.ERROR_MESSAGE);
        }
        selfLabel.setText("<html><b>" + esc(deviceName()) + "</b> &nbsp; " + NetUtil.primaryHost() + ":" + node.port()
                + " &nbsp; <span style='color:gray'>Kayıt: " + esc(saveDir().getAbsolutePath()) + "</span></html>");
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(820, 560));

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        top.add(selfLabel, BorderLayout.CENTER);
        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        topButtons.add(button("Bağlantıdan al…", this::receiveFromLink));
        topButtons.add(button("Ayarlar", this::settings));
        top.add(topButtons, BorderLayout.EAST);

        // Devices
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerList.setCellRenderer((list, p, index, sel, focus) -> {
            JLabel l = new JLabel("<html><b>" + esc(p.name) + "</b><br><span style='color:gray'>" + esc(p.os) + " • " + p.host + "</span></html>");
            l.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            l.setOpaque(true);
            l.setBackground(sel ? list.getSelectionBackground() : list.getBackground());
            l.setForeground(sel ? list.getSelectionForeground() : list.getForeground());
            return l;
        });
        JPanel devices = titled("Yakındaki cihazlar", new JScrollPane(peerList));
        JPanel devButtons = new JPanel(new GridLayout(1, 2, 6, 0));
        devButtons.add(button("Yenile", () -> node.refreshPeers()));
        devButtons.add(button("IP ile ekle…", this::addPeerByIp));
        devices.add(devButtons, BorderLayout.SOUTH);

        // Files to send (drag & drop)
        fileList.setTransferHandler(new FileDrop());
        fileList.setCellRenderer((list, f, index, sel, focus) -> {
            JLabel l = new JLabel((f.isDirectory() ? "📁 " : "📄 ") + f.getName() + "   " + f.getParent());
            l.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            l.setOpaque(true);
            l.setBackground(sel ? list.getSelectionBackground() : list.getBackground());
            l.setForeground(sel ? list.getSelectionForeground() : list.getForeground());
            return l;
        });
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setTransferHandler(fileList.getTransferHandler());
        JLabel hint = new JLabel("Dosya ve klasörleri buraya sürükleyip bırakın", JLabel.CENTER);
        hint.setForeground(java.awt.Color.GRAY);
        JPanel filesPanel = titled("Gönderilecekler", fileScroll);
        filesPanel.add(hint, BorderLayout.NORTH);
        JPanel fileButtons = new JPanel(new BorderLayout());
        JPanel fb = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fb.add(button("Dosya ekle…", () -> choose(false)));
        fb.add(button("Klasör ekle…", () -> choose(true)));
        fb.add(button("Temizle", () -> { fileModel.clear(); updateSummary(); }));
        fileButtons.add(fb, BorderLayout.WEST);
        fileButtons.add(fileSummary, BorderLayout.CENTER);
        JPanel sb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton send = button("Seçili cihaza gönder ➜", this::sendToSelected);
        send.setFont(send.getFont().deriveFont(Font.BOLD));
        sb.add(button("Bağlantı oluştur", this::createLink));
        sb.add(send);
        fileButtons.add(sb, BorderLayout.EAST);
        filesPanel.add(fileButtons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, devices, filesPanel);
        split.setDividerLocation(260);
        split.setBorder(null);

        transfers.setLayout(new BoxLayout(transfers, BoxLayout.Y_AXIS));
        JScrollPane trScroll = new JScrollPane(transfers);
        trScroll.setPreferredSize(new Dimension(100, 180));
        JPanel trPanel = titled("Aktarımlar", trScroll);

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, split, trPanel);
        main.setResizeWeight(0.7);
        main.setBorder(null);

        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(main, BorderLayout.CENTER);
        frame.setSize(980, 660);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JButton button(String text, Runnable r) {
        JButton b = new JButton(text);
        b.addActionListener(e -> r.run());
        return b;
    }

    private static JPanel titled(String title, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8), BorderFactory.createTitledBorder(title)));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private final class FileDrop extends TransferHandler {
        @Override public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport s) {
            try {
                addFiles((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private void choose(boolean dirs) {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setFileSelectionMode(dirs ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            List<File> l = new ArrayList<>();
            for (File f : fc.getSelectedFiles()) l.add(f);
            addFiles(l);
        }
    }

    private void addFiles(List<File> files) {
        for (File f : files) if (!fileModel.contains(f)) fileModel.addElement(f);
        updateSummary();
    }

    private void updateSummary() {
        List<FileSource> s = LocalFileSource.collect(selectedRoots());
        long total = 0;
        for (FileSource f : s) total += f.size();
        fileSummary.setText(s.isEmpty() ? " " : "  " + s.size() + " dosya, " + NetUtil.formatSize(total));
    }

    private List<File> selectedRoots() {
        List<File> l = new ArrayList<>();
        for (int i = 0; i < fileModel.size(); i++) l.add(fileModel.get(i));
        return l;
    }

    private void refreshPeerList(List<Peer> discovered) {
        SwingUtilities.invokeLater(() -> {
            Peer sel = peerList.getSelectedValue();
            Map<String, Peer> all = new LinkedHashMap<>(manualPeers);
            for (Peer p : discovered) all.put(p.id, p);
            peerModel.clear();
            for (Peer p : all.values()) peerModel.addElement(p);
            if (sel != null) {
                for (int i = 0; i < peerModel.size(); i++) if (peerModel.get(i).id.equals(sel.id)) peerList.setSelectedIndex(i);
            }
            if (peerList.getSelectedIndex() < 0 && peerModel.size() == 1) peerList.setSelectedIndex(0);
        });
    }

    private TransferRow addRow(String title, Runnable cancel) {
        TransferRow r = new TransferRow(title, cancel);
        transfers.add(r, 0);
        transfers.revalidate();
        transfers.repaint();
        return r;
    }

    // ------------------------------------------------------------------ actions

    private Share publishSelection() {
        List<FileSource> files = LocalFileSource.collect(selectedRoots());
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Önce gönderilecek dosya veya klasör ekleyin.", "LanBeam", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return node.publish(files);
    }

    private void sendToSelected() {
        Peer peer = peerList.getSelectedValue();
        if (peer == null) {
            JOptionPane.showMessageDialog(frame, "Soldaki listeden bir cihaz seçin.\nCihaz görünmüyorsa uygulamanın açık ve aynı ağda olduğundan emin olun\nveya \"IP ile ekle\" kullanın.", "LanBeam", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final Share share = publishSelection();
        if (share == null) return;
        final TransferRow row = addRow("⬆ " + peer.name + " cihazına: " + share.files.size() + " dosya, " + NetUtil.formatSize(share.total),
                () -> node.unpublish(share));
        sendRows.put(share.token, row);
        new Thread(() -> {
            try {
                node.sendOffer(peer, share);
                row.finish("Karşı tarafın onayı bekleniyor…", "İptal", () -> { node.unpublish(share); row.finish("İptal edildi", null, null); });
            } catch (Exception e) {
                node.unpublish(share);
                row.finish("Gönderilemedi: " + e.getMessage(), null, null);
            }
        }, "offer").start();
    }

    private void createLink() {
        Share share = publishSelection();
        if (share == null) return;
        String url = node.linkFor(share, NetUtil.primaryHost()).toString();
        TransferRow row = addRow("🔗 Bağlantı: " + url, null);
        sendRows.put(share.token, row);
        row.finish("Paylaşımda — telefonda LanBeam'de \"Bağlantıdan al\" ya da tarayıcıda açın", "Kapat",
                () -> { node.unpublish(share); row.finish("Paylaşım kapatıldı", null, null); });
        JTextField f = new JTextField(url);
        f.setEditable(false);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
        JOptionPane.showMessageDialog(frame, new Object[]{
                "Paylaşım bağlantısı (panoya kopyalandı):", f,
                "Aynı ağdaki herhangi bir cihaz bu adresi tarayıcıda açarak indirebilir.",
                "LanBeam uygulamasında \"Bağlantıdan al\" ile açılırsa hızlı akış kullanılır."}, "Bağlantı", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addPeerByIp() {
        String s = JOptionPane.showInputDialog(frame, "Cihazın IP adresi (örn. 192.168.1.34 veya 192.168.1.34:8080):");
        if (s == null || s.trim().isEmpty()) return;
        String host = s.trim();
        int port = 8080;
        int c = host.lastIndexOf(':');
        if (c > 0) { port = Integer.parseInt(host.substring(c + 1)); host = host.substring(0, c); }
        final String h = host;
        final int p = port;
        new Thread(() -> {
            try {
                Peer peer = LanBeamNode.probe(h, p);
                manualPeers.put(peer.id, peer);
                refreshPeerList(node.peers());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Cihaza ulaşılamadı: " + e.getMessage(), "LanBeam", JOptionPane.WARNING_MESSAGE));
            }
        }).start();
    }

    private void receiveFromLink() {
        String s = JOptionPane.showInputDialog(frame, "Paylaşım bağlantısı (http://ip:port/s/...):");
        if (s == null || s.trim().isEmpty()) return;
        try {
            startReceive(ShareLink.parse(s), "bağlantı");
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, "Geçersiz bağlantı.", "LanBeam", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void startReceive(ShareLink link, String from) {
        final File dir = saveDir();
        dir.mkdirs();
        final TransferRow[] holder = new TransferRow[1];
        final Receiver rx = new Receiver(link, new Receiver.DirectorySink(dir), new Receiver.Listener() {
            @Override public void onStart(Receiver r, int count, long total) {}

            @Override public void onProgress(Receiver r, long done, long total, double bps) {
                holder[0].progress(done, total, bps);
            }

            @Override public void onFinished(Receiver r, boolean ok, String error, List<String> saved) {
                if (ok) holder[0].finish("Tamamlandı — " + saved.size() + " dosya", "Klasörü aç", () -> openFolder(dir));
                else holder[0].finish("Hata: " + error, saved.isEmpty() ? null : "Klasörü aç", () -> openFolder(dir));
            }
        });
        holder[0] = addRow("⬇ " + from + " → " + dir.getAbsolutePath(), rx::cancel);
        rx.startThread();
    }

    private static void openFolder(File dir) {
        try { Desktop.getDesktop().open(dir); } catch (Exception ignored) {}
    }

    private void settings() {
        JTextField name = new JTextField(deviceName());
        JTextField dir = new JTextField(saveDir().getAbsolutePath());
        JButton browse = button("Seç…", () -> {
            JFileChooser fc = new JFileChooser(dir.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) dir.setText(fc.getSelectedFile().getAbsolutePath());
        });
        JPanel dirRow = new JPanel(new BorderLayout(4, 0));
        dirRow.add(dir, BorderLayout.CENTER);
        dirRow.add(browse, BorderLayout.EAST);
        int r = JOptionPane.showConfirmDialog(frame, new Object[]{"Cihaz adı:", name, Box.createVerticalStrut(6), "Gelen dosyaların kaydedileceği klasör:", dirRow},
                "Ayarlar", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        File d = new File(dir.getText().trim());
        if (!d.isDirectory() && !d.mkdirs()) {
            JOptionPane.showMessageDialog(frame, "Klasör oluşturulamadı.", "LanBeam", JOptionPane.WARNING_MESSAGE);
            return;
        }
        prefs.put("saveDir", d.getAbsolutePath());
        if (!name.getText().trim().isEmpty()) prefs.put("name", name.getText().trim());
        startNode();
    }

    // ------------------------------------------------------------------ node callbacks (background threads)

    @Override public void onPeersChanged(List<Peer> peers) {
        refreshPeerList(peers);
    }

    @Override public void onOffer(Offer offer) {
        SwingUtilities.invokeLater(() -> {
            frame.toFront();
            int r = JOptionPane.showConfirmDialog(frame,
                    "<html><b>" + esc(offer.fromName) + "</b> size dosya göndermek istiyor:<br><br>"
                            + esc(offer.describe()) + "<br><br>Kayıt yeri: " + esc(saveDir().getAbsolutePath()) + "<br><br>Kabul ediyor musunuz?</html>",
                    "Gelen dosyalar", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r == JOptionPane.YES_OPTION) startReceive(offer.link, offer.fromName);
            else node.reject(offer);
        });
    }

    @Override public void onSendProgress(Share share, long sent, long total, double bps) {
        TransferRow row = sendRows.get(share.token);
        if (row != null) row.progress(sent, total, bps);
    }

    @Override public void onSendFinished(Share share, boolean ok) {
        TransferRow row = sendRows.get(share.token);
        if (row == null) return;
        if (ok) {
            row.finish("Tamamlandı", "Paylaşımı kapat", () -> { node.unpublish(share); row.finish("Tamamlandı", null, null); });
        } else {
            row.finish("Bağlantı kesildi", "Paylaşımı kapat", () -> { node.unpublish(share); row.finish("Kapatıldı", null, null); });
        }
    }

    @Override public void onRejected(Share share) {
        node.unpublish(share);
        TransferRow row = sendRows.remove(share.token);
        if (row != null) row.finish("Karşı taraf reddetti", null, null);
    }
}
