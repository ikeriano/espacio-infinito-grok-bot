package com.ikeriano.espacioinfinito;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Galería: tus archivos como si siguieran en el móvil (miniaturas guardadas en local). */
public class GalleryActivity extends Activity {
    private String filter = null;
    private List<Db.Rec> items;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); } };
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @Override protected void onResume() { super.onResume(); build(); }
    @Override protected void onDestroy() { super.onDestroy(); pool.shutdownNow(); }

    void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 14); root.setPadding(p, p, p, 0);
        root.addView(Ui.title(this, "Mis archivos"));
        items = Db.get(this).list(filter);
        long[] t = Db.get(this).totals();
        root.addView(Ui.small(this, items.size() + " archivos · " + Logic.size(t[0]) + " viven en la nube y no ocupan nada"));
        LinearLayout chips = new LinearLayout(this); chips.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        String[][] f = {{null, "Todo"}, {"foto", "Fotos"}, {"video", "Vídeos"}, {"apk", "Apps"}, {"audio", "Audio"}, {"otro", "Otros"}};
        for (final String[] x : f) {
            boolean on = x[0] == null ? filter == null : x[0].equals(filter);
            chips.addView(Ui.chip(this, x[1], on, new View.OnClickListener() { public void onClick(View v) { filter = x[0]; build(); } }));
        }
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); hs.addView(chips);
        root.addView(hs);
        if (items.isEmpty()) {
            root.addView(Ui.text(this, "Aún no hay nada aquí. Pulsa «Liberar espacio ahora» en el inicio y tus archivos aparecerán en esta galería."));
            root.addView(Ui.big(this, "Liberar espacio ahora", Ui.GREEN, new View.OnClickListener() { public void onClick(View v) {
                startActivity(new Intent(GalleryActivity.this, PickActivity.class)); } }));
        }
        GridView g = new GridView(this); g.setNumColumns(3); g.setVerticalSpacing(Ui.dp(this, 8)); g.setHorizontalSpacing(Ui.dp(this, 8));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); g.setAdapter(new Adapter());
        g.setOnItemClickListener(new AdapterView.OnItemClickListener() { public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
            startActivity(new Intent(GalleryActivity.this, FileActivity.class).putExtra("id", items.get(pos).id)); } });
        root.addView(g, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    class Adapter extends BaseAdapter {
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return items.get(i).id; }
        public View getView(int i, View cv, ViewGroup parent) {
            Context c = GalleryActivity.this;
            Holder h;
            if (cv == null) {
                h = new Holder(); LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL);
                FrameLayout fr = new FrameLayout(c);
                h.img = new ImageView(c); h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.img.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 104)));
                fr.addView(h.img);
                h.letter = new TextView(c); h.letter.setTextColor(Color.WHITE); h.letter.setTextSize(22); h.letter.setTypeface(Typeface.DEFAULT_BOLD);
                h.letter.setGravity(Gravity.CENTER); fr.addView(h.letter, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 104)));
                h.badge = new TextView(c); h.badge.setTextSize(11); h.badge.setTextColor(Color.WHITE); h.badge.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
                bl.setMargins(Ui.dp(c, 4), 0, 0, Ui.dp(c, 4)); fr.addView(h.badge, bl);
                l.addView(fr);
                h.name = new TextView(c); h.name.setTextSize(13); h.name.setTextColor(Ui.INK); h.name.setSingleLine(true); h.name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                h.size = new TextView(c); h.size.setTextSize(12); h.size.setTextColor(Ui.GREY);
                l.addView(h.name); l.addView(h.size);
                l.setTag(h); cv = l;
            } else h = (Holder) cv.getTag();
            final Db.Rec r = items.get(i);
            h.name.setText(r.name); h.size.setText(Logic.size(r.size));
            boolean cloud = Db.NUBE.equals(r.status);
            h.badge.setText(cloud ? "En la nube" : "En el móvil");
            h.badge.setBackground(Ui.round(cloud ? 0xCC2E7DFF : 0xCC1FAA6B, Ui.dp(c, 8)));
            h.img.setBackgroundColor(Ui.kindColor(r.kind));
            h.key = r.thumb;
            Bitmap b = r.thumb == null ? null : CACHE.get(r.thumb);
            h.img.setImageBitmap(b);
            h.letter.setText(b == null ? Ui.kindLabel(r.kind).toUpperCase() : "");
            if (b == null && r.thumb != null) load(h, r.thumb);
            return cv;
        }
    }
    static class Holder { ImageView img; TextView name, size, badge, letter; String key; }

    private void load(final Holder h, final String path) {
        pool.execute(new Runnable() { public void run() {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inPreferredConfig = Bitmap.Config.RGB_565;
            final Bitmap b = BitmapFactory.decodeFile(path, o);
            if (b == null) return;
            CACHE.put(path, b);
            runOnUiThread(new Runnable() { public void run() { if (path.equals(h.key)) { h.img.setImageBitmap(b); h.letter.setText(""); } } });
        } });
    }
}
