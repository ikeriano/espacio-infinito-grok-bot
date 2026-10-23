package com.ikeriano.espacioinfinito;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Looper;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Capturas de pantalla de la interfaz (pantalla tipo Redmi A2: 720x1600, 320 dpi). */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w360dp-h800dp-xhdpi")
public class ScreenshotTest {
    static final File OUT = new File("/workspace/envio/espacioinfinito");
    static final long G = 1024L * 1024 * 1024;

    @Before public void seed() throws Exception {
        Ui.demo = true; Ui.demoTotal = 32 * G; Ui.demoFree = (long) (9.4 * G);
        Db.reset();
        Db db = Db.get(RuntimeEnvironment.getApplication());
        File th = new File(RuntimeEnvironment.getApplication().getFilesDir(), "thumbs"); th.mkdirs();
        String[][] f = {{"VID_20250812_boda.mp4", "video", "1.8"}, {"IMG_20250901_playa.jpg", "foto", "0.004"}, {"Minecraft_1.21.apk", "apk", "0.75"},
                {"IMG_20250903_perro.jpg", "foto", "0.003"}, {"VID_20240101_fiesta.mp4", "video", "3.2"}, {"cancion.mp3", "audio", "0.008"},
                {"IMG_20240620_monte.jpg", "foto", "0.005"}, {"Peli_vacaciones.mkv", "video", "4.6"}, {"documento.pdf", "otro", "0.002"}};
        int i = 0;
        for (String[] x : f) {
            Db.Rec r = new Db.Rec(); r.name = x[0]; r.kind = x[1]; r.size = (long) (Double.parseDouble(x[2]) * G); r.mime = Logic.mime(x[0]);
            r.status = i % 4 == 3 ? Db.LOCAL : Db.NUBE; r.sha = "sha" + i; r.msgIds = "1"; r.path = "/storage/emulated/0/DCIM/Camera/" + x[0];
            r.mtime = System.currentTimeMillis() - i * 86400000L * 20; r.uploadedAt = System.currentTimeMillis() - (i % 5) * 86400000L;
            if (!"otro".equals(x[1]) && !"audio".equals(x[1])) { File t = new File(th, "t" + i + ".jpg"); fakeThumb(t, i); r.thumb = t.getPath(); }
            db.insert(r); i++;
        }
        List<Logic.Cand> cands = new ArrayList<>();
        String[] n = {"VID_20230514_cumple.mp4", "IMG_20230102_nieve.jpg", "WhatsApp Video 2023.mp4", "Fortnite_installer.apk", "VID_20240211_concierto.mp4", "Screenrecorder.mp4"};
        long[] s = {1200, 6, 85, 640, 2300, 410};
        for (int k = 0; k < n.length; k++) cands.add(new Logic.Cand("/storage/emulated/0/DCIM/" + n[k], s[k] * 1024 * 1024, 1680000000000L + k * 86400000L * 30));
        PickActivity.demoList = cands;
        OUT.mkdirs();
    }

    static void fakeThumb(File f, int seed) throws Exception {
        Bitmap b = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b);
        int[][] pal = {{0xFFFFB86B, 0xFFFF5E62}, {0xFF56CCF2, 0xFF2F80ED}, {0xFF6FCF97, 0xFF219653}, {0xFFF2C94C, 0xFFF2994A}, {0xFFBB6BD9, 0xFF6C5CE7}};
        Paint p = new Paint(); p.setShader(new LinearGradient(0, 0, 240, 240, pal[seed % 5][0], pal[seed % 5][1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 240, 240, p);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG); q.setColor(0x66FFFFFF); c.drawCircle(70 + seed * 13 % 100, 80, 40, q);
        q.setColor(0x55000000); c.drawRect(0, 170, 240, 240, q);
        FileOutputStream o = new FileOutputStream(f); b.compress(Bitmap.CompressFormat.JPEG, 85, o); o.close();
    }

    void shot(Class<? extends Activity> cls, Intent extra, String name) throws Exception {
        Intent i = extra != null ? extra : new Intent(RuntimeEnvironment.getApplication(), cls);
        Activity a = Robolectric.buildActivity(cls, i).setup().get();
        for (int k = 0; k < 10; k++) { Thread.sleep(150); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
        View v = a.getWindow().getDecorView();
        int w = 720, h = 1600;
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); c.drawColor(Color.WHITE); v.draw(c);
        FileOutputStream o = new FileOutputStream(new File(OUT, name)); b.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();
    }

    @Test public void inicio() throws Exception { shot(MainActivity.class, null, "01_inicio.png"); }
    @Test public void galeria() throws Exception { shot(GalleryActivity.class, null, "02_galeria.png"); }
    @Test public void archivo() throws Exception {
        long id = Db.get(RuntimeEnvironment.getApplication()).list(null).get(0).id;
        shot(FileActivity.class, new Intent(RuntimeEnvironment.getApplication(), FileActivity.class).putExtra("id", id), "03_archivo.png");
    }
    @Test public void liberar() throws Exception { shot(PickActivity.class, null, "04_liberar.png"); }
    @Test public void ajustes() throws Exception { Tg.prefs(RuntimeEnvironment.getApplication()).edit().putBoolean("auto", true).apply(); shot(SettingsActivity.class, null, "05_ajustes.png"); }
    @Test public void login() throws Exception { Ui.demo = false; shot(LoginActivity.class, null, "06_conectar_telegram.png"); }
    @Test public void inicioSinConectar() throws Exception { Ui.demo = false; shot(MainActivity.class, null, "07_inicio_sin_conectar.png"); }
}
