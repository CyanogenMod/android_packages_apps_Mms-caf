package com.android.mms.widget;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * A Drawable that encompasses two drawables - a placeholder drawable and an action drawable - and
 * allows switching between the two drawables.
 */
public class LayeredRevealDrawable extends Drawable implements Drawable.Callback {

    private static final float NORMAL_SIZE = 1f;

    private Drawable mPlaceholder;
    private Drawable mAction;
    private int mAnimDuration;
    private ValueAnimator mScaleAnimator;
    private float mCurrentScale;
    private boolean mIsActionDrawableShowing;

    public LayeredRevealDrawable(Drawable placeholder, Drawable action, int revealDuration) {
        setActionDrawable(action);
        setPlaceholderDrawable(placeholder);
        mAnimDuration = revealDuration;

        mScaleAnimator = ValueAnimator.ofFloat(0, NORMAL_SIZE)
                .setDuration(revealDuration);
        mScaleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float oldScaleValue = mCurrentScale;
                mCurrentScale = (Float) animation.getAnimatedValue();
                if (oldScaleValue != mCurrentScale) {
                    invalidateSelf();
                }
            }
        });

        reset();
    }

    public void setActionDrawable(Drawable drawable) {
        setActionDrawable(drawable, false);
    }

    public void setActionDrawable(Drawable drawable, boolean forceRedraw) {
        if (drawable != null) {
            mAction = drawable;
            mAction.setCallback(this);
            mAction.setBounds(getBounds());
            if (forceRedraw) invalidateSelf();
        }
    }

    public void setPlaceholderDrawable(Drawable drawable) {
        setPlaceholderDrawable(drawable, false);
    }

    public void setPlaceholderDrawable(Drawable drawable, boolean forceRedraw) {
        if (drawable != null) {
            mPlaceholder = drawable;
            mPlaceholder.setCallback(this);
            mPlaceholder.setBounds(getBounds());
            if (forceRedraw) invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (bounds.isEmpty()) {
            if (mPlaceholder !=null) mPlaceholder.setBounds(0, 0, 0, 0);
            if (mAction != null) mAction.setBounds(0, 0, 0, 0);
        } else {
            if (mPlaceholder !=null) mPlaceholder.setBounds(bounds);
            if (mAction != null) mAction.setBounds(bounds);
        }
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

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }

        if (mPlaceholder != null) mPlaceholder.draw(canvas);
        canvas.scale(mCurrentScale, mCurrentScale, bounds.exactCenterX(), bounds.exactCenterY());
        if (mAction != null) mAction.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        if (mPlaceholder != null) mPlaceholder.setAlpha(alpha);
        if (mAction != null) mAction.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        if (mPlaceholder != null) mPlaceholder.setColorFilter(cf);
        if (mAction != null) mAction.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return resolveOpacity(
                mPlaceholder != null ? mPlaceholder.getOpacity() : PixelFormat.UNKNOWN,
                mAction != null ? mAction.getOpacity() : PixelFormat.UNKNOWN );
    }

    public void reset() {
        final float scale = mCurrentScale;
        mScaleAnimator.cancel();
        mCurrentScale = 0f;
        if (mCurrentScale != scale) {
            invalidateSelf();
        }
        mIsActionDrawableShowing = false;
    }

    public Drawable getPlaceHolder() {
        return mPlaceholder;
    }

    public void showAction(boolean show) {
        if (show && !mIsActionDrawableShowing) {
            if (!mScaleAnimator.isStarted()) {
                mScaleAnimator.start();
                mIsActionDrawableShowing = true;
            }
        } else if (!show && mIsActionDrawableShowing) {
            mScaleAnimator.reverse();
            mIsActionDrawableShowing = false;
        }
    }

    public Drawable getActionDrawable() {
        return mAction;
    }
}
