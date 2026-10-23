package com.ikeriano.espacioinfinito;

import android.content.Context;
import org.drinkless.tdlib.TdApi;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Subir + verificar + liberar, y recuperar. Nunca borra nada que no esté confirmado en Telegram. */
public final class Engine {
    public interface Status { void on(String text, int percent); }
    private Engine() {}
    public static final Object LOCK = new Object();

    /** Devuelve los bytes liberados (0 si no se pudo borrar o se omitió). */
    public static long offload(Context c, File f, Status st, AtomicBoolean cancel) throws Exception {
        synchronized (LOCK) { return offloadLocked(c, f, st, cancel); }
    }

    private static long offloadLocked(Context c, File f, final Status st, AtomicBoolean cancel) throws Exception {
        Tg tg = Tg.get();
        if (!tg.ready()) throw new Exception("Conecta primero tu cuenta de Telegram");
        if (!f.isFile() || Logic.excluded(f.getPath())) return 0;
        Db db = Db.get(c);
        final String name = f.getName();
        final long size = f.length(), mtime = f.lastModified();
        st.on("Revisando " + name, 0);
        String sha = Files.sha256(f);
        Db.Rec existing = db.bySha(sha);
        if (existing != null && existing.size == size && Db.NUBE.equals(existing.status) == false && existing.msgIds != null
                && allRemoteOk(tg, existing)) {
            // Ya está en la nube (por ejemplo, se recuperó antes): solo liberar.
            return deleteLocal(c, f, existing, size, mtime);
        }
        String kind = Logic.kind(name);
        String thumb = Files.thumb(c, f, kind, sha.substring(0, 24));
        List<long[]> plan = Logic.plan(size, tg.partLimit());
        long chat = tg.savedChat();

        Db.Rec r = new Db.Rec();
        r.name = name; r.path = f.getPath(); r.size = size; r.mime = Logic.mime(name); r.kind = kind; r.mtime = mtime;
        r.status = Db.SUBIENDO; r.sha = sha; r.thumb = thumb; r.chatId = chat; r.msgIds = "";
        db.insert(r);
        StringBuilder ids = new StringBuilder();
        long[] planned = new long[plan.size()];
        try {
            final long[] doneBefore = {0};
            for (int i = 0; i < plan.size(); i++) {
                if (cancel != null && cancel.get()) throw new Exception("Cancelado");
                long off = plan.get(i)[0], len = plan.get(i)[1];
                planned[i] = len;
                File src = f;
                File tmp = null;
                if (plan.size() > 1) {
                    if (Files.free() < len + 200L * Logic.MIB)
                        throw new Exception("Falta espacio temporal para trocear " + name + " (necesita " + Logic.size(len) + " libres)");
                    st.on("Preparando parte " + (i + 1) + " de " + plan.size(), 0);
                    tmp = Files.writePart(f, off, len, new File(c.getCacheDir(), "parte_" + sha.substring(0, 12) + "_" + (i + 1) + ".bin"));
                    src = tmp;
                }
                final int pi = i; final int pn = plan.size();
                try {
                    TdApi.Message m = tg.upload(src.getPath(), Logic.caption(name, f.getPath(), i + 1, plan.size(), size, sha, mtime),
                            i == 0 ? thumb : null, new Tg.Progress() {
                                public void on(long done, long total) {
                                    int pct = (int) ((doneBefore[0] + done) * 100 / Math.max(1, size));
                                    st.on("Subiendo " + name + (pn > 1 ? " (parte " + (pi + 1) + "/" + pn + ")" : ""), Math.min(99, pct));
                                }
                            }, cancel);
                    if (ids.length() > 0) ids.append(',');
                    ids.append(m.id);
                } finally { if (tmp != null) tmp.delete(); }
                doneBefore[0] += len;
            }
        } catch (Exception e) {
            db.delete(r.id);
            throw e;
        }
        r.msgIds = ids.toString();
        // Verificación final contra el servidor.
        st.on("Comprobando que " + name + " está a salvo", 100);
        long[] ms = r.msgs();
        long[] remote = new long[ms.length]; boolean[] ok = new boolean[ms.length];
        for (int i = 0; i < ms.length; i++) { remote[i] = tg.verifyRemote(chat, ms[i]); ok[i] = remote[i] >= 0; }
        boolean safe = Logic.safeToDelete(f.length(), mtime, f.lastModified(), remote, ok, planned) && f.length() == size;
        r.uploadedAt = System.currentTimeMillis();
        if (!safe) {
            r.status = Db.LOCAL; db.update(r);
            throw new Exception("No se pudo confirmar la subida de " + name + ". No se ha borrado nada.");
        }
        r.status = Db.LOCAL; db.update(r);
        return deleteLocal(c, f, r, size, mtime);
    }

    private static long deleteLocal(Context c, File f, Db.Rec r, long size, long mtime) {
        if (f.length() != size || f.lastModified() != mtime) return 0; // cambió: no tocar
        if (!f.delete()) return 0;
        Files.rescan(c, f.getPath());
        r.status = Db.NUBE; r.path = f.getPath();
        if (r.uploadedAt == 0) r.uploadedAt = System.currentTimeMillis();
        Db.get(c).update(r);
        return size;
    }

    static boolean allRemoteOk(Tg tg, Db.Rec r) {
        long[] ms = r.msgs(); long sum = 0;
        if (ms.length == 0) return false;
        for (long m : ms) { long s = tg.verifyRemote(r.chatId, m); if (s < 0) return false; sum += s; }
        return sum == r.size;
    }

    /** Descarga el archivo completo a la caché (une partes si hace falta). */
    public static File fetch(Context c, Db.Rec r, final Status st) throws Exception {
        Tg tg = Tg.get();
        if (Db.LOCAL.equals(r.status) && new File(r.path).isFile()) return new File(r.path);
        if (!tg.ready()) throw new Exception("Conecta primero tu cuenta de Telegram");
        long[] ms = r.msgs();
        final long[] before = {0};
        final long total = r.size;
        if (ms.length == 1) {
            TdApi.File f = tg.download(r.chatId, ms[0], new Tg.Progress() { public void on(long d, long t) { st.on("Descargando " , (int) (d * 100 / Math.max(1, total))); } });
            return new File(f.local.path);
        }
        File out = new File(new File(c.getCacheDir(), "unidos"), r.sha.substring(0, 16) + "_" + r.name);
        out.getParentFile().mkdirs();
        if (out.isFile() && out.length() == r.size) return out;
        out.delete();
        for (int i = 0; i < ms.length; i++) {
            TdApi.File f = tg.download(r.chatId, ms[i], new Tg.Progress() { public void on(long d, long t) { st.on("Descargando", (int) ((before[0] + d) * 100 / Math.max(1, total))); } });
            Files.copy(new File(f.local.path), out, true);
            before[0] += f.size;
            try { tg.call(new TdApi.DeleteFile(f.id)); } catch (Exception ignored) {}
        }
        return out;
    }

    /** Devuelve el archivo al móvil (a su carpeta original si es posible) y verifica el SHA-256. */
    public static File restore(Context c, Db.Rec r, Status st) throws Exception {
        synchronized (LOCK) {
            File src = fetch(c, r, st);
            if (Db.LOCAL.equals(r.status) && src.getPath().equals(r.path)) return src;
            File dst = Files.restoreTarget(r.path, r.name);
            if (Files.free() < r.size + 50L * Logic.MIB) throw new Exception("No hay espacio suficiente en el móvil (" + Logic.size(r.size) + ")");
            st.on("Guardando en el móvil", 100);
            File tmp = new File(dst.getParentFile(), "." + dst.getName() + ".ei.tmp");
            Files.copy(src, tmp, false);
            if (!Files.sha256(tmp).equals(r.sha)) { tmp.delete(); throw new Exception("El archivo descargado no coincide. Inténtalo otra vez."); }
            if (!tmp.renameTo(dst)) { tmp.delete(); throw new Exception("No se pudo guardar en " + dst.getParent()); }
            dst.setLastModified(r.mtime);
            r.status = Db.LOCAL; r.path = dst.getPath();
            Db.get(c).update(r);
            Files.rescan(c, dst.getPath());
            // liberar caché de TDLib
            if (src.getPath().contains("/cache/")) src.delete();
            return dst;
        }
    }

    /** Reconstruye la lista leyendo Mensajes guardados (por si se reinstala la app). Devuelve nº añadidos. */
    public static int rebuild(Context c, Status st) throws Exception {
        Tg tg = Tg.get(); Db db = Db.get(c);
        long chat = tg.savedChat();
        long from = 0; int added = 0, seen = 0;
        java.util.Map<String, String[]> partsBySha = new java.util.HashMap<>();
        java.util.Map<String, Map<String, String>> meta = new java.util.HashMap<>();
        java.util.Map<String, TdApi.Thumbnail> thumbs = new java.util.HashMap<>();
        for (int guard = 0; guard < 500; guard++) {
            TdApi.Messages ms = (TdApi.Messages) tg.call(new TdApi.GetChatHistory(chat, from, 0, 100, false));
            if (ms.messages == null || ms.messages.length == 0) break;
            for (TdApi.Message m : ms.messages) {
                from = m.id; seen++;
                if (!(m.content instanceof TdApi.MessageDocument)) continue;
                TdApi.MessageDocument d = (TdApi.MessageDocument) m.content;
                Map<String, String> mm = Logic.parseCaption(d.caption == null ? null : d.caption.text);
                if (mm == null) continue;
                String sha = mm.get("sha256");
                int parts = Integer.parseInt(mm.containsKey("parts") ? mm.get("parts") : "1");
                int part = Integer.parseInt(mm.containsKey("part") ? mm.get("part") : "1");
                String[] arr = partsBySha.get(sha);
                if (arr == null) { arr = new String[parts]; partsBySha.put(sha, arr); meta.put(sha, mm); }
                if (part >= 1 && part <= arr.length) arr[part - 1] = "" + m.id;
                if (part == 1 && d.document.thumbnail != null) thumbs.put(sha, d.document.thumbnail);
            }
            st.on("Leyendo Telegram… " + seen + " mensajes", 0);
        }
        for (String sha : partsBySha.keySet()) {
            String[] arr = partsBySha.get(sha);
            boolean complete = true; for (String s : arr) if (s == null) complete = false;
            if (!complete || db.bySha(sha) != null) continue;
            Map<String, String> mm = meta.get(sha);
            Db.Rec r = new Db.Rec();
            r.name = mm.get("nombre"); r.path = mm.get("ruta"); r.size = Long.parseLong(mm.get("tamano"));
            r.mtime = Long.parseLong(mm.containsKey("fecha") ? mm.get("fecha") : "0");
            r.kind = Logic.kind(r.name); r.mime = Logic.mime(r.name); r.sha = sha; r.chatId = chat;
            r.msgIds = android.text.TextUtils.join(",", arr); r.uploadedAt = System.currentTimeMillis();
            File local = r.path == null ? null : new File(r.path);
            r.status = local != null && local.isFile() && local.length() == r.size ? Db.LOCAL : Db.NUBE;
            TdApi.Thumbnail th = thumbs.get(sha);
            if (th != null) {
                try {
                    TdApi.File tf = (TdApi.File) tg.call(new TdApi.DownloadFile(th.file.id, 1, 0, 0, true));
                    File dir = new File(c.getFilesDir(), "thumbs"); dir.mkdirs();
                    File out = new File(dir, sha.substring(0, 24) + ".jpg");
                    Files.copy(new File(tf.local.path), out, false);
                    r.thumb = out.getPath();
                } catch (Exception ignored) {}
            }
            db.insert(r); added++;
        }
        return added;
    }
}
