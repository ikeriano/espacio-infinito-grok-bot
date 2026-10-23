package com.ikeriano.espacioinfinito;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {
    public static final String NUBE = "nube", LOCAL = "local", SUBIENDO = "subiendo";
    private static Db inst;
    public static synchronized Db get(Context c) { if (inst == null) inst = new Db(c.getApplicationContext()); return inst; }
    public static synchronized void reset() { inst = null; }
    private Db(Context c) { super(c, "espacio.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE files(_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, path TEXT, size INTEGER, mime TEXT, kind TEXT," +
                " mtime INTEGER, status TEXT, sha TEXT, thumb TEXT, msg_ids TEXT, chat_id INTEGER, uploaded_at INTEGER)");
        d.execSQL("CREATE INDEX i_sha ON files(sha)");
    }
    @Override public void onUpgrade(SQLiteDatabase d, int a, int b) {}

    public static class Rec {
        public long id, size, mtime, chatId, uploadedAt; public String name, path, mime, kind, status, sha, thumb, msgIds;
        public long[] msgs() {
            if (msgIds == null || msgIds.isEmpty()) return new long[0];
            String[] p = msgIds.split(","); long[] r = new long[p.length];
            for (int i = 0; i < p.length; i++) r[i] = Long.parseLong(p[i].trim());
            return r;
        }
    }

    private static Rec read(Cursor c) {
        Rec r = new Rec();
        r.id = c.getLong(0); r.name = c.getString(1); r.path = c.getString(2); r.size = c.getLong(3); r.mime = c.getString(4);
        r.kind = c.getString(5); r.mtime = c.getLong(6); r.status = c.getString(7); r.sha = c.getString(8); r.thumb = c.getString(9);
        r.msgIds = c.getString(10); r.chatId = c.getLong(11); r.uploadedAt = c.getLong(12);
        return r;
    }

    public long insert(Rec r) {
        ContentValues v = values(r);
        return r.id = getWritableDatabase().insert("files", null, v);
    }
    public void update(Rec r) { getWritableDatabase().update("files", values(r), "_id=?", new String[]{"" + r.id}); }
    public void delete(long id) { getWritableDatabase().delete("files", "_id=?", new String[]{"" + id}); }

    private static ContentValues values(Rec r) {
        ContentValues v = new ContentValues();
        v.put("name", r.name); v.put("path", r.path); v.put("size", r.size); v.put("mime", r.mime); v.put("kind", r.kind);
        v.put("mtime", r.mtime); v.put("status", r.status); v.put("sha", r.sha); v.put("thumb", r.thumb);
        v.put("msg_ids", r.msgIds); v.put("chat_id", r.chatId); v.put("uploaded_at", r.uploadedAt);
        return v;
    }

    public List<Rec> list(String kind) {
        String where = "status != 'subiendo'" + (kind == null ? "" : " AND kind=?");
        Cursor c = getReadableDatabase().query("files", null, where, kind == null ? null : new String[]{kind}, null, null, "mtime DESC");
        List<Rec> out = new ArrayList<>();
        try { while (c.moveToNext()) out.add(read(c)); } finally { c.close(); }
        return out;
    }
    public Rec byId(long id) { return one("_id=?", "" + id); }
    public Rec bySha(String sha) { return one("sha=? AND status != 'subiendo'", sha); }
    public Rec byPath(String path) { return one("path=? AND status != 'subiendo'", path); }
    private Rec one(String w, String a) {
        Cursor c = getReadableDatabase().query("files", null, w, new String[]{a}, null, null, null, "1");
        try { return c.moveToNext() ? read(c) : null; } finally { c.close(); }
    }
    public void clearStale() { getWritableDatabase().delete("files", "status='subiendo'", null); }

    /** {bytes liberados (en la nube y no en el móvil), número de archivos en la nube} */
    public long[] totals() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(CASE WHEN status='nube' THEN size ELSE 0 END),0), COUNT(*) FROM files WHERE status!='subiendo'", null);
        try { c.moveToNext(); return new long[]{c.getLong(0), c.getLong(1)}; } finally { c.close(); }
    }
    /** Bytes liberados por día en los últimos n días (índice 0 = hace n-1 días). */
    public long[] freedPerDay(int n) {
        long[] out = new long[n];
        long day = 86400000L, now = System.currentTimeMillis(), start = (now / day - (n - 1)) * day;
        Cursor c = getReadableDatabase().rawQuery("SELECT uploaded_at, size FROM files WHERE status='nube' AND uploaded_at>=?", new String[]{"" + start});
        try { while (c.moveToNext()) { int i = (int) ((c.getLong(0) - start) / day); if (i >= 0 && i < n) out[i] += c.getLong(1); } } finally { c.close(); }
        return out;
    }
}
