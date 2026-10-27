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
