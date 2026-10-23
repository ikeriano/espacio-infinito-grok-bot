package com.ikeriano.espacioinfinito;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

/** Reproductor sencillo: vídeo/audio en streaming desde Telegram, o foto a pantalla completa. */
public class PlayerActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Db.Rec r = Db.get(this).byId(getIntent().getLongExtra("id", -1));
        if (r == null) { finish(); return; }
        String path = getIntent().getStringExtra("path");
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(0xFF000000);
        setContentView(root);
        if ("foto".equals(r.kind) && path != null) {
            ImageView iv = new ImageView(this); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeFile(path, o);
            int s = 1; while (o.outWidth / (s * 2) >= 1600 || o.outHeight / (s * 2) >= 1600) s *= 2;
            o = new BitmapFactory.Options(); o.inSampleSize = s; Bitmap bm = BitmapFactory.decodeFile(path, o);
            iv.setImageBitmap(bm); root.addView(iv); return;
        }
        final TextView wait = new TextView(this); wait.setText("Cargando desde la nube…"); wait.setTextColor(0xFFFFFFFF); wait.setTextSize(20);
        VideoView vv = new VideoView(this);
        root.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(wait, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        MediaController mc = new MediaController(this); vv.setMediaController(mc);
        try {
            Uri u = path != null ? Uri.fromFile(new java.io.File(path)) : Uri.parse(StreamServer.get().url(r.chatId, r.msgs()[0], r.name));
            vv.setVideoURI(u);
            vv.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() { public void onPrepared(android.media.MediaPlayer mp) { wait.setText(""); } });
            vv.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() { public boolean onError(android.media.MediaPlayer mp, int a, int c) {
                wait.setText("No se puede reproducir aquí. Vuelve y pulsa «Descargar al móvil» o «Compartir»."); return true; } });
            vv.start();
        } catch (Exception e) { wait.setText("Error: " + e.getMessage()); }
    }
}
