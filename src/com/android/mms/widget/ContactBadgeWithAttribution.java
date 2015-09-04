package com.android.mms.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Checkable;
import android.widget.QuickContactBadge;
import com.android.contacts.common.widget.CheckableFlipDrawable;

import com.android.mms.R;

/**
 * This view is used to display a contact's avatar and an attribution badge, if needed. It is also
 * used to show a checkmark drawable when in 'selected' state. This view contains a
 * {@link CheckableFlipDrawable} as its primary drawable, which enables it to present the varied
 * states outlined before.
 *
 * Within the CheckableFlipDrawable, the front drawable could be a {@link LayerDrawable} or
 * a {@link android.graphics.drawable.BitmapDrawable}. It is a LayerDrawable, if attribution needs
 * to be shown : with layer 0 being the contact photo and layer 1 being the attribution logo. If
 * there isn't a need to an attribution logo, then the front drawable is a regular BitmapDrawable.
 *
 * The back drawable of the CheckableFlipDrawable, will always be a checkmark.
 */
public class ContactBadgeWithAttribution extends QuickContactBadge implements Checkable {
    private static final int FLIP_DURATION = 150;
    private boolean mChecked = false;
    private int mCheckMarkBackgroundColor;
    private CheckableFlipDrawable mFlipDrawable;
    private boolean mInitCalled;

    private Drawable mAttributionDrawable;

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
        mInitCalled = true;
        mCheckMarkBackgroundColor = context.getResources().
                getColor(R.color.action_mode_checkmark_color);
    }

    public void setCheckMarkBackgroundColor(int color) {
        mCheckMarkBackgroundColor = color;
        if (mFlipDrawable != null) {
            mFlipDrawable.setCheckMarkBackgroundColor(color);
        }
    }

    public void setAttributionDrawable(Drawable drawable) {
        mAttributionDrawable = drawable;
        setImageDrawable(extractContactDrawable());
    }

    /**
     * Used to get the contact bitmap from within the Front drawable in the CheckableFlipDrawable
     */
    private Drawable extractContactDrawable() {
        Drawable currentDrawable = super.getDrawable();
        if (currentDrawable instanceof CheckableFlipDrawable) {
            currentDrawable = ((CheckableFlipDrawable) currentDrawable).getFrontDrawable();
            // current drawable could be an instance of LayerDrawable composed of a contact drawable
            // and the attribution drawable
            if (currentDrawable instanceof LayerDrawable) {
                // extract the contact drawable
                currentDrawable = ((LayerDrawable) currentDrawable).getDrawable(0);
            }
        }

        return currentDrawable;
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
        if (mFlipDrawable != null) {
            applyCheckState(animate);
        }
    }

    @Override
    public void setImageDrawable(Drawable d) {
        if (d != null) {
            // ImageView's constructor can call setImageDrawable (which is over-ridden here), if
            // a 'src' is specified
            //
            // init() wouldn't have been called by this time and we can't use an object initializer
            // to circumvent this problem as they are executed after the super classes are
            // constructed
            if (!mInitCalled) {
                init(getContext());
            }

            LayerDrawable ld = null;
            if (mAttributionDrawable != null) {
                Drawable[] drawables = new Drawable[2];
                drawables[0] = d;
                ScaleDrawable scaleDrawable = new ScaleDrawable(mAttributionDrawable,
                        Gravity.BOTTOM | Gravity.RIGHT, 1.0f, 1.0f);
                // level is the parameter imparting scale to the drawable
                // see {@link ScaleDrawable#onBoundsChange}
                // TODO : setting scale isn't working, the drawable is drawn intermittently
                // but, setting level on the drawable eliminates the issue, investigate
                scaleDrawable.setLevel(3200);
                drawables[1] = scaleDrawable;
                ld = new LayerDrawable(drawables);
            }

            if (mFlipDrawable == null) {
                mFlipDrawable = new CheckableFlipDrawable(ld == null ? d : ld, getResources(),
                        mCheckMarkBackgroundColor, FLIP_DURATION);
                applyCheckState(false);
            } else {
                mFlipDrawable.setFront(ld == null ? d : ld);
            }

            d = mFlipDrawable;
        }

        super.setImageDrawable(d);
    }

    /**
     * Overriding the default behavior of getDrawable to return the contact drawable.
     *
     * This avoids issues when using this view with image loading libraries that try to get the
     * current drawable and cross-fade it with the new drawable we asked the library to fetch. If we
     * return the actual drawable, the CheckableFlipDrawable, it will already have a reference to
     * the new drawable from when the library called setImageDrawable on this view. When the library
     * goes to draw the new drawable, it first draws the drawable it got through
     * getDrawable(placeholder) before drawing the new drawable in an attempt to fade between
     * the two states. The act of drawing the placeholder triggers a draw on the new drawable, as
     * the placeholder references the new drawable. The new drawable in turn wants to draw the
     * placeholder before drawing itself. We end up with circular calls that ultimately end in
     * a stack overflow error.
     */
    @Override
    public Drawable getDrawable() {
        return extractContactDrawable();
    }

    private void applyCheckState(boolean animate) {
        mFlipDrawable.flipTo(!mChecked);
        if (!animate) {
            mFlipDrawable.reset();
        }
    }
}