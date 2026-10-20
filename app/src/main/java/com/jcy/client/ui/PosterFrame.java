package com.jcy.client.ui;

import android.content.Context;
import android.widget.FrameLayout;

public final class PosterFrame extends FrameLayout {
    private final float ratio;

    public PosterFrame(Context context, float heightToWidth) {
        super(context);
        this.ratio = heightToWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (width * ratio);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
