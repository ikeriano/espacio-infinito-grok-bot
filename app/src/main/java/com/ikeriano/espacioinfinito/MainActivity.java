package com.ikeriano.espacioinfinito;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final Handler h = new Handler();
    private TextView status; private ProgressBar bar;
    private final Runnable tick = new Runnable() { public void run() { updateStatus(); h.postDelayed(this, 1000); } };
    private final Tg.Listener authL = new Tg.Listener() { public void onAuth(org.drinkless.tdlib.TdApi.AuthorizationState s) { runOnUiThread(new Runnable() { public void run() { build(); } }); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33 && !Ui.demo && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != 0)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        if (Build.VERSION.SDK_INT < 30 && Build.VERSION.SDK_INT >= 23 && !Ui.demo && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != 0)
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
    }
    @Override protected void onResume() { super.onResume(); Tg.get().listeners.add(authL); build(); h.post(tick); }
    @Override protected void onPause() { super.onPause(); Tg.get().listeners.remove(authL); h.removeCallbacks(tick); }

    void build() {
        LinearLayout l = Ui.page(this);
        l.addView(Ui.title(this, "Espacio Infinito"));
        String who = Ui.demo ? "Iker" : (Tg.get().me() != null ? Tg.get().me().firstName : "");
        l.addView(Ui.text(this, Ui.connected() ? "¡Hola" + (who.isEmpty() ? "" : ", " + who) + "! Tu nube de Telegram no tiene límite: guarda todo lo que quieras."
                : "¡Hola! Conecta tu Telegram y tu móvil tendrá espacio sin límite."));

        long free = Ui.free(), total = Ui.total(); long[] t = Db.get(this).totals();
        LinearLayout card = Ui.card(this);
        ChartView chart = new ChartView(this);
        chart.set(total - free, free, t[0], Db.get(this).freedPerDay(7));
        card.addView(chart);
        card.addView(Ui.text(this, "Libre en el móvil: " + Logic.size(free) + " de " + Logic.size(total)));
        TextView g = Ui.text(this, "Has liberado " + Logic.size(t[0]) + " · " + t[1] + " archivos guardados en la nube");
        g.setTextColor(Ui.GREEN); card.addView(g);
        l.addView(card);

        status = Ui.small(this, ""); bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100);
        l.addView(status); l.addView(bar); updateStatus();

        if (!Ui.connected()) {
            l.addView(Ui.big(this, "Conectar con Telegram", Ui.BLUE, go(LoginActivity.class)));
        }
        if (!Ui.demo && !Files.canManage()) {
            l.addView(Ui.big(this, "Dar permiso para gestionar archivos", Ui.ORANGE, new View.OnClickListener() { public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            } }));
        }
        l.addView(Ui.big(this, "Mis archivos", Ui.BLUE, go(GalleryActivity.class)));
        l.addView(Ui.big(this, "Liberar espacio ahora", Ui.GREEN, go(PickActivity.class)));
        l.addView(Ui.big(this, "Modo automático: " + (Worker.auto(this) ? "ACTIVADO" : "apagado"), Worker.auto(this) ? Ui.PURPLE : Ui.GREY, go(SettingsActivity.class)));
        l.addView(Ui.big(this, "Ajustes y cuenta", 0xFF4B5563, go(SettingsActivity.class)));
    }

    void updateStatus() {
        if (status == null) return;
        String s = Worker.status;
        status.setText(Worker.running ? s : (s == null || s.isEmpty() ? "Todo tranquilo. Tus archivos están a salvo." : s));
        bar.setVisibility(Worker.running && Worker.percent >= 0 ? View.VISIBLE : View.GONE);
        if (Worker.percent >= 0) bar.setProgress(Worker.percent);
    }

    View.OnClickListener go(final Class<?> c) { return new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(MainActivity.this, c)); } }; }
}
