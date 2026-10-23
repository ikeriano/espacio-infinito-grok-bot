package com.ikeriano.espacioinfinito;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Utilidades de archivos del móvil: espacio, miniaturas, hash, búsqueda. */
public final class Files {
    private Files() {}

    public static File root() { return Environment.getExternalStorageDirectory(); }
    public static long free() { try { return new StatFs(root().getPath()).getAvailableBytes(); } catch (Exception e) { return 0; } }
    public static long total() { try { return new StatFs(root().getPath()).getTotalBytes(); } catch (Exception e) { return 0; } }

    public static boolean canManage() {
        if (Build.VERSION.SDK_INT >= 30) { try { return Environment.isExternalStorageManager(); } catch (Exception e) { return false; } }
        return true;
    }

    public static String sha256(File f) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[256 * 1024];
            InputStream in = new FileInputStream(f);
            try { int n; while ((n = in.read(buf)) > 0) md.update(buf, 0, n); } finally { in.close(); }
            return hex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    public static String hex(byte[] d) { StringBuilder sb = new StringBuilder(); for (byte b : d) sb.append(String.format("%02x", b)); return sb.toString(); }

    /** Copia un trozo [off, off+len) a un archivo temporal. */
    public static File writePart(File src, long off, long len, File dst) throws IOException {
        RandomAccessFile in = new RandomAccessFile(src, "r");
        OutputStream out = new FileOutputStream(dst);
        try {
            in.seek(off); byte[] buf = new byte[512 * 1024]; long left = len;
            while (left > 0) { int n = in.read(buf, 0, (int) Math.min(buf.length, left)); if (n < 0) throw new IOException("EOF"); out.write(buf, 0, n); left -= n; }
        } finally { in.close(); out.close(); }
        return dst;
    }

    public static void copy(File src, File dst, boolean append) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst, append);
        try { copy(in, out); } finally { in.close(); out.close(); }
    }
    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[512 * 1024]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    public static void rescan(Context c, String path) {
        try { MediaScannerConnection.scanFile(c, new String[]{path}, null, null); } catch (Exception ignored) {}
    }

    /** Miniatura pequeña (máx. 320 px) guardada en la memoria interna de la app. */
    public static String thumb(Context c, File f, String kind, String key) {
        File dir = new File(c.getFilesDir(), "thumbs"); dir.mkdirs();
        File out = new File(dir, key + ".jpg");
        if (out.exists()) return out.getPath();
        Bitmap b = null;
        try {
            if ("foto".equals(kind)) {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                int s = 1; while (o.outWidth / (s * 2) >= 320 && o.outHeight / (s * 2) >= 320) s *= 2;
                o = new BitmapFactory.Options(); o.inSampleSize = s; o.inPreferredConfig = Bitmap.Config.RGB_565;
                b = BitmapFactory.decodeFile(f.getPath(), o);
            } else if ("video".equals(kind)) {
                b = ThumbnailUtils.createVideoThumbnail(f.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
            } else if ("apk".equals(kind)) {
                PackageManager pm = c.getPackageManager();
                PackageInfo pi = pm.getPackageArchiveInfo(f.getPath(), 0);
                if (pi != null) {
                    pi.applicationInfo.sourceDir = f.getPath(); pi.applicationInfo.publicSourceDir = f.getPath();
                    Drawable d = pi.applicationInfo.loadIcon(pm);
                    b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
                    Canvas cv = new Canvas(b); cv.drawColor(0xFFFFFFFF); d.setBounds(0, 0, 192, 192); d.draw(cv);
                }
            }
            if (b == null) return null;
            int w = b.getWidth(), h = b.getHeight(); float k = 320f / Math.max(w, h);
            if (k < 1) b = Bitmap.createScaledBitmap(b, Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)), true);
            FileOutputStream os = new FileOutputStream(out);
            try { b.compress(Bitmap.CompressFormat.JPEG, 80, os); } finally { os.close(); }
            return out.getPath();
        } catch (Throwable t) { return null; }
    }

    /** Busca archivos candidatos en las carpetas típicas (fotos, vídeos, descargas, APKs). */
    public static List<Logic.Cand> scan() {
        List<Logic.Cand> out = new ArrayList<>();
        File r = root();
        String[] dirs = {Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_DOCUMENTS};
        for (String d : dirs) walk(new File(r, d), out, 0);
        return out;
    }
    private static void walk(File dir, List<Logic.Cand> out, int depth) {
        if (depth > 8) return;
        File[] l = dir.listFiles();
        if (l == null) return;
        for (File f : l) {
            String p = f.getPath();
            if (Logic.excluded(p + (f.isDirectory() ? "/" : ""))) continue;
            if (f.isDirectory()) walk(f, out, depth + 1);
            else if (f.length() >= Logic.MIN_FILE) out.add(new Logic.Cand(p, f.length(), f.lastModified()));
        }
    }

    public static File restoreTarget(String origPath, String name) {
        File o = origPath == null ? null : new File(origPath);
        if (o != null && o.getParentFile() != null && o.getParentFile().isDirectory() && !o.exists()) return o;
        File dir = new File(new File(root(), Environment.DIRECTORY_DOWNLOADS), "EspacioInfinito"); dir.mkdirs();
        File t = new File(dir, name);
        int i = 1;
        while (t.exists()) { int dot = name.lastIndexOf('.'); t = new File(dir, (dot > 0 ? name.substring(0, dot) : name) + " (" + i++ + ")" + (dot > 0 ? name.substring(dot) : "")); }
        return t;
    }
}
