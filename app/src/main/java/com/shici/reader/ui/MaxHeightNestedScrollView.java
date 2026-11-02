package com.shici.reader.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * Caps the detail sheet at a comfortable portion of the screen so the sheet
 * never grows into a full-screen blank page on devices with long definitions.
 */
public class MaxHeightNestedScrollView extends NestedScrollView {

    private static final float MAX_SCREEN_FRACTION = 0.78f;

    public MaxHeightNestedScrollView(@NonNull Context context) {
        super(context);
    }

    public MaxHeightNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs,
                                     int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int maxHeight = (int) (screenHeight * MAX_SCREEN_FRACTION);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int available = MeasureSpec.getSize(heightMeasureSpec);
        if (mode == MeasureSpec.EXACTLY) {
            maxHeight = Math.min(maxHeight, available);
        } else if (mode == MeasureSpec.AT_MOST) {
            maxHeight = Math.min(maxHeight, available);
        } else {
            mode = MeasureSpec.AT_MOST;
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, mode));
    }
}
