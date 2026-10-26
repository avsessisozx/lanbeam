package com.lanbeam.android;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import com.lanbeam.core.FileSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** A file picked through the Storage Access Framework or shared from another app. */
final class UriFileSource implements FileSource {
    private final ContentResolver resolver;
    private final Uri uri;
    private final String path;
    private final long size;

    UriFileSource(ContentResolver resolver, Uri uri, String path, long size) {
        this.resolver = resolver;
        this.uri = uri;
        this.path = path;
        this.size = size;
    }

    @Override public String path() { return path; }
    @Override public long size() { return size; }

    @Override public InputStream open() throws IOException {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new FileNotFoundException(uri.toString());
        return in;
    }

    /** Single document: name and size from OpenableColumns. */
    static UriFileSource of(Context ctx, Uri uri) {
        ContentResolver cr = ctx.getContentResolver();
        String name = uri.getLastPathSegment();
        long size = -1;
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                if (!c.isNull(0)) name = c.getString(0);
                if (!c.isNull(1)) size = c.getLong(1);
            }
        } catch (Exception ignored) {
        }
        if (size < 0) size = measure(cr, uri);
        if (name == null || name.isEmpty()) name = "dosya";
        return new UriFileSource(cr, uri, name, size);
    }

    /** Some providers do not report a size: count the bytes once. */
    private static long measure(ContentResolver cr, Uri uri) {
        long n = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return 0;
            byte[] buf = new byte[256 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) n += r;
        } catch (IOException ignored) {
        }
        return n;
    }

    /** Walks a folder picked with ACTION_OPEN_DOCUMENT_TREE; paths keep the folder structure. */
    static void addTree(Context ctx, Uri treeUri, List<FileSource> out) {
        ContentResolver cr = ctx.getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        String rootName = "Klasör";
        Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
        try (Cursor c = cr.query(rootDoc, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) rootName = c.getString(0);
        } catch (Exception ignored) {
        }
        walk(cr, treeUri, rootId, rootName, out);
    }

    private static void walk(ContentResolver cr, Uri treeUri, String docId, String rel, List<FileSource> out) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
        try (Cursor c = cr.query(children, cols, null, null, DocumentsContract.Document.COLUMN_DISPLAY_NAME)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(cr, treeUri, id, rel + "/" + name, out);
                } else {
                    out.add(new UriFileSource(cr, DocumentsContract.buildDocumentUriUsingTree(treeUri, id), rel + "/" + name, c.getLong(3)));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
