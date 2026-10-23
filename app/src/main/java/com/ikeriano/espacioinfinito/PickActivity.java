package com.ikeriano.espacioinfinito;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Elegir qué subir a la nube y liberar. */
public class PickActivity extends Activity {
    public static List<Logic.Cand> demoList;
    private String policy; private List<Logic.Cand> list; private final Set<String> sel = new HashSet<>();
    private android.widget.Button go; private TextView info;

    @Override protected void onCreate(Bundle b) { super.onCreate(b); policy = Worker.policy(this); load(); }

    void load() {
        LinearLayout p = Ui.page(this);
        p.addView(Ui.title(this, "Liberar espacio"));
        if (!Ui.connected()) { p.addView(Ui.text(this, "Primero conecta tu Telegram desde el inicio.")); return; }
        if (!Ui.demo && !Files.canManage()) { p.addView(Ui.text(this, "Falta el permiso para gestionar archivos. Dalo desde el inicio.")); return; }
        p.addView(Ui.text(this, "Buscando archivos…"));
        new Thread(new Runnable() { public void run() {
            final List<Logic.Cand> all = demoList != null ? demoList : Files.scan();
            runOnUiThread(new Runnable() { public void run() { list = all; build(); } });
        } }).start();
    }

    void build() {
        List<Logic.Cand> sorted = Logic.choose(list, policy, Long.MAX_VALUE);
        if (sorted.size() > 300) sorted = sorted.subList(0, 300);
        LinearLayout p = Ui.page(this);
        p.addView(Ui.title(this, "Liberar espacio"));
        p.addView(Ui.text(this, "Marca lo que quieras guardar en la nube. Primero se sube y se comprueba; solo después se borra del móvil."));
        LinearLayout chips = new LinearLayout(this); chips.setPadding(0, 0, 0, Ui.dp(this, 8));
        chips.addView(Ui.chip(this, "Más antiguos", !"grandes".equals(policy), new View.OnClickListener() { public void onClick(View v) { policy = "antiguos"; build(); } }));
        chips.addView(Ui.chip(this, "Más grandes", "grandes".equals(policy), new View.OnClickListener() { public void onClick(View v) { policy = "grandes"; build(); } }));
        p.addView(chips);
        final List<Logic.Cand> shown = sorted;
        p.addView(Ui.big(this, "Elegir los 10 primeros", Ui.BLUE, new View.OnClickListener() { public void onClick(View v) {
            sel.clear(); for (int i = 0; i < Math.min(10, shown.size()); i++) sel.add(shown.get(i).path); build(); } }));
        go = Ui.big(this, "", Ui.GREEN, new View.OnClickListener() { public void onClick(View v) {
            if (sel.isEmpty()) return;
            if (!Ui.demo) OffloadService.start(PickActivity.this, new ArrayList<>(sel), false);
            Ui.toast(PickActivity.this, "¡En marcha! Puedes seguir usando el móvil."); finish(); } });
        info = Ui.small(this, "");
        p.addView(info); p.addView(go);
        if (shown.isEmpty()) p.addView(Ui.text(this, "No hay archivos grandes que subir. ¡Tu móvil está en forma!"));
        for (final Logic.Cand c : shown) {
            LinearLayout row = Ui.card(this); row.setOrientation(LinearLayout.HORIZONTAL);
            CheckBox cb = new CheckBox(this); cb.setChecked(sel.contains(c.path)); cb.setScaleX(1.4f); cb.setScaleY(1.4f);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { public void onCheckedChanged(CompoundButton b, boolean on) {
                if (on) sel.add(c.path); else sel.remove(c.path); refresh(); } });
            row.addView(cb);
            LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); txt.setPadding(Ui.dp(this, 12), 0, 0, 0);
            String n = new File(c.path).getName();
            TextView t = Ui.text(this, n); t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); txt.addView(t);
            txt.addView(Ui.small(this, Ui.kindLabel(Logic.kind(n)) + " · " + Logic.size(c.size) + " · " +
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, new java.util.Locale("es", "ES")).format(new java.util.Date(c.mtime))));
            row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ((CheckBox) ((ViewGroup) v).getChildAt(0)).toggle(); } });
            p.addView(row);
        }
        refresh();
    }

    void refresh() {
        long s = 0; for (Logic.Cand c : list) if (sel.contains(c.path)) s += c.size;
        go.setText(sel.isEmpty() ? "Marca algún archivo" : "Subir y liberar " + Logic.size(s));
        go.setAlpha(sel.isEmpty() ? 0.5f : 1f);
        info.setText(sel.size() + " elegidos · Libre ahora: " + Logic.size(Ui.free()));
    }
}
