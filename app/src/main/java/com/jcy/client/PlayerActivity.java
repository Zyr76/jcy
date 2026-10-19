package com.jcy.client;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.jcy.client.api.PlaybackRepository;
import com.jcy.client.model.PlayInfo;

public final class PlayerActivity extends Activity {
    private static final String TAG = "JcyPlayer";

    private VideoView videoView;
    private ProgressBar progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String videoId = getIntent().getStringExtra("videoId");
        if (videoId == null || videoId.isEmpty()) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(this);
        videoView.setMediaController(new MediaController(this));
        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER;
        root.addView(progress, progressParams);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0x99000000);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.gravity = Gravity.BOTTOM;
        root.addView(status, statusParams);

        setContentView(root);
        status.setText("正在准备播放...");
        resolve(videoId);
    }

    private void resolve(String videoId) {
        new Thread(() -> {
            try {
                Session session = Session.init(getApplicationContext());
                PlayInfo info = PlaybackRepository.resolve(
                        getApplicationContext(), session.api, videoId);
                runOnUiThread(() -> play(info));
            } catch (Exception e) {
                Log.e(TAG, "resolve failed", e);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText("解析失败: " + errorText(e));
                });
            }
        }, "jcy-play").start();
    }

    private void play(PlayInfo info) {
        status.setText((info.localPlaylist ? "本地 HLS · " : "") + info.title);
        videoView.setOnPreparedListener(mp -> {
            progress.setVisibility(View.GONE);
            status.setText(info.title);
            videoView.start();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            progress.setVisibility(View.GONE);
            status.setText("播放错误 what=" + what + " extra=" + extra);
            return true;
        });
        videoView.setOnCompletionListener(mp -> status.setText("播放完成"));
        videoView.setVideoPath(info.playUrl);
        videoView.requestFocus();
    }

    private static String errorText(Throwable error) {
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (out.length() > 0) {
                out.append(" <- ");
            }
            out.append(current.getClass().getSimpleName()).append(": ")
                    .append(current.getMessage());
            current = current.getCause();
        }
        return out.toString();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
        super.onDestroy();
    }
}
