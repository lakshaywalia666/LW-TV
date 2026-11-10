package com.zerotv.next;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

final class WeatherScene extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader sun = new RadialGradient(34, 32, 25,
            new int[]{0xFFFFF0B7, 0xFFFFCC66, 0xFFDE873D}, null, Shader.TileMode.CLAMP);
    private final Shader moon = new RadialGradient(35, 32, 26,
            new int[]{0xFFF4F5FF, 0xFFC3C8EB, 0xFF7B83B6}, null, Shader.TileMode.CLAMP);
    private final Shader cloud = new LinearGradient(0, 40, 0, 83,
            0xFFF3F5FF, 0xFF747EAB, Shader.TileMode.CLAMP);
    private int code = -1;
    private boolean day;

    WeatherScene(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setConditions(int code, boolean day) {
        this.code = code;
        this.day = day;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.scale(getWidth() / 100f, getHeight() / 100f);
        if (code < 0) {
            paint.setColor(0xFF8995B4);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            canvas.drawCircle(50, 45, 24, paint);
            canvas.restoreToCount(save);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (day) {
            paint.setColor(0xFFF6C673);
            for (int ray = 0; ray < 8; ray++) {
                double angle = Math.PI * ray / 4;
                canvas.drawLine(34 + (float) Math.cos(angle) * 29, 33 + (float) Math.sin(angle) * 29,
                        34 + (float) Math.cos(angle) * 34, 33 + (float) Math.sin(angle) * 34, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(day ? sun : moon);
        canvas.drawCircle(34, 33, 23, paint);
        paint.setShader(null);
        if (!day) {
            paint.setColor(0xFF7680AB);
            canvas.drawCircle(24, 27, 4, paint);
            canvas.drawCircle(39, 39, 5, paint);
            paint.setColor(0x66F4F5FF);
            canvas.drawCircle(22, 25, 2, paint);
        }
        if (code > 1) {
            paint.setShader(cloud);
            canvas.drawOval(29, 47, 92, 76, paint);
            canvas.drawCircle(46, 48, 16, paint);
            canvas.drawCircle(65, 45, 22, paint);
            canvas.drawCircle(81, 57, 14, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(0x55FFFFFF);
            canvas.drawArc(43, 24, 87, 66, 220, 95, false, paint);
        }
        if (code >= 51) {
            paint.setStrokeWidth(3);
            paint.setColor(code >= 71 && code <= 77 ? 0xFFE5EDFF : 0xFF79B8EE);
            for (int drop = 0; drop < 3; drop++) {
                float x = 42 + drop * 17;
                canvas.drawLine(x, 83, x - 4, 94, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x22000000);
        canvas.drawOval(17, 95, 89, 99, paint);
        canvas.restoreToCount(save);
    }
}
