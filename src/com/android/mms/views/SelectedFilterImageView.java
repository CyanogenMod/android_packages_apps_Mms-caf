package com.android.mms.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.widget.ImageView;

public class SelectedFilterImageView extends ImageView {

    private int mSelectColor;

    public SelectedFilterImageView(Context context) {
        super(context);
    }

    public SelectedFilterImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SelectedFilterImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SelectedFilterImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setSelectColor(int color) {
        mSelectColor = color;
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (selected) {
            setColorFilter(mSelectColor, PorterDuff.Mode.MULTIPLY);
        } else {
            setColorFilter(null);
        }
    }
}
