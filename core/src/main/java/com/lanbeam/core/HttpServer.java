package com.lanbeam.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny HTTP/1.1 server (one request per connection). Small on purpose: the heavy lifting is a raw byte stream. */
public final class HttpServer {
    public static final Charset UTF8 = Charset.forName("UTF-8");

    public interface Handler {
        void handle(Request req, Response res) throws IOException;
    }

    private final Handler handler;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "lanbeam-http");
        t.setDaemon(true);
        return t;
    });
    private ServerSocket server;
    private volatile boolean running;

    public HttpServer(Handler handler) {
        this.handler = handler;
    }

    /** Binds the first free port in [firstPort, firstPort + attempts) and starts accepting. */
    public int start(int firstPort, int attempts) throws IOException {
        IOException last = null;
        for (int p = firstPort; p < firstPort + attempts; p++) {
            ServerSocket ss = new ServerSocket();
            try {
                ss.setReuseAddress(true);
                ss.setReceiveBufferSize(Protocol.SOCKET_BUFFER);
                ss.bind(new InetSocketAddress(p));
                server = ss;
                break;
            } catch (IOException e) {
                last = e;
                ss.close();
            }
        }
        if (server == null) throw last != null ? last : new IOException("No free port");
        running = true;
        Thread t = new Thread(this::acceptLoop, "lanbeam-accept");
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket s = server.accept();
                pool.execute(() -> serve(s));
            } catch (IOException e) {
                if (!running) return;
            }
        }
    }

    private void serve(Socket s) {
        try {
            s.setSendBufferSize(Protocol.SOCKET_BUFFER);
            s.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(s.getInputStream(), 16 * 1024);
            OutputStream out = s.getOutputStream();
            Request req = Request.read(in, s.getInetAddress().getHostAddress());
            if (req == null) return;
            Response res = new Response(out, req.method.equals("HEAD"));
            try {
                handler.handle(req, res);
                if (!res.committed) res.text(404, "Not found");
            } catch (SocketException e) {
                // client went away
            } catch (Exception e) {
                if (!res.committed) res.text(500, "Error: " + e);
            }
            out.flush();
        } catch (IOException ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    public static final class Request {
        public String method;
        public String path;
        public final Map<String, String> query = new HashMap<>();
        public final Map<String, String> headers = new HashMap<>();
        public String remoteAddr;
        InputStream body;

        static Request read(InputStream in, String remote) throws IOException {
            String line = readLine(in);
            if (line == null || line.isEmpty()) return null;
            String[] parts = line.split(" ");
            if (parts.length < 2) return null;
            Request r = new Request();
            r.remoteAddr = remote;
            r.method = parts[0].toUpperCase();
            String target = parts[1];
            int q = target.indexOf('?');
            String rawPath = q >= 0 ? target.substring(0, q) : target;
            r.path = URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8");
            if (q >= 0) {
                for (String kv : target.substring(q + 1).split("&")) {
                    if (kv.isEmpty()) continue;
                    int e = kv.indexOf('=');
                    String k = URLDecoder.decode(e >= 0 ? kv.substring(0, e) : kv, "UTF-8");
                    String v = e >= 0 ? URLDecoder.decode(kv.substring(e + 1), "UTF-8") : "";
                    r.query.put(k, v);
                }
            }
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int c = h.indexOf(':');
                if (c > 0) r.headers.put(h.substring(0, c).trim().toLowerCase(), h.substring(c + 1).trim());
            }
            r.body = in;
            return r;
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream(128);
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') break;
                if (c != '\r') b.write(c);
                if (b.size() > 16 * 1024) throw new IOException("Header line too long");
            }
            if (c == -1 && b.size() == 0) return null;
            return new String(b.toByteArray(), UTF8);
        }

        public String header(String name) {
            return headers.get(name.toLowerCase());
        }

        public InputStream bodyStream() { return body; }

        /** Reads the request body (bounded by Content-Length and maxBytes). */
        public String bodyText(int maxBytes) throws IOException {
            String cl = header("content-length");
            int len = cl == null ? 0 : (int) Math.min(Long.parseLong(cl), maxBytes);
            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) {
                int n = body.read(buf, off, len - off);
                if (n < 0) break;
                off += n;
            }
            return new String(buf, 0, off, UTF8);
        }
    }

    public static final class Response {
        private final OutputStream out;
        private final boolean headOnly;
        boolean committed;
        public final Map<String, String> headers = new LinkedHashMap<>();

        Response(OutputStream out, boolean headOnly) {
            this.out = out;
            this.headOnly = headOnly;
        }

        /**
         * Writes status line and headers and returns the body stream.
         * length &lt; 0 means unknown length: the body ends when the connection closes.
         */
        public OutputStream start(int status, String contentType, long length) throws IOException {
            if (committed) throw new IllegalStateException("Response already started");
            committed = true;
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
            sb.append("Server: LanBeam\r\n");
            sb.append("Connection: close\r\n");
            sb.append("Access-Control-Allow-Origin: *\r\n");
            if (contentType != null) sb.append("Content-Type: ").append(contentType).append("\r\n");
            if (length >= 0) sb.append("Content-Length: ").append(length).append("\r\n");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            sb.append("\r\n");
            out.write(sb.toString().getBytes(UTF8));
            if (headOnly) return new OutputStream() {
                @Override public void write(int b) throws IOException { throw new SocketException("HEAD"); }
            };
            return out;
        }

        public void send(int status, String contentType, byte[] body) throws IOException {
            OutputStream o = start(status, contentType, body.length);
            if (!headOnly) o.write(body);
        }

        public void text(int status, String text) throws IOException {
            send(status, "text/plain; charset=utf-8", text.getBytes(UTF8));
        }

        public void json(int status, String json) throws IOException {
            send(status, "application/json; charset=utf-8", json.getBytes(UTF8));
        }

        private static String reason(int s) {
            switch (s) {
                case 200: return "OK";
                case 206: return "Partial Content";
                case 400: return "Bad Request";
                case 403: return "Forbidden";
                case 404: return "Not Found";
                case 416: return "Range Not Satisfiable";
                default: return s >= 500 ? "Server Error" : "OK";
            }
        }
    }
}
