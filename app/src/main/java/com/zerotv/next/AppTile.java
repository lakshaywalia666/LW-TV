package com.zerotv.next;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppTile extends FrameLayout {
    final AppCatalog.Entry app;
    final int ambient;
    private final ImageView icon;
    private final VisualStyle style;

    AppTile(Context context, AppCatalog.Entry app, Drawable artwork, int ambient,
            ThemePalette theme, VisualStyle style, boolean pinned) {
        super(context);
        this.app = app;
        this.ambient = ambient;
        this.style = style;
        setClipChildren(false);
        setClipToPadding(false);
        setCameraDistance(dp(5000));
        setElevation(dp(2));
        LinearLayout content = new LinearLayout(context);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setClipChildren(false);
        content.setPadding(dp(15), 0, dp(12), 0);
        addView(content, new FrameLayout.LayoutParams(-1, -1));
        icon = new ImageView(context);
        icon.setImageDrawable(artwork);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dp(5), dp(5), dp(5), dp(5));
        icon.setBackground(new GlassSurface(theme, style, dp(15), dp(1), 0, false, true));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setElevation(dp(3));
        content.addView(icon, new LinearLayout.LayoutParams(dp(style.iconSize), dp(style.iconSize)));
        LinearLayout caption = new LinearLayout(context);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(12), 0, 0, 0);
        content.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        TextView label = new TextView(context);
        label.setText(app.label);
        label.setTextColor(0xFFF4F6FF);
        label.setTextSize(16);
        label.setTypeface(Typeface.create("sans-serif-medium", 0));
        label.setIncludeFontPadding(false);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        caption.addView(label);
        if (pinned) {
            TextView hint = new TextView(context);
            hint.setText("Pinned");
            hint.setTextSize(11);
            hint.setTextColor(theme.accent);
            LinearLayout.LayoutParams hintSize = new LinearLayout.LayoutParams(-2, -2);
            hintSize.topMargin = dp(4);
            caption.addView(hint, hintSize);
        }
        setContentDescription(app.label + (pinned ? ", pinned to Home" : ""));
    }

    void showDepth(boolean focused, int direction, boolean neighbor, boolean arranging) {
        float amount = style.depthAmount();
        float scale = focused ? 1f + .045f * amount : neighbor ? 1f - .018f * amount : 1f;
        animate().scaleX(scale).scaleY(scale).rotationX(focused ? -1.7f * amount : 0)
                .rotationY(focused ? direction * 2.5f * amount : 0)
                .translationZ(dp(focused ? (arranging ? 18 : 12) * amount : 0))
                .setInterpolator(new DecelerateInterpolator()).setDuration(style.duration(230)).start();
        icon.animate().translationY(dp(focused ? -4 * amount : 0))
                .translationZ(dp(focused ? 13 * amount : 0)).rotationY(focused ? -direction * amount : 0)
                .setDuration(style.duration(230)).start();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
