package com.ikeriano.espacioinfinito;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lógica pura (sin Android) para poder probarla con tests unitarios. */
public final class Logic {
    private Logic() {}

    /** Límite por archivo de Telegram: 2 GB normal, 4 GB Premium. Dejamos margen. */
    public static final long MIB = 1024L * 1024L;
    public static final long PART_NORMAL = 1950L * MIB;
    public static final long PART_PREMIUM = 3900L * MIB;
    public static final long MIN_FILE = 512L * 1024L;

    /** Plan de partes: lista de {offset, longitud}. */
    public static List<long[]> plan(long size, long limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit");
        List<long[]> out = new ArrayList<>();
        if (size <= limit) { out.add(new long[]{0, size}); return out; }
        long n = (size + limit - 1) / limit;
        long base = size / n, extra = size % n, off = 0;
        for (int i = 0; i < n; i++) {
            long len = base + (i < extra ? 1 : 0);
            out.add(new long[]{off, len});
            off += len;
        }
        return out;
    }

    public static String kind(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String e = dot >= 0 ? n.substring(dot + 1) : "";
        switch (e) {
            case "jpg": case "jpeg": case "png": case "webp": case "heic": case "heif": case "gif": case "bmp": return "foto";
            case "mp4": case "mkv": case "3gp": case "webm": case "mov": case "avi": case "ts": return "video";
            case "apk": case "apks": case "xapk": return "apk";
            case "mp3": case "m4a": case "aac": case "ogg": case "opus": case "wav": case "flac": case "amr": return "audio";
            default: return "otro";
        }
    }

    public static String mime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String e = dot >= 0 ? n.substring(dot + 1) : "";
        switch (e) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "webp": return "image/webp";
            case "gif": return "image/gif";
            case "heic": return "image/heic";
            case "mp4": return "video/mp4";
            case "mkv": return "video/x-matroska";
            case "3gp": return "video/3gpp";
            case "webm": return "video/webm";
            case "mov": return "video/quicktime";
            case "apk": return "application/vnd.android.package-archive";
            case "mp3": return "audio/mpeg";
            case "m4a": return "audio/mp4";
            case "ogg": case "opus": return "audio/ogg";
            case "pdf": return "application/pdf";
            case "zip": return "application/zip";
            default: return "application/octet-stream";
        }
    }

    /** Tamaño legible en español: "1,5 GB". */
    public static String size(long b) {
        if (b < 1024) return b + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = b; int i = -1;
        do { v /= 1024; i++; } while (v >= 1024 && i < u.length - 1);
        String s = v >= 100 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.replace('.', ',') + " " + u[i];
    }

    // ---------- Pie de foto (caption) que guarda los metadatos en Telegram ----------
    public static final String TAG = "#EspacioInfinito";

    public static String caption(String name, String path, int part, int parts, long total, String sha, long mtime) {
        if (path != null && path.length() > 600) path = path.substring(path.length() - 600);
        return TAG + " v1\nnombre: " + name + "\nruta: " + path + "\nparte: " + part + "/" + parts
                + "\ntamano: " + total + "\nsha256: " + sha + "\nfecha: " + mtime;
    }

    public static Map<String, String> parseCaption(String text) {
        if (text == null || !text.startsWith(TAG)) return null;
        Map<String, String> m = new HashMap<>();
        for (String line : text.split("\n")) {
            int i = line.indexOf(": ");
            if (i > 0) m.put(line.substring(0, i).trim(), line.substring(i + 2));
        }
        String p = m.get("parte");
        if (p != null && p.contains("/")) {
            m.put("part", p.substring(0, p.indexOf('/')));
            m.put("parts", p.substring(p.indexOf('/') + 1));
        }
        return m.containsKey("sha256") && m.containsKey("nombre") ? m : null;
    }

    // ---------- Selección automática ----------
    public static final class Cand {
        public final String path; public final long size; public final long mtime;
        public Cand(String path, long size, long mtime) { this.path = path; this.size = size; this.mtime = mtime; }
    }

    /** Ordena por política ("antiguos" o "grandes") y elige hasta liberar 'needed' bytes. */
    public static List<Cand> choose(List<Cand> all, String policy, long needed) {
        List<Cand> l = new ArrayList<>(all);
        if ("grandes".equals(policy)) Collections.sort(l, new Comparator<Cand>() {
            public int compare(Cand a, Cand b) { return Long.compare(b.size, a.size); } });
        else Collections.sort(l, new Comparator<Cand>() {
            public int compare(Cand a, Cand b) { return Long.compare(a.mtime, b.mtime); } });
        List<Cand> out = new ArrayList<>();
        long sum = 0;
        for (Cand c : l) { if (sum >= needed) break; out.add(c); sum += c.size; }
        return out;
    }

    /** Bytes a liberar para volver a tener 'threshold' + margen libre. 0 si no hace falta. */
    public static long needed(long free, long threshold, long margin) {
        return free >= threshold ? 0 : (threshold + margin - free);
    }

    /** ¿Se puede borrar el original? Solo si todo cuadra. */
    public static boolean safeToDelete(long localSize, long localMtimeBefore, long localMtimeNow,
                                       long[] remoteSizes, boolean[] remoteDone, long[] plannedSizes) {
        if (remoteSizes == null || remoteDone == null || plannedSizes == null) return false;
        if (remoteSizes.length != plannedSizes.length || remoteDone.length != plannedSizes.length) return false;
        if (localMtimeBefore != localMtimeNow) return false;
        long sum = 0;
        for (int i = 0; i < plannedSizes.length; i++) {
            if (!remoteDone[i] || remoteSizes[i] != plannedSizes[i]) return false;
            sum += remoteSizes[i];
        }
        return sum == localSize && localSize > 0;
    }

    /** Carpetas o archivos que nunca tocamos. */
    public static boolean excluded(String path) {
        String p = path.replace('\\', '/');
        if (p.contains("/Android/data/") || p.contains("/Android/obb/") || p.contains("/Android/media/")) return true;
        if (p.contains("/EspacioInfinito/")) return true;
        for (String seg : p.split("/")) if (seg.startsWith(".") && seg.length() > 1) return true;
        String n = new File(p).getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".tmp") || n.endsWith(".part") || n.endsWith(".crdownload") || n.endsWith(".pending");
    }
}
