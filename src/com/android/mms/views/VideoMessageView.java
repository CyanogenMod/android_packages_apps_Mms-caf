package com.android.mms.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import com.android.mms.R;
import com.android.mms.ui.ScaleCenterDrawable;

public class VideoMessageView extends ThumbnailMessageView {

    public VideoMessageView(Context context) {
        super(context);
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
        super.setImage(layerDrawable);
    }

}
