package com.android.mms.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;

import com.android.mms.R;
import com.android.mms.ui.ScaleCenterDrawable;

public class VideoMessageView extends ThumbnailMessageView {

    public VideoMessageView(Context context) {
        super(context);
    }

    public VideoMessageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VideoMessageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public VideoMessageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setImage(Drawable drawable) {
        LayerDrawable layerDrawable = null;
        if (drawable != null) {
            Drawable[] layers = new Drawable[3];
            layers[0] = drawable;
            layers[1] = new ColorDrawable(Color.parseColor("#55000000"));
            Drawable attachment = getResources().getDrawable(R.drawable.ic_video_attachment_play);
            // Construct this once and only update layer one. OPTIMIZATIONS !!!
            ScaleCenterDrawable scaleDrawable = new ScaleCenterDrawable(getContext(), attachment, 0.45f);
            layers[2] = scaleDrawable;
            layerDrawable = new LayerDrawable(layers);
        }
        super.setImage(layerDrawable);
    }

}
