package com.android.mms.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import com.android.mms.R;

/**
 * Drawable that is a composition of a sim icon and a drop-down arrow. The drop-down drawable
 * is drawn below the sim icon.
 */
public class ScaleCenterDrawable extends Drawable implements Drawable.Callback {

    private final float mScale;
    private Drawable mDrawable;
//    private Paint mDebugPaint;

    public ScaleCenterDrawable(Context context, Drawable drawable, float scale) {
        if (drawable == null) {
            throw new IllegalArgumentException("drawable can be null");
        }

        mDrawable = drawable;
        mScale = scale;
//        mDebugPaint = new Paint();
//        mDebugPaint.setStyle(Paint.Style.STROKE);
//        mDebugPaint.setColor(Color.rgb(250, 0, 0));
//        mDebugPaint.setStrokeWidth(0);
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }

        canvas.save();
        //canvas.scale(mScale, mScale);


        int scaledWidth = (int) (mScale * bounds.width());
        int scaledHeight = (int) (mScale * bounds.height());
//        canvas.translate(bounds.centerX() - scaledWidth / 2,
//                bounds.centerY() - scaledHeight / 2);
        System.out.println("Canvas " + bounds);
        System.out.println("Scaled height " + scaledHeight);
        System.out.println("Translate " + (bounds.centerX() - scaledWidth / 2) + " " + (bounds.centerY() - scaledHeight / 2));

        int width = Math.min(scaledWidth, scaledHeight);
        int halfWidth = width / 2;
        Rect drawableBounds = new Rect(
                bounds.centerX() - halfWidth,
                bounds.centerY() - halfWidth,
                bounds.centerX() + halfWidth,
                bounds.centerY() + halfWidth);
        mDrawable.setBounds(drawableBounds);
        mDrawable.draw(canvas);

        canvas.restore();
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
        mDrawable.setColorFilter(cf);
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