package com.ikeriano.espacioinfinito;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;

public class SettingsActivity extends Activity {
    @Override protected void onResume() { super.onResume(); build(); }

    void build() {
        final SharedPreferences sp = Tg.prefs(this);
        LinearLayout p = Ui.page(this);
        p.addView(Ui.title(this, "Ajustes"));

        LinearLayout a = Ui.card(this);
        a.addView(Ui.text(this, "Modo automático"));
        a.addView(Ui.small(this, "Cuando quede poco espacio, la app sube sola los archivos y libera el móvil. Solo borra lo que ya está comprobado en la nube."));
        final boolean auto = Worker.auto(this);
        a.addView(Ui.big(this, auto ? "ACTIVADO (tocar para apagar)" : "Apagado (tocar para activar)", auto ? Ui.PURPLE : Ui.GREY, new View.OnClickListener() { public void onClick(View v) {
            sp.edit().putBoolean("auto", !auto).apply(); AutoJob.schedule(SettingsActivity.this); build(); } }));
        a.addView(Ui.text(this, "Actuar cuando queden menos de:"));
        LinearLayout ch = new LinearLayout(this);
        long th = Worker.threshold(this);
        for (final long mb : new long[]{500, 1024, 2048, 4096}) {
            ch.addView(Ui.chip(this, mb < 1024 ? mb + " MB" : (mb / 1024) + " GB", th == mb * Logic.MIB, new View.OnClickListener() { public void onClick(View v) {
                sp.edit().putLong("umbral", mb * Logic.MIB).apply(); build(); } }));
        }
        a.addView(ch);
        a.addView(Ui.text(this, "Subir primero:"));
        LinearLayout ch2 = new LinearLayout(this);
        String pol = Worker.policy(this);
        ch2.addView(Ui.chip(this, "Los más antiguos", !"grandes".equals(pol), new View.OnClickListener() { public void onClick(View v) { sp.edit().putString("politica", "antiguos").apply(); build(); } }));
        ch2.addView(Ui.chip(this, "Los más grandes", "grandes".equals(pol), new View.OnClickListener() { public void onClick(View v) { sp.edit().putString("politica", "grandes").apply(); build(); } }));
        a.addView(ch2);
        final boolean wifi = Worker.wifiOnly(this);
        a.addView(Ui.big(this, wifi ? "Solo con WiFi: SÍ" : "Solo con WiFi: no (usa datos)", wifi ? Ui.BLUE : Ui.ORANGE, new View.OnClickListener() { public void onClick(View v) {
            sp.edit().putBoolean("wifi", !wifi).apply(); AutoJob.schedule(SettingsActivity.this); build(); } }));
        p.addView(a);

        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Cuenta de Telegram"));
        TdApi.User me = Tg.get().me();
        c.addView(Ui.small(this, Ui.demo ? "Conectado como Iker (+34 ··· ·· ·· 12)" : me != null ? "Conectado como " + me.firstName + " (+" + me.phoneNumber + ")" + (me.isPremium ? " · Premium: archivos de hasta 4 GB" : " · archivos de hasta 2 GB (los más grandes se trocean solos)") : "No conectado"));
        if (!Ui.connected()) c.addView(Ui.big(this, "Conectar con Telegram", Ui.BLUE, new View.OnClickListener() { public void onClick(View v) {
            startActivity(new android.content.Intent(SettingsActivity.this, LoginActivity.class)); } }));
        else {
            final TextView res = Ui.small(this, "");
            c.addView(Ui.big(this, "Recuperar mi lista desde Telegram", Ui.GREEN, new View.OnClickListener() { public void onClick(View v) {
                res.setText("Leyendo tus Mensajes guardados…");
                new Thread(new Runnable() { public void run() {
                    String out;
                    try { int n = Engine.rebuild(SettingsActivity.this, new Engine.Status() { public void on(final String t, int x) { runOnUiThread(new Runnable() { public void run() { res.setText(t); } }); } });
                        out = n == 0 ? "Tu lista ya estaba al día." : "¡Recuperados " + n + " archivos!"; }
                    catch (Exception e) { out = "No se pudo: " + e.getMessage(); }
                    final String o = out; runOnUiThread(new Runnable() { public void run() { res.setText(o); } });
                } }).start(); } }));
            c.addView(res);
            c.addView(Ui.big(this, "Cerrar sesión", Ui.RED, new View.OnClickListener() { public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this).setMessage("¿Cerrar sesión? Tus archivos siguen a salvo en Telegram; al volver a entrar usa «Recuperar mi lista».")
                        .setPositiveButton("Cerrar sesión", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { Tg.get().logout(); finish(); } })
                        .setNegativeButton("Cancelar", null).show(); } }));
        }
        p.addView(c);
        p.addView(Ui.small(this, "Espacio Infinito 1.0 · Tus archivos se guardan en «Mensajes guardados» de tu Telegram."));
    }
}
