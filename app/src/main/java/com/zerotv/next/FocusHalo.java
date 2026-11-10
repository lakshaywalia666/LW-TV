package com.zerotv.next;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

final class FocusHalo extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private ValueAnimator travel;
    private int color;

    FocusHalo(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
    }

    void follow(View target, ViewGroup root, int tint, VisualStyle style) {
        if (travel != null) travel.cancel();
        color = tint;
        int[] origin = new int[2];
        int[] location = new int[2];
        root.getLocationInWindow(origin);
        target.getLocationInWindow(location);
        float inset = getResources().getDisplayMetrics().density * 4;
        RectF end = new RectF(location[0] - origin[0] - inset, location[1] - origin[1] - inset,
                location[0] - origin[0] + target.getWidth() + inset,
                location[1] - origin[1] + target.getHeight() + inset);
        if (bounds.isEmpty() || style.motion == 3) {
            bounds.set(end);
            invalidate();
            return;
        }
        RectF start = new RectF(bounds);
        travel = ValueAnimator.ofFloat(0, 1);
        travel.setDuration(style.duration(240));
        travel.setInterpolator(new DecelerateInterpolator());
        travel.addUpdateListener(animation -> {
            float p = (float) animation.getAnimatedValue();
            bounds.set(start.left + (end.left - start.left) * p, start.top + (end.top - start.top) * p,
                    start.right + (end.right - start.right) * p,
                    start.bottom + (end.bottom - start.bottom) * p);
            invalidate();
        });
        travel.start();
    }

    void clear() {
        if (travel != null) travel.cancel();
        bounds.setEmpty();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bounds.isEmpty()) return;
        float density = getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        for (int layer = 3; layer >= 1; layer--) {
            paint.setColor(GlassSurface.tint(color, 9 + (4 - layer) * 7));
            paint.setStrokeWidth(density * layer * 3);
            canvas.drawRoundRect(bounds, density * 21, density * 21, paint);
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (travel != null) travel.cancel();
        super.onDetachedFromWindow();
    }
}
