package com.lanbeam.core;

import java.io.IOException;
import java.io.InputStream;

/** One file offered in a share. PC uses java.io.File, Android uses content:// URIs. */
public interface FileSource {
    /** Relative path shown to the receiver, '/' separated (e.g. "Photos/IMG_1.jpg"). */
    String path();

    long size();

    InputStream open() throws IOException;
}
