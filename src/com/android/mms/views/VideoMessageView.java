package com.android.mms.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import com.android.mms.R;
import com.android.mms.presenters.ThumbnailViewInterface;
import com.android.mms.ui.ScaleCenterDrawable;

public class VideoMessageView extends SelectedFilterImageView implements ThumbnailViewInterface {

    public VideoMessageView(Context context) {
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
        Drawable[] layers = new Drawable[3];
        layers[0] = drawable;
        layers[1] = new ColorDrawable(Color.parseColor("#55000000"));
        Drawable attachment = getResources().getDrawable(R.drawable.ic_video_attachment_play);
        // Construct this once and only update layer one. OPTIMIZATIONS !!!
        ScaleCenterDrawable scaleDrawable = new ScaleCenterDrawable(getContext(), attachment, 0.45f);
        layers[2] = scaleDrawable;
        LayerDrawable layerDrawable = new LayerDrawable(layers);
        setImageDrawable(layerDrawable);
        invalidateOutline();
    }

    @Override
    public View getView() {
        return this;
    }
}
