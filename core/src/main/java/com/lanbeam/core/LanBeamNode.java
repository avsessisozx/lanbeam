package com.lanbeam.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** One LanBeam device: HTTP server + discovery + share registry. Used as-is by the PC and Android apps. */
public final class LanBeamNode {

    public interface Listener {
        void onPeersChanged(List<Peer> peers);

        /** Someone wants to send us files. Call {@link #reject(Offer)} or start a {@link Receiver} on offer.link. */
        void onOffer(Offer offer);

        /** Bytes of a share served to a receiver (sender-side progress). */
        void onSendProgress(Share share, long sent, long total, double bytesPerSec);

        void onSendFinished(Share share, boolean ok);

        void onRejected(Share share);
    }

    public final String id;
    private volatile String name;
    public final String os;
    private final Listener listener;
    private final Map<String, Share> shares = new ConcurrentHashMap<>();
    private final HttpServer http;
    private final Discovery discovery;
    private int port = -1;

    public LanBeamNode(String id, String name, String os, Listener listener) {
        this.id = id;
        this.name = name;
        this.os = os;
        this.listener = listener;
        this.http = new HttpServer(this::route);
        this.discovery = new Discovery(id, listener::onPeersChanged);
    }

    public void start() throws IOException {
        port = http.start(Protocol.HTTP_PORT, Protocol.HTTP_PORT_ATTEMPTS);
        try {
            discovery.start(name, os, port);
        } catch (IOException e) {
            // Discovery is a convenience; links and manual IPs still work without it.
            System.err.println("LanBeam discovery disabled: " + e);
        }
    }

    public void stop() {
        discovery.stop();
        http.stop();
    }

    public int port() { return port; }

    public String name() { return name; }

    public List<Peer> peers() { return discovery.peers(); }

    public void refreshPeers() { discovery.announceNow(); }

    public Share publish(List<FileSource> files) {
        Share s = new Share(files);
        shares.put(s.token, s);
        return s;
    }

    public void unpublish(Share s) {
        shares.remove(s.token);
    }

    public ShareLink linkFor(Share s, String host) {
        return new ShareLink(host, port, s.token, null, null);
    }

    /** Pushes an offer to a peer; the peer pulls the share if its user accepts. */
    public void sendOffer(Peer peer, Share share) throws IOException {
        sendOffer(peer, share, null, null);
    }

    /** With ssid/pass the receiver first joins that Wi-Fi (the sender's hotspot) and pulls over the direct link. */
    public void sendOffer(Peer peer, Share share, String ssid, String pass) throws IOException {
        String first = share.files.isEmpty() ? "" : share.files.get(0).path();
        int slash = first.lastIndexOf('/');
        Offer o = new Offer(id, name, new ShareLink("-", port, share.token, ssid, pass), share.files.size(), share.total, first.substring(slash + 1));
        post(peer.baseUrl() + "/api/offer", o.toJson());
    }

    /** Asks a device directly (for networks where broadcast is blocked). */
    public static Peer probe(String host, int port) throws IOException {
        Map<String, Object> m = Json.parseObject(new String(Receiver.get("http://" + host + ":" + port + "/api/info"), HttpServer.UTF8));
        if (!Protocol.APP.equals(m.get("app"))) throw new IOException("Not a LanBeam device");
        return new Peer(Json.str(m, "id", host), Json.str(m, "name", host), Json.str(m, "os", "?"), host, port);
    }

    public void reject(Offer offer) {
        new Thread(() -> {
            try { post("http://" + offer.link.host + ":" + offer.link.port + "/s/" + offer.link.token + "/reject", "{}"); }
            catch (IOException ignored) {}
        }, "lanbeam-reject").start();
    }

    private static void post(String url, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] b = json.getBytes(HttpServer.UTF8);
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream o = c.getOutputStream()) { o.write(b); }
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
        } finally {
            c.disconnect();
        }
    }

    // ---------------------------------------------------------------- HTTP routes

    private void route(HttpServer.Request req, HttpServer.Response res) throws IOException {
        String p = req.path;
        if (p.equals("/api/info")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("app", Protocol.APP);
            m.put("v", Protocol.VERSION);
            m.put("id", id);
            m.put("name", name);
            m.put("os", os);
            m.put("port", port);
            res.json(200, Json.write(m));
            return;
        }
        if (p.equals("/api/offer") && req.method.equals("POST")) {
            Offer o = Offer.fromJson(req.bodyText(64 * 1024), req.remoteAddr);
            res.json(200, "{\"status\":\"pending\"}");
            listener.onOffer(o);
            return;
        }
        if (p.equals("/api/speedtest")) {
            if (req.method.equals("POST")) {
                SpeedTest.serveUpload(req, res);
            } else {
                long n = SpeedTest.DEFAULT_BYTES;
                try { n = Long.parseLong(req.query.get("bytes")); } catch (Exception ignored) {}
                SpeedTest.serveDownload(Math.max(0, Math.min(n, SpeedTest.MAX_BYTES)), res);
            }
            return;
        }
        if (p.equals("/")) {
            res.send(200, "text/html; charset=utf-8", page("LanBeam", "<p>Bu cihazda LanBeam çalışıyor. Bir paylaşım bağlantısı açın.</p>"));
            return;
        }
        if (!p.startsWith("/s/")) {
            res.text(404, "Not found");
            return;
        }
        String[] parts = p.substring(3).split("/", 4);
        Share s = shares.get(parts[0]);
        if (s == null) {
            res.text(404, "Paylaşım bulunamadı veya kapatıldı");
            return;
        }
        String action = parts.length > 1 ? parts[1] : "";
        switch (action) {
            case "":
                res.send(200, "text/html; charset=utf-8", sharePage(s));
                break;
            case "list":
                res.json(200, s.listJson());
                break;
            case "stream":
                serveStream(s, req, res);
                break;
            case "file":
                serveFile(s, parts.length > 2 ? parts[2] : "", req, res);
                break;
            case "zip":
                serveZip(s, res);
                break;
            case "reject":
                res.json(200, "{}");
                listener.onRejected(s);
                break;
            default:
                res.text(404, "Not found");
        }
    }

    /** All files back to back, raw. Optional ?from=N resumes at byte N of the whole stream. */
    private void serveStream(Share s, HttpServer.Request req, HttpServer.Response res) throws IOException {
        long from = 0;
        try { from = Long.parseLong(req.query.containsKey("from") ? req.query.get("from") : "0"); } catch (NumberFormatException ignored) {}
        from = Math.max(0, Math.min(from, s.total));
        OutputStream out = res.start(200, "application/octet-stream", s.total - from);
        byte[] buf = new byte[Protocol.COPY_BUFFER];
        long sent = from;
        long started = System.currentTimeMillis();
        long lastReport = 0;
        boolean ok = false;
        String err = null;
        long diskNs = 0, netNs = 0;
        double peak = 0;
        try {
            long skip = from;
            for (FileSource f : s.files) {
                if (skip >= f.size()) { skip -= f.size(); continue; }
                try (InputStream in = f.open()) {
                    skipFully(in, skip);
                    long left = f.size() - skip;
                    skip = 0;
                    while (left > 0) {
                        long a = System.nanoTime();
                        int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                        long b = System.nanoTime();
                        if (n < 0) throw new IOException("File shrank");
                        out.write(buf, 0, n);
                        netNs += System.nanoTime() - b;
                        diskNs += b - a;
                        left -= n;
                        sent += n;
                        long now = System.currentTimeMillis();
                        if (now - lastReport >= Protocol.PROGRESS_INTERVAL_MS) {
                            lastReport = now;
                            double bps = (sent - from) / Math.max(0.001, (now - started) / 1000.0);
                            peak = Math.max(peak, bps);
                            listener.onSendProgress(s, sent, s.total, bps);
                        }
                    }
                }
            }
            out.flush();
            ok = true;
        } catch (IOException e) {
            err = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            double secs = Math.max(0.001, (System.currentTimeMillis() - started) / 1000.0);
            Diagnostics.TransferStat st = new Diagnostics.TransferStat();
            st.sent = true; st.bytes = sent - from; st.seconds = secs; st.peakBps = Math.max(peak, (sent - from) / secs);
            st.netSeconds = netNs / 1e9; st.diskSeconds = diskNs / 1e9; st.ok = ok; st.error = err;
            Diagnostics.transfer(st);
            listener.onSendProgress(s, sent, s.total, (sent - from) / secs);
            listener.onSendFinished(s, ok);
        }
    }

    private void serveFile(Share s, String index, HttpServer.Request req, HttpServer.Response res) throws IOException {
        int i;
        try { i = Integer.parseInt(index); } catch (NumberFormatException e) { res.text(400, "Bad index"); return; }
        if (i < 0 || i >= s.files.size()) { res.text(404, "Not found"); return; }
        FileSource f = s.files.get(i);
        long start = 0, end = f.size() - 1;
        int status = 200;
        String range = req.header("range");
        if (range != null && range.startsWith("bytes=")) {
            String[] r = range.substring(6).split("-", 2);
            try {
                if (!r[0].isEmpty()) start = Long.parseLong(r[0]);
                if (r.length > 1 && !r[1].isEmpty()) end = Math.min(end, Long.parseLong(r[1]));
                if (r[0].isEmpty() && r.length > 1) { start = f.size() - Long.parseLong(r[1]); end = f.size() - 1; }
            } catch (NumberFormatException e) { res.text(400, "Bad range"); return; }
            if (start > end || start < 0) {
                res.headers.put("Content-Range", "bytes */" + f.size());
                res.text(416, "Bad range");
                return;
            }
            status = 206;
            res.headers.put("Content-Range", "bytes " + start + "-" + end + "/" + f.size());
        }
        String name = f.path().substring(f.path().lastIndexOf('/') + 1);
        res.headers.put("Accept-Ranges", "bytes");
        res.headers.put("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(name, "UTF-8").replace("+", "%20"));
        long len = end - start + 1;
        OutputStream out = res.start(status, "application/octet-stream", len);
        byte[] buf = new byte[Protocol.COPY_BUFFER];
        try (InputStream in = f.open()) {
            skipFully(in, start);
            while (len > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, len));
                if (n < 0) break;
                out.write(buf, 0, n);
                len -= n;
            }
        }
    }

    private void serveZip(Share s, HttpServer.Response res) throws IOException {
        res.headers.put("Content-Disposition", "attachment; filename=\"lanbeam.zip\"");
        ZipOutputStream zip = new ZipOutputStream(res.start(200, "application/zip", -1), HttpServer.UTF8);
        zip.setLevel(Deflater.NO_COMPRESSION);
        byte[] buf = new byte[Protocol.COPY_BUFFER];
        for (FileSource f : s.files) {
            zip.putNextEntry(new ZipEntry(f.path()));
            try (InputStream in = f.open()) {
                int n;
                while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
            }
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        byte[] scratch = null;
        while (n > 0) {
            long k = in.skip(n);
            if (k <= 0) {
                if (scratch == null) scratch = new byte[64 * 1024];
                int r = in.read(scratch, 0, (int) Math.min(scratch.length, n));
                if (r < 0) throw new IOException("Unexpected end of file");
                k = r;
            }
            n -= k;
        }
    }

    // ---------------------------------------------------------------- browser page

    private byte[] sharePage(Share s) throws IOException {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < s.files.size(); i++) {
            FileSource f = s.files.get(i);
            String name = f.path().substring(f.path().lastIndexOf('/') + 1);
            rows.append("<tr><td><a href=\"").append(s.token).append("/file/").append(i).append('/')
                    .append(URLEncoder.encode(name, "UTF-8").replace("+", "%20")).append("\">")
                    .append(esc(f.path())).append("</a></td><td class=r>")
                    .append(NetUtil.formatSize(f.size())).append("</td></tr>");
        }
        String body = "<p><b>" + esc(name) + "</b> sizinle " + s.files.size() + " dosya paylaşıyor ("
                + NetUtil.formatSize(s.total) + ").</p>"
                + "<p><a class=btn href=\"" + s.token + "/zip\">Hepsini ZIP olarak indir</a></p>"
                + "<table><tr><th>Dosya</th><th class=r>Boyut</th></tr>" + rows + "</table>";
        return page("LanBeam – " + name, body);
    }

    private static byte[] page(String title, String body) {
        String html = "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content=\"width=device-width,initial-scale=1\"><title>" + esc(title) + "</title>"
                + "<base href=\"/s/\"><style>body{font-family:system-ui,sans-serif;max-width:760px;margin:24px auto;padding:0 16px;color:#222}"
                + "table{width:100%;border-collapse:collapse}td,th{padding:8px;border-bottom:1px solid #ddd;text-align:left;word-break:break-all}"
                + ".r{text-align:right;white-space:nowrap}.btn{display:inline-block;background:#1565c0;color:#fff;padding:10px 16px;"
                + "border-radius:6px;text-decoration:none}</style></head><body><h2>" + esc(title) + "</h2>" + body + "</body></html>";
        return html.getBytes(HttpServer.UTF8);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
