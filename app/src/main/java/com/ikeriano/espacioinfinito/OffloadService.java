package com.ikeriano.espacioinfinito;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.ArrayList;
import java.util.Arrays;

/** Servicio en primer plano que sube archivos aunque la pantalla se apague. */
public class OffloadService extends Service {
    public static void start(Context c, ArrayList<String> paths, boolean auto) {
        Intent i = new Intent(c, OffloadService.class).putStringArrayListExtra("paths", paths).putExtra("auto", auto);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    private Notification note(String text, int pct) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "trabajo") : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Espacio Infinito").setContentText(text).setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        if (pct >= 0) b.setProgress(100, pct, false);
        return b.build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, note("Preparando…", -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, note("Preparando…", -1));
        if (Worker.running) return START_NOT_STICKY;
        final ArrayList<String> paths = intent != null && intent.getStringArrayListExtra("paths") != null ? intent.getStringArrayListExtra("paths") : new ArrayList<String>();
        final boolean auto = intent != null && intent.getBooleanExtra("auto", false);
        new Thread(new Runnable() { public void run() {
            PowerManager.WakeLock wl = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ei:subida");
            wl.acquire(6L * 3600 * 1000);
            final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            final boolean[] stop = {false};
            Thread ticker = new Thread(new Runnable() { public void run() {
                while (!stop[0]) { nm.notify(1, note(Worker.status, Worker.percent)); try { Thread.sleep(2000); } catch (InterruptedException e) { return; } }
            } });
            ticker.start();
            try {
                String res = Worker.run(OffloadService.this, paths, auto);
                stop[0] = true; ticker.interrupt();
                Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(OffloadService.this, "avisos") : new Notification.Builder(OffloadService.this);
                nm.notify(2, b.setSmallIcon(android.R.drawable.stat_sys_upload_done).setContentTitle("Espacio Infinito").setContentText(res)
                        .setStyle(new Notification.BigTextStyle().bigText(res)).setAutoCancel(true)
                        .setContentIntent(PendingIntent.getActivity(OffloadService.this, 0, new Intent(OffloadService.this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE)).build());
            } finally { stop[0] = true; if (wl.isHeld()) wl.release(); stopForeground(true); stopSelf(); }
        } }).start();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
