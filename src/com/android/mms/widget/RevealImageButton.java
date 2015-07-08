package com.android.mms.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageButton;
import com.android.mms.R;

/**
 * @author Rohit Yengisetty
 */
public class RevealImageButton extends ImageButton {

    public RevealDrawable mDrawable;

    public RevealImageButton(Context context) {
        super(context);
    }

    public RevealImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RevealImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RevealImageButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setBackground(Drawable background) {
        if (background != null) {
            if (mDrawable == null) {
                Drawable temp = getResources().getDrawable(R.drawable.add_attachment);
                mDrawable = new RevealDrawable(background, temp, 300);
            } else {
                mDrawable.reset();
            }

            background = mDrawable;
        }
        super.setBackground(background);
    }

    public void setActionDrawable(Drawable drawable) {
        RevealDrawable oldDrawable = (RevealDrawable) getBackground();
        mDrawable = new RevealDrawable(oldDrawable.getPlaceHolder(), drawable, 300);
        super.setBackground(mDrawable);
    }

    public void setPlaceholderDrawable(Drawable drawable) {
        RevealDrawable oldDrawable = (RevealDrawable) getBackground();
        mDrawable = new RevealDrawable(drawable, oldDrawable.getActionDrawable(), 300);
        super.setBackground(mDrawable);
    }


    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
    }

    public void reveal(boolean show) {
        if (mDrawable != null) {
            mDrawable.showAction(show);
        }
    }
}
