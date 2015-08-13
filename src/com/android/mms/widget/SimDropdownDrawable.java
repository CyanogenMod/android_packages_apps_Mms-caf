package com.android.mms.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import com.android.mms.R;

/**
 * Drawable that is a composition of a sim icon and a drop-down arrow. The drop-down drawable
 * is drawn below the sim icon.
 */
public class SimDropdownDrawable extends Drawable implements Drawable.Callback {

    private Drawable mSimDrawable;
    private Drawable mDropdownDrawable;
    private Rect mBounds;
    private int mSimIconPadding;
    private int mSimIconPaddingBottom;
    private int mDropdownIconSize;
//    private Paint mDebugPaint;

    public SimDropdownDrawable(Context context, Drawable simDrawable, Drawable dropdownDrawable) {
        if (simDrawable == null || dropdownDrawable == null) {
            throw new IllegalArgumentException("sim drawable nor the drop-down " +
                    "drawable can be null");
        }

        mSimDrawable = simDrawable;
        mDropdownDrawable = dropdownDrawable;
        Resources res = context.getResources();
        mSimIconPadding = res.getDimensionPixelSize(R.dimen.sim_selector_sim_icon_padding);
        mDropdownIconSize = res.getDimensionPixelSize(R.dimen.sim_selector_dropdown_icon_size);
        mSimIconPaddingBottom = res.getDimensionPixelSize(R.dimen.sim_selector_sim_icon_padding_bottom);
//        mDebugPaint = new Paint();
//        mDebugPaint.setStyle(Paint.Style.STROKE);
//        mDebugPaint.setColor(Color.rgb(250, 0, 0));
//        mDebugPaint.setStrokeWidth(0);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if(bounds.isEmpty()) {
            mSimDrawable.setBounds(0, 0, 0, 0);
            mDropdownDrawable.setBounds(0, 0, 0, 0);
        } else {
            mSimDrawable.setBounds(bounds.left + mSimIconPadding,
                    bounds.top + mSimIconPadding,
                    bounds.right - mSimIconPadding,
                    bounds.bottom - mDropdownIconSize - mSimIconPaddingBottom);
            mDropdownDrawable.setBounds(bounds.left,
                    bounds.top + mDropdownIconSize,
                    bounds.right,
                    bounds.bottom);
        }
        mBounds = bounds;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }

        // draw debug rect
//        canvas.drawRect(mBounds, mDebugPaint);
        mSimDrawable.draw(canvas);
        mDropdownDrawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        mSimDrawable.setAlpha(alpha);
        mDropdownDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mSimDrawable.setColorFilter(cf);
        mDropdownDrawable.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return resolveOpacity(mSimDrawable.getOpacity(), mDropdownDrawable.getOpacity());
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }
}