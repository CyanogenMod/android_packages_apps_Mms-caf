package com.android.mms.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Checkable;
import android.widget.QuickContactBadge;
import com.android.contacts.common.widget.CheckableFlipDrawable;

import com.android.mms.R;

public class ContactBadgeWithAttribution extends QuickContactBadge implements Checkable {
    private boolean mChecked = false;
    private int mCheckMarkBackgroundColor;
    private CheckableFlipDrawable mDrawable;

    private Drawable mAttributionDrawable;
    private int mAttributionBadgeHeight;
    private int mAttributionBadgeWidth;

    public ContactBadgeWithAttribution(Context context) {
        super(context);
        init(context);
    }

    public ContactBadgeWithAttribution(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ContactBadgeWithAttribution(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public ContactBadgeWithAttribution(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        TypedArray a = context.obtainStyledAttributes(android.R.styleable.Theme);
        setCheckMarkBackgroundColor(a.getColor(android.R.styleable.Theme_colorPrimary,
                context.getResources().getColor(R.color.people_app_theme_color)));
        a.recycle();

        // mAttributionDrawable = context.getResources().getDrawable(R.drawable.ic_launcher_smsmms);
    }

    public void setCheckMarkBackgroundColor(int color) {
        mCheckMarkBackgroundColor = color;
        if (mDrawable != null) {
            mDrawable.setCheckMarkBackgroundColor(color);
        }
    }

    public void setAttributionBadgeParams(int badgeWidth, int badgeHeight) {
        mAttributionBadgeWidth = badgeWidth;
        mAttributionBadgeHeight = badgeHeight;
    }

    public void setAttributionDrawable(Drawable drawable) {
        mAttributionDrawable = drawable;
        // setImageDrawable(getDrawable());
        amendPrimaryDrawable();
    }

    private void amendPrimaryDrawable() {
        Drawable currentDrawable = getDrawable();
        if (currentDrawable instanceof CheckableFlipDrawable) {
            currentDrawable = ((CheckableFlipDrawable) currentDrawable).getFrontDrawable();
        }
        setImageDrawable(currentDrawable);
    }

    public void toggle() {
        setChecked(!mChecked);
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean animate) {
        if (mChecked == checked) {
            return;
        }

        mChecked = checked;
        if (mDrawable != null) {
            applyCheckState(animate);
        }
    }

    @Override
    public void setImageDrawable(Drawable d) {
        if (d != null) {
            LayerDrawable ld = null;
            if (mAttributionDrawable != null) {
                Drawable[] drawables = new Drawable[2];
                drawables[0] = d;
                ScaleDrawable scaleDrawable = new ScaleDrawable(mAttributionDrawable, Gravity.BOTTOM | Gravity.RIGHT, 1.0f, 1.0f);
                // level is the parameter imparting scale to the drawable
                // see {@link ScaleDrawable#onBoundsChange}
                scaleDrawable.setLevel(3200);
                drawables[1] = scaleDrawable;
                ld = new LayerDrawable(drawables);
            }

            if (mDrawable == null) {
                mDrawable = new CheckableFlipDrawable(ld == null ? d : ld, getResources(),
                        mCheckMarkBackgroundColor, 150);
                applyCheckState(false);
            } else {
                mDrawable.setFront(ld == null ? d : ld);
            }
            d = mDrawable;
        }
        super.setImageDrawable(d);
    }

    private void applyCheckState(boolean animate) {
        mDrawable.flipTo(!mChecked);
        if (!animate) {
            mDrawable.reset();
        }
    }
}