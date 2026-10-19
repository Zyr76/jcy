package com.jcy.client;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.jcy.client.api.HomeRepository;
import com.jcy.client.model.VideoItem;
import com.jcy.client.ui.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String TAG = "JcyMain";

    private final List<VideoItem> items = new ArrayList<>();
    private TextView status;
    private Button refresh;
    private VideoAdapter adapter;
    private ImageLoader imageLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imageLoader = new ImageLoader();
        setContentView(buildUi());
        load();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), 0);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("囧次元极简客户端");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.START);
        root.addView(title);

        status = new TextView(this);
        status.setText("准备中...");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setTextColor(Color.rgb(0x66, 0x66, 0x66));
        status.setPadding(0, dp(6), 0, dp(6));
        root.addView(status);

        refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> load());
        root.addView(refresh);

        ListView listView = new ListView(this);
        listView.setDivider(null);
        adapter = new VideoAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            VideoItem item = items.get(position);
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            intent.putExtra("videoId", item.id);
            intent.putExtra("title", item.title);
            startActivity(intent);
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void load() {
        refresh.setEnabled(false);
        status.setText("正在解析 endpoint 与 bootstrap...");
        new Thread(() -> {
            try {
                Session session = Session.init(getApplicationContext());
                runOnUiThread(() -> status.setText(
                        "已连接 " + session.api.getCurrentBaseUrl() + "，正在加载首页..."));
                List<VideoItem> loaded = HomeRepository.load(session.api);
                runOnUiThread(() -> {
                    items.clear();
                    items.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    status.setText("共 " + items.size() + " 部影视 · "
                            + session.api.getCurrentBaseUrl());
                    refresh.setEnabled(true);
                });
            } catch (Exception e) {
                Log.e(TAG, "load failed", e);
                runOnUiThread(() -> {
                    status.setText("加载失败: " + errorText(e));
                    refresh.setEnabled(true);
                });
            }
        }, "jcy-home").start();
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
        if (imageLoader != null) {
            imageLoader.shutdown();
        }
        super.onDestroy();
    }

    private final class VideoAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            VideoItem item = items.get(position);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setGravity(Gravity.CENTER_VERTICAL);

            ImageView cover = new ImageView(MainActivity.this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackgroundColor(Color.rgb(0xEE, 0xEE, 0xEE));
            row.addView(cover, new LinearLayout.LayoutParams(dp(84), dp(116)));

            LinearLayout info = new LinearLayout(MainActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, 0, 0);
            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(MainActivity.this);
            name.setText(item.title);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            name.setTextColor(Color.BLACK);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(name);

            TextView subtitle = new TextView(MainActivity.this);
            subtitle.setText(item.subTitle);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subtitle.setTextColor(Color.rgb(0x66, 0x66, 0x66));
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(subtitle);

            TextView episode = new TextView(MainActivity.this);
            episode.setText(item.episodeText()
                    + (item.score > 0 ? "  ·  " + item.score + " 分" : ""));
            episode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            episode.setTextColor(Color.rgb(0x99, 0x66, 0x00));
            info.addView(episode);

            imageLoader.load(item.coverUrl, cover);
            return row;
        }
    }
}
