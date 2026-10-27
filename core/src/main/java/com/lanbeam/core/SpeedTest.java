package com.lanbeam.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures the raw link to another LanBeam device and the local storage write speed (nothing is saved).
 * Network tests are time based: the first second (Wi-Fi rate adaptation, TCP slow start) is not counted,
 * then bytes are counted for a fixed time. A short fixed-size test reads far below the real transfer speed.
 */
public final class SpeedTest {
    private SpeedTest() {}

    /** Server default when a caller does not ask for a size (older clients). */
    public static final long DEFAULT_BYTES = 48L * 1024 * 1024;
    /** Upper bound per stream; the test stops on time long before this on any real link. */
    public static final long MAX_BYTES = 4L * 1024 * 1024 * 1024;
    static final long WARMUP_NS = TimeUnit.SECONDS.toNanos(1);
    static final long MEASURE_NS = TimeUnit.SECONDS.toNanos(4);

    public static final class Result {
        public double downloadBps;
        public double uploadBps;
        public String summary() {
            return String.format(Locale.ROOT, "indirme %s, yükleme %s", NetUtil.formatSpeed(downloadBps), NetUtil.formatSpeed(uploadBps));
        }
    }

    /** Lets the app hold its power locks while a peer measures against this device (Android Wi-Fi locks). */
    public interface ServerHook {
        void begin();

        void end();
    }

    public static volatile ServerHook serverHook;

    /** Server side of GET: sends n zero bytes. */
    static void serveDownload(long n, HttpServer.Response res) throws IOException {
        ServerHook h = serverHook;
        if (h != null) h.begin();
        try {
            OutputStream out = res.start(200, "application/octet-stream", n);
            byte[] buf = new byte[Protocol.COPY_BUFFER];
            while (n > 0) {
                int k = (int) Math.min(buf.length, n);
                out.write(buf, 0, k);
                n -= k;
            }
        } finally {
            if (h != null) h.end();
        }
    }

    /** Server side of POST: drains the body. */
    static void serveUpload(HttpServer.Request req, HttpServer.Response res) throws IOException {
        ServerHook h = serverHook;
        if (h != null) h.begin();
        try {
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
        } finally {
            if (h != null) h.end();
        }
    }

    /** One connection each way. maxBytes only bounds the test; it normally ends on time. */
    public static Result run(String host, int port, long maxBytes) throws IOException {
        Result r = new Result();
        String base = "http://" + host + ":" + port + "/api/speedtest";
        r.downloadBps = measure(base, 1, false, maxBytes);
        r.uploadBps = measure(base, 1, true, maxBytes);
        Diagnostics.test("Ağ testi (karşı cihazdan indirme)", NetUtil.formatSpeed(r.downloadBps));
        Diagnostics.test("Ağ testi (karşı cihaza yükleme)", NetUtil.formatSpeed(r.uploadBps));
        return r;
    }

    /** 2 and 4 parallel connections: shows whether a single stream is the limit (compare with {@link #run}). */
    public static String runParallel(final String host, final int port, long maxBytesPerStream) throws IOException {
        String base = "http://" + host + ":" + port + "/api/speedtest";
        StringBuilder sb = new StringBuilder();
        for (int n : new int[]{2, 4}) {
            double down = measure(base, n, false, maxBytesPerStream);
            double up = measure(base, n, true, maxBytesPerStream);
            String line = "indirme " + NetUtil.formatSpeed(down) + ", yükleme " + NetUtil.formatSpeed(up);
            Diagnostics.test("Paralel test (" + n + ")", line);
            if (sb.length() > 0) sb.append('\n');
            sb.append(n).append(" bağlantı: ").append(line);
        }
        return sb.toString();
    }

    /**
     * Moves zero bytes over n connections and returns bytes/second counted after the warm-up.
     * Connections are closed when the time is up; the peer just sees a client that went away.
     */
    private static double measure(final String base, int streams, final boolean upload, final long maxBytes) throws IOException {
        final AtomicLong moved = new AtomicLong();
        final AtomicBoolean stop = new AtomicBoolean();
        final CountDownLatch finished = new CountDownLatch(streams);
        final List<HttpURLConnection> conns = Collections.synchronizedList(new ArrayList<HttpURLConnection>());
        final IOException[] err = new IOException[1];
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < streams; i++) {
            Thread t = new Thread(() -> {
                try {
                    byte[] buf = new byte[Protocol.COPY_BUFFER];
                    HttpURLConnection c = (HttpURLConnection) new URL(upload ? base : base + "?bytes=" + maxBytes).openConnection();
                    conns.add(c);
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(20000);
                    if (upload) {
                        c.setRequestMethod("POST");
                        c.setDoOutput(true);
                        c.setFixedLengthStreamingMode(maxBytes);
                        // Not closed on purpose when stopped early: closing a short fixed-length body throws.
                        OutputStream o = c.getOutputStream();
                        long left = maxBytes;
                        while (left > 0 && !stop.get()) {
                            int k = (int) Math.min(buf.length, left);
                            o.write(buf, 0, k);
                            moved.addAndGet(k);
                            left -= k;
                        }
                        if (left == 0) {
                            o.close();
                            c.getResponseCode();
                        }
                    } else {
                        try (InputStream in = c.getInputStream()) {
                            int n;
                            while (!stop.get() && (n = in.read(buf)) > 0) moved.addAndGet(n);
                        }
                    }
                } catch (IOException e) {
                    if (!stop.get()) err[0] = e;
                } finally {
                    finished.countDown();
                }
            }, "lanbeam-speed");
            t.setDaemon(true);
            threads.add(t);
        }
        long t0 = System.nanoTime();
        for (Thread t : threads) t.start();
        try {
            finished.await(WARMUP_NS, TimeUnit.NANOSECONDS);
            long b1 = moved.get(), t1 = System.nanoTime();
            finished.await(MEASURE_NS, TimeUnit.NANOSECONDS);
            long b2 = moved.get(), t2 = System.nanoTime();
            stop.set(true);
            synchronized (conns) { for (HttpURLConnection c : conns) c.disconnect(); }
            for (Thread t : threads) t.join(2000);
            if (b2 == 0 && err[0] != null) throw err[0];
            // Finished during warm-up (tiny/old peer limit): fall back to the plain average.
            if (t2 - t1 < TimeUnit.MILLISECONDS.toNanos(300)) return b2 / Math.max(1e-3, (t2 - t0) / 1e9);
            return (b2 - b1) / ((t2 - t1) / 1e9);
        } catch (InterruptedException e) {
            stop.set(true);
            throw new IOException("interrupted");
        }
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
