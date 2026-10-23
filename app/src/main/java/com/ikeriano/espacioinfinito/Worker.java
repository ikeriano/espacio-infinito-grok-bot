package com.ikeriano.espacioinfinito;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Trabajo compartido por el servicio y el modo automático. */
public final class Worker {
    private Worker() {}
    public static volatile String status = "";
    public static volatile int percent = -1;
    public static volatile boolean running = false;
    public static final AtomicBoolean cancel = new AtomicBoolean(false);

    public static long threshold(Context c) { return Tg.prefs(c).getLong("umbral", 1024L * Logic.MIB); }
    public static String policy(Context c) { return Tg.prefs(c).getString("politica", "antiguos"); }
    public static boolean auto(Context c) { return Tg.prefs(c).getBoolean("auto", false); }
    public static boolean wifiOnly(Context c) { return Tg.prefs(c).getBoolean("wifi", true); }

    /** Sube y libera la lista dada. Devuelve un resumen en español. */
    public static String run(Context c, List<String> paths, boolean autoMode) {
        running = true; cancel.set(false);
        long freed = 0; int ok = 0, fail = 0; String lastErr = null;
        try {
            if (autoMode) {
                long need = Logic.needed(Files.free(), threshold(c), 512L * Logic.MIB);
                if (need == 0) return "Hay espacio de sobra. ¡Todo bien!";
                List<Logic.Cand> all = Files.scan();
                java.util.ArrayList<Logic.Cand> fresh = new java.util.ArrayList<>();
                fresh.addAll(all);
                paths = new java.util.ArrayList<>();
                for (Logic.Cand cd : Logic.choose(fresh, policy(c), need)) paths.add(cd.path);
            }
            int i = 0;
            for (String p : paths) {
                if (cancel.get()) break;
                i++;
                final String prefix = paths.size() > 1 ? "(" + i + "/" + paths.size() + ") " : "";
                try {
                    long f = Engine.offload(c, new File(p), new Engine.Status() { public void on(String t, int pct) { status = prefix + t; percent = pct; } }, cancel);
                    freed += f; if (f > 0) ok++;
                } catch (Exception e) { fail++; lastErr = e.getMessage(); }
            }
        } finally { running = false; percent = -1; }
        String s = ok > 0 ? "¡Listo! " + ok + " archivo(s) a salvo en la nube. Has ganado " + Logic.size(freed) + "." : "No se ha liberado espacio.";
        if (fail > 0) s += " " + fail + " no se pudieron subir y siguen en el móvil" + (lastErr != null ? " (" + lastErr + ")" : "") + ".";
        status = s;
        return s;
    }
}
