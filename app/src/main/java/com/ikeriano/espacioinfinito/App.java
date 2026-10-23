package com.ikeriano.espacioinfinito;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("trabajo", "Subidas a la nube", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            NotificationChannel ch2 = new NotificationChannel("avisos", "Avisos de espacio", NotificationManager.IMPORTANCE_DEFAULT);
            getSystemService(NotificationManager.class).createNotificationChannel(ch2);
        }
        Db.get(this).clearStale();
        Tg.get().start(this);
        AutoJob.schedule(this);
    }
}
