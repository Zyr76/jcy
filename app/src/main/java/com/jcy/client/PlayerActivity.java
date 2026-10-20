package com.jcy.client;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.jcy.client.api.PlaybackRepository;
import com.jcy.client.model.PlayInfo;
import com.jcy.client.ui.Theme;

import java.util.Locale;

public final class PlayerActivity extends Activity {
    private static final long HIDE_DELAY = 3500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private VideoView videoView;
    private FrameLayout root;
    private View controls;
    private LinearLayout loadingBox;
    private TextView loadingText;
    private LinearLayout errorBox;
    private TextView errorText;
    private ProgressBar buffering;
    private SeekBar seekBar;
    private TextView position;
    private TextView duration;
    private TextView playToggle;
    private TextView titleView;
    private TextView badge;
    private boolean controlsVisible = true;
    private boolean prepared;
    private String videoId;
    private String videoTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        videoId = getIntent().getStringExtra("videoId");
        videoTitle = getIntent().getStringExtra("title");
        if (videoId == null || videoId.isEmpty()) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Theme.configureWindow(getWindow());
        getWindow().setNavigationBarColor(Color.BLACK);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);

        videoView = new VideoView(this);
        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buffering = new ProgressBar(this);
        FrameLayout.LayoutParams bufferParams = new FrameLayout.LayoutParams(
                Theme.dp(this, 34), Theme.dp(this, 34));
        bufferParams.gravity = Gravity.CENTER;
        root.addView(buffering, bufferParams);
        buffering.setVisibility(View.GONE);

        loadingBox = buildLoading();
        root.addView(loadingBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        errorBox = buildError();
        errorBox.setVisibility(View.GONE);
        root.addView(errorBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        controls = buildControls();
        controls.setVisibility(View.VISIBLE);
        root.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.setOnClickListener(v -> toggleControls());
        setContentView(root);
        resolve();
    }

    private LinearLayout buildLoading() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.BLACK);

        ProgressBar spinner = new ProgressBar(this);
        box.addView(spinner, new LinearLayout.LayoutParams(
                Theme.dp(this, 30), Theme.dp(this, 30)));

        loadingText = new TextView(this);
        loadingText.setText("正在解析播放地址…");
        loadingText.setTextColor(Theme.TEXT_MUTED);
        loadingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = Theme.dp(this, 14);
        box.addView(loadingText, textParams);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(Theme.TEXT);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cancel.setPadding(Theme.dp(this, 20), Theme.dp(this, 8),
                Theme.dp(this, 20), Theme.dp(this, 8));
        cancel.setBackground(Theme.pressable(0x1FFFFFFF, 18, this));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.topMargin = Theme.dp(this, 18);
        box.addView(cancel, cancelParams);
        cancel.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        return box;
    }

    private LinearLayout buildError() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.BLACK);
        box.setPadding(Theme.dp(this, 28), 0, Theme.dp(this, 28), 0);

        TextView title = new TextView(this);
        title.setText("无法播放");
        title.setTextColor(Theme.TEXT);
        title.setTypeface(Theme.medium());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        box.addView(title);

        errorText = new TextView(this);
        errorText.setTextColor(Theme.TEXT_MUTED);
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        errorText.setGravity(Gravity.CENTER);
        errorText.setMaxLines(5);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = Theme.dp(this, 10);
        box.addView(errorText, textParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Theme.dp(this, 20);
        box.addView(actions, actionParams);

        TextView retry = pill("重试", Theme.ACCENT, 0xFFB45CFF);
        retry.setOnClickListener(v -> {
            errorBox.setVisibility(View.GONE);
            loadingBox.setVisibility(View.VISIBLE);
            resolve();
        });
        actions.addView(retry);

        TextView back = pill("返回", 0x33FFFFFF, 0x33FFFFFF);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backParams.setMargins(Theme.dp(this, 12), 0, 0, 0);
        back.setOnClickListener(v -> finish());
        actions.addView(back, backParams);
        return box;
    }

    private TextView pill(String text, int from, int to) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(Theme.medium());
        view.setGravity(Gravity.CENTER);
        view.setPadding(Theme.dp(this, 24), Theme.dp(this, 10),
                Theme.dp(this, 24), Theme.dp(this, 10));
        if (from == to) {
            view.setBackground(Theme.pressable(from, 22, this));
        } else {
            view.setBackground(Theme.gradient(22, this, from, to));
        }
        return view;
    }

    private View buildControls() {
        FrameLayout layer = new FrameLayout(this);

        View topScrim = new View(this);
        GradientDrawable top = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC000000, 0x00000000});
        topScrim.setBackground(top);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 120));
        topParams.gravity = Gravity.TOP;
        layer.addView(topScrim, topParams);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Theme.dp(this, 10), Theme.statusBarHeight(this) + Theme.dp(this, 6),
                Theme.dp(this, 16), 0);
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBarParams.gravity = Gravity.TOP;
        layer.addView(topBar, topBarParams);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Theme.TEXT);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, 0, 0, Theme.dp(this, 4));
        topBar.addView(back, new LinearLayout.LayoutParams(
                Theme.dp(this, 40), Theme.dp(this, 40)));
        back.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(Theme.dp(this, 4), 0, Theme.dp(this, 8), 0);
        topBar.addView(titles, titleParams);

        titleView = new TextView(this);
        titleView.setText(videoTitle == null ? "" : videoTitle);
        titleView.setTextColor(Theme.TEXT);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleView.setTypeface(Theme.medium());
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(titleView);

        badge = new TextView(this);
        badge.setTextColor(Theme.TEXT_MUTED);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setVisibility(View.GONE);
        titles.addView(badge);

        View bottomScrim = new View(this);
        GradientDrawable bottom = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x00000000});
        bottomScrim.setBackground(bottom);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 160));
        bottomParams.gravity = Gravity.BOTTOM;
        layer.addView(bottomScrim, bottomParams);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16),
                Theme.dp(this, 12) + Theme.navigationBarHeight(this));
        FrameLayout.LayoutParams bottomBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomBarParams.gravity = Gravity.BOTTOM;
        layer.addView(bottomBar, bottomBarParams);

        seekBar = new SeekBar(this);
        seekBar.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.ACCENT_2));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        seekBar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF));
        bottomBar.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && prepared) {
                    videoView.seekTo(progress);
                    position.setText(format(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                handler.removeCallbacks(hideControls);
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                scheduleHide();
            }
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playToggle = new TextView(this);
        playToggle.setText("暂停");
        playToggle.setTextColor(Color.WHITE);
        playToggle.setTypeface(Theme.medium());
        playToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        playToggle.setGravity(Gravity.CENTER);
        playToggle.setPadding(Theme.dp(this, 14), Theme.dp(this, 7),
                Theme.dp(this, 14), Theme.dp(this, 7));
        playToggle.setBackground(Theme.pressable(0x26FFFFFF, 18, this));
        actions.addView(playToggle);
        playToggle.setOnClickListener(v -> togglePlayback());

        position = new TextView(this);
        position.setText("00:00");
        position.setTextColor(Theme.TEXT);
        position.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        position.setPadding(Theme.dp(this, 12), 0, 0, 0);
        actions.addView(position);

        TextView divider = new TextView(this);
        divider.setText(" / ");
        divider.setTextColor(Theme.TEXT_MUTED);
        divider.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        actions.addView(divider);

        duration = new TextView(this);
        duration.setText("00:00");
        duration.setTextColor(Theme.TEXT_MUTED);
        duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        actions.addView(duration);

        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        TextView rotate = new TextView(this);
        rotate.setText("全屏");
        rotate.setTextColor(Theme.TEXT);
        rotate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        rotate.setTypeface(Theme.medium());
        rotate.setGravity(Gravity.CENTER);
        rotate.setPadding(Theme.dp(this, 14), Theme.dp(this, 7),
                Theme.dp(this, 14), Theme.dp(this, 7));
        rotate.setBackground(Theme.pressable(0x26FFFFFF, 18, this));
        rotate.setOnClickListener(v -> toggleOrientation());
        actions.addView(rotate);

        layer.setOnTouchListener((v, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                scheduleHide();
            }
            return false;
        });
        return layer;
    }

    private void resolve() {
        prepared = false;
        handler.removeCallbacks(hideControls);
        new Thread(() -> {
            try {
                Session session = Session.init(getApplicationContext());
                PlayInfo info = PlaybackRepository.resolve(
                        getApplicationContext(), session.api, videoId);
                runOnUiThread(() -> play(info));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    loadingBox.setVisibility(View.GONE);
                    buffering.setVisibility(View.GONE);
                    errorText.setText(errorText(error));
                    errorBox.setVisibility(View.VISIBLE);
                    Theme.fadeIn(errorBox);
                });
            }
        }, "jcy-play").start();
    }

    private void play(PlayInfo info) {
        badge.setText(info.localPlaylist ? "本地 HLS 播放列表" : "在线播放");
        badge.setVisibility(View.VISIBLE);
        if (info.title != null && !info.title.isEmpty()) {
            titleView.setText(info.title);
        }
        videoView.setOnPreparedListener(mp -> {
            prepared = true;
            loadingBox.setVisibility(View.GONE);
            buffering.setVisibility(View.GONE);
            seekBar.setMax(mp.getDuration());
            duration.setText(format(mp.getDuration()));
            videoView.start();
            scheduleHide();
        });
        videoView.setOnInfoListener((mp, what, extra) -> {
            if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                buffering.setVisibility(View.VISIBLE);
            } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                buffering.setVisibility(View.GONE);
            }
            return false;
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            loadingBox.setVisibility(View.GONE);
            buffering.setVisibility(View.GONE);
            errorText.setText("播放器错误 what=" + what + " extra=" + extra);
            errorBox.setVisibility(View.VISIBLE);
            Theme.fadeIn(errorBox);
            return true;
        });
        videoView.setOnCompletionListener(mp -> {
            playToggle.setText("重播");
            showControls();
        });
        videoView.setVideoPath(info.playUrl);
        videoView.requestFocus();
        startProgressLoop();
    }

    private final Runnable progressLoop = new Runnable() {
        @Override
        public void run() {
            if (prepared) {
                int current = videoView.getCurrentPosition();
                seekBar.setProgress(current);
                position.setText(format(current));
                boolean playing = videoView.isPlaying();
                playToggle.setText(playing ? "暂停" : "播放");
            }
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable hideControls = () -> {
        if (prepared) {
            hideControls();
        }
    };

    private void startProgressLoop() {
        handler.removeCallbacks(progressLoop);
        handler.post(progressLoop);
    }

    private void togglePlayback() {
        if (!prepared) {
            return;
        }
        if (videoView.isPlaying()) {
            videoView.pause();
            playToggle.setText("播放");
            showControls();
        } else {
            if (videoView.getCurrentPosition() >= videoView.getDuration() - 800) {
                videoView.seekTo(0);
            }
            videoView.start();
            playToggle.setText("暂停");
            scheduleHide();
        }
    }

    private void toggleOrientation() {
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        setRequestedOrientation(landscape
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        controlsVisible = true;
        controls.animate().alpha(1f).setDuration(180).start();
        controls.setVisibility(View.VISIBLE);
        scheduleHide();
    }

    private void hideControls() {
        controlsVisible = false;
        controls.animate().alpha(0f).setDuration(220)
                .withEndAction(() -> {
                    if (!controlsVisible) {
                        controls.setVisibility(View.INVISIBLE);
                    }
                }).start();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideControls);
        if (prepared) {
            handler.postDelayed(hideControls, HIDE_DELAY);
        }
    }

    private static String format(int millis) {
        int total = Math.max(0, millis / 1000);
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    private static String errorText(Throwable error) {
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (out.length() > 0) {
                out.append("\n");
            }
            out.append(current.getClass().getSimpleName()).append(": ")
                    .append(current.getMessage());
            current = current.getCause();
        }
        return out.toString();
    }

    @Override
    protected void onPause() {
        if (videoView != null && prepared && videoView.isPlaying()) {
            videoView.pause();
            playToggle.setText("播放");
            showControls();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) {
            videoView.stopPlayback();
        }
        super.onDestroy();
    }
}
