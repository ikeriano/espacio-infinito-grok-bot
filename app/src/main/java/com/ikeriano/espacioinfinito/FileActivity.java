package com.ikeriano.espacioinfinito;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Detalle de un archivo: ver/reproducir, devolver al móvil, compartir y exportar. */
public class FileActivity extends Activity {
    private Db.Rec r; private TextView msg; private ProgressBar bar; private boolean busy;
    private static final int EXPORT = 11;

    @Override protected void onResume() { super.onResume(); r = Db.get(this).byId(getIntent().getLongExtra("id", -1)); if (r == null) { finish(); return; } if (!busy) build(); }

    void build() {
        LinearLayout p = Ui.page(this);
        ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackgroundColor(Ui.kindColor(r.kind));
        if (r.thumb != null) { Bitmap b = BitmapFactory.decodeFile(r.thumb); if (b != null) img.setImageBitmap(b); }
        p.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));
        TextView t = Ui.title(this, r.name); t.setTextSize(22); p.addView(t);
        boolean cloud = Db.NUBE.equals(r.status);
        p.addView(Ui.text(this, Ui.kindLabel(r.kind) + " · " + Logic.size(r.size) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("es", "ES")).format(new Date(r.mtime))));
        TextView st = Ui.text(this, cloud ? "Guardado en tu nube de Telegram. No ocupa espacio en el móvil." : "Está en el móvil y también a salvo en la nube.");
        st.setTextColor(cloud ? Ui.BLUE : Ui.GREEN); p.addView(st);
        msg = Ui.small(this, ""); bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setVisibility(View.GONE);
        p.addView(msg); p.addView(bar);
        boolean media = "video".equals(r.kind) || "audio".equals(r.kind);
        p.addView(Ui.big(this, media ? "Reproducir" : "Ver / Abrir", Ui.BLUE, new View.OnClickListener() { public void onClick(View v) { open(); } }));
        if (cloud) p.addView(Ui.big(this, "Descargar al móvil", Ui.GREEN, new View.OnClickListener() { public void onClick(View v) { restore(); } }));
        p.addView(Ui.big(this, "Compartir", Ui.PURPLE, new View.OnClickListener() { public void onClick(View v) { share(); } }));
        p.addView(Ui.big(this, "Exportar a…", Ui.ORANGE, new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(r.mime).putExtra(Intent.EXTRA_TITLE, r.name);
            startActivityForResult(i, EXPORT); } }));
        if (!cloud) p.addView(Ui.big(this, "Volver a liberar espacio", 0xFF4B5563, new View.OnClickListener() { public void onClick(View v) {
            java.util.ArrayList<String> l = new java.util.ArrayList<>(); l.add(r.path); OffloadService.start(FileActivity.this, l, false);
            Ui.toast(FileActivity.this, "Comprobando la copia en la nube y liberando…"); finish(); } }));
    }

    interface Then { void run(File f) throws Exception; }
    private void work(final boolean restore, final Then then) {
        if (busy) return; busy = true; bar.setVisibility(View.VISIBLE); bar.setProgress(0);
        new Thread(new Runnable() { public void run() {
            try {
                Engine.Status s = new Engine.Status() { public void on(final String t, final int pct) { runOnUiThread(new Runnable() { public void run() { msg.setText(t + " " + pct + "%"); bar.setProgress(pct); } }); } };
                final File f = restore ? Engine.restore(FileActivity.this, r, s) : Engine.fetch(FileActivity.this, r, s);
                runOnUiThread(new Runnable() { public void run() {
                    busy = false; bar.setVisibility(View.GONE); msg.setText("");
                    try { then.run(f); } catch (Exception e) { msg.setText(e.getMessage()); }
                } });
            } catch (final Exception e) { runOnUiThread(new Runnable() { public void run() { busy = false; bar.setVisibility(View.GONE); msg.setText("Uy: " + e.getMessage()); } }); }
        } }).start();
    }

    void open() {
        boolean media = "video".equals(r.kind) || "audio".equals(r.kind);
        if (Db.NUBE.equals(r.status) && media && r.msgs().length == 1) {
            startActivity(new Intent(this, PlayerActivity.class).putExtra("id", r.id)); return; // en streaming
        }
        work(false, new Then() { public void run(File f) {
            if ("foto".equals(r.kind) || media) { startActivity(new Intent(FileActivity.this, PlayerActivity.class).putExtra("id", r.id).putExtra("path", f.getPath())); return; }
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(ShareProvider.uri(f, r.name), r.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(i); } catch (Exception e) { msg.setText("No hay ninguna app para abrir este archivo. Prueba «Compartir» o «Exportar»."); }
        } });
    }
    void restore() {
        work(true, new Then() { public void run(File f) {
            new AlertDialog.Builder(FileActivity.this).setTitle("¡De vuelta en el móvil!").setMessage("Guardado en:\n" + f.getPath() + "\n\nSigue también a salvo en la nube.")
                    .setPositiveButton("Genial", null).show();
            build();
        } });
    }
    void share() {
        work(false, new Then() { public void run(File f) {
            Intent i = new Intent(Intent.ACTION_SEND).setType(r.mime).putExtra(Intent.EXTRA_STREAM, ShareProvider.uri(f, r.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Compartir " + r.name));
        } });
    }
    @Override protected void onActivityResult(int req, int res, final Intent data) {
        if (req != EXPORT || res != RESULT_OK || data == null) return;
        final Uri dest = data.getData();
        work(false, new Then() { public void run(final File f) {
            msg.setText("Exportando…");
            new Thread(new Runnable() { public void run() {
                String out;
                try { InputStream in = new FileInputStream(f); OutputStream os = getContentResolver().openOutputStream(dest);
                    try { Files.copy(in, os); } finally { in.close(); os.close(); } out = "Exportado. ¡Hecho!"; }
                catch (Exception e) { out = "No se pudo exportar: " + e.getMessage(); }
                final String o = out; runOnUiThread(new Runnable() { public void run() { msg.setText(o); } });
            } }).start();
        } });
    }
}
