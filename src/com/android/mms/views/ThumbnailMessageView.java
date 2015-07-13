package com.android.mms.views;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import com.android.mms.presenters.ThumbnailViewInterface;

import com.android.mms.R;

public class ThumbnailMessageView extends SelectedFilterImageView implements ThumbnailViewInterface {

    public ThumbnailMessageView(Context context) {
        super(context);
        init();
    }

    public ThumbnailMessageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThumbnailMessageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ThumbnailMessageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    @Override
    public void setImage(Drawable drawable) {
        setImageDrawable(drawable);
        invalidateOutline();
    }

    private void init() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                if (getDrawable() != null) {
                    int radius = getContext().getResources()
                            .getDimensionPixelOffset(R.dimen.thumbnail_attachment_corner_radius);
                    outline.setRoundRect(0, 0, getWidth(), getHeight(), radius);
                }
            }
        });
    }
}
