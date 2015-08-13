package com.android.mms.widget;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class CompoundDrawable extends Drawable implements Drawable.Callback {

    private Drawable mPrimaryDrawable;
    private Drawable mSecondaryDrawable;
    private Rect mBounds;
    private Paint mPaint;


    public CompoundDrawable(Drawable primaryDrawable, Drawable secondaryDrawable) {
        if (primaryDrawable == null || secondaryDrawable == null) {
            throw new IllegalArgumentException("primary or secondary drawables cannot be null");
        }

        mPrimaryDrawable = primaryDrawable;
        mSecondaryDrawable = secondaryDrawable;
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.rgb(250, 0, 0));
        mPaint.setStrokeWidth(0);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if(bounds.isEmpty()) {
            mPrimaryDrawable.setBounds(0, 0, 0, 0);
            mSecondaryDrawable.setBounds(0, 0, 0, 0);
        } else {
            mPrimaryDrawable.setBounds(bounds.left + 3, bounds.top + 3, bounds.right - 3, bounds.bottom - 42 - 10);
            mSecondaryDrawable.setBounds(bounds.left, bounds.top + 42, bounds.right, bounds.bottom);
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
//        canvas.drawRect(mBounds, mPaint);
        mPrimaryDrawable.draw(canvas);
        mSecondaryDrawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        mPrimaryDrawable.setAlpha(alpha);
        mSecondaryDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPrimaryDrawable.setColorFilter(cf);
        mSecondaryDrawable.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return resolveOpacity(mPrimaryDrawable.getOpacity(), mSecondaryDrawable.getOpacity());
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