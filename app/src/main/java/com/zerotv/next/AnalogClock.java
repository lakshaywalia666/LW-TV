package com.zerotv.next;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import java.util.Calendar;

final class AnalogClock extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private Shader face;

    AnalogClock(Context context, int accent) {
        super(context);
        this.accent = accent;
        setContentDescription("Analog clock");
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float radius = Math.min(width, height) * .46f;
        if (radius > 0) face = new RadialGradient(width * .4f, height * .3f, radius * 1.4f,
                new int[]{0xDD35405A, 0xEE111827}, null, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * .45f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(face);
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(GlassSurface.tint(accent, 120));
        canvas.drawCircle(centerX, centerY, radius, paint);
        for (int mark = 0; mark < 60; mark++) {
            double angle = Math.PI * mark / 30 - Math.PI / 2;
            float outer = radius * .90f;
            float inner = radius * (mark % 5 == 0 ? .78f : .86f);
            paint.setColor(mark % 5 == 0 ? 0xFFCBD4E6 : 0x777D8BA6);
            paint.setStrokeWidth(mark % 5 == 0 ? 3 : 1);
            canvas.drawLine(centerX + (float) Math.cos(angle) * inner,
                    centerY + (float) Math.sin(angle) * inner,
                    centerX + (float) Math.cos(angle) * outer,
                    centerY + (float) Math.sin(angle) * outer, paint);
        }
        Calendar now = Calendar.getInstance();
        float minute = now.get(Calendar.MINUTE);
        hand(canvas, centerX, centerY, radius * .48f,
                (now.get(Calendar.HOUR) + minute / 60f) * 30, 6, 0xFFE5EAF4);
        hand(canvas, centerX, centerY, radius * .69f, minute * 6, 4, accent);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        canvas.drawCircle(centerX, centerY, 5, paint);
        postInvalidateDelayed(30_000);
    }

    private void hand(Canvas canvas, float x, float y, float length, float degrees, float width, int color) {
        double angle = Math.toRadians(degrees - 90);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        canvas.drawLine(x, y, x + (float) Math.cos(angle) * length,
                y + (float) Math.sin(angle) * length, paint);
    }
}
