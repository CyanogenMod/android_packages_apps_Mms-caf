package com.android.mms.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * An ImageButton that animates between different background drawables as dictated. There is a
 * notion of a placeholder drawable and an action drawable. The placeholder drawable is shown
 * until commanded to show the action drawable. You can also go back to showing the placeholder
 * drawable, when deemed appropriate. Reveal animations are performed when fluctuating between
 * showing the action and placeholder drawables.
 */
public class FluctuatingImageButton extends ImageButton {

    private static int ANIMATION_DURATION = 200; // ms

    public FluctuatingRevealDrawable mBackgroundDrawable;

    public FluctuatingImageButton(Context context) {
        super(context);
    }

    public FluctuatingImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FluctuatingImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FluctuatingImageButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setBackground(Drawable background) {
        if (mBackgroundDrawable == null) {
            mBackgroundDrawable = new FluctuatingRevealDrawable(background, null, ANIMATION_DURATION);
        } else {
            mBackgroundDrawable.setPlaceholderDrawable(background, true /* force re-draw */);
        }
        super.setBackground(mBackgroundDrawable);
    }

    public void setActionDrawable(Drawable actionDrawable) {
        mBackgroundDrawable.setActionDrawable(actionDrawable, true /* force re-draw */);
        super.setBackground(mBackgroundDrawable);
    }

    public void setPlaceholderDrawable(Drawable placeholderDrawable) {
        mBackgroundDrawable.setPlaceholderDrawable(placeholderDrawable, true /* force re-draw */);
        super.setBackground(mBackgroundDrawable);
    }

    public void showActionDrawable(boolean show) {
        if (mBackgroundDrawable != null) {
            mBackgroundDrawable.showAction(show);
        }
    }
}
