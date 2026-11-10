package com.zerotv.next;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;

public final class MainActivity extends Activity {
    private static final int TEXT = 0xFFF4F6FF;
    private static final int MUTED = 0xFFB9C6DB;
    private static final ThemePalette[] PALETTES = ThemePalette.values();
    private static final int DEFAULT_THEME = ThemePalette.AMETHYST.ordinal();
    private static final int[] TILE_HEIGHTS = {84, 96, 104};
    private static final int[] IDLE_MINUTES = {0, 1, 2, 5, 10, 30};
    private static final String DEFAULT_FAVORITES = "in.startv.hotstar,com.lionsgateplay.videoapp,"
            + "com.netflix.ninja,com.android.vending,com.amazon.amazonvideo.livingroom,com.sonyliv,"
            + "com.google.android.youtube.tv,com.spotify.tv.android";
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private enum Page {
        HOME("Home"), APPS("All apps"), INPUTS("Inputs"), SETTINGS("Settings");
        final String label;
        Page(String label) { this.label = label; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private final EnumMap<Page, String> focusByPage = new EnumMap<>(Page.class);
    private final Map<String, View> focusTargets = new LinkedHashMap<>();
    private final List<View> tabButtons = new ArrayList<>();
    private final List<AppTile> appTiles = new ArrayList<>();
    private final Runnable showIdle = this::showIdleClock;
    private final Runnable shiftClock = this::shiftIdleClock;
    private final Runnable tick = this::updateHeader;
    private Page page = Page.HOME;
    private SharedPreferences prefs;
    private AppCatalog catalog;
    private List<AppCatalog.Entry> apps;
    private Weather weather;
    private ThemePalette theme;
    private VisualStyle style;
    private Atmosphere wallpaper;
    private FocusHalo focusHalo;
    private WeatherScene weatherScene;
    private AqiRing aqiRing;
    private FrameLayout root;
    private LinearLayout screen;
    private LinearLayout content;
    private View firstItem;
    private TextView greeting;
    private TextView conditions;
    private TextView airQuality;
    private View weatherInfo;
    private FrameLayout idle;
    private LinearLayout idleReadout;
    private View idlePreviousFocus;
    private int idlePosition;
    private int gridColumns = 4;
    private int lastAppPosition = -1;
    private Panel activePanel;
    private String arrangingPackage;
    private List<String> arrangementOrder;
    private int wakeKey = KeyEvent.KEYCODE_UNKNOWN;
    private boolean active;
    private boolean weatherInFlight;
    private long nextWeatherAttempt;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("ZeroTVNext", MODE_PRIVATE);
        catalog = new AppCatalog(this);
        weather = loadWeather();
        if (state != null) {
            page = Page.valueOf(state.getString("page", Page.HOME.name()));
            focusByPage.put(page, state.getString("focus"));
        } else {
            focusByPage.put(Page.HOME, prefs.getString("home_focus", null));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        apps = catalog.load();
        render();
        hideSystemBars(getWindow());
        updateHeader();
        resetIdleTimer();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            boolean wasArranging = arrangingPackage != null;
            if (activePanel != null) activePanel.dialog.dismiss();
            arrangementOrder = null;
            arrangingPackage = null;
            dismissIdle();
            if (page != Page.HOME || wasArranging) openPage(Page.HOME);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putString("page", page.name());
        out.putString("focus", focusByPage.get(page));
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onPause() {
        active = false;
        prefs.edit().putString("home_focus", focusByPage.get(Page.HOME)).apply();
        if (activePanel != null) activePanel.dialog.dismiss();
        arrangementOrder = null;
        arrangingPackage = null;
        if (root != null) root.setKeepScreenOn(false);
        wakeKey = KeyEvent.KEYCODE_UNKNOWN;
        dismissIdle();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(showIdle);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        weatherWorker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars(getWindow());
        resetIdleTimer();
    }

    static void hideSystemBars(Window window) {
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        resetIdleTimer();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == wakeKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) wakeKey = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (idle != null && event.getAction() == KeyEvent.ACTION_DOWN
                && (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU
                || (key >= KeyEvent.KEYCODE_DPAD_UP && key <= KeyEvent.KEYCODE_DPAD_CENTER)
                || key == KeyEvent.KEYCODE_ENTER)) {
            wakeKey = key;
            dismissIdle();
            return true;
        }
        if (key == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
            showQuickSettings();
            return true;
        }
        if (arrangingPackage != null && activePanel == null) return handleArrangementKey(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (idle != null) {
            dismissIdle();
        } else if (page != Page.HOME) {
            openPage(Page.HOME);
        }
    }

    private void openPage(Page target) {
        if (target != Page.HOME) {
            arrangementOrder = null;
            arrangingPackage = null;
        }
        page = target;
        render();
        content.setAlpha(.5f);
        content.setTranslationY(dp(8));
        content.animate().alpha(1).translationY(0).setDuration(style.duration(220)).start();
        resetIdleTimer();
    }

    private void render() {
        if (apps == null) return;
        String restore = focusByPage.get(page);
        focusTargets.clear();
        tabButtons.clear();
        appTiles.clear();
        lastAppPosition = -1;
        firstItem = null;
        greeting = null;
        conditions = null;
        airQuality = null;
        weatherInfo = null;
        weatherScene = null;
        aqiRing = null;
        theme = PALETTES[setting("accent", DEFAULT_THEME, PALETTES.length)];
        style = new VisualStyle(prefs);

        root = new FrameLayout(this);
        root.setBackgroundColor(style.sceneBase(theme));
        wallpaper = new Atmosphere(this, theme, style, false);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        screen = column();
        screen.setClipChildren(false);
        screen.setClipToPadding(false);
        int margin = getResources().getConfiguration().screenWidthDp < 720 ? 26 : 40;
        screen.setPadding(dp(margin), dp(17), dp(margin), 0);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        addTopBar();

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFocusable(false);
        scroll.setClipChildren(false);
        scroll.setPadding(0, dp(14), 0, dp(12));
        scroll.setClipToPadding(false);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = column();
        content.setClipChildren(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            View focused = getCurrentFocus();
            if (focused instanceof AppTile) {
                focusHalo.follow(focused, root, ((AppTile) focused).ambient, style);
            }
        });

        switch (page) {
            case HOME:
                addHero();
                addAppGrid(true);
                break;
            case APPS:
                addPageTitle("All apps", "Open an app or hold OK for its options.");
                addAppGrid(false);
                break;
            case INPUTS:
                addInputs();
                break;
            case SETTINGS:
                addSettings();
                break;
        }
        View tab = tabButtons.get(page.ordinal());
        if (firstItem != null) {
            for (View button : tabButtons) button.setNextFocusDownId(firstItem.getId());
        }
        focusHalo = new FocusHalo(this);
        root.addView(focusHalo, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        View target = focusTargets.get(restore);
        if (target == null) target = firstItem == null ? tab : firstItem;
        target.requestFocus();
        drawHeader();
        resetIdleTimer();
    }

    private void addTopBar() {
        LinearLayout rail = row();
        rail.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(rail, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView wordmark = label(getString(R.string.app_name), 22, TEXT, MEDIUM);
        wordmark.setLetterSpacing(.06f);
        wordmark.setPadding(0, 0, dp(10), 0);
        wordmark.setSingleLine();
        rail.addView(wordmark);

        rail.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout tabs = row();
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(glass(21, Color.TRANSPARENT, false, true));
        tabs.setClipChildren(false);
        rail.addView(tabs, new LinearLayout.LayoutParams(-2, dp(43)));
        for (Page destination : Page.values()) {
            TextView tab = button(destination.label, 15, destination == page,
                    "tab:" + destination.name(), () -> openPage(destination));
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(13), 0, dp(13), 0);
            tabs.addView(tab, new LinearLayout.LayoutParams(-2, -1));
            if (!tabButtons.isEmpty()) {
                View previous = tabButtons.get(tabButtons.size() - 1);
                previous.setNextFocusRightId(tab.getId());
                tab.setNextFocusLeftId(previous.getId());
            }
            tabButtons.add(tab);
        }
        rail.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextClock clock = new TextClock(this);
        clock.setTextColor(TEXT);
        clock.setTextSize(25);
        clock.setTypeface(LIGHT);
        clock.setIncludeFontPadding(false);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("HH:mm");
        rail.addView(clock);
        TextView quick = button("≡", 22, false, "quick", this::showQuickSettings);
        quick.setGravity(Gravity.CENTER);
        quick.setContentDescription("Quick settings");
        LinearLayout.LayoutParams quickSize = new LinearLayout.LayoutParams(dp(38), dp(36));
        quickSize.leftMargin = dp(16);
        rail.addView(quick, quickSize);
        quick.setNextFocusLeftId(tabButtons.get(Page.SETTINGS.ordinal()).getId());
        tabButtons.get(Page.SETTINGS.ordinal()).setNextFocusRightId(quick.getId());
    }

    private void addHero() {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(glass(24, Color.TRANSPARENT, true, false));
        hero.setClipToOutline(true);
        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(132)));
        FrameLayout.LayoutParams artSize = new FrameLayout.LayoutParams(-1, -1);
        artSize.setMargins(dp(1), dp(1), dp(1), dp(1));
        hero.addView(new Atmosphere(this, theme, style, true), artSize);

        LinearLayout foreground = row();
        foreground.setGravity(Gravity.CENTER_VERTICAL);
        foreground.setPadding(dp(25), 0, dp(18), 0);
        hero.addView(foreground, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout welcome = column();
        foreground.addView(welcome, new LinearLayout.LayoutParams(0, -2, 1));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(14);
        date.setTextColor(accent());
        date.setTypeface(MEDIUM);
        date.setIncludeFontPadding(false);
        date.setLetterSpacing(.09f);
        welcome.addView(date);
        greeting = label("", 32, TEXT, MEDIUM);
        greeting.setMaxLines(2);
        greeting.setEllipsize(TextUtils.TruncateAt.END);
        spaceAbove(welcome, greeting, 10);
        if (showWeather()) addWeatherCard(foreground);
    }

    private void addWeatherCard(LinearLayout foreground) {
        LinearLayout card = column();
        card.setBackground(glass(18, Color.TRANSPARENT, false, false));
        card.setPadding(dp(13), dp(7), dp(10), dp(9));
        int cardWidth = getResources().getConfiguration().screenWidthDp < 720 ? 265 : 310;
        LinearLayout.LayoutParams cardSize = new LinearLayout.LayoutParams(dp(cardWidth), -2);
        cardSize.leftMargin = dp(20);
        foreground.addView(card, cardSize);

        LinearLayout details = row();
        details.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(details, new LinearLayout.LayoutParams(-1, -2));
        TextView location = label(weather.location, 13, MUTED, MEDIUM);
        location.setSingleLine(true);
        location.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(location, new LinearLayout.LayoutParams(0, -2, 1));
        TextView info = button("↗", 17, false, "weather:source", this::showWeatherDetails);
        info.setGravity(Gravity.CENTER);
        info.setContentDescription("Weather and air quality details");
        details.addView(info, new LinearLayout.LayoutParams(dp(30), dp(26)));
        weatherInfo = info;

        LinearLayout data = row();
        data.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(data, new LinearLayout.LayoutParams(-1, dp(68)));
        weatherScene = new WeatherScene(this);
        data.addView(weatherScene, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout readings = column();
        readings.setPadding(dp(7), 0, 0, 0);
        data.addView(readings, new LinearLayout.LayoutParams(0, -2, 1));
        conditions = label("", 20, TEXT, LIGHT);
        conditions.setMaxLines(2);
        readings.addView(conditions);
        airQuality = label("", 12, MUTED, MEDIUM);
        airQuality.setMaxLines(2);
        spaceAbove(readings, airQuality, 5);
        aqiRing = new AqiRing(this);
        data.addView(aqiRing, new LinearLayout.LayoutParams(dp(66), dp(66)));
    }

    private void showWeatherSource() {
        new AlertDialog.Builder(this).setTitle("Weather and air quality")
                .setMessage("Weather data by Open-Meteo under CC BY 4.0. LW TV rounds the temperature "
                        + "and presents weather codes as condition names. Air quality is a model estimate "
                        + "from the Copernicus Atmosphere Monitoring Service (CAMS), supplied by Open-Meteo. "
                        + "AQI is calculated for your selected location using CPCB Indian AQI "
                        + "breakpoints and rolling 24-hour pollutant averages (8 hours for CO and ozone). "
                        + "The model grid covers about 45 km. This estimate can differ from a nearby "
                        + "CPCB station reading.")
                .setNeutralButton("Licence", (dialog, which) -> openWeatherLink("https://open-meteo.com/en/licence"))
                .setNegativeButton("Close", null)
                .setPositiveButton("Open source", (dialog, which) -> openWeatherLink("https://open-meteo.com/"))
                .show();
    }

    private void openWeatherLink(String url) {
        launch(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void showWeatherDetails() {
        Panel panel = new Panel(weather.location + " weather", "Indian AQI · model estimate");
        panel.text(weather.summary() == null ? "Weather unavailable" : weather.summary(), 22, TEXT);
        Integer aqi = weather.airQualityIndex();
        panel.text(aqi == null ? "AQI unavailable"
                : "India AQI " + aqi + " · " + Weather.airQualityCategory(aqi),
                17, aqi == null ? MUTED : Weather.airQualityColor(aqi));
        panel.text(weather.updatedTime(), 12, MUTED);
        panel.body.addView(new AqiTrend(this, weather.history(), accent()),
                new LinearLayout.LayoutParams(-1, dp(150)));
        panel.text("Recent 24-hour model trend", 12, MUTED);
        for (String pollutant : weather.pollutantDetails()) panel.text(pollutant, 12, TEXT);
        panel.action("Data source and licence", "Open-Meteo · CAMS · CPCB scale",
                this::showWeatherSource);
        panel.preferred = panel.closeButton;
        panel.show();
    }

    private void addPageTitle(String title, String subtitle) {
        TextView heading = label(title, 32, TEXT, MEDIUM);
        content.addView(heading);
        spaceAbove(content, label(subtitle, 15, MUTED, null), 7);
        content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(18)));
    }

    private void addAppGrid(boolean home) {
        List<String> pins = favorites();
        List<AppCatalog.Entry> displayed = new ArrayList<>();
        if (home) {
            Map<String, AppCatalog.Entry> installed = new LinkedHashMap<>();
            for (AppCatalog.Entry app : apps) installed.put(app.packageName(), app);
            for (String name : pins) {
                AppCatalog.Entry app = installed.get(name);
                if (app != null) displayed.add(app);
            }
            LinearLayout title = row();
            title.setGravity(Gravity.CENTER_VERTICAL);
            TextView heading = label(arrangingPackage == null ? "Favorites" : "Arrange favorites", 22, TEXT, MEDIUM);
            title.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
            String hint = arrangingPackage == null ? "Hold OK to arrange · Menu for options"
                    : "Arrows move · OK saves · Back cancels";
            title.addView(label(hint, 12, MUTED, null));
            spaceAbove(content, title, 16);
            content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(11)));
        } else {
            displayed.addAll(apps);
        }

        int density = setting("tile_size", 2, TILE_HEIGHTS.length);
        int width = getResources().getConfiguration().screenWidthDp;
        int columns = width < 690 ? 3 : density == 0 && width >= 870 ? 5 : 4;
        gridColumns = columns;
        List<View> cells = new ArrayList<>();
        for (AppCatalog.Entry app : displayed) cells.add(appTile(app, !home && pins.contains(app.packageName())));
        if (home) {
            TextView browse = button("+   Browse apps", 16, false, "browse", () -> openPage(Page.APPS));
            browse.setGravity(Gravity.CENTER);
            cells.add(browse);
        }
        if (cells.isEmpty()) {
            content.addView(label("Installed TV apps will appear here.", 16, MUTED, null));
        } else {
            addGrid(cells, columns, TILE_HEIGHTS[density]);
        }
    }

    private View appTile(AppCatalog.Entry app, boolean pinned) {
        Drawable artwork = catalog.artwork(app);
        int ambient = app.ambientColor(artwork);
        AppTile tile = new AppTile(this, app, artwork, ambient, theme, style, pinned);
        appTiles.add(tile);
        focusable(tile, "app:" + app.packageName(), glass(18, ambient, false, false),
                () -> launch(app.launchIntent(), tile));
        tile.setOnLongClickListener(view -> {
            view.requestFocusFromTouch();
            if (page == Page.HOME) startArrangement(app);
            else showAppOptions(app);
            return true;
        });
        return tile;
    }

    private void showAppOptions(AppCatalog.Entry app) {
        List<String> pins = favorites();
        String name = app.packageName();
        int index = pins.indexOf(name);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(index >= 0 ? "Unpin from Home" : "Pin to Home");
        actions.add(() -> {
            if (index >= 0) pins.remove(index);
            else pins.add(name);
            saveFavorites(pins);
        });
        if (page == Page.HOME) {
            int earlier = visibleNeighbor(pins, index, -1);
            int later = visibleNeighbor(pins, index, 1);
            if (earlier >= 0) {
                labels.add("Move earlier");
                actions.add(() -> moveFavorite(pins, index, earlier));
            }
            if (later >= 0) {
                labels.add("Move later");
                actions.add(() -> moveFavorite(pins, index, later));
            }
        }
        labels.add("App info");
        actions.add(() -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", name, null))));
        if (page == Page.HOME) {
            labels.add(0, "Arrange favorites");
            actions.add(0, () -> startArrangement(app));
        }
        Panel panel = new Panel(app.label, "App options");
        for (int i = 0; i < labels.size(); i++) panel.action(labels.get(i), "", actions.get(i));
        panel.show();
    }

    private List<String> favorites() {
        if (arrangementOrder != null) return new ArrayList<>(arrangementOrder);
        String saved = prefs.getString("favorites", DEFAULT_FAVORITES);
        return saved.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(saved.split(",")));
    }

    private int visibleNeighbor(List<String> pins, int index, int direction) {
        Set<String> installed = new HashSet<>();
        for (AppCatalog.Entry app : apps) installed.add(app.packageName());
        for (int next = index + direction; next >= 0 && next < pins.size(); next += direction) {
            if (installed.contains(pins.get(next))) return next;
        }
        return -1;
    }

    private void moveFavorite(List<String> pins, int index, int neighbor) {
        Collections.swap(pins, index, neighbor);
        saveFavorites(pins);
    }

    private void saveFavorites(List<String> pins) {
        arrangementOrder = null;
        arrangingPackage = null;
        prefs.edit().putString("favorites", TextUtils.join(",", pins)).apply();
        render();
    }

    private void startArrangement(AppCatalog.Entry app) {
        arrangementOrder = favorites();
        arrangingPackage = app.packageName();
        page = Page.HOME;
        focusByPage.put(Page.HOME, "app:" + arrangingPackage);
        render();
    }

    private boolean handleArrangementKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK) {
            arrangementOrder = null;
            arrangingPackage = null;
            render();
            return true;
        }
        if ((key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)
                && event.getRepeatCount() == 0) {
            saveFavorites(new ArrayList<>(arrangementOrder));
            return true;
        }
        List<AppTile> visible = new ArrayList<>(appTiles);
        int current = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).app.packageName().equals(arrangingPackage)) current = i;
        }
        int direction;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: direction = -gridColumns; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = gridColumns; break;
            default: return true;
        }
        int next = current + direction;
        if (current < 0 || next < 0 || next >= visible.size()) return true;
        Map<String, int[]> positions = new LinkedHashMap<>();
        for (AppTile tile : visible) {
            int[] location = new int[2];
            tile.getLocationInWindow(location);
            positions.put(tile.app.packageName(), location);
        }
        String other = visible.get(next).app.packageName();
        Collections.swap(arrangementOrder, arrangementOrder.indexOf(arrangingPackage), arrangementOrder.indexOf(other));
        render();
        root.post(() -> {
            for (AppTile tile : appTiles) {
                int[] before = positions.get(tile.app.packageName());
                int[] after = new int[2];
                tile.getLocationInWindow(after);
                if (before == null) continue;
                tile.setTranslationX(before[0] - after[0]);
                tile.setTranslationY(before[1] - after[1]);
                tile.animate().translationX(0).translationY(0).setDuration(style.duration(230)).start();
            }
        });
        return true;
    }

    private void addInputs() {
        addPageTitle("Inputs", "Switch to a connected device or open the built-in TV app.");
        List<View> cells = new ArrayList<>();
        TvInputManager manager = getSystemService(TvInputManager.class);
        if (manager != null) {
            List<TvInputInfo> hdmi = new ArrayList<>();
            for (TvInputInfo input : manager.getTvInputList()) {
                if (input.getType() == TvInputInfo.TYPE_HDMI) hdmi.add(input);
            }
            Collections.sort(hdmi, (a, b) -> inputLabel(a).compareToIgnoreCase(inputLabel(b)));
            for (TvInputInfo input : hdmi) {
                String name = inputLabel(input);
                int state = manager.getInputState(input.getId());
                String detail = state == TvInputManager.INPUT_STATE_CONNECTED ? "Connected"
                        : state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? "Standby" : "No signal";
                int kind = setting("input_type:" + input.getId(), 0, InputGlyph.TYPES.length);
                LinearLayout tile = row();
                tile.setGravity(Gravity.CENTER_VERTICAL);
                tile.setPadding(dp(17), 0, dp(17), 0);
                int color = state == TvInputManager.INPUT_STATE_CONNECTED ? 0xFF9AE6C2 : theme.accent;
                tile.addView(new InputGlyph(this, kind, color),
                        new LinearLayout.LayoutParams(dp(55), dp(55)));
                LinearLayout caption = column();
                caption.setPadding(dp(17), 0, 0, 0);
                caption.addView(label(name, 20, TEXT, MEDIUM));
                spaceAbove(caption, label(detail + " · " + InputGlyph.TYPES[kind], 13, MUTED, null), 5);
                tile.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
                focusable(tile, "input:" + input.getId(), glass(19,
                        state == TvInputManager.INPUT_STATE_CONNECTED ? color : 0, false, false),
                        () -> launch(new Intent(this, InputActivity.class)
                                .putExtra(InputActivity.INPUT_ID, input.getId())
                                .putExtra(InputActivity.INPUT_LABEL, name)));
                tile.setContentDescription(name + ", " + detail + ", hold OK to customize");
                tile.setOnLongClickListener(view -> { showInputOptions(input); return true; });
                cells.add(tile);
            }
        }
        for (AppCatalog.Entry app : apps) {
            if ("com.mediatek.wwtv.tvcenter".equals(app.packageName())) {
                cells.add(settingTile("Live TV", "Open the TV tuner", "input:live",
                        () -> launch(app.launchIntent())));
                break;
            }
        }
        if (cells.isEmpty()) {
            content.addView(label("There are no available inputs.", 16, MUTED, null));
        } else {
            addGrid(cells, 2, 104);
        }
    }

    private String inputLabel(TvInputInfo input) {
        String saved = prefs.getString("input_name:" + input.getId(), "");
        if (!saved.isEmpty()) return saved;
        CharSequence custom = Build.VERSION.SDK_INT >= 24 ? input.loadCustomLabel(this) : null;
        return TextUtils.isEmpty(custom) ? input.loadLabel(this).toString() : custom.toString();
    }

    private void showInputOptions(TvInputInfo input) {
        Panel panel = new Panel(inputLabel(input), "Input appearance");
        panel.action("Rename input", "", () -> editText("Input name",
                "input_name:" + input.getId(), inputLabel(input)));
        panel.action("Device symbol", InputGlyph.TYPES[setting("input_type:" + input.getId(), 0, 3)],
                () -> showChoice("Device symbol", "input_type:" + input.getId(), 0, InputGlyph.TYPES));
        panel.show();
    }

    private void addSettings() {
        addPageTitle("Settings", "Materials, lighting, motion and personal details.");
        List<View> cells = new ArrayList<>();
        cells.add(settingTile("TV settings", "Network, sound and device preferences", "settings:system",
                () -> launch(new Intent(Settings.ACTION_SETTINGS))));
        cells.add(settingTile("Default Home app", "Choose which launcher the Home button opens", "settings:home",
                () -> launch(new Intent(Settings.ACTION_HOME_SETTINGS))));
        addChoice(cells, "App tile size", "tile_size", 2, "Compact", "Standard", "Large");
        cells.add(settingTile("Color theme", theme.label, "settings:theme", this::showThemes));
        addChoice(cells, "Glass finish", "material", 0, VisualStyle.MATERIALS);
        addChoice(cells, "Wallpaper", "wallpaper", 0, VisualStyle.WALLPAPERS);
        addChoice(cells, "3D depth", "depth", 2, VisualStyle.DEPTHS);
        addChoice(cells, "Ambient light", "ambient", 2, VisualStyle.LIGHT_LEVELS);
        addChoice(cells, "Icon size", "icon_size", 1, "Small", "Medium", "Large");
        addChoice(cells, "Scene lighting", "lighting", 0, VisualStyle.LIGHTING);
        addChoice(cells, "Motion", "motion", 2, "Full", "Standard", "Subtle", "Off");
        String name = prefs.getString("display_name", "");
        cells.add(settingTile("Personal greeting", name.isEmpty() ? "Add your name" : name,
                "settings:name", () -> editText("Your name", "display_name", "")));
        addChoice(cells, "Clock design", "clock_style", 0, VisualStyle.CLOCKS);
        addChoice(cells, "Idle clock", "idle_timeout", 5, "Off", "After 1 minute", "After 2 minutes",
                "After 5 minutes", "After 10 minutes", "After 30 minutes");
        addChoice(cells, "Weather on Home", "weather", 1, "Off", "On");
        cells.add(settingTile("Weather location", weather.location, "settings:location", this::editWeatherLocation));
        cells.add(settingTile("Open idle clock", "Show the clock now", "settings:idle_now", this::showIdleClock));
        cells.add(settingTile("Reset appearance", "Restore the default theme, size and clock choices",
                "settings:reset", this::confirmResetAppearance));
        addGrid(cells, 2, 64);
    }

    private void showThemes() {
        Panel panel = new Panel("Color theme", "Applies to glass, focus and wallpaper");
        for (ThemePalette choice : PALETTES) {
            View option = panel.action(choice.label, choice == theme ? "Selected" : "", () -> {
                prefs.edit().putInt("accent", choice.ordinal()).apply();
                render();
            });
            option.setBackground(new GlassSurface(choice, style, dp(14), dp(1),
                    choice.aurora, choice == theme, false));
            if (choice == theme) panel.preferred = option;
        }
        panel.show();
    }

    private void confirmResetAppearance() {
        new AlertDialog.Builder(this).setTitle("Reset appearance?")
                .setMessage("Restore the default materials, colors, motion and clock settings?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, button) -> {
                    SharedPreferences.Editor reset = prefs.edit();
                    for (String key : new String[]{"tile_size", "accent", "motion", "idle", "idle_timeout",
                            "weather", "material", "wallpaper", "depth", "ambient", "icon_size",
                            "lighting", "clock_style"}) reset.remove(key);
                    reset.apply();
                    render();
                    updateHeader();
                }).show();
    }

    private void addChoice(List<View> cells, String title, String key, int initial, String... choices) {
        int current = setting(key, initial, choices.length);
        cells.add(settingTile(title, choices[current], "settings:" + key,
                () -> showChoice(title, key, initial, choices)));
    }

    private void showChoice(String title, String key, int initial, String... choices) {
        Panel panel = new Panel(title, "Choose your preference");
        int selected = setting(key, initial, choices.length);
        for (int index = 0; index < choices.length; index++) {
            final int choice = index;
            View row = panel.action(choices[index], index == selected ? "Selected" : "", () -> {
                prefs.edit().putInt(key, choice).apply();
                render();
                updateHeader();
            });
            if (index == selected) panel.preferred = row;
        }
        panel.show();
    }

    private void editText(String title, String key, String fallback) {
        Panel panel = new Panel(title, "Use your remote or a connected keyboard");
        EditText input = editField(prefs.getString(key, fallback), InputType.TYPE_CLASS_TEXT, 40);
        panel.body.addView(input, new LinearLayout.LayoutParams(-1, dp(55)));
        panel.action("Save", "", () -> {
            String entered = input.getText().toString().trim();
            prefs.edit().putString(key, entered.isEmpty() ? fallback : entered).apply();
            render();
        });
        panel.preferred = input;
        panel.show();
    }

    private EditText editField(String value, int type, int limit) {
        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        input.setInputType(type);
        input.setSingleLine(true);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setTextColor(TEXT);
        input.setTextSize(19);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(glass(12, 0, false, false));
        return input;
    }

    private double weatherCoordinate(String key, double fallback) {
        return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToLongBits(fallback)));
    }

    private Weather loadWeather() {
        double latitude = weatherCoordinate("weather_latitude", Weather.DEFAULT_LATITUDE);
        double longitude = weatherCoordinate("weather_longitude", Weather.DEFAULT_LONGITUDE);
        String location = prefs.getString("weather_location", Weather.DEFAULT_LOCATION);
        String cache = "LwTvWeather_" + Long.toHexString(Double.doubleToLongBits(latitude))
                + "_" + Long.toHexString(Double.doubleToLongBits(longitude));
        return new Weather(getSharedPreferences(cache, MODE_PRIVATE), location, latitude, longitude);
    }

    private void editWeatherLocation() {
        Panel panel = new Panel("Weather location", "Enter a place name and decimal coordinates");
        int number = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        EditText name = editField(weather.location, InputType.TYPE_CLASS_TEXT, 40);
        EditText latitude = editField(Double.toString(weatherCoordinate("weather_latitude", Weather.DEFAULT_LATITUDE)), number, 20);
        EditText longitude = editField(Double.toString(weatherCoordinate("weather_longitude", Weather.DEFAULT_LONGITUDE)), number, 20);
        String[] labels = {"Place name", "Latitude", "Longitude"};
        EditText[] fields = {name, latitude, longitude};
        for (int i = 0; i < fields.length; i++) {
            panel.text(labels[i], 13, MUTED);
            fields[i].setContentDescription(labels[i]);
            panel.body.addView(fields[i], new LinearLayout.LayoutParams(-1, dp(44)));
        }
        View save = settingTile("Save location", "", null, () -> {
            String place = name.getText().toString().trim();
            try {
                double lat = Double.parseDouble(latitude.getText().toString().trim().replace(',', '.'));
                double lon = Double.parseDouble(longitude.getText().toString().trim().replace(',', '.'));
                if (place.isEmpty() || !(lat >= -90 && lat <= 90) || !(lon >= -180 && lon <= 180)) {
                    throw new NumberFormatException();
                }
                prefs.edit().putString("weather_location", place)
                        .putLong("weather_latitude", Double.doubleToLongBits(lat))
                        .putLong("weather_longitude", Double.doubleToLongBits(lon)).apply();
                panel.dialog.dismiss();
                weather = loadWeather();
                weatherInFlight = false;
                nextWeatherAttempt = 0;
                render();
                updateHeader();
            } catch (NumberFormatException invalid) {
                Toast.makeText(this, "Enter a name, latitude −90 to 90 and longitude −180 to 180.",
                        Toast.LENGTH_LONG).show();
            }
        });
        spaceAbove(panel.body, save, 10);
        save.getLayoutParams().height = dp(44);
        panel.preferred = name;
        panel.show();
    }

    private void showQuickSettings() {
        Panel panel = new Panel("Quick settings", "LW TV");
        String focused = focusByPage.get(page);
        if ((focused == null || !focused.startsWith("app:"))
                && lastAppPosition >= 0 && lastAppPosition < appTiles.size()) {
            focused = "app:" + appTiles.get(lastAppPosition).app.packageName();
        }
        for (AppCatalog.Entry app : apps) {
            if (("app:" + app.packageName()).equals(focused)) {
                panel.action("App options", app.label, () -> showAppOptions(app));
                break;
            }
        }
        panel.action("Wi-Fi", "Network connections", () -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        panel.action("TV settings", "Sound and device preferences", () -> launch(new Intent(Settings.ACTION_SETTINGS)));
        panel.action("HDMI inputs", "Connected devices", () -> openPage(Page.INPUTS));
        panel.action("Appearance", "Glass, wallpaper and lighting", () -> openPage(Page.SETTINGS));
        panel.action("Screensaver", VisualStyle.CLOCKS[style.clock], this::showClockSettings);
        panel.action("Weather and air quality", weather.location, this::showWeatherDetails);
        panel.show();
    }

    private void showClockSettings() {
        Panel panel = new Panel("Screensaver", "Starts after 30 minutes by default");
        panel.action("Clock design", VisualStyle.CLOCKS[style.clock],
                () -> showChoice("Clock design", "clock_style", 0, VisualStyle.CLOCKS));
        String[] timeouts = {"Off", "After 1 minute", "After 2 minutes", "After 5 minutes",
                "After 10 minutes", "After 30 minutes"};
        panel.action("Start after", timeouts[setting("idle_timeout", 5, timeouts.length)],
                () -> showChoice("Start screensaver", "idle_timeout", 5, timeouts));
        panel.action("Preview screensaver", "", this::showIdleClock);
        panel.show();
    }

    private View settingTile(String title, String detail, String key, Runnable action) {
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(16), 0, dp(16), 0);
        tile.addView(label(title, 17, TEXT, MEDIUM));
        spaceAbove(tile, label(detail, 13, MUTED, null), 4);
        tile.setContentDescription(title + ", " + detail);
        focusable(tile, key, glass(16, Color.TRANSPARENT, false, false), action);
        return tile;
    }

    private void addGrid(List<View> cells, int columns, int height) {
        firstItem = cells.get(0);
        int up = tabButtons.get(page.ordinal()).getId();
        int weatherColumn = Math.min(columns, cells.size()) - 1;
        if (weatherInfo != null) {
            weatherInfo.setNextFocusUpId(up);
            weatherInfo.setNextFocusLeftId(up);
            weatherInfo.setNextFocusDownId(cells.get(weatherColumn).getId());
        }
        for (int start = 0; start < cells.size(); start += columns) {
            LinearLayout line = row();
            line.setClipChildren(false);
            LinearLayout.LayoutParams lineSize = new LinearLayout.LayoutParams(-1, dp(height));
            lineSize.bottomMargin = dp(10);
            content.addView(line, lineSize);
            for (int column = 0; column < columns; column++) {
                int index = start + column;
                LinearLayout.LayoutParams cellSize = new LinearLayout.LayoutParams(0, -1, 1);
                cellSize.rightMargin = column == columns - 1 ? 0 : dp(12);
                if (index >= cells.size()) {
                    line.addView(new View(this), cellSize);
                    continue;
                }
                View cell = cells.get(index);
                line.addView(cell, cellSize);
                int above = start == 0 ? up : cells.get(index - columns).getId();
                if (weatherInfo != null && start == 0 && column == weatherColumn) {
                    above = weatherInfo.getId();
                }
                cell.setNextFocusUpId(above);
                cell.setNextFocusLeftId(column == 0 ? cell.getId() : cells.get(index - 1).getId());
                cell.setNextFocusRightId(column == columns - 1 || index == cells.size() - 1
                        ? cell.getId() : cells.get(index + 1).getId());
                if (start + columns < cells.size()) {
                    cell.setNextFocusDownId(cells.get(Math.min(index + columns, cells.size() - 1)).getId());
                } else {
                    cell.setNextFocusDownId(cell.getId());
                }
            }
        }
    }

    private void launch(Intent intent) {
        launch(intent, getCurrentFocus());
    }

    private void launch(Intent intent, View origin) {
        try {
            if (origin != null && origin.isAttachedToWindow() && style.motion != 3) {
                ActivityOptions transition = ActivityOptions.makeScaleUpAnimation(origin, 0, 0,
                        Math.max(1, origin.getWidth()), Math.max(1, origin.getHeight()));
                startActivity(intent, transition.toBundle());
            } else {
                startActivity(intent);
            }
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "This action is unavailable on this TV.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHeader() {
        handler.removeCallbacks(tick);
        if (!active) return;
        if (style != null && style.lighting == 0 && style.period != VisualStyle.currentPeriod()
                && activePanel == null && idle == null) render();
        drawHeader();
        long now = SystemClock.elapsedRealtime();
        if (showWeather() && !weatherInFlight && now >= nextWeatherAttempt) {
            weatherInFlight = true;
            drawHeader();
            Weather source = weather;
            weatherWorker.execute(() -> {
                boolean success = true;
                try {
                    source.refresh();
                } catch (IOException | JSONException ignored) {
                    success = false;
                }
                try {
                    source.refreshAirQuality();
                } catch (IOException | JSONException ignored) {
                    success = false;
                }
                final long delay = success ? 600_000 : 300_000;
                handler.post(() -> {
                    if (weather != source) return;
                    weatherInFlight = false;
                    nextWeatherAttempt = SystemClock.elapsedRealtime() + delay;
                    if (active) drawHeader();
                });
            });
        }
        handler.postDelayed(tick, 60_000);
    }

    private void drawHeader() {
        if (greeting == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String partOfDay = hour < 12 ? "morning" : hour < 18 ? "afternoon" : "evening";
        String name = prefs.getString("display_name", "");
        greeting.setText("Good " + partOfDay + (name.isEmpty() ? "" : ", " + name));
        if (conditions == null) return;
        String summary = weather.summary();
        conditions.setText(summary == null ? (weatherInFlight ? "Updating weather…" : "Weather unavailable")
                : summary);
        Integer aqi = weather.airQualityIndex();
        airQuality.setText(aqi == null ? (weatherInFlight ? "Updating AQI…" : "AQI unavailable")
                : Weather.airQualityCategory(aqi) + " · estimated");
        airQuality.setTextColor(aqi == null ? MUTED : Weather.airQualityColor(aqi));
        weatherScene.setConditions(weather.code(), weather.isDay());
        aqiRing.setIndex(aqi);
        weatherInfo.setContentDescription("Weather and air quality details, " + weather.location
                + ", " + conditions.getText() + (aqi == null ? ", AQI unavailable"
                : ", Indian AQI estimate " + aqi + ", " + Weather.airQualityCategory(aqi)));
    }

    private void resetIdleTimer() {
        handler.removeCallbacks(showIdle);
        int minutes = IDLE_MINUTES[setting("idle_timeout", 5, IDLE_MINUTES.length)];
        if (root != null) {
            root.setKeepScreenOn(active && (idle != null || (minutes > 0 && page == Page.HOME)));
        }
        if (active && minutes > 0 && idle == null && activePanel == null
                && arrangingPackage == null && page == Page.HOME && hasWindowFocus()) {
            handler.postDelayed(showIdle, minutes * 60_000L);
        }
    }

    private void showIdleClock() {
        if (!active || idle != null) return;
        handler.removeCallbacks(showIdle);
        idlePreviousFocus = getCurrentFocus();
        idle = new FrameLayout(this);
        idle.setBackgroundColor(Color.BLACK);
        idle.setFocusable(true);
        idle.setKeepScreenOn(true);
        idle.setOnClickListener(view -> dismissIdle());
        screen.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        root.addView(idle, new FrameLayout.LayoutParams(-1, -1));
        Atmosphere idleBackground = new Atmosphere(this, theme, style, false);
        idleBackground.setDrifting(true);
        idle.addView(idleBackground, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(0x80000000);
        idle.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        idle.requestFocus();
        idleReadout = column();
        idleReadout.setGravity(Gravity.CENTER);
        idleReadout.setPadding(dp(28), dp(14), dp(28), dp(14));
        if (style.clock != 2) idleReadout.setBackground(glass(25, 0, false, false));
        if (style.clock == 1) {
            idleReadout.addView(new AnalogClock(this, accent()),
                    new LinearLayout.LayoutParams(dp(200), dp(200)));
        } else {
            TextClock time = new TextClock(this);
            time.setFormat12Hour("h:mm a");
            time.setFormat24Hour("HH:mm");
            time.setTypeface(LIGHT);
            time.setTextColor(0xFFE4E9F6);
            time.setTextSize(style.clock == 2 ? 70 : 60);
            time.setGravity(Gravity.CENTER);
            idleReadout.addView(time, new LinearLayout.LayoutParams(-1, -2));
        }
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(17);
        date.setTextColor(0xFFAAB6CF);
        date.setGravity(Gravity.CENTER);
        idleReadout.addView(date);
        idle.addView(idleReadout, new FrameLayout.LayoutParams(dp(style.clock == 1 ? 260 : 390),
                -2, Gravity.CENTER));
        idlePosition = 0;
        idle.post(this::shiftIdleClock);
    }

    private void shiftIdleClock() {
        if (idleReadout == null) return;
        float[][] positions = {{0, 0}, {-.65f, -.55f}, {.65f, .55f}, {.65f, -.55f}, {-.65f, .55f}};
        float horizontal = Math.max(0, (idle.getWidth() - idleReadout.getWidth()) / 2f - dp(25));
        float vertical = Math.max(0, (idle.getHeight() - idleReadout.getHeight()) / 2f - dp(20));
        idleReadout.animate().translationX(positions[idlePosition][0] * horizontal)
                .translationY(positions[idlePosition][1] * vertical)
                .setDuration(idlePosition == 0 ? 0 : style.duration(6000)).start();
        idlePosition = (idlePosition + 1) % positions.length;
        handler.postDelayed(shiftClock, 45_000);
    }

    private void dismissIdle() {
        if (idle == null) return;
        handler.removeCallbacks(shiftClock);
        if (idleReadout != null) idleReadout.animate().cancel();
        root.removeView(idle);
        idle = null;
        idleReadout = null;
        screen.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (idlePreviousFocus != null) idlePreviousFocus.requestFocus();
        idlePreviousFocus = null;
        resetIdleTimer();
    }

    private TextView button(String name, int size, boolean selected, String key, Runnable action) {
        TextView view = label(name, size, selected ? accent() : TEXT, MEDIUM);
        if (selected) view.setSelected(true);
        focusable(view, key, glass(15, Color.TRANSPARENT, selected, true), action);
        return view;
    }

    private void focusable(View view, String key, GlassSurface surface, Runnable action) {
        view.setId(View.generateViewId());
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnClickListener(v -> {
            v.requestFocusFromTouch();
            if (key != null && !key.startsWith("tab:")) focusByPage.put(page, key);
            action.run();
        });
        view.setBackground(surface);
        if (Build.VERSION.SDK_INT >= 26) view.setDefaultFocusHighlightEnabled(false);
        if (key != null) focusTargets.put(key, view);
        view.setOnFocusChangeListener((v, focused) -> {
            if (focused && key != null && !key.startsWith("tab:")) focusByPage.put(page, key);
            if (v instanceof AppTile) {
                if (focused) focusAppTile((AppTile) v);
                else ((AppTile) v).showDepth(false, 0, false, false);
            } else {
                float scale = focused ? 1.015f : 1f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(style.duration(160)).start();
                if (focused && activePanel == null && focusHalo != null) {
                    focusHalo.clear();
                    for (AppTile tile : appTiles) tile.showDepth(false, 0, false, false);
                }
            }
        });
    }

    private void focusAppTile(AppTile selected) {
        int index = appTiles.indexOf(selected);
        int direction = lastAppPosition < 0 || index >= lastAppPosition ? -1 : 1;
        for (int i = 0; i < appTiles.size(); i++) {
            appTiles.get(i).showDepth(i == index, direction, Math.abs(i - index) == 1,
                    arrangingPackage != null);
        }
        lastAppPosition = index;
        selected.post(() -> {
            if (!selected.hasFocus() || !selected.isAttachedToWindow() || activePanel != null) return;
            float x = (selected.getLeft() + selected.getWidth() / 2f) / Math.max(1, content.getWidth()) * 2 - 1;
            wallpaper.focusLight(selected.ambient, x, index / (float) Math.max(1, appTiles.size() - 1) - .5f);
            focusHalo.follow(selected, root, selected.ambient, style);
        });
    }

    private GlassSurface glass(int radius, int ambient, boolean selected, boolean quiet) {
        return new GlassSurface(theme, style, dp(radius), dp(1), ambient, selected, quiet);
    }

    private final class Panel {
        final Dialog dialog = new Dialog(MainActivity.this);
        final LinearLayout body = column();
        final TextView closeButton;
        View preferred;
        private final View previousFocus = getCurrentFocus();
        private Runnable afterDismiss;
        private View lastAction;

        Panel(String title, String subtitle) {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout shell = column();
            shell.setPadding(dp(18), dp(21), dp(18), dp(16));
            android.graphics.drawable.GradientDrawable backing = new android.graphics.drawable.GradientDrawable();
            backing.setColor(GlassSurface.tint(style.sceneBase(theme), 220));
            backing.setCornerRadius(dp(24));
            shell.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{
                    backing, glass(24, theme.aurora, false, false)}));
            shell.setElevation(dp(24));
            shell.addView(label(title, 23, TEXT, MEDIUM));
            spaceAbove(shell, label(subtitle, 12, MUTED, null), 6);
            ScrollView scroll = new ScrollView(MainActivity.this);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.setClipChildren(false);
            body.setPadding(dp(3), dp(13), dp(3), dp(8));
            body.setClipChildren(false);
            scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
            shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            closeButton = button("Close", 15, false, null, dialog::dismiss);
            closeButton.setGravity(Gravity.CENTER);
            shell.addView(closeButton, new LinearLayout.LayoutParams(-1, dp(38)));
            dialog.setContentView(shell);
            dialog.setOnDismissListener(ignored -> {
                if (activePanel == this) activePanel = null;
                Runnable next = afterDismiss;
                afterDismiss = null;
                if (active) {
                    if (next != null) handler.post(next);
                    else if (previousFocus != null && previousFocus.isAttachedToWindow()) previousFocus.requestFocus();
                    resetIdleTimer();
                }
            });
        }

        View action(String title, String detail, Runnable action) {
            View row = settingTile(title, detail, null, () -> {
                afterDismiss = action;
                dialog.dismiss();
            });
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(detail.isEmpty() ? 44 : 59));
            size.bottomMargin = dp(8);
            body.addView(row, size);
            row.setNextFocusLeftId(row.getId());
            row.setNextFocusRightId(row.getId());
            if (lastAction != null) {
                lastAction.setNextFocusDownId(row.getId());
                row.setNextFocusUpId(lastAction.getId());
            }
            if (preferred == null) preferred = row;
            lastAction = row;
            return row;
        }

        void text(String value, int size, int color) {
            TextView text = label(value, size, color, null);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
            layout.topMargin = dp(7);
            layout.bottomMargin = dp(5);
            body.addView(text, layout);
        }

        void show() {
            if (activePanel != null) activePanel.dialog.dismiss();
            activePanel = this;
            handler.removeCallbacks(showIdle);
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(.34f);
                window.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                int available = getResources().getDisplayMetrics().heightPixels - dp(45);
                window.setLayout(Math.min(dp(355), getResources().getDisplayMetrics().widthPixels - dp(40)), available);
                WindowManager.LayoutParams placement = window.getAttributes();
                placement.x = dp(22);
                window.setAttributes(placement);
                window.setWindowAnimations(style.motion == 3 ? 0 : R.style.SheetAnimation);
                hideSystemBars(window);
            }
            if (preferred != null) preferred.requestFocus();
        }
    }

    private TextView label(String text, int size, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (typeface != null) view.setTypeface(typeface);
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private void spaceAbove(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(child, params);
    }

    private int setting(String key, int initial, int options) {
        int saved = prefs.getInt(key, initial);
        return saved < 0 || saved >= options ? initial : saved;
    }

    private int accent() { return theme.accent; }
    private boolean showWeather() { return setting("weather", 1, 2) == 1; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
