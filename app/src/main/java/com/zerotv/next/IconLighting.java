package com.zerotv.next;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

final class IconLighting {
    private static final int SAMPLE_SIZE = 28;
    private static final int HUES = 12;

    private IconLighting() {}

    static int colorOf(Drawable icon) {
        if (icon == null) return Color.TRANSPARENT;
        Bitmap sample = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888);
        Rect original = icon.copyBounds();
        int width = Math.max(1, icon.getIntrinsicWidth());
        int height = Math.max(1, icon.getIntrinsicHeight());
        float scale = (float) SAMPLE_SIZE / Math.max(width, height);
        int sampleWidth = Math.max(1, Math.round(width * scale));
        int sampleHeight = Math.max(1, Math.round(height * scale));
        int left = (SAMPLE_SIZE - sampleWidth) / 2;
        int top = (SAMPLE_SIZE - sampleHeight) / 2;
        int[] pixels = new int[SAMPLE_SIZE * SAMPLE_SIZE];
        try {
            icon.setBounds(left, top, left + sampleWidth, top + sampleHeight);
            icon.draw(new Canvas(sample));
            sample.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE);
        } finally {
            icon.setBounds(original);
            sample.recycle();
        }
        return dominantColor(pixels);
    }

    private static int dominantColor(int[] pixels) {
        float[] hsv = new float[3];
        float[] weights = new float[HUES];
        float[] strongest = new float[HUES];
        int[] swatches = new int[HUES];
        float neutralSum = 0;
        float neutralWeight = 0;
        for (int pixel : pixels) {
            float alpha = Color.alpha(pixel) / 255f;
            if (alpha < .35f) continue;
            Color.colorToHSV(pixel, hsv);
            if (hsv[2] < .16f) continue;
            neutralSum += hsv[2] * alpha;
            neutralWeight += alpha;
            if (hsv[1] < .22f) continue;
            int bucket = ((int) ((hsv[0] + 15f) / 30f)) % HUES;
            float weight = alpha * hsv[1] * hsv[1] * hsv[2];
            weights[bucket] += weight;
            if (weight > strongest[bucket]) {
                strongest[bucket] = weight;
                swatches[bucket] = pixel;
            }
        }
        int best = 0;
        for (int i = 1; i < HUES; i++) {
            if (weights[i] > weights[best]) best = i;
        }
        if (weights[best] >= 1.2f) {
            Color.colorToHSV(swatches[best], hsv);
            hsv[1] = Math.min(.94f, hsv[1]);
            hsv[2] = Math.max(.68f, hsv[2]);
            return Color.HSVToColor(hsv);
        }
        if (neutralWeight == 0) return Color.TRANSPARENT;
        int gray = Math.round(128 + 92 * neutralSum / neutralWeight);
        return Color.rgb(gray, gray, gray);
    }
}
