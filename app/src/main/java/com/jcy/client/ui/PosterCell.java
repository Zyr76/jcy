package com.jcy.client.ui;

import android.content.Context;
import android.graphics.Outline;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jcy.client.model.VideoItem;

import java.util.Locale;

public final class PosterCell extends LinearLayout {
    private final PosterFrame poster;
    private final ImageView cover;
    private final TextView badge;
    private final TextView episodes;
    private final TextView title;
    private final TextView meta;
    private final View progressLine;
    private final FrameLayout.LayoutParams progressParams;

    public PosterCell(Context context) {
        super(context);
        setOrientation(VERTICAL);

        poster = new PosterFrame(context, 1.45f);
        poster.setClipToOutline(true);
        poster.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        Theme.dp(getContext(), 16));
            }
        });
        poster.setBackground(Theme.pressable(Theme.BG_CARD, 16, context));
        addView(poster, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        cover = new ImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.addView(cover, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        View scrim = new View(context);
        scrim.setBackground(Theme.verticalScrim(0x00000000, 0xCC08090D));
        poster.addView(scrim, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        badge = new TextView(context);
        badge.setTextColor(0xFF2A2008);
        badge.setTypeface(Theme.medium());
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setBackground(Theme.rounded(Theme.GOLD, 8, context));
        badge.setPadding(Theme.dp(context, 6), Theme.dp(context, 1),
                Theme.dp(context, 6), Theme.dp(context, 1));
        badge.setVisibility(GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.TOP | Gravity.END;
        badgeParams.setMargins(0, Theme.dp(context, 8), Theme.dp(context, 8), 0);
        poster.addView(badge, badgeParams);

        episodes = new TextView(context);
        episodes.setTextColor(Theme.TEXT);
        episodes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        episodes.setTypeface(Theme.medium());
        episodes.setSingleLine(true);
        episodes.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams episodeParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        episodeParams.gravity = Gravity.BOTTOM;
        episodeParams.setMargins(Theme.dp(context, 10), 0,
                Theme.dp(context, 10), Theme.dp(context, 9));
        poster.addView(episodes, episodeParams);

        title = new TextView(context);
        title.setTextColor(Theme.TEXT);
        title.setTypeface(Theme.medium());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Theme.dp(context, 8);
        addView(title, titleParams);

        meta = new TextView(context);
        meta.setTextColor(Theme.TEXT_MUTED);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Theme.dp(context, 2);
        addView(meta, metaParams);

        progressLine = new View(context);
        progressLine.setBackground(Theme.rounded(Theme.ACCENT_2, 2, context));
        progressLine.setVisibility(GONE);
        progressParams = new FrameLayout.LayoutParams(0, Theme.dp(context, 3));
        progressParams.gravity = Gravity.BOTTOM;
        poster.addView(progressLine, progressParams);

        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                animate().scaleX(0.96f).scaleY(0.96f).setDuration(110).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            }
            return false;
        });
    }

    public void bind(VideoItem item, ImageLoader loader) {
        title.setText(item.title);
        boolean hasSecondLine = !item.subTitle.isEmpty() || item.score > 0;
        if (!item.subTitle.isEmpty()) {
            meta.setText(item.subTitle);
        } else if (item.score > 0) {
            meta.setText(String.format(Locale.US, "%.1f 分", item.score));
        } else {
            meta.setText("");
        }
        meta.setVisibility(hasSecondLine ? VISIBLE : GONE);

        String episode = item.episodeText();
        episodes.setText(episode.isEmpty() ? " " : episode);
        if (item.score > 0) {
            badge.setText(String.format(Locale.US, "%.1f", item.score));
            badge.setVisibility(VISIBLE);
        } else {
            badge.setVisibility(GONE);
        }
        float ratio = item.totalEpisodes > 0
                ? Math.min(1f, (float) item.currentEpisodes / item.totalEpisodes)
                : -1f;
        if (ratio > 0 && ratio < 1) {
            progressLine.setVisibility(VISIBLE);
            final float share = ratio;
            poster.post(() -> {
                progressParams.width = (int) (poster.getWidth() * share);
                progressLine.setLayoutParams(progressParams);
            });
        } else {
            progressLine.setVisibility(GONE);
        }
        loader.load(item.coverUrl, cover, null);
    }
}
