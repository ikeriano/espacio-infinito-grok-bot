package com.ikeriano.espacioinfinito;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Piezas de interfaz: botones grandes, textos claros, colores alegres. */
public final class Ui {
    private Ui() {}
    public static final int BLUE = 0xFF2E7DFF, GREEN = 0xFF1FAA6B, ORANGE = 0xFFFF8A00, PURPLE = 0xFF7B5CFF, RED = 0xFFE5484D,
            GREY = 0xFF6B7280, BG = 0xFFF3F6FF, INK = 0xFF1B2440, CARD = 0xFFFFFFFF;
    /** Solo para capturas/tests: simula una cuenta conectada. */
    public static boolean demo = false;
    public static long demoFree = 0, demoTotal = 0;

    public static int dp(Context c, float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

    public static LinearLayout page(Activity a) {
        LinearLayout l = new LinearLayout(a); l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 18); l.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(a); sv.setBackgroundColor(BG); sv.setFillViewport(true); sv.addView(l);
        a.setContentView(sv);
        if (android.os.Build.VERSION.SDK_INT >= 21) a.getWindow().setStatusBarColor(BLUE);
        return l;
    }

    public static TextView title(Context c, String t) {
        TextView v = new TextView(c); v.setText(t); v.setTextColor(INK); v.setTextSize(28); v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, 0, 0, dp(c, 6)); return v;
    }
    public static TextView text(Context c, String t) {
        TextView v = new TextView(c); v.setText(t); v.setTextColor(INK); v.setTextSize(18); v.setLineSpacing(0, 1.15f);
        v.setPadding(0, dp(c, 4), 0, dp(c, 4)); return v;
    }
    public static TextView small(Context c, String t) { TextView v = text(c, t); v.setTextSize(15); v.setTextColor(GREY); return v; }

    public static GradientDrawable round(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(r); return g; }

    public static Button big(Context c, String t, int color, View.OnClickListener l) {
        Button b = new Button(c); b.setText(t); b.setAllCaps(false); b.setTextSize(21); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(color, dp(c, 18))); b.setMinHeight(dp(c, 72)); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 7), 0, dp(c, 7)); b.setLayoutParams(lp);
        b.setOnClickListener(l); return b;
    }
    public static Button chip(Context c, String t, boolean on, View.OnClickListener l) {
        Button b = new Button(c); b.setText(t); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(on ? Color.WHITE : INK);
        b.setBackground(round(on ? BLUE : 0xFFDDE5FF, dp(c, 22))); b.setMinHeight(dp(c, 48)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 48));
        lp.setMargins(0, 0, dp(c, 8), 0); b.setLayoutParams(lp);
        b.setOnClickListener(l); return b;
    }
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, dp(c, 20))); int p = dp(c, 16); l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 8), 0, dp(c, 8)); l.setLayoutParams(lp);
        if (android.os.Build.VERSION.SDK_INT >= 21) l.setElevation(dp(c, 2));
        return l;
    }
    public static EditText input(Context c, String hint, int type) {
        EditText e = new EditText(c); e.setHint(hint); e.setTextSize(22); e.setInputType(type); e.setSingleLine(true);
        e.setBackground(round(CARD, dp(c, 14))); int p = dp(c, 14); e.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6)); e.setLayoutParams(lp); return e;
    }
    public static int NUMBER = InputType.TYPE_CLASS_NUMBER, PHONE = InputType.TYPE_CLASS_PHONE,
            TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, PASSWORD = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;

    public static void toast(Context c, String t) { Toast.makeText(c, t, Toast.LENGTH_LONG).show(); }

    public static long free() { return demo ? demoFree : Files.free(); }
    public static long total() { return demo ? demoTotal : Files.total(); }
    public static boolean connected() { return demo || Tg.get().ready(); }

    public static String kindLabel(String k) {
        if ("foto".equals(k)) return "Foto"; if ("video".equals(k)) return "Vídeo"; if ("apk".equals(k)) return "App (APK)";
        if ("audio".equals(k)) return "Audio"; return "Archivo";
    }
    public static int kindColor(String k) {
        if ("foto".equals(k)) return 0xFF4FA3FF; if ("video".equals(k)) return 0xFFFF7A59; if ("apk".equals(k)) return 0xFF35C27D;
        if ("audio".equals(k)) return 0xFFA77BFF; return 0xFF8A94A8;
    }
}
