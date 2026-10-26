package com.lanbeam.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class LocalFileSource implements FileSource {
    private final File file;
    private final String path;
    private final long size;

    public LocalFileSource(File file, String path) {
        this.file = file;
        this.path = path;
        this.size = file.length();
    }

    @Override public String path() { return path; }
    @Override public long size() { return size; }
    @Override public InputStream open() throws IOException { return new FileInputStream(file); }

    /** Expands files and folders (recursively) into sources; folder contents keep the folder name as prefix. */
    public static List<FileSource> collect(List<File> roots) {
        List<FileSource> out = new ArrayList<>();
        for (File f : roots) add(out, f, f.getName());
        return out;
    }

    private static void add(List<FileSource> out, File f, String rel) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids == null) return;
            java.util.Arrays.sort(kids);
            for (File k : kids) add(out, k, rel + "/" + k.getName());
        } else if (f.isFile()) {
            out.add(new LocalFileSource(f, rel));
        }
    }
}
