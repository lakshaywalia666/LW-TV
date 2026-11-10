package com.zerotv.next;

import android.content.SharedPreferences;
import android.graphics.Color;
import java.util.Calendar;

final class VisualStyle {
    static final String[] MATERIALS = {"Smoked Glass", "Frosted Glass", "Obsidian"};
    static final String[] WALLPAPERS = {"Aurora", "Misty Mountains", "Flowing Silk", "Abstract Landscape"};
    static final String[] DEPTHS = {"Flat", "Gentle", "Moderate", "Pronounced"};
    static final String[] LIGHT_LEVELS = {"Off", "Soft", "Balanced", "Vivid"};
    static final String[] LIGHTING = {"Automatic", "Daylight", "Evening", "Night", "Theme colors"};
    static final String[] CLOCKS = {"Floating Digital", "Glass Analog", "Aurora Clock"};
    final int material;
    final int wallpaper;
    final int depth;
    final int ambient;
    final int motion;
    final int iconSize;
    final int clock;
    final int lighting;
    final int period;

    VisualStyle(SharedPreferences preferences) {
        material = read(preferences, "material", 0, MATERIALS.length);
        wallpaper = read(preferences, "wallpaper", 0, WALLPAPERS.length);
        depth = read(preferences, "depth", 2, DEPTHS.length);
        ambient = read(preferences, "ambient", 2, LIGHT_LEVELS.length);
        motion = read(preferences, "motion", 2, 4);
        iconSize = new int[]{48, 60, 70}[read(preferences, "icon_size", 1, 3)];
        clock = read(preferences, "clock_style", 0, CLOCKS.length);
        lighting = read(preferences, "lighting", 0, LIGHTING.length);
        period = currentPeriod();
    }

    static int read(SharedPreferences preferences, String key, int fallback, int count) {
        int value = preferences.getInt(key, fallback);
        return value < 0 || value >= count ? fallback : value;
    }

    static int currentPeriod() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 17 ? 1 : hour >= 17 && hour < 21 ? 2 : 3;
    }

    long duration(long normal) {
        return Math.round(normal * new float[]{1.15f, 1f, .75f, 0f}[motion]);
    }

    float depthAmount() { return new float[]{0f, .45f, .75f, 1f}[depth]; }
    float lightAmount() { return new float[]{0f, .45f, .75f, 1f}[ambient]; }

    int sceneBase(ThemePalette palette) {
        int mode = lighting == 0 ? period : lighting;
        if (mode == 1) return blend(palette.base, 0xFF3C4559, .12f);
        if (mode == 2) return blend(palette.base, 0xFF673A32, .16f);
        if (mode == 3) return blend(palette.base, Color.BLACK, .18f);
        return palette.base;
    }

    static int blend(int from, int to, float amount) {
        float bounded = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * bounded),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * bounded),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * bounded));
    }
}
