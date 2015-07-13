package com.android.mms.views;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import com.android.mms.presenters.ThumbnailViewInterface;

public class ImageMessageView extends SelectedFilterImageView implements ThumbnailViewInterface {

    public ImageMessageView(Context context) {
        super(context);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                if (getDrawable() != null) {
                    outline.setRoundRect(0, 0, getWidth(), getHeight(), 10);
                }
            }
        });
    }

    @Override
    public void setImage(Drawable drawable) {
        setImageDrawable(drawable);
        invalidateOutline();
    }

    @Override
    public View getView() {
        return this;
    }
}
