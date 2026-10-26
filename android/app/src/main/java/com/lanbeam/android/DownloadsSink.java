package com.lanbeam.android;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import com.lanbeam.core.Protocol;
import com.lanbeam.core.Receiver;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/** Saves received files under Download/LanBeam (MediaStore on Android 10+, plain files before). */
@android.annotation.TargetApi(29) // MediaStore paths only run when legacy == null, i.e. API 29+
final class DownloadsSink implements Receiver.FileSink {
    static final String FOLDER = "LanBeam";

    private final Context ctx;
    private final ContentResolver cr;
    private final Map<String, Uri> pending = new HashMap<>();
    private final Receiver.DirectorySink legacy;

    DownloadsSink(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.cr = ctx.getContentResolver();
        this.legacy = Build.VERSION.SDK_INT < 29 ? new Receiver.DirectorySink(legacyRoot()) : null;
    }

    static File legacyRoot() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
    }

    static String displayRoot() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER;
    }

    @Override
    public OutputStream create(String rel, long size) throws IOException {
        if (legacy != null) return legacy.create(rel, size);
        int slash = rel.lastIndexOf('/');
        String name = rel.substring(slash + 1);
        String dir = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + (slash > 0 ? "/" + rel.substring(0, slash) : "");
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime(name));
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, dir);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("MediaStore insert failed for " + rel);
        OutputStream os = cr.openOutputStream(uri, "w");
        if (os == null) throw new IOException("Cannot open " + uri);
        pending.put(rel, uri);
        return new BufferedOutputStream(os, Protocol.COPY_BUFFER);
    }

    @Override
    public void discard(String rel) {
        if (legacy != null) { legacy.discard(rel); return; }
        Uri uri = pending.remove(rel);
        if (uri != null) try { cr.delete(uri, null, null); } catch (Exception ignored) {}
    }

    @Override
    public String finish(String rel) {
        if (legacy != null) {
            String path = legacy.finish(rel);
            MediaScannerConnection.scanFile(ctx, new String[]{path}, null, null);
            return path;
        }
        Uri uri = pending.remove(rel);
        if (uri != null) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, v, null, null);
        }
        return displayRoot() + "/" + rel;
    }

    private static String mime(String name) {
        int dot = name.lastIndexOf('.');
        String m = dot > 0 ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase()) : null;
        return m != null ? m : "application/octet-stream";
    }
}
