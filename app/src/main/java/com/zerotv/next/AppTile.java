package com.zerotv.next;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppTile extends FrameLayout {
    private static final DecelerateInterpolator EASE_OUT = new DecelerateInterpolator(1.5f);
    private static final OvershootInterpolator SETTLE = new OvershootInterpolator(.85f);
    final AppCatalog.Entry app;
    final int ambient;
    private final ImageView icon;
    private final LinearLayout caption;
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
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setDuplicateParentStateEnabled(true);
        addView(content, new FrameLayout.LayoutParams(-1, -1));
        icon = new ImageView(context);
        icon.setImageDrawable(artwork);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setElevation(dp(3));
        FrameLayout artworkArea = new FrameLayout(context);
        artworkArea.setClipChildren(false);
        artworkArea.setDuplicateParentStateEnabled(true);
        artworkArea.setBackground(new GlassSurface(theme, style, dp(16), dp(1), ambient, false, false));
        artworkArea.setPadding(dp(8), dp(5), dp(8), dp(5));
        content.addView(artworkArea, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout.LayoutParams iconSize = new FrameLayout.LayoutParams(
                dp(style.iconSize * 1.6f), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        icon.setMaxHeight(dp(style.iconSize));
        icon.setAdjustViewBounds(true);
        artworkArea.addView(icon, iconSize);
        caption = new LinearLayout(context);
        caption.setAlpha(.8f);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(dp(4), dp(5), dp(4), 0);
        content.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        TextView label = new TextView(context);
        label.setText(app.label);
        label.setTextColor(0xFFF4F6FF);
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", 0));
        label.setIncludeFontPadding(false);
        label.setSingleLine();
        label.setEllipsize(TextUtils.TruncateAt.END);
        caption.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        if (pinned) {
            TextView hint = new TextView(context);
            hint.setText("★");
            hint.setTextSize(12);
            hint.setTextColor(theme.accent);
            LinearLayout.LayoutParams hintSize = new LinearLayout.LayoutParams(-2, -2);
            hintSize.leftMargin = dp(4);
            caption.addView(hint, hintSize);
        }
        setContentDescription(app.label + (pinned ? ", pinned to Home" : ""));
    }

    void showDepth(boolean focused, int direction, boolean arranging) {
        float amount = style.depthAmount();
        float scale = focused ? 1f + .055f * amount : 1f;
        float iconScale = focused ? 1f + .12f * amount : 1f;
        animate().scaleX(scale).scaleY(scale).rotationX(focused ? -1.7f * amount : 0)
                .rotationY(focused ? direction * 2.5f * amount : 0)
                .translationZ(dp(focused ? (arranging ? 18 : 12) * amount : 0))
                .setInterpolator(EASE_OUT).setDuration(style.duration(210)).start();
        icon.animate().translationX(focused ? dp(direction * 3 * amount) : 0)
                .translationY(dp(focused ? -3 * amount : 0)).scaleX(iconScale).scaleY(iconScale)
                .translationZ(dp(focused ? 13 * amount : 0)).rotationY(focused ? -direction * amount : 0)
                .setInterpolator(focused ? SETTLE : EASE_OUT).setDuration(style.duration(330)).start();
        caption.animate().alpha(focused ? 1f : .8f).setInterpolator(EASE_OUT)
                .setDuration(style.duration(180)).start();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
