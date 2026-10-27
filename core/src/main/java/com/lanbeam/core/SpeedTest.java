package com.lanbeam.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Measures the raw link to another LanBeam device and the local storage write speed (nothing is saved). */
public final class SpeedTest {
    private SpeedTest() {}

    public static final long DEFAULT_BYTES = 48L * 1024 * 1024;
    public static final long MAX_BYTES = 256L * 1024 * 1024;

    public static final class Result {
        public double downloadBps;
        public double uploadBps;
        public String summary() {
            return String.format(Locale.ROOT, "indirme %s, yükleme %s", NetUtil.formatSpeed(downloadBps), NetUtil.formatSpeed(uploadBps));
        }
    }

    /** Server side of GET: sends n zero bytes. */
    static void serveDownload(long n, HttpServer.Response res) throws IOException {
        OutputStream out = res.start(200, "application/octet-stream", n);
        byte[] buf = new byte[Protocol.COPY_BUFFER];
        while (n > 0) {
            int k = (int) Math.min(buf.length, n);
            out.write(buf, 0, k);
            n -= k;
        }
    }

    /** Server side of POST: drains the body. */
    static void serveUpload(HttpServer.Request req, HttpServer.Response res) throws IOException {
        long total = 0;
        try { total = Long.parseLong(req.header("content-length")); } catch (Exception ignored) {}
        total = Math.min(total, MAX_BYTES);
        byte[] buf = new byte[Protocol.COPY_BUFFER];
        long got = 0;
        while (got < total) {
            int n = req.bodyStream().read(buf, 0, (int) Math.min(buf.length, total - got));
            if (n < 0) break;
            got += n;
        }
        res.json(200, "{\"bytes\":" + got + "}");
    }

    public static Result run(String host, int port, long bytes) throws IOException {
        Result r = new Result();
        String base = "http://" + host + ":" + port + "/api/speedtest";
        HttpURLConnection c = (HttpURLConnection) new URL(base + "?bytes=" + bytes).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(20000);
        try {
            long t0 = System.nanoTime();
            long got = 0;
            byte[] buf = new byte[Protocol.COPY_BUFFER];
            try (InputStream in = c.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) got += n;
            }
            r.downloadBps = got / Math.max(1e-3, (System.nanoTime() - t0) / 1e9);
        } finally {
            c.disconnect();
        }
        HttpURLConnection u = (HttpURLConnection) new URL(base).openConnection();
        u.setConnectTimeout(5000);
        u.setReadTimeout(20000);
        u.setRequestMethod("POST");
        u.setDoOutput(true);
        u.setFixedLengthStreamingMode(bytes);
        try {
            long t0 = System.nanoTime();
            byte[] buf = new byte[Protocol.COPY_BUFFER];
            try (OutputStream o = u.getOutputStream()) {
                long left = bytes;
                while (left > 0) {
                    int k = (int) Math.min(buf.length, left);
                    o.write(buf, 0, k);
                    left -= k;
                }
            }
            if (u.getResponseCode() != 200) throw new IOException("HTTP " + u.getResponseCode());
            r.uploadBps = bytes / Math.max(1e-3, (System.nanoTime() - t0) / 1e9);
        } finally {
            u.disconnect();
        }
        Diagnostics.test("Ağ testi (karşı cihazdan indirme)", NetUtil.formatSpeed(r.downloadBps));
        Diagnostics.test("Ağ testi (karşı cihaza yükleme)", NetUtil.formatSpeed(r.uploadBps));
        return r;
    }


    /** Download and upload with 1, 2 and 4 parallel connections: shows whether a single stream is the limit. */
    public static String runParallel(final String host, final int port, long bytesPerStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int n : new int[]{1, 2, 4}) {
            double down = parallel(host, port, n, bytesPerStream, false);
            double up = parallel(host, port, n, bytesPerStream, true);
            String line = n + " bağlantı: indirme " + NetUtil.formatSpeed(down) + ", yükleme " + NetUtil.formatSpeed(up);
            Diagnostics.test("Paralel test (" + n + ")", "indirme " + NetUtil.formatSpeed(down) + ", yükleme " + NetUtil.formatSpeed(up));
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static double parallel(final String host, final int port, int n, final long bytes, final boolean upload) throws IOException {
        final IOException[] err = new IOException[1];
        Thread[] ts = new Thread[n];
        final String base = "http://" + host + ":" + port + "/api/speedtest";
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            ts[i] = new Thread(() -> {
                try {
                    byte[] buf = new byte[Protocol.COPY_BUFFER];
                    if (!upload) {
                        HttpURLConnection c = (HttpURLConnection) new URL(base + "?bytes=" + bytes).openConnection();
                        c.setConnectTimeout(5000);
                        c.setReadTimeout(20000);
                        try (InputStream in = c.getInputStream()) { while (in.read(buf) > 0) { } } finally { c.disconnect(); }
                    } else {
                        HttpURLConnection u = (HttpURLConnection) new URL(base).openConnection();
                        u.setConnectTimeout(5000);
                        u.setReadTimeout(20000);
                        u.setRequestMethod("POST");
                        u.setDoOutput(true);
                        u.setFixedLengthStreamingMode(bytes);
                        try {
                            try (OutputStream o = u.getOutputStream()) {
                                long left = bytes;
                                while (left > 0) { int k = (int) Math.min(buf.length, left); o.write(buf, 0, k); left -= k; }
                            }
                            if (u.getResponseCode() != 200) throw new IOException("HTTP " + u.getResponseCode());
                        } finally { u.disconnect(); }
                    }
                } catch (IOException e) {
                    err[0] = e;
                }
            }, "lanbeam-speed-" + i);
            ts[i].start();
        }
        for (Thread t : ts) {
            try { t.join(); } catch (InterruptedException e) { throw new IOException("interrupted"); }
        }
        if (err[0] != null) throw err[0];
        return n * bytes / Math.max(1e-3, (System.nanoTime() - t0) / 1e9);
    }

    /** Writes bytes into a temporary file in dir (flushed to disk) and deletes it. Returns bytes/second. */
    public static double diskWrite(File dir, long bytes) throws IOException {
        File f = File.createTempFile("lanbeam-speed", ".tmp", dir);
        try {
            byte[] buf = new byte[Protocol.COPY_BUFFER];
            for (int i = 0; i < buf.length; i += 4096) buf[i] = (byte) (i * 31);
            long t0 = System.nanoTime();
            try (FileOutputStream o = new FileOutputStream(f)) {
                long left = bytes;
                while (left > 0) {
                    int k = (int) Math.min(buf.length, left);
                    o.write(buf, 0, k);
                    left -= k;
                }
                o.getFD().sync();
            }
            double bps = bytes / Math.max(1e-3, (System.nanoTime() - t0) / 1e9);
            Diagnostics.test("Depolama yazma hızı", NetUtil.formatSpeed(bps));
            return bps;
        } finally {
            f.delete();
        }
    }
}
