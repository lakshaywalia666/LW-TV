package com.zerotv.next;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class GlassSurface extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF shape = new RectF();
    private final Path clip = new Path();
    private final ThemePalette theme;
    private final VisualStyle style;
    private final float radius;
    private final float stroke;
    private final int ambient;
    private final boolean selected;
    private final boolean quiet;
    private Shader glaze;
    private Shader edge;
    private Shader glow;
    private Shader bevel;
    private Shader reflection;
    private ValueAnimator sweep;
    private float sweepPosition = -1f;
    private boolean focused;
    private boolean pressed;

    GlassSurface(ThemePalette theme, VisualStyle style, float radius, float stroke, int ambient,
                 boolean selected, boolean quiet) {
        this.theme = theme;
        this.style = style;
        this.radius = radius;
        this.stroke = stroke;
        this.ambient = ambient;
        this.selected = selected;
        this.quiet = quiet;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        shape.set(bounds);
        shape.inset(stroke, stroke);
        clip.reset();
        clip.addRoundRect(shape, radius, radius, Path.Direction.CW);
        rebuildShaders();
    }

    @Override
    protected boolean onStateChange(int[] states) {
        boolean newFocus = false;
        boolean newPress = false;
        for (int state : states) {
            if (state == android.R.attr.state_focused) newFocus = true;
            if (state == android.R.attr.state_pressed) newPress = true;
        }
        if (newFocus == focused && newPress == pressed) return false;
        focused = newFocus;
        pressed = newPress;
        rebuildShaders();
        if (sweep != null) sweep.cancel();
        sweepPosition = -1f;
        if (focused && style.duration(1) > 0) {
            sweep = ValueAnimator.ofFloat(-.5f, 1.5f);
            sweep.setDuration(style.duration(650));
            sweep.addUpdateListener(animation -> {
                sweepPosition = (float) animation.getAnimatedValue();
                invalidateSelf();
            });
            sweep.start();
        }
        invalidateSelf();
        return true;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    private void rebuildShaders() {
        if (shape.isEmpty()) return;
        boolean lit = focused || selected || pressed;
        int surface = theme.panel;
        int opacity = 174;
        if (style.material == 1) {
            surface = VisualStyle.blend(theme.panel, 0xFFE2E9FF, .16f);
            opacity = 210;
        } else if (style.material == 2) {
            surface = VisualStyle.blend(theme.panel, Color.BLACK, .64f);
            opacity = 235;
        }
        int upper = lit ? tint(surface, pressed ? 248 : 222) : tint(surface, quiet ? 100 : opacity);
        int lower = lit ? tint(style.sceneBase(theme), 212) : tint(style.sceneBase(theme), quiet ? 72 : 158);
        glaze = new LinearGradient(shape.left, shape.top, shape.right, shape.bottom,
                upper, lower, Shader.TileMode.CLAMP);
        edge = new LinearGradient(shape.left, shape.top, shape.right, shape.bottom,
                focused ? tint(theme.accent, 248) : tint(theme.accent, selected ? 136 : 67),
                focused ? tint(theme.accent, 174) : tint(theme.accent, selected ? 65 : 28),
                Shader.TileMode.CLAMP);
        if (ambient != Color.TRANSPARENT && style.lightAmount() > 0) {
            float strength = style.lightAmount();
            glow = new RadialGradient(shape.left + shape.width() * .17f, shape.centerY(),
                    Math.max(shape.height() * 1.32f, shape.width() * .47f),
                    new int[]{tint(ambient, Math.round((focused ? 186 : 122) * strength)),
                            tint(ambient, Math.round((focused ? 85 : 44) * strength)),
                            tint(ambient, 0)}, new float[]{0f, .40f, 1f}, Shader.TileMode.CLAMP);
        }
        bevel = new LinearGradient(0, shape.top, 0, shape.bottom,
                new int[]{tint(0xFFFFFFFF, style.material == 1 ? 65 : 39), tint(0xFFFFFFFF, 0),
                        tint(Color.BLACK, 58)}, new float[]{0f, .25f, 1f}, Shader.TileMode.CLAMP);
        reflection = new LinearGradient(0, 0, shape.width() * .48f, 0,
                new int[]{0x00FFFFFF, 0x28FFFFFF, 0x00FFFFFF},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    public void draw(Canvas canvas) {
        if (shape.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(glaze);
        canvas.drawRoundRect(shape, radius, radius, paint);
        if (glow != null) {
            paint.setShader(glow);
            canvas.drawRoundRect(shape, radius, radius, paint);
        }
        paint.setShader(edge);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(focused ? stroke * 2f : stroke);
        canvas.drawRoundRect(shape, radius, radius, paint);
        paint.setShader(bevel);
        paint.setStrokeWidth(stroke);
        canvas.drawRoundRect(shape.left + stroke * 2, shape.top + stroke * 2,
                shape.right - stroke * 2, shape.bottom - stroke * 2,
                Math.max(0, radius - stroke * 2), Math.max(0, radius - stroke * 2), paint);
        if (sweepPosition >= -.5f && sweepPosition <= 1.5f && focused) {
            int layer = canvas.save();
            canvas.clipPath(clip);
            canvas.translate(shape.left + (sweepPosition - .24f) * shape.width(), 0);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(reflection);
            canvas.drawRect(0, shape.top, shape.width() * .48f, shape.bottom, paint);
            canvas.restoreToCount(layer);
        }
        paint.setShader(null);
    }

    @Override
    public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    static int tint(int color, int opacity) {
        return (color & 0x00FFFFFF) | (opacity << 24);
    }
}
