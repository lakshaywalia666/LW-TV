package com.zerotv.next;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

final class Atmosphere extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path[] layers = {new Path(), new Path(), new Path(), new Path()};
    private final ThemePalette theme;
    private final VisualStyle style;
    private final boolean panel;
    private Shader base;
    private Shader light;
    private Shader haze;
    private Shader roomLight;
    private int ambient;
    private float offsetX;
    private float offsetY;
    private ValueAnimator transition;
    private boolean drifting;

    Atmosphere(Context context, ThemePalette theme, VisualStyle style, boolean panel) {
        super(context);
        this.theme = theme;
        this.style = style;
        this.panel = panel;
        ambient = theme.aurora;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (width == 0 || height == 0) return;
        base = new LinearGradient(0, 0, width, height,
                panel ? theme.panel : style.sceneBase(theme), style.sceneBase(theme), Shader.TileMode.CLAMP);
        light = bloom(width * .84f, height * .13f, width * .75f, theme.aurora, panel ? 95 : 90);
        haze = bloom(width * .18f, height * .76f, width * .65f, theme.accent, panel ? 25 : 30);
        updateRoomLight();
        for (int layer = 0; layer < layers.length; layer++) {
            Path path = layers[layer];
            path.reset();
            if (style.wallpaper == 1) {
                path.moveTo(-width * .12f, height);
                for (int point = 0; point <= 10; point++) {
                    float x = width * (point / 8f - .12f);
                    float peak = (point % 2 == 0 ? .19f : .35f) + layer * .15f;
                    path.lineTo(x, height * peak);
                }
                path.lineTo(width * 1.2f, height * 1.2f);
                path.close();
            } else if (style.wallpaper == 3) {
                path.moveTo(-width * .1f, height * (1.1f - layer * .14f));
                path.cubicTo(width * .21f, height * (.24f + layer * .06f),
                        width * .54f, height * (1.4f - layer * .22f),
                        width * 1.15f, height * (.38f + layer * .16f));
                path.lineTo(width * 1.2f, height * 1.2f);
                path.lineTo(-width * .1f, height * 1.2f);
                path.close();
            } else {
                for (int line = 0; line < 5; line++) {
                    float y = height * (.32f + layer * .10f + line * .027f);
                    path.moveTo(-width * .12f, y);
                    path.cubicTo(width * .27f, y + height * .55f,
                            width * .67f, y - height * .48f,
                            width * 1.16f, y + height * .02f);
                }
            }
        }
    }

    void focusLight(int color, float horizontal, float vertical) {
        if (transition != null) transition.cancel();
        int from = ambient;
        float startX = offsetX;
        float startY = offsetY;
        float distance = getResources().getDisplayMetrics().density * 16f * style.depthAmount();
        float targetX = -horizontal * distance;
        float targetY = -vertical * distance * .5f;
        transition = ValueAnimator.ofFloat(0f, 1f);
        transition.setDuration(style.duration(300));
        transition.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            ambient = VisualStyle.blend(from, color, progress);
            offsetX = startX + (targetX - startX) * progress;
            offsetY = startY + (targetY - startY) * progress;
            updateRoomLight();
            invalidate();
        });
        transition.start();
    }

    void setDrifting(boolean enabled) {
        drifting = enabled && style.motion != 3;
        invalidate();
    }

    private void updateRoomLight() {
        if (getWidth() == 0 || getHeight() == 0) return;
        roomLight = bloom(getWidth() * .43f, getHeight() * .72f, getWidth() * .76f,
                ambient, Math.round(75 * style.lightAmount()));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (base == null) return;
        paint.setStyle(Paint.Style.FILL);
        fill(canvas, base);
        fill(canvas, light);
        fill(canvas, haze);
        fill(canvas, roomLight);
        paint.setShader(null);
        float drift = drifting ? (float) Math.sin(SystemClock.uptimeMillis() / 24_000d) : 0;
        for (int layer = 0; layer < layers.length; layer++) {
            int save = canvas.save();
            float depth = (layer + 1) / 4f;
            canvas.translate(offsetX * depth + drift * 20 * depth, offsetY * depth);
            if (style.wallpaper == 1 || style.wallpaper == 3) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(GlassSurface.tint(
                        VisualStyle.blend(theme.panel, theme.aurora, .12f + layer * .1f), 44 + layer * 15));
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(getResources().getDisplayMetrics().density
                        * (style.wallpaper == 2 ? 1.25f : .75f));
                paint.setColor(GlassSurface.tint(theme.accent, panel ? 14 : 12 + layer * 4));
            }
            canvas.drawPath(layers[layer], paint);
            canvas.restoreToCount(save);
        }
        if (style.wallpaper == 3 && !panel) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GlassSurface.tint(theme.accent, 24));
            canvas.drawCircle(getWidth() * .80f + offsetX * .4f, getHeight() * .22f,
                    getHeight() * .11f, paint);
        }
        if (drifting && getWindowVisibility() == VISIBLE) postInvalidateDelayed(100);
    }

    private void fill(Canvas canvas, Shader shader) {
        paint.setAlpha(255);
        paint.setShader(shader);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    private RadialGradient bloom(float x, float y, float radius, int color, int strength) {
        return new RadialGradient(x, y, Math.max(1, radius),
                new int[]{GlassSurface.tint(color, strength), GlassSurface.tint(color, strength / 3),
                        GlassSurface.tint(color, 0)}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (transition != null) transition.cancel();
        drifting = false;
        super.onDetachedFromWindow();
    }
}
