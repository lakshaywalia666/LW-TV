package com.zerotv.next;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AppCatalog {
    private static final Set<String> HIDDEN_PACKAGES = new HashSet<>(Arrays.asList(
            "com.mitv.gallery", "com.xiaomi.mitv.gallery", "com.xiaomi.mitv.smartshare",
            "com.xiaomi.mitv.tvmanager", "mitv.service", "com.mediatek.tv.service",
            "com.mediatek.wwtv.webview", "com.xiaomi.mitv.manualhelp",
            "com.mstar.android.tv.disclaimercustomization", "com.android.tv.settings",
            "com.google.android.tvlauncher", "com.mitv.tvhome.atv",
            "com.mitv.tvhome.michannel", "com.mitv.tvhome.mitvplus", "com.zerotv.launcher"));

    private final Context context;
    private final PackageManager manager;

    AppCatalog(Context context) {
        this.context = context;
        manager = context.getPackageManager();
    }

    List<Entry> load() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        addActivities(entries, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addActivities(entries, Intent.CATEGORY_LAUNCHER);
        List<Entry> apps = new ArrayList<>(entries.values());
        Collator labels = Collator.getInstance();
        Collections.sort(apps, (a, b) -> labels.compare(a.label, b.label));
        return apps;
    }

    private void addActivities(Map<String, Entry> entries, String category) {
        for (ResolveInfo info : manager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            if (info.activityInfo == null || !info.activityInfo.exported) {
                continue;
            }
            String name = info.activityInfo.packageName;
            if (entries.containsKey(name) || HIDDEN_PACKAGES.contains(name)
                    || name.equals(context.getPackageName())) {
                continue;
            }
            ComponentName component = new ComponentName(name, info.activityInfo.name);
            entries.put(name, new Entry(info.loadLabel(manager).toString(), component, category, info));
        }
    }

    Drawable artwork(Entry app) {
        switch (app.packageName()) {
            case "com.android.vending":
                return context.getDrawable(R.drawable.brand_play_store);
            case "com.netflix.ninja":
                return context.getDrawable(R.drawable.brand_netflix);
            case "com.google.android.youtube.tv":
                return context.getDrawable(R.drawable.brand_youtube);
            case "com.google.android.youtube.tvmusic":
                return context.getDrawable(R.drawable.brand_youtube_music);
            case "com.amazon.amazonvideo.livingroom":
            case "com.lionsgateplay.videoapp":
                try {
                    Drawable banner = manager.getActivityBanner(app.component);
                    if (banner != null) {
                        return banner;
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                    // The app can be updated while the launcher is open.
                }
                break;
        }
        return app.info.loadIcon(manager);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String category;
        final ResolveInfo info;
        private Integer ambient;

        Entry(String label, ComponentName component, String category, ResolveInfo info) {
            this.label = label;
            this.component = component;
            this.category = category;
            this.info = info;
        }

        String packageName() {
            return component.getPackageName();
        }

        int ambientColor(Drawable artwork) {
            if (ambient == null) ambient = IconLighting.colorOf(artwork);
            return ambient;
        }

        Intent launchIntent() {
            return new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }
}
