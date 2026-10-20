package com.jcy.client.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jcy.client.model.VideoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HeroCarousel extends FrameLayout {
    private static final long AUTO_MS = 5000L;

    private final List<VideoItem> items = new ArrayList<>();
    private final ImageView cover;
    private final View scrim;
    private final LinearLayout info;
    private final LinearLayout badges;
    private final TextView title;
    private final TextView subtitle;
    private final LinearLayout dots;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageLoader imageLoader;
    private OnPickListener listener;
    private int index;
    private boolean paused;
    private VideoItem current;

    public interface OnPickListener {
        void onPick(VideoItem item);
    }

    public HeroCarousel(Context context) {
        super(context);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        Theme.dp(getContext(), 20));
            }
        });

        cover = new ImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Theme.BG_CARD);
        addView(cover, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrim = new View(context);
        scrim.setBackground(Theme.verticalScrim(0x0A000000, 0xF008090D));
        addView(scrim, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        LayoutParams infoParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.gravity = Gravity.BOTTOM;
        infoParams.setMargins(Theme.dp(context, 18), 0,
                Theme.dp(context, 18), Theme.dp(context, 18));
        addView(info, infoParams);

        badges = new LinearLayout(context);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        info.addView(badges);

        title = new TextView(context);
        title.setTextColor(Theme.TEXT);
        title.setTypeface(Theme.display());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Theme.dp(context, 8);
        info.addView(title, titleParams);

        subtitle = new TextView(context);
        subtitle.setTextColor(0xCCF4F6FB);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Theme.dp(context, 4);
        info.addView(subtitle, subParams);

        TextView play = new TextView(context);
        play.setText("立即播放");
        play.setTextColor(Color.WHITE);
        play.setTypeface(Theme.medium());
        play.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        play.setGravity(Gravity.CENTER);
        play.setPadding(Theme.dp(context, 16), Theme.dp(context, 7),
                Theme.dp(context, 16), Theme.dp(context, 7));
        play.setBackground(Theme.gradient(20, context, Theme.ACCENT, 0xFFB45CFF));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playParams.topMargin = Theme.dp(context, 12);
        info.addView(play, playParams);

        dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams dotParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotParams.gravity = Gravity.BOTTOM | Gravity.END;
        dotParams.setMargins(0, 0, Theme.dp(context, 18), Theme.dp(context, 18));
        addView(dots, dotParams);

        setOnClickListener(v -> {
            if (listener != null && current != null) {
                listener.onPick(current);
            }
        });
    }

    public void bind(List<VideoItem> source, ImageLoader loader, OnPickListener pickListener) {
        imageLoader = loader;
        listener = pickListener;
        items.clear();
        items.addAll(source.subList(0, Math.min(6, source.size())));
        index = 0;
        rebuildDots();
        if (!items.isEmpty()) {
            show(items.get(0), false);
        }
        schedule();
    }

    public void pause() {
        paused = true;
        handler.removeCallbacks(tick);
    }

    public void resume() {
        paused = false;
        schedule();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!paused && items.size() > 1) {
                index = (index + 1) % items.size();
                show(items.get(index), true);
            }
            schedule();
        }
    };

    private void schedule() {
        handler.removeCallbacks(tick);
        if (!paused && items.size() > 1) {
            handler.postDelayed(tick, AUTO_MS);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void show(VideoItem item, boolean animated) {
        current = item;
        updateDots();
        badges.removeAllViews();
        if (item.score > 0) {
            badges.addView(chip(String.format(Locale.US, "%.1f 分", item.score),
                    Theme.GOLD, 0xFF2A2008));
        }
        String episode = item.episodeText();
        if (!episode.isEmpty()) {
            badges.addView(chip(episode, 0x33FFFFFF, Theme.TEXT));
        }
        title.setText(item.title);
        subtitle.setText(item.subTitle);
        subtitle.setVisibility(item.subTitle.isEmpty() ? GONE : VISIBLE);

        if (!animated) {
            imageLoader.load(item.coverUrl, cover, null);
            return;
        }
        info.animate().alpha(0f).setDuration(120)
                .withEndAction(() -> {
                    imageLoader.load(item.coverUrl, cover, () -> {
                        info.animate().alpha(1f).setDuration(220).start();
                    });
                }).start();
    }

    private void rebuildDots() {
        dots.removeAllViews();
        int size = Theme.dp(getContext(), 5);
        for (int i = 0; i < items.size(); i++) {
            View dot = new View(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(Theme.dp(getContext(), 5), 0, 0, 0);
            dot.setLayoutParams(params);
            dots.addView(dot);
        }
        updateDots();
    }

    private void updateDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            boolean active = i == index;
            dot.setBackground(Theme.rounded(active ? Theme.ACCENT_2 : 0x66FFFFFF,
                    3, getContext()));
            dot.setAlpha(active ? 1f : 0.5f);
        }
    }

    private View chip(String text, int background, int textColor) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setTypeface(Theme.medium());
        view.setBackground(Theme.rounded(background, 9, getContext()));
        view.setPadding(Theme.dp(getContext(), 8), Theme.dp(getContext(), 2),
                Theme.dp(getContext(), 8), Theme.dp(getContext(), 2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, Theme.dp(getContext(), 6), 0);
        view.setLayoutParams(params);
        return view;
    }
}
