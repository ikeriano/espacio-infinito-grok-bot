package com.ikeriano.espacioinfinito;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Gráfico: anillo de espacio del móvil + barras de lo liberado en los últimos 7 días. */
public class ChartView extends View {
    private long used, free, freed; private long[] week = new long[7];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public ChartView(Context c) { super(c); }
    public void set(long used, long free, long freed, long[] week) { this.used = used; this.free = free; this.freed = freed; this.week = week; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) (width * 0.62f));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
        float size = Math.min(w * 0.5f, h) - 16 * d, cx = 8 * d + size / 2, cy = h / 2;
        RectF r = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        float stroke = size * 0.16f;
        r.inset(stroke / 2, stroke / 2);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(stroke); p.setStrokeCap(Paint.Cap.BUTT);
        long total = Math.max(1, used + free);
        float usedA = 360f * used / total;
        p.setColor(0xFFDDE5FF); c.drawArc(r, 0, 360, false, p);
        p.setColor(Ui.BLUE); c.drawArc(r, -90, usedA, false, p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setColor(Ui.INK);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(size * 0.17f);
        c.drawText(Math.round(100.0 * free / total) + "%", cx, cy + size * 0.02f, p);
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(size * 0.09f); p.setColor(Ui.GREY);
        c.drawText("libre", cx, cy + size * 0.15f, p);

        // barras
        float left = w * 0.55f, right = w - 8 * d, top = 34 * d, bottom = h - 26 * d;
        p.setTextAlign(Paint.Align.LEFT); p.setColor(Ui.INK); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(14 * d);
        c.drawText("Liberado", left, 16 * d, p);
        p.setTypeface(Typeface.DEFAULT); p.setColor(Ui.GREEN); p.setTextSize(13 * d);
        c.drawText(Logic.size(freed) + " en total", left, 31 * d + 2, p);
        long max = 1; for (long v : week) max = Math.max(max, v);
        float bw = (right - left) / 7f;
        String[] lbl = {"-6", "-5", "-4", "-3", "-2", "-1", "hoy"};
        top += 10 * d;
        for (int i = 0; i < 7; i++) {
            float bh = (bottom - top) * week[i] / max;
            p.setColor(week[i] > 0 ? Ui.GREEN : 0xFFDDE5FF);
            float x0 = left + i * bw + bw * 0.15f, x1 = left + (i + 1) * bw - bw * 0.15f;
            c.drawRoundRect(new RectF(x0, bottom - Math.max(bh, 3 * d), x1, bottom), 4 * d, 4 * d, p);
            p.setColor(Ui.GREY); p.setTextSize(10 * d); p.setTextAlign(Paint.Align.CENTER);
            c.drawText(lbl[i], (x0 + x1) / 2, bottom + 14 * d, p);
        }
    }
}
