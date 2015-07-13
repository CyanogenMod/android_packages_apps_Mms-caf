package com.android.mms.views;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import com.android.mms.presenters.ThumbnailViewInterface;

import com.android.mms.R;

public class ThumbnailMessageView extends SelectedFilterImageView implements ThumbnailViewInterface {

    public ThumbnailMessageView(Context context) {
        super(context);
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

    @Override
    public void setImage(Drawable drawable) {
        setImageDrawable(drawable);
        invalidateOutline();
    }
}
