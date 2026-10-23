package com.ikeriano.espacioinfinito;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Modo automático: cada ~30 min comprueba el espacio libre. */
public class AutoJob extends JobService {
    public static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!Worker.auto(c)) { js.cancel(7); return; }
        JobInfo.Builder b = new JobInfo.Builder(7, new ComponentName(c, AutoJob.class))
                .setPeriodic(30 * 60 * 1000L).setPersisted(true)
                .setRequiredNetworkType(Worker.wifiOnly(c) ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY);
        try { js.schedule(b.build()); } catch (Exception ignored) {}
    }

    private Thread t;
    @Override public boolean onStartJob(final JobParameters p) {
        if (!Worker.auto(this) || Worker.running || Logic.needed(Files.free(), Worker.threshold(this), 0) == 0) return false;
        Tg.get().start(this);
        t = new Thread(new Runnable() { public void run() {
            for (int i = 0; i < 30 && !Tg.get().ready(); i++) try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            if (!Tg.get().ready() || !Files.canManage()) { notifyLow(); jobFinished(p, false); return; }
            String res = Worker.run(AutoJob.this, new java.util.ArrayList<String>(), true);
            post("Modo automático", res);
            jobFinished(p, false);
        } });
        t.start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters p) { Worker.cancel.set(true); return true; }

    private void notifyLow() { post("Queda poco espacio", "Toca aquí para liberar espacio subiendo archivos a tu nube de Telegram."); }
    private void post(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "avisos") : new Notification.Builder(this);
        nm.notify(3, b.setSmallIcon(android.R.drawable.stat_notify_sdcard).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE)).build());
    }
}
