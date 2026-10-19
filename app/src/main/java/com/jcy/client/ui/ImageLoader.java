package com.jcy.client.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public void load(String url, ImageView view) {
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        Bitmap cached = cache.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setTag(url);
        view.setImageDrawable(null);
        executor.execute(() -> {
            Bitmap bitmap = download(url);
            if (bitmap != null) {
                cache.put(url, bitmap);
            }
            view.post(() -> {
                if (url.equals(view.getTag()) && bitmap != null) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    private Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", "JcyClient/1.0");
            try (InputStream in = connection.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
