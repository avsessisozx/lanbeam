package com.lanbeam.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pulls a share: GET /list for names and sizes, then GET /stream and cuts the single
 * raw byte stream into files by size (one connection, no per-file overhead: that is what makes it fast).
 */
public final class Receiver implements Runnable {

    /** Where received files go. PC: a folder. Android: MediaStore Downloads. */
    public interface FileSink {
        OutputStream create(String safeRelPath, long size) throws IOException;

        /** Called for a file that was not completed (delete the partial file). */
        void discard(String safeRelPath);

        /** Called after a file was fully written; returns a user-visible location. */
        String finish(String safeRelPath);
    }

    public interface Listener {
        void onStart(Receiver r, int count, long total);

        void onProgress(Receiver r, long done, long total, double bytesPerSec);

        void onFinished(Receiver r, boolean ok, String error, List<String> savedFiles);
    }

    public final ShareLink link;
    private final FileSink sink;
    private final Listener listener;
    private volatile boolean cancelled;
    private volatile HttpURLConnection current;

    public Receiver(ShareLink link, FileSink sink, Listener listener) {
        this.link = link;
        this.sink = sink;
        this.listener = listener;
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection c = current;
        if (c != null) c.disconnect();
    }

    public Thread startThread() {
        Thread t = new Thread(this, "lanbeam-receive");
        t.start();
        return t;
    }

    private static final class Entry {
        final String path;
        final long size;

        Entry(String path, long size) {
            this.path = path;
            this.size = size;
        }
    }

    @Override
    public void run() {
        List<String> saved = new ArrayList<>();
        String inProgress = null;
        try {
            List<Entry> entries = new ArrayList<>();
            long total = 0;
            Map<String, Object> list = Json.parseObject(new String(get(link.baseUrl() + "/list"), HttpServer.UTF8));
            for (Object o : (List<?>) list.get("files")) {
                @SuppressWarnings("unchecked") Map<String, Object> f = (Map<String, Object>) o;
                Entry e = new Entry(safePath(Json.str(f, "path", "file")), Json.num(f, "size", 0));
                entries.add(e);
                total += e.size;
            }
            listener.onStart(this, entries.size(), total);

            HttpURLConnection c = open(link.baseUrl() + "/stream");
            current = c;
            if (c.getResponseCode() != 200) throw new IOException("Sender answered HTTP " + c.getResponseCode());
            byte[] buf = new byte[Protocol.COPY_BUFFER];
            long done = 0;
            long started = System.currentTimeMillis();
            long lastReport = 0;
            try (InputStream in = c.getInputStream()) {
                for (Entry e : entries) {
                    if (cancelled) throw new IOException("Cancelled");
                    inProgress = e.path;
                    try (OutputStream out = sink.create(e.path, e.size)) {
                        long left = e.size;
                        while (left > 0) {
                            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                            if (n < 0) throw new IOException("Connection closed early");
                            out.write(buf, 0, n);
                            left -= n;
                            done += n;
                            long now = System.currentTimeMillis();
                            if (now - lastReport >= Protocol.PROGRESS_INTERVAL_MS) {
                                lastReport = now;
                                double secs = Math.max(0.001, (now - started) / 1000.0);
                                listener.onProgress(this, done, total, done / secs);
                            }
                            if (cancelled) throw new IOException("Cancelled");
                        }
                    }
                    saved.add(sink.finish(e.path));
                    inProgress = null;
                }
            }
            double secs = Math.max(0.001, (System.currentTimeMillis() - started) / 1000.0);
            listener.onProgress(this, done, total, done / secs);
            listener.onFinished(this, true, null, saved);
        } catch (Exception ex) {
            if (inProgress != null) sink.discard(inProgress);
            listener.onFinished(this, false, cancelled ? "İptal edildi" : String.valueOf(ex.getMessage()), saved);
        } finally {
            HttpURLConnection c = current;
            if (c != null) c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "lanbeam/" + Protocol.VERSION);
        c.setUseCaches(false);
        return c;
    }

    static byte[] get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return b.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    /** Never trust names from the network: no "..", no absolute paths, no characters Windows/Android reject. */
    public static String safePath(String p) {
        StringBuilder out = new StringBuilder();
        for (String part : p.replace('\\', '/').split("/")) {
            String s = part.replaceAll("[<>:\"|?*\\x00-\\x1f]", "_").trim();
            while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
            if (s.isEmpty() || s.equals("..")) continue;
            if (out.length() > 0) out.append('/');
            out.append(s);
        }
        return out.length() == 0 ? "file" : out.toString();
    }

    /** Saves into a folder, keeping sub-folders and never overwriting ("name (1).ext"). */
    public static final class DirectorySink implements FileSink {
        private final File root;
        private final Map<String, File> files = new java.util.HashMap<>();

        public DirectorySink(File root) {
            this.root = root;
        }

        @Override
        public OutputStream create(String rel, long size) throws IOException {
            File f = unique(new File(root, rel.replace('/', File.separatorChar)));
            File parent = f.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
            files.put(rel, f);
            return new java.io.BufferedOutputStream(new FileOutputStream(f), Protocol.COPY_BUFFER);
        }

        @Override
        public void discard(String rel) {
            File f = files.remove(rel);
            if (f != null) f.delete();
        }

        @Override
        public String finish(String rel) {
            File f = files.remove(rel);
            return f == null ? rel : f.getAbsolutePath();
        }

        private static File unique(File f) {
            if (!f.exists()) return f;
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            for (int i = 1; ; i++) {
                File c = new File(f.getParentFile(), base + " (" + i + ")" + ext);
                if (!c.exists()) return c;
            }
        }
    }
}
