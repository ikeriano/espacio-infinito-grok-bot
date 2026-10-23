package com.ikeriano.espacioinfinito;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.ConcurrentHashMap;

/** Comparte archivos con otras apps sin exponer rutas. */
public class ShareProvider extends ContentProvider {
    private static final ConcurrentHashMap<String, String[]> MAP = new ConcurrentHashMap<>();
    public static Uri uri(File f, String displayName) {
        String token = Long.toHexString(Double.doubleToLongBits(Math.random())) + Long.toHexString(System.nanoTime());
        MAP.put(token, new String[]{f.getPath(), displayName});
        return Uri.parse("content://com.ikeriano.espacioinfinito.share/" + token + "/" + Uri.encode(displayName));
    }
    private static String[] entry(Uri u) throws FileNotFoundException {
        String[] e = u.getPathSegments().isEmpty() ? null : MAP.get(u.getPathSegments().get(0));
        if (e == null) throw new FileNotFoundException();
        return e;
    }
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(new File(entry(u)[0]), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        try {
            String[] e = entry(u);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{e[1], new File(e[0]).length()});
            return c;
        } catch (FileNotFoundException ex) { return null; }
    }
    @Override public String getType(Uri u) { try { return Logic.mime(entry(u)[1]); } catch (FileNotFoundException e) { return null; } }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
