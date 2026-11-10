package com.zerotv.next;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class InputActivity extends Activity {
    static final String INPUT_ID = "input_id";
    static final String INPUT_LABEL = "input_label";
    private String inputId;
    private String label;
    private TvView tv;
    private TextView status;
    private AudioManager audio;
    private final AudioManager.OnAudioFocusChangeListener audioFocus = change -> {
        if (tv != null) {
            tv.setStreamVolume(change == AudioManager.AUDIOFOCUS_GAIN ? 1f : 0f);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        inputId = getIntent().getStringExtra(INPUT_ID);
        label = getIntent().getStringExtra(INPUT_LABEL);
        TvInputManager inputs = getSystemService(TvInputManager.class);
        TvInputInfo input = inputs == null || inputId == null ? null : inputs.getTvInputInfo(inputId);
        if (input == null || input.getType() != TvInputInfo.TYPE_HDMI) {
            finish();
            return;
        }
        if (label == null) {
            label = input.loadLabel(this).toString();
        }
        audio = getSystemService(AudioManager.class);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        tv = new TvView(this);
        root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(0xFFDCE7E5);
        status.setTextSize(20);
        root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String id) { status.setVisibility(TextView.GONE); }
            @Override public void onVideoUnavailable(String id, int reason) {
                showStatus(reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
                        ? "Connecting…" : "No signal");
            }
            @Override public void onConnectionFailed(String id) { showStatus("Couldn’t open this input"); }
            @Override public void onDisconnected(String id) { showStatus("Input disconnected"); }
            @Override public void onContentBlocked(String id, TvContentRating rating) {
                showStatus("This input is restricted");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tv == null) return;
        MainActivity.hideSystemBars(getWindow());
        showStatus("Connecting…");
        int focus = audio == null ? AudioManager.AUDIOFOCUS_REQUEST_FAILED
                : audio.requestAudioFocus(audioFocus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        tv.setStreamVolume(focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? 1f : 0f);
        try {
            tv.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId));
        } catch (RuntimeException exception) {
            showStatus("Couldn’t open this input");
        }
    }

    @Override
    protected void onPause() {
        if (tv != null) tv.reset();
        if (audio != null) audio.abandonAudioFocus(audioFocus);
        super.onPause();
    }

    private void showStatus(String message) {
        status.setText(label + "\n\n" + message);
        status.setVisibility(TextView.VISIBLE);
    }
}
