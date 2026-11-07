package com.spinfit.bike.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

/** 环形功率仪表：底环 + 渐变进度弧 + 中心数字 + 内圈目标刻度。 */
public final class GaugeView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float value, target, max = 400f;
    private int color = Ui.ACCENT;
    private boolean valid;

    public GaugeView(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(0xFF232B3E);
        track.setStrokeCap(Paint.Cap.ROUND);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        tick.setStyle(Paint.Style.STROKE);
        tick.setColor(0x55FFFFFF);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        sub.setColor(0xFF8A93A8);
        sub.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void set(float value, float target, float max, int color) {
        set(value, target, max, color, true);
    }

    public void set(float value, float target, float max, int color, boolean valid) {
        if (Float.isNaN(value) || Float.isInfinite(value)) value = 0;
        this.value = Math.max(0, value);
        this.target = Math.max(0, target);
        this.max = Math.max(1, max);
        this.color = color;
        this.valid = valid;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float stroke = Math.min(w, h) * 0.085f;
        float r = Math.min(w, h) / 2f - stroke;
        box.set(cx - r, cy - r, cx + r, cy + r);

        track.setStrokeWidth(stroke);
        c.drawArc(box, 135, 270, false, track);

        // 渐变进度弧：从青绿到当前色
        SweepGradient sg = new SweepGradient(cx, cy,
                new int[]{Ui.ACCENT, Ui.BLUE, color, Ui.VIOLET},
                new float[]{0f, 0.35f, 0.75f, 1f});
        arc.setShader(sg);
        arc.setStrokeWidth(stroke);
        float ratio = Math.min(1f, value / max);
        if (ratio > 0.001f) c.drawArc(box, 135, 270 * ratio, false, arc);
        arc.setShader(null);

        // 目标刻度
        if (target > 0) {
            float tr = Math.min(1f, target / max);
            float angle = (float) Math.toRadians(135 + 270 * tr);
            float x1 = cx + (r - stroke * 0.9f) * (float) Math.cos(angle);
            float y1 = cy + (r - stroke * 0.9f) * (float) Math.sin(angle);
            float x2 = cx + (r + stroke * 0.9f) * (float) Math.cos(angle);
            float y2 = cy + (r + stroke * 0.9f) * (float) Math.sin(angle);
            tick.setStrokeWidth(stroke * 0.18f);
            tick.setColor(0xFFFFFFFF);
            c.drawLine(x1, y1, x2, y2, tick);
            tick.setColor(0x55FFFFFF);
        }

        text.setTextSize(Math.min(w, h) * 0.30f);
        c.drawText(valid ? String.valueOf(Math.round(value)) : "--",
                cx, cy + text.getTextSize() * 0.30f, text);
        sub.setTextSize(Math.min(w, h) * 0.072f);
        c.drawText("功率  W", cx, cy + text.getTextSize() * 0.85f, sub);
        if (target > 0) {
            sub.setTextSize(Math.min(w, h) * 0.062f);
            c.drawText("目标 " + Math.round(target) + " W", cx, cy + r * 0.62f, sub);
        }
    }
}
