package com.jcy.client.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class RefreshLayout extends FrameLayout {
    public interface OnRefresh {
        void onRefresh();
    }

    private final TextView hint;
    private final View content;
    private final int trigger;
    private final int maxPull;
    private OnRefresh listener;
    private float downY;
    private boolean dragging;
    private boolean refreshing;
    private boolean enabled = true;

    public RefreshLayout(Context context, View content) {
        super(context);
        this.content = content;
        trigger = Theme.dp(context, 72);
        maxPull = Theme.dp(context, 120);

        hint = new TextView(context);
        hint.setTextColor(Theme.TEXT_MUTED);
        hint.setTextSize(12);
        hint.setText("下拉刷新");
        hint.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, trigger);
        params.gravity = android.view.Gravity.TOP;
        addView(hint, params);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        content.setBackgroundColor(Theme.BG);
    }

    public void setOnRefresh(OnRefresh onRefresh) {
        this.listener = onRefresh;
    }

    public void finish() {
        refreshing = false;
        hint.setText("下拉刷新");
        content.animate().translationY(0).setDuration(220).start();
        hint.animate().alpha(0f).setDuration(220).start();
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!enabled || refreshing) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = event.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (atTop() && event.getY() - downY > ViewConfiguration.get(getContext())
                        .getScaledTouchSlop()) {
                    dragging = true;
                    hint.setAlpha(1f);
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled || refreshing) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float pull = Math.min(maxPull, (event.getY() - downY) * 0.55f);
                if (pull > 0) {
                    content.setTranslationY(pull);
                    hint.setText(pull >= trigger * 0.8f ? "松开刷新" : "下拉刷新");
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float pull = content.getTranslationY();
                if (pull >= trigger * 0.8f && listener != null) {
                    refreshing = true;
                    hint.setText("正在刷新…");
                    content.animate().translationY(trigger * 0.55f).setDuration(160).start();
                    listener.onRefresh();
                } else {
                    finish();
                }
                dragging = false;
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private boolean atTop() {
        if (content instanceof AbsListView) {
            AbsListView list = (AbsListView) content;
            if (list.getChildCount() == 0) {
                return true;
            }
            return list.getFirstVisiblePosition() == 0
                    && list.getChildAt(0).getTop() >= 0;
        }
        return true;
    }
}
