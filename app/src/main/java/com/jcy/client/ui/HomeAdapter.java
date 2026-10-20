package com.jcy.client.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jcy.client.model.VideoItem;

import java.util.ArrayList;
import java.util.List;

public final class HomeAdapter extends BaseAdapter {
    public interface OnPick {
        void onPick(VideoItem item);
    }

    private final Context context;
    private final ImageLoader loader;
    private final List<VideoItem> items = new ArrayList<>();
    private final OnPick onPick;
    private int heroCount;
    private HeroCarousel hero;
    private LinearLayout header;
    private TextView sectionCount;

    public HomeAdapter(Context context, ImageLoader loader, OnPick onPick) {
        this.context = context;
        this.loader = loader;
        this.onPick = onPick;
    }

    public void setItems(List<VideoItem> source) {
        items.clear();
        items.addAll(source);
        heroCount = Math.min(items.size(), items.size() > 8 ? 6 : 2);
        notifyDataSetChanged();
    }

    public int gridCount() {
        return Math.max(0, items.size() - heroCount);
    }

    @Override
    public int getCount() {
        if (items.isEmpty()) {
            return 0;
        }
        int rows = (gridCount() + 1) / 2;
        return 1 + rows;
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 0 : 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            if (header == null) {
                header = buildHeader();
            }
            hero.bind(items.subList(0, heroCount), loader, onPick::onPick);
            sectionCount.setText(String.valueOf(gridCount()));
            return header;
        }
        Row row;
        if (convertView instanceof Row) {
            row = (Row) convertView;
        } else {
            row = new Row(context);
        }
        int index = (position - 1) * 2;
        row.bind(items.get(heroCount + index),
                index + 1 < gridCount() ? items.get(heroCount + index + 1) : null,
                loader, onPick);
        return row;
    }

    public HeroCarousel hero() {
        return hero;
    }

    private LinearLayout buildHeader() {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);

        hero = new HeroCarousel(context);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heroHeight(context));
        heroParams.setMargins(Theme.dp(context, 16), Theme.dp(context, 4),
                Theme.dp(context, 16), 0);
        block.addView(hero, heroParams);

        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(Theme.dp(context, 18), Theme.dp(context, 22),
                Theme.dp(context, 18), Theme.dp(context, 10));
        block.addView(section, sectionParams);

        View accent = new View(context);
        accent.setBackground(Theme.gradient(3, context, Theme.ACCENT_2, Theme.ACCENT));
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 4), Theme.dp(context, 16));
        accentParams.setMargins(0, 0, Theme.dp(context, 8), Theme.dp(context, 1));
        section.addView(accent, accentParams);

        TextView title = new TextView(context);
        title.setText("为你推荐");
        title.setTextColor(Theme.TEXT);
        title.setTypeface(Theme.display());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        section.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        sectionCount = new TextView(context);
        sectionCount.setTextColor(Theme.TEXT_MUTED);
        sectionCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        section.addView(sectionCount);
        return block;
    }

    public static int heroHeight(Context context) {
        int width = context.getResources().getDisplayMetrics().widthPixels
                - Theme.dp(context, 32);
        return Math.min(Theme.dp(context, 340), (int) (width * 0.72f));
    }

    private static final class Row extends LinearLayout {
        private final PosterCell left;
        private final PosterCell right;

        Row(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(Theme.dp(context, 16), 0, Theme.dp(context, 16), Theme.dp(context, 18));

            left = new PosterCell(context);
            LayoutParams leftParams = new LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            addView(left, leftParams);

            View gap = new View(context);
            addView(gap, new LayoutParams(Theme.dp(context, 12), 1));

            right = new PosterCell(context);
            addView(right, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }

        void bind(VideoItem first, VideoItem second, ImageLoader loader, OnPick onPick) {
            left.bind(first, loader);
            left.setOnClickListener(v -> onPick.onPick(first));
            left.setVisibility(VISIBLE);

            if (second != null) {
                right.bind(second, loader);
                right.setOnClickListener(v -> onPick.onPick(second));
                right.setVisibility(VISIBLE);
            } else {
                right.setVisibility(INVISIBLE);
            }
        }
    }
}
