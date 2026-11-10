package com.zerotv.next;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class InputGlyph extends View {
    static final String[] TYPES = {"Display", "Game console", "Television"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int type;
    private final int color;

    InputGlyph(Context context, int type, int color) {
        super(context);
        this.type = type;
        this.color = color;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.scale(getWidth() / 64f, getHeight() / 64f);
        paint.setColor(GlassSurface.tint(color, 22));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(1, 1, 63, 63, 14, 14, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(2.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (type == 1) {
            canvas.drawRoundRect(11, 22, 53, 45, 10, 10, paint);
            canvas.drawLine(20, 29, 20, 38, paint);
            canvas.drawLine(16, 33.5f, 24, 33.5f, paint);
            canvas.drawCircle(42, 29, 1.5f, paint);
            canvas.drawCircle(46, 35, 1.5f, paint);
        } else {
            canvas.drawRoundRect(10, 15, 54, 43, 5, 5, paint);
            canvas.drawLine(25, 43, 25, 49, paint);
            canvas.drawLine(39, 43, 39, 49, paint);
            canvas.drawLine(20, 50, 44, 50, paint);
            if (type == 2) {
                canvas.drawLine(24, 9, 32, 15, paint);
                canvas.drawLine(40, 9, 32, 15, paint);
            }
        }
        canvas.restoreToCount(save);
    }
}
