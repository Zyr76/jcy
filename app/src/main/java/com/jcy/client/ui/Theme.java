package com.jcy.client.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;

public final class Theme {
    public static final int BG = 0xFF08090D;
    public static final int BG_CARD = 0xFF12141C;
    public static final int SURFACE = 0xFF181B26;
    public static final int OUTLINE = 0x14FFFFFF;
    public static final int TEXT = 0xFFF4F6FB;
    public static final int TEXT_MUTED = 0xFF8B90A3;
    public static final int ACCENT = 0xFF7C5CFF;
    public static final int ACCENT_2 = 0xFF3BE1F1;
    public static final int DANGER = 0xFFFF5C7A;
    public static final int GOLD = 0xFFFFC857;

    private Theme() {
    }

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static Typeface display() {
        return Typeface.create("sans-serif-black", Typeface.BOLD);
    }

    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static Drawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static Drawable stroked(int fill, int stroke, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static Drawable gradient(float radiusDp, Context context, int... colors) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static Drawable verticalScrim(int topColor, int bottomColor) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topColor, bottomColor});
    }

    public static Drawable pressable(int baseColor, float radiusDp, Context context) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(baseColor);
        mask.setCornerRadius(dp(context, radiusDp));
        GradientDrawable content = new GradientDrawable();
        content.setColor(baseColor);
        content.setCornerRadius(dp(context, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

    public static void fadeIn(View view) {
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(220).start();
    }

    public static void configureWindow(Window window) {
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(BG);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    public static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : dp(context, 24);
    }

    public static int navigationBarHeight(Context context) {
        int id = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : 0;
    }

    public static float sp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                context.getResources().getDisplayMetrics());
    }
}
