package com.lanbeam.core;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A set of files published under a random token. */
public final class Share {
    private static final SecureRandom RNG = new SecureRandom();
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    public final String token;
    public final List<FileSource> files;
    public final long total;

    public Share(List<FileSource> files) {
        this.token = newToken();
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
        long t = 0;
        for (FileSource f : files) t += f.size();
        this.total = t;
    }

    static String newToken() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    public String listJson() {
        List<Object> list = new ArrayList<>();
        for (FileSource f : files) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", f.path());
            m.put("size", f.size());
            list.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("files", list);
        root.put("total", total);
        return Json.write(root);
    }
}
