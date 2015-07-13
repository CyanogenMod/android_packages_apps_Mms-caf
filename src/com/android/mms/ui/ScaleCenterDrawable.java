package com.android.mms.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Drawable that is a composition of a sim icon and a drop-down arrow. The drop-down drawable
 * is drawn below the sim icon.
 */
public class ScaleCenterDrawable extends Drawable implements Drawable.Callback {

    private final float mScale;
    private Drawable mDrawable;

    public ScaleCenterDrawable(Context context, Drawable drawable, float scale) {
        if (drawable == null) {
            throw new IllegalArgumentException("drawable can be null");
        }

        mDrawable = drawable;
        mScale = scale;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }
        int scaledWidth = (int) (mScale * bounds.width());
        int scaledHeight = (int) (mScale * bounds.height());

        int width = Math.min(scaledWidth, scaledHeight);
        int halfWidth = width / 2;
        Rect drawableBounds = new Rect(
                bounds.centerX() - halfWidth,
                bounds.centerY() - halfWidth,
                bounds.centerX() + halfWidth,
                bounds.centerY() + halfWidth);
        mDrawable.setBounds(drawableBounds);
        mDrawable.draw(canvas);
    }

    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                return ScaleCenterDrawable.this;
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }

    @Override
    public void setAlpha(int alpha) {
        mDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return mDrawable.getOpacity();
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