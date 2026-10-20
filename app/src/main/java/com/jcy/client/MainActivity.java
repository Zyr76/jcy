package com.jcy.client;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.jcy.client.api.HomeRepository;
import com.jcy.client.model.VideoItem;
import com.jcy.client.ui.HomeAdapter;
import com.jcy.client.ui.ImageLoader;
import com.jcy.client.ui.RefreshLayout;
import com.jcy.client.ui.SkeletonView;
import com.jcy.client.ui.Theme;

import java.util.List;

public final class MainActivity extends Activity {
    private ImageLoader imageLoader;
    private HomeAdapter adapter;
    private RefreshLayout refreshLayout;
    private ListView listView;
    private View header;
    private View skeleton;
    private View errorBox;
    private TextView errorText;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imageLoader = new ImageLoader();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(android.R.color.transparent);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setBackgroundColor(Theme.BG);
        int headerSpace = Theme.statusBarHeight(this) + Theme.dp(this, 58);
        listView.setPadding(0, headerSpace, 0, Theme.dp(this, 24) + navBar());
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                float offset = 0f;
                if (view.getChildCount() > 0) {
                    offset = -view.getChildAt(0).getTop() + view.getPaddingTop();
                }
                float progress = Math.min(1f, Math.max(0f, offset / Theme.dp(MainActivity.this, 90)));
                header.setBackgroundColor(withAlpha(Theme.BG, progress));
                header.setTranslationZ(progress > 0.02f ? Theme.dp(MainActivity.this, 6) : 0);
            }
        });

        adapter = new HomeAdapter(this, imageLoader, this::openPlayer);
        listView.setAdapter(adapter);

        refreshLayout = new RefreshLayout(this, listView);
        refreshLayout.setOnRefresh(this::load);
        root.addView(refreshLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        header = buildHeader();
        root.addView(header, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        skeleton = buildSkeleton();
        root.addView(skeleton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        errorBox = buildError();
        errorBox.setVisibility(View.GONE);
        root.addView(errorBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        load();
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Theme.dp(this, 18), Theme.statusBarHeight(this),
                Theme.dp(this, 14), 0);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        bar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView title = new TextView(this);
        title.setText("囧次元");
        title.setTextColor(Theme.TEXT);
        title.setTypeface(Theme.display());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titles.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("发现 · 追剧 · 收藏");
        subtitle.setTextColor(Theme.TEXT_MUTED);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Theme.dp(this, 1);
        titles.addView(subtitle, subParams);

        TextView action = new TextView(this);
        action.setText("刷新");
        action.setTextColor(Theme.TEXT);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        action.setTypeface(Theme.medium());
        action.setGravity(Gravity.CENTER);
        action.setPadding(Theme.dp(this, 14), Theme.dp(this, 7),
                Theme.dp(this, 14), Theme.dp(this, 7));
        action.setBackground(Theme.pressable(0x1FFFFFFF, 18, this));
        action.setOnClickListener(v -> {
            if (!loaded) {
                return;
            }
            refreshLayout.setEnabled(true);
            load();
        });
        bar.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.setPadding(0, 0, 0, Theme.dp(this, 8));
        return wrapper;
    }

    private View buildSkeleton() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(this, 16), Theme.statusBarHeight(this) + Theme.dp(this, 74),
                Theme.dp(this, 16), 0);

        SkeletonView hero = new SkeletonView(this);
        box.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, HomeAdapter.heroHeight(this)));

        View section = new SkeletonView(this);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                Theme.dp(this, 96), Theme.dp(this, 18));
        sectionParams.topMargin = Theme.dp(this, 24);
        box.addView(section, sectionParams);

        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lineParams.topMargin = Theme.dp(this, 16);
            box.addView(line, lineParams);
            for (int col = 0; col < 2; col++) {
                if (col == 1) {
                    View gap = new View(this);
                    line.addView(gap, new LinearLayout.LayoutParams(Theme.dp(this, 12), 1));
                }
                SkeletonView cell = new SkeletonView(this);
                line.addView(cell, new LinearLayout.LayoutParams(0,
                        Theme.dp(this, 200), 1));
            }
        }
        return box;
    }

    private View buildError() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Theme.BG);
        box.setPadding(Theme.dp(this, 32), 0, Theme.dp(this, 32), 0);

        TextView glyph = new TextView(this);
        glyph.setText("! ");
        glyph.setTextColor(Theme.DANGER);
        glyph.setTypeface(Theme.display());
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        box.addView(glyph);

        TextView title = new TextView(this);
        title.setText("内容加载失败");
        title.setTextColor(Theme.TEXT);
        title.setTypeface(Theme.medium());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Theme.dp(this, 10);
        box.addView(title, titleParams);

        errorText = new TextView(this);
        errorText.setTextColor(Theme.TEXT_MUTED);
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        errorText.setGravity(Gravity.CENTER);
        errorText.setMaxLines(4);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = Theme.dp(this, 8);
        box.addView(errorText, textParams);

        TextView retry = new TextView(this);
        retry.setText("重新加载");
        retry.setTextColor(Color.WHITE);
        retry.setTypeface(Theme.medium());
        retry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(Theme.dp(this, 26), Theme.dp(this, 10),
                Theme.dp(this, 26), Theme.dp(this, 10));
        retry.setBackground(Theme.gradient(24, this, Theme.ACCENT, 0xFFB45CFF));
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = Theme.dp(this, 20);
        box.addView(retry, retryParams);
        retry.setOnClickListener(v -> load());
        return box;
    }

    private void load() {
        refreshLayout.setEnabled(false);
        if (!loaded) {
            skeleton.setVisibility(View.VISIBLE);
            errorBox.setVisibility(View.GONE);
            Theme.fadeIn(skeleton);
        }
        new Thread(() -> {
            try {
                Session session = Session.init(getApplicationContext());
                List<VideoItem> loaded = HomeRepository.load(session.api);
                runOnUiThread(() -> {
                    adapter.setItems(loaded);
                    this.loaded = true;
                    skeleton.setVisibility(View.GONE);
                    errorBox.setVisibility(View.GONE);
                    refreshLayout.setEnabled(true);
                    refreshLayout.finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    skeleton.setVisibility(View.GONE);
                    refreshLayout.finish();
                    refreshLayout.setEnabled(true);
                    errorText.setText(errorText(error));
                    errorBox.setVisibility(View.VISIBLE);
                    Theme.fadeIn(errorBox);
                });
            }
        }, "jcy-home").start();
    }

    private void openPlayer(VideoItem item) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("videoId", item.id);
        intent.putExtra("title", item.title);
        intent.putExtra("cover", item.coverUrl);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(alpha * 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int navBar() {
        return Theme.navigationBarHeight(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && adapter.hero() != null) {
            adapter.hero().resume();
        }
    }

    @Override
    protected void onPause() {
        if (adapter != null && adapter.hero() != null) {
            adapter.hero().pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (imageLoader != null) {
            imageLoader.shutdown();
        }
        super.onDestroy();
    }
}
