package com.android.mms.widget;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * @author Rohit Yengisetty
 */
public class RevealDrawable extends Drawable implements Drawable.Callback {

    private static final float NORMAL_SIZE = 1f;

    private Drawable mPlaceholder;
    private Drawable mAction;
    private int mAnimDuration;
    private ValueAnimator mScaleAnimator;
    private float mCurrentScale;
    private boolean mIsActionDrawableShowing;

    public RevealDrawable(Drawable placeholder, Drawable action, int revealDuration) {
        if (placeholder == null || action == null) {
            throw new IllegalArgumentException("// TODO //");
        }

        mPlaceholder = placeholder;
        mAction  = action;

        mPlaceholder.setCallback(this);
        mAction.setCallback(this);
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

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (bounds.isEmpty()) {
            mPlaceholder.setBounds(0, 0, 0, 0);
            mAction.setBounds(0, 0, 0, 0);
        } else {
            mPlaceholder.setBounds(bounds);
            mAction.setBounds(bounds);
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

        mPlaceholder.draw(canvas);
        canvas.scale(mCurrentScale, mCurrentScale, bounds.exactCenterX(), bounds.exactCenterY());
        mAction.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        mPlaceholder.setAlpha(alpha);
        mAction.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPlaceholder.setColorFilter(cf);
        mAction.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return resolveOpacity(mPlaceholder.getOpacity(), mAction.getOpacity());
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

    public void setActionDrawable(Drawable drawable) {
        mAction = drawable;
        mAction.setCallback(this);
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
