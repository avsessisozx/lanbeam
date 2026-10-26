package com.lanbeam.core;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Link that identifies a share: http://host:port/s/token[?ssid=..&pass=..].
 * A browser gets a download page; a LanBeam app pulls the fast stream.
 */
public final class ShareLink {
    public final String host;
    public final int port;
    public final String token;
    /** Optional Wi-Fi to join first (Android hotspot mode, no router). */
    public final String ssid;
    public final String pass;

    public ShareLink(String host, int port, String token, String ssid, String pass) {
        this.host = host;
        this.port = port;
        this.token = token;
        this.ssid = ssid;
        this.pass = pass;
    }

    public String baseUrl() {
        return "http://" + host + ":" + port + "/s/" + token;
    }

    public ShareLink withHost(String newHost) {
        return new ShareLink(newHost, port, token, ssid, pass);
    }

    @Override public String toString() {
        String u = baseUrl();
        if (ssid != null && !ssid.isEmpty()) {
            u += "?ssid=" + enc(ssid) + "&pass=" + enc(pass == null ? "" : pass);
        }
        return u;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static String dec(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /** Parses a link; accepts a missing "http://" prefix. Throws IllegalArgumentException if it is not a share link. */
    public static ShareLink parse(String text) {
        String t = text.trim();
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "http://" + t;
        URI u = URI.create(t);
        String path = u.getPath() == null ? "" : u.getPath();
        if (!path.startsWith("/s/") || u.getHost() == null) throw new IllegalArgumentException("Not a LanBeam link: " + text);
        String token = path.substring(3);
        int slash = token.indexOf('/');
        if (slash >= 0) token = token.substring(0, slash);
        String ssid = null, pass = null;
        if (u.getRawQuery() != null) {
            for (String kv : u.getRawQuery().split("&")) {
                int e = kv.indexOf('=');
                if (e < 0) continue;
                String k = kv.substring(0, e), v = dec(kv.substring(e + 1));
                if (k.equals("ssid")) ssid = v;
                else if (k.equals("pass")) pass = v;
            }
        }
        return new ShareLink(u.getHost(), u.getPort() > 0 ? u.getPort() : Protocol.HTTP_PORT, token, ssid, pass);
    }
}
