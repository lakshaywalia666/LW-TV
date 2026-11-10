package com.zerotv.next;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class AqiTrend extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int[] history;
    private final int accent;

    AqiTrend(Context context, int[] history, int accent) {
        super(context);
        this.history = history;
        this.accent = accent;
        setContentDescription("Indian AQI model estimates over the last 24 hours");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float left = density * 30;
        float right = getWidth() - density * 8;
        float top = density * 10;
        float bottom = getHeight() - density * 26;
        paint.setStrokeWidth(density);
        paint.setTextSize(density * 10);
        paint.setTextAlign(Paint.Align.RIGHT);
        for (int value = 0; value <= 500; value += 100) {
            float y = bottom - (bottom - top) * value / 500f;
            paint.setColor(0x227B8CAA);
            canvas.drawLine(left, y, right, y, paint);
            paint.setColor(0xFFADBCD4);
            canvas.drawText(Integer.toString(value), left - density * 7, y + density * 3, paint);
        }
        path.reset();
        boolean connected = false;
        for (int i = 0; i < history.length; i++) {
            if (history[i] < 0) { connected = false; continue; }
            float x = left + (right - left) * i / Math.max(1, history.length - 1);
            float y = bottom - (bottom - top) * history[i] / 500f;
            if (connected) path.lineTo(x, y);
            else path.moveTo(x, y);
            connected = true;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density * 2.5f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(accent);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0xFFADBCD4);
        canvas.drawText("24 hours ago", left, getHeight() - density * 6, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Latest", right, getHeight() - density * 6, paint);
    }
}
