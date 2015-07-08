package com.android.mms.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageButton;
import com.android.mms.R;

/**
 * An ImageButton that animates between different background drawables as dictated. There is a
 * notion of a placeholder drawable and an action drawable. The placeholder drawable is shown
 * until commanded to show the action drawable. You can also go back to showing the placeholder
 * drawable, when deemed appropriate. Reveal animations are performed when fluctuating between
 * showing the action and placeholder drawables.
 */
public class FluctuatingImageButton extends ImageButton {

    private static int ANIMATION_DURATION = 200; // ms

    public FluctuatingRevealDrawable mDrawable;

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
        if (background != null && background instanceof FluctuatingRevealDrawable) {
            mDrawable = (FluctuatingRevealDrawable) background;
        }
        super.setBackground(background);
    }

    public void setActionDrawable(Drawable actionDrawable) {
        Drawable backgroundDrawable = getBackground();
        if (backgroundDrawable == null) return;

        Drawable placeholderDrawable;
        if (backgroundDrawable instanceof FluctuatingRevealDrawable) {
            placeholderDrawable = ((FluctuatingRevealDrawable) backgroundDrawable).getPlaceHolder();
        } else {
            placeholderDrawable = backgroundDrawable;
        }

        mDrawable = new FluctuatingRevealDrawable(placeholderDrawable, actionDrawable,
                ANIMATION_DURATION);
        super.setBackground(mDrawable);
    }

    public void setPlaceholderDrawable(Drawable placeholderDrawable) {
        Drawable backgroundDrawable = getBackground();
        if (backgroundDrawable == null) return;

        Drawable actionDrawable;
        if (backgroundDrawable instanceof FluctuatingRevealDrawable) {
            actionDrawable = ((FluctuatingRevealDrawable) backgroundDrawable).getActionDrawable();
        } else {
            actionDrawable = backgroundDrawable;
        }

        mDrawable = new FluctuatingRevealDrawable(placeholderDrawable, actionDrawable,
                ANIMATION_DURATION);
        super.setBackground(mDrawable);
    }

    public void showActionDrawable(boolean show) {
        if (mDrawable != null) {
            mDrawable.showAction(show);
        }
    }
}
