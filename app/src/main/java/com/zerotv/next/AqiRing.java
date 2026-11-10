package com.zerotv.next;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

final class AqiRing extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private Integer index;

    AqiRing(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setIndex(Integer value) {
        index = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float density = getResources().getDisplayMetrics().density;
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = size / 2f - density * 5;
        arc.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(density * 4);
        paint.setColor(0x337A89A6);
        canvas.drawArc(arc, 135, 270, false, paint);
        if (index != null) {
            paint.setColor(Weather.airQualityColor(index));
            canvas.drawArc(arc, 135, 270 * index / 500f, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-medium", 0));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(density * 21);
        paint.setColor(0xFFF4F6FF);
        canvas.drawText(index == null ? "—" : index.toString(), centerX, centerY + density * 3, paint);
        paint.setTextSize(density * 11);
        paint.setColor(0xFFBCC7DD);
        canvas.drawText("INDIA AQI", centerX, centerY + density * 16, paint);
    }
}
