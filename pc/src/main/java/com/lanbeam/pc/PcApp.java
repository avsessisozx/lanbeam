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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.geom.RoundRectangle2D;
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

    private JFrame frame;
    private final DefaultListModel<Peer> peerModel = new DefaultListModel<>();
    private final JList<Peer> peerList = new JList<Peer>(peerModel) {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getModel().getSize() == 0)
                drawEmpty(g, this, false, "Cihaz bulunamadı", "Diğer cihazda LanBeam açık ve",
                        "aynı Wi-Fi / modemde olmalı.", "Bulunamazsa \"IP ile ekle\" kullanın.");
        }
    };
    private final Map<String, Peer> manualPeers = new LinkedHashMap<>();
    private final DefaultListModel<File> fileModel = new DefaultListModel<>();
    private final JList<File> fileList = new JList<File>(fileModel) {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getModel().getSize() == 0)
                drawEmpty(g, this, true, "Dosya ve klasörleri buraya sürükleyip bırakın",
                        "ya da yukarıdan \"Dosya ekle\" / \"Klasör ekle\" ile seçin", null, null);
        }
    };
    private final JLabel fileSummary = new JLabel(" ");
    private final JLabel selfLabel = new JLabel();
    private final JLabel saveLabel = new JLabel();
    private final JLabel transfersEmpty = new JLabel("Henüz aktarım yok. Gönderirken veya alırken burada görünür.");
    private final JPanel transfers = new JPanel();
    private final Map<String, TransferRow> sendRows = new ConcurrentHashMap<>();
    /** Shares pushed to one chosen device: closed as soon as the transfer ends (link shares stay open until closed). */
    private final java.util.Set<String> offerTokens = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private Widgets.Btn sendBtn;

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        Theme.init(Preferences.userNodeForPackage(PcApp.class).get("theme", "auto"));
        Theme.applyToDialogs();
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
            Dialogs.message(frame, "LanBeam", "Sunucu başlatılamadı (8080-8089 portları dolu olabilir):\n" + e, Dialogs.Level.ERROR);
        }
        selfLabel.setText(deviceName() + "  •  " + NetUtil.primaryHost() + ":" + node.port());
        saveLabel.setText("Gelen dosyalar: " + saveDir().getAbsolutePath());
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        frame = new JFrame("LanBeam");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(940, 640));
        java.util.List<java.awt.Image> icons = new java.util.ArrayList<>();
        for (int sz : new int[]{16, 32, 64, 256}) {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(sz, sz, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = Widgets.g2(im.getGraphics());
            Widgets.logo(ig, 0, 0, sz);
            ig.dispose();
            icons.add(im);
        }
        frame.setIconImages(icons);
        frame.setContentPane(buildContent());
        frame.setSize(1060, 720);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // ---- header
        Widgets.Card header = new Widgets.Card(18, 22, true);
        header.setLayout(new BorderLayout(12, 0));
        JPanel hl = new JPanel();
        hl.setOpaque(false);
        hl.setLayout(new BoxLayout(hl, BoxLayout.Y_AXIS));
        hl.add(label("LanBeam", 26, Font.BOLD, Color.WHITE));
        hl.add(Box.createVerticalStrut(2));
        selfLabel.setFont(Theme.font(13f, Font.PLAIN));
        selfLabel.setForeground(new Color(255, 255, 255, 235));
        saveLabel.setFont(Theme.font(12f, Font.PLAIN));
        saveLabel.setForeground(new Color(255, 255, 255, 180));
        hl.add(selfLabel);
        hl.add(saveLabel);
        header.add(hl, BorderLayout.CENTER);
        JComponent mark = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Widgets.g2(g);
                Widgets.logo(g2, 0, 0, 62);
                g2.dispose();
            }
        };
        mark.setPreferredSize(new Dimension(62, 62));
        JPanel markHolder = new JPanel(new GridBagLayout());
        markHolder.setOpaque(false);
        markHolder.add(mark);
        header.add(markHolder, BorderLayout.WEST);
        JPanel hb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hb.setOpaque(false);
        hb.add(btn("Bağlantıdan al…", Widgets.Kind.HEADER, Widgets.Ico.DOWNLOAD, this::receiveFromLink));
        hb.add(btn("Bağlantı / Rapor", Widgets.Kind.HEADER, Widgets.Ico.REPORT, () -> ReportDialog.show(frame, peerList.getSelectedValue(), saveDir())));
        hb.add(btn("Ayarlar", Widgets.Kind.HEADER, Widgets.Ico.GEAR, this::settings));
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        east.add(hb);
        header.add(east, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ---- nearby devices
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerList.setOpaque(false);
        peerList.setFixedCellHeight(72);
        peerList.setCellRenderer(new PeerCell());
        peerList.addListSelectionListener(e -> updateHint());
        Widgets.Card devices = new Widgets.Card(16);
        devices.setLayout(new BorderLayout(0, 10));
        devices.setPreferredSize(new Dimension(330, 100));
        devices.add(new Widgets.Section("Yakındaki cihazlar", Widgets.Ico.DEVICES, teal()), BorderLayout.NORTH);
        devices.add(scroll(peerList), BorderLayout.CENTER);
        JPanel devButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        devButtons.setOpaque(false);
        devButtons.add(btn("Yenile", Widgets.Kind.TONAL, Widgets.Ico.REFRESH, () -> node.refreshPeers()));
        devButtons.add(btn("IP ile ekle…", Widgets.Kind.TONAL, Widgets.Ico.PLUS, this::addPeerByIp));
        devices.add(devButtons, BorderLayout.SOUTH);

        // ---- files to send (drag & drop)
        fileList.setTransferHandler(new FileDrop());
        fileList.setOpaque(false);
        fileList.setFixedCellHeight(48);
        fileList.setCellRenderer(new FileCell());
        fileList.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("DELETE"), "del");
        fileList.getActionMap().put("del", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { removeSelectedFiles(); }
        });
        JScrollPane fileScroll = scroll(fileList);
        fileScroll.setTransferHandler(fileList.getTransferHandler());

        Widgets.Card filesPanel = new Widgets.Card(16);
        filesPanel.setLayout(new BorderLayout(0, 10));
        JPanel ftop = new JPanel(new BorderLayout(8, 0));
        ftop.setOpaque(false);
        ftop.add(new Widgets.Section("Gönderilecekler", Widgets.Ico.UPLOAD, Theme.PRIMARY), BorderLayout.WEST);
        JPanel fadd = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        fadd.setOpaque(false);
        fadd.add(btn("Dosya ekle…", Widgets.Kind.TONAL, Widgets.Ico.FILE_ADD, () -> choose(false)));
        fadd.add(btn("Klasör ekle…", Widgets.Kind.TONAL, Widgets.Ico.FOLDER_ADD, () -> choose(true)));
        ftop.add(fadd, BorderLayout.EAST);
        filesPanel.add(ftop, BorderLayout.NORTH);
        filesPanel.add(fileScroll, BorderLayout.CENTER);

        JPanel fbottom = new JPanel();
        fbottom.setOpaque(false);
        fbottom.setLayout(new BoxLayout(fbottom, BoxLayout.Y_AXIS));
        JPanel hintRow = new JPanel(new BorderLayout(8, 0));
        hintRow.setOpaque(false);
        fileSummary.setFont(Theme.font(13f, Font.PLAIN));
        fileSummary.setForeground(Theme.DIM);
        hintRow.add(fileSummary, BorderLayout.CENTER);
        JPanel hr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        hr.setOpaque(false);
        hr.add(btn("Seçileni kaldır", Widgets.Kind.TEXT, Widgets.Ico.TRASH, this::removeSelectedFiles));
        hr.add(btn("Temizle", Widgets.Kind.TEXT, Widgets.Ico.BROOM, () -> { fileModel.clear(); updateSummary(); }));
        hintRow.add(hr, BorderLayout.EAST);
        fbottom.add(hintRow);
        fbottom.add(Box.createVerticalStrut(6));
        JPanel sendRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        sendRow.setOpaque(false);
        sendRow.add(btn("Bağlantı oluştur", Widgets.Kind.TONAL, Widgets.Ico.LINK, this::createLink));
        sendBtn = btn("Cihaza gönder", Widgets.Kind.PRIMARY, Widgets.Ico.SEND, this::sendToSelected);
        sendRow.add(sendBtn);
        fbottom.add(sendRow);
        filesPanel.add(fbottom, BorderLayout.SOUTH);

        JPanel columns = new JPanel(new BorderLayout(12, 0));
        columns.setOpaque(false);
        columns.add(devices, BorderLayout.WEST);
        columns.add(filesPanel, BorderLayout.CENTER);

        // ---- transfers
        transfers.setLayout(new BoxLayout(transfers, BoxLayout.Y_AXIS));
        transfers.setOpaque(false);
        transfersEmpty.setFont(Theme.font(13f, Font.PLAIN));
        transfersEmpty.setForeground(Theme.DIM);
        transfersEmpty.setVerticalAlignment(JLabel.TOP);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(transfers, BorderLayout.NORTH);
        holder.add(transfersEmpty, BorderLayout.CENTER);
        Widgets.Card trPanel = new Widgets.Card(16);
        trPanel.setLayout(new BorderLayout(0, 8));
        trPanel.setPreferredSize(new Dimension(100, 230));
        JPanel trTop = new JPanel(new BorderLayout());
        trTop.setOpaque(false);
        trTop.add(new Widgets.Section("Aktarımlar", Widgets.Ico.SPEED, violet()), BorderLayout.WEST);
        trTop.add(btn("Bitenleri temizle", Widgets.Kind.TEXT, Widgets.Ico.BROOM, this::clearFinishedTransfers), BorderLayout.EAST);
        trPanel.add(trTop, BorderLayout.NORTH);
        trPanel.add(scroll(holder), BorderLayout.CENTER);

        root.add(columns, BorderLayout.CENTER);
        root.add(trPanel, BorderLayout.SOUTH);
        updateHint();
        return root;
    }

    private static Widgets.Btn btn(String text, Widgets.Kind kind, Runnable r) {
        return btn(text, kind, Widgets.Ico.NONE, r);
    }

    private static Widgets.Btn btn(String text, Widgets.Kind kind, Widgets.Ico icon, Runnable r) {
        Widgets.Btn b = new Widgets.Btn(text, kind, icon);
        b.addActionListener(e -> r.run());
        return b;
    }

    private static Color teal() { return Theme.dark ? new Color(0x4DB6AC) : new Color(0x00897B); }

    private static Color violet() { return Theme.dark ? new Color(0xB39DDB) : new Color(0x6A1B9A); }

    private static JLabel label(String text, float size, int style, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(size, style));
        l.setForeground(c);
        return l;
    }

    private static JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.getVerticalScrollBar().setUnitIncrement(16);
        s.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Widgets.slimScroll(s);
        return s;
    }

    private static String fit(String s, FontMetrics fm, int max) {
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Centered placeholder text (optionally inside a dashed drop zone) for an empty list. */
    private static void drawEmpty(Graphics gr, JComponent c, boolean dashed, String title, String l1, String l2, String l3) {
        Graphics2D g = Widgets.g2(gr);
        int w = c.getWidth(), h = c.getHeight();
        if (dashed) {
            g.setColor(Theme.STROKE);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{8f, 7f}, 0));
            g.draw(new RoundRectangle2D.Float(2, 2, w - 5, h - 5, 18, 18));
        }
        int y = h / 2 - (dashed ? 30 : 34);
        if (dashed) { if (h >= 125) Widgets.dropIcon(g, w / 2, y - 6); y += h >= 125 ? 40 : 0; }
        else { Widgets.radar(g, w / 2, y - 14); y += 16; }
        g.setFont(Theme.font(14.5f, Font.BOLD));
        g.setColor(Theme.TEXT);
        FontMetrics fm = g.getFontMetrics();
        String t = fit(title, fm, w - 24);
        g.drawString(t, (w - fm.stringWidth(t)) / 2, y);
        g.setFont(Theme.font(12.5f, Font.PLAIN));
        g.setColor(Theme.DIM);
        fm = g.getFontMetrics();
        String[] ls = {l1, l2, l3};
        for (String s : ls) {
            if (s == null) continue;
            y += 20;
            String f = fit(s, fm, w - 24);
            g.drawString(f, (w - fm.stringWidth(f)) / 2, y);
        }
        g.dispose();
    }

    private static String osName(String os) {
        if ("windows".equals(os)) return "Windows";
        if ("android".equals(os)) return "Android";
        return os;
    }

    /** Card-style row for a nearby device. */
    private static final class PeerCell extends JPanel implements ListCellRenderer<Peer> {
        private Peer p;
        private boolean sel;

        PeerCell() { setOpaque(false); }

        @Override public Component getListCellRendererComponent(JList<? extends Peer> l, Peer v, int i, boolean s, boolean f) {
            p = v;
            sel = s;
            return this;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 72); }

        @Override protected void paintComponent(Graphics gr) {
            if (p == null) return;
            Graphics2D g = Widgets.g2(gr);
            int w = getWidth(), h = getHeight() - 8;
            g.setColor(sel ? Theme.TONAL : Theme.CARD);
            g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h, 16, 16));
            g.setColor(sel ? Theme.PRIMARY : Theme.STROKE);
            g.setStroke(new BasicStroke(sel ? 2f : 1f));
            g.draw(new RoundRectangle2D.Float(sel ? 1 : 0.5f, sel ? 1 : 0.5f, w - (sel ? 3 : 2), h - (sel ? 2 : 1), 16, 16));
            Widgets.deviceIcon(g, 12, (h - 44) / 2, 44, !"android".equals(p.os), sel);
            g.setFont(Theme.font(14.5f, Font.BOLD));
            g.setColor(Theme.TEXT);
            g.drawString(fit(p.name, g.getFontMetrics(), w - 80), 68, h / 2 - 3);
            g.setFont(Theme.font(12f, Font.PLAIN));
            g.setColor(Theme.DIM);
            g.drawString(fit(osName(p.os) + "  •  " + p.host, g.getFontMetrics(), w - 80), 68, h / 2 + 15);
            g.dispose();
        }
    }

    /** Row for a file or folder queued for sending. */
    private static final class FileCell extends JPanel implements ListCellRenderer<File> {
        private File f;
        private boolean sel;

        FileCell() { setOpaque(false); }

        @Override public Component getListCellRendererComponent(JList<? extends File> l, File v, int i, boolean s, boolean fo) {
            f = v;
            sel = s;
            return this;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(100, 48); }

        @Override protected void paintComponent(Graphics gr) {
            if (f == null) return;
            Graphics2D g = Widgets.g2(gr);
            int w = getWidth(), h = getHeight() - 4;
            if (sel) {
                g.setColor(Theme.TONAL);
                g.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
            }
            Widgets.fileIcon(g, 12, (h - 18) / 2, f.isDirectory(), sel);
            String right = f.isDirectory() ? "klasör" : NetUtil.formatSize(f.length());
            g.setFont(Theme.font(12f, Font.PLAIN));
            g.setColor(Theme.DIM);
            FontMetrics fm = g.getFontMetrics();
            int rw = fm.stringWidth(right);
            g.drawString(right, w - rw - 12, h / 2 + 5);
            int max = w - rw - 80;
            g.setFont(Theme.font(13.5f, Font.BOLD));
            g.setColor(Theme.TEXT);
            g.drawString(fit(f.getName(), g.getFontMetrics(), max), 44, h / 2 - 2);
            g.setFont(Theme.font(11.5f, Font.PLAIN));
            g.setColor(Theme.DIM);
            String parent = f.getParent() == null ? "" : f.getParent();
            g.drawString(fit(parent, g.getFontMetrics(), max), 44, h / 2 + 13);
            g.dispose();
        }
    }

    private void removeSelectedFiles() {
        List<File> sel = fileList.getSelectedValuesList();
        for (File f : sel) fileModel.removeElement(f);
        updateSummary();
    }

    private void clearFinishedTransfers() {
        for (Component c : transfers.getComponents()) {
            if (c instanceof JPanel && ((JPanel) c).getComponentCount() > 0
                    && ((JPanel) c).getComponent(0) instanceof TransferRow
                    && ((TransferRow) ((JPanel) c).getComponent(0)).isFinished()) transfers.remove(c);
        }
        transfers.revalidate();
        transfers.repaint();
        updateTransfersEmpty();
    }

    private void updateTransfersEmpty() {
        transfersEmpty.setVisible(transfers.getComponentCount() == 0);
    }

    private void updateHint() {
        Peer peer = peerList.getSelectedValue();
        List<FileSource> s = LocalFileSource.collect(selectedRoots());
        if (s.isEmpty()) {
            fileSummary.setText("Önce dosya veya klasör ekleyin");
            fileSummary.setForeground(Theme.DIM);
            return;
        }
        long total = 0;
        for (FileSource f : s) total += f.size();
        String what = s.size() + " dosya, " + NetUtil.formatSize(total);
        if (peer == null) {
            fileSummary.setText(what + "  —  soldan bir cihaz seçin");
            fileSummary.setForeground(Theme.DIM);
        } else {
            fileSummary.setText(what + "  →  " + peer.name);
            fileSummary.setForeground(Theme.SUCCESS);
        }
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
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
        updateHint();
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
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        wrap.add(r, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap.getPreferredSize().height));
        transfers.add(wrap, 0);
        transfers.revalidate();
        transfers.repaint();
        updateTransfersEmpty();
        return r;
    }

    // ------------------------------------------------------------------ actions

    private Share publishSelection() {
        List<FileSource> files = LocalFileSource.collect(selectedRoots());
        if (files.isEmpty()) {
            Dialogs.message(frame, "LanBeam", "Önce gönderilecek dosya veya klasör ekleyin.", Dialogs.Level.INFO);
            return null;
        }
        return node.publish(files);
    }

    private void sendToSelected() {
        Peer peer = peerList.getSelectedValue();
        if (peer == null) {
            Dialogs.message(frame, "LanBeam", "Soldaki listeden bir cihaz seçin.\nCihaz görünmüyorsa uygulamanın açık ve aynı ağda olduğundan emin olun\nveya \"IP ile ekle\" kullanın.", Dialogs.Level.INFO);
            return;
        }
        final Share share = publishSelection();
        if (share == null) return;
        final TransferRow row = addRow("↑ " + peer.name + " cihazına: " + share.files.size() + " dosya, " + NetUtil.formatSize(share.total),
                () -> node.unpublish(share));
        sendRows.put(share.token, row);
        offerTokens.add(share.token);
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
        TransferRow row = addRow("Bağlantı: " + url, null);
        sendRows.put(share.token, row);
        row.finish("Paylaşımda — telefonda LanBeam'de \"Bağlantıdan al\" ya da tarayıcıda açın", "Kapat",
                () -> { node.unpublish(share); row.finish("Paylaşım kapatıldı", null, null); });
        JTextField f = Dialogs.field(url);
        f.setEditable(false);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
        JPanel body = Dialogs.column();
        body.add(Dialogs.caption("Paylaşım bağlantısı (panoya kopyalandı)"));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(f);
        body.add(Box.createVerticalStrut(10));
        JLabel note = label("<html><body style='width:340px'>Aynı ağdaki herhangi bir cihaz bu adresi tarayıcıda açarak indirebilir. "
                + "LanBeam uygulamasında \"Bağlantıdan al\" ile açılırsa hızlı akış kullanılır.</body></html>", 12.5f, Font.PLAIN, Theme.DIM);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(note);
        Dialogs.form(frame, "Bağlantı", body, "Tamam");
    }

    private void addPeerByIp() {
        String s = Dialogs.input(frame, "IP ile ekle", "Cihazın IP adresi (örn. 192.168.1.34 veya 192.168.1.34:8080):");
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
                SwingUtilities.invokeLater(() -> Dialogs.message(frame, "LanBeam", "Cihaza ulaşılamadı: " + e.getMessage(), Dialogs.Level.WARN));
            }
        }).start();
    }

    private void receiveFromLink() {
        String s = Dialogs.input(frame, "Bağlantıdan al", "Paylaşım bağlantısı (http://ip:port/s/...):");
        if (s == null || s.trim().isEmpty()) return;
        try {
            startReceive(ShareLink.parse(s), "bağlantı");
        } catch (IllegalArgumentException e) {
            Dialogs.message(frame, "LanBeam", "Geçersiz bağlantı.", Dialogs.Level.WARN);
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
        holder[0] = addRow("↓ " + from + " → " + dir.getAbsolutePath(), rx::cancel);
        rx.startThread();
    }

    private static void openFolder(File dir) {
        try { Desktop.getDesktop().open(dir); } catch (Exception ignored) {}
    }

    private void settings() {
        JTextField name = Dialogs.field(deviceName());
        JTextField dir = Dialogs.field(saveDir().getAbsolutePath());
        Widgets.Btn browse = new Widgets.Btn("Seç…", Widgets.Kind.TONAL, Widgets.Ico.FOLDER);
        browse.addActionListener(ev -> {
            JFileChooser fc = new JFileChooser(dir.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) dir.setText(fc.getSelectedFile().getAbsolutePath());
        });
        JPanel dirRow = new JPanel(new BorderLayout(8, 0));
        dirRow.setOpaque(false);
        dirRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dirRow.add(dir, BorderLayout.CENTER);
        dirRow.add(browse, BorderLayout.EAST);
        JComboBox<String> theme = Dialogs.combo("Otomatik (Windows'a uyar)", "Açık", "Koyu");
        String cur = prefs.get("theme", "auto");
        theme.setSelectedIndex("light".equals(cur) ? 1 : "dark".equals(cur) ? 2 : 0);

        JPanel body = Dialogs.column();
        body.setPreferredSize(new Dimension(420, 230));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        theme.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(Dialogs.caption("Cihaz adı"));
        body.add(name);
        body.add(Box.createVerticalStrut(14));
        body.add(Dialogs.caption("Gelen dosyaların kaydedileceği klasör"));
        body.add(dirRow);
        body.add(Box.createVerticalStrut(14));
        body.add(Dialogs.caption("Tema (uygulama yeniden açılınca değişir)"));
        body.add(theme);
        if (!Dialogs.form(frame, "Ayarlar", body, "Kaydet")) return;
        File d = new File(dir.getText().trim());
        if (!d.isDirectory() && !d.mkdirs()) {
            Dialogs.message(frame, "LanBeam", "Klasör oluşturulamadı.", Dialogs.Level.WARN);
            return;
        }
        prefs.put("saveDir", d.getAbsolutePath());
        prefs.put("theme", theme.getSelectedIndex() == 1 ? "light" : theme.getSelectedIndex() == 2 ? "dark" : "auto");
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
            boolean yes = Dialogs.confirmHtml(frame, "Gelen dosyalar",
                    "<b>" + Dialogs.esc(offer.fromName) + "</b> size dosya göndermek istiyor:<br><br>"
                            + Dialogs.esc(offer.describe()) + "<br><br><span style='color:" + hex(Theme.DIM) + "'>Kayıt yeri: "
                            + Dialogs.esc(saveDir().getAbsolutePath()) + "</span>", "Kabul et", "Reddet");
            if (yes) startReceive(offer.link, offer.fromName);
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
        if (offerTokens.remove(share.token)) {
            // Plain-HTTP share: do not leave it downloadable once the chosen receiver is done.
            node.unpublish(share);
            row.finish(ok ? "Tamamlandı" : "Bağlantı kesildi", null, null);
            return;
        }
        if (ok) {
            row.finish("Tamamlandı", "Paylaşımı kapat", () -> { node.unpublish(share); row.finish("Tamamlandı", null, null); });
        } else {
            row.finish("Bağlantı kesildi", "Paylaşımı kapat", () -> { node.unpublish(share); row.finish("Kapatıldı", null, null); });
        }
    }

    @Override public void onRejected(Share share) {
        node.unpublish(share);
        offerTokens.remove(share.token);
        TransferRow row = sendRows.remove(share.token);
        if (row != null) row.finish("Karşı taraf reddetti", null, null);
    }
}
