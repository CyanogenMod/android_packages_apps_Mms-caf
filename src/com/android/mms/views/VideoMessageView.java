package com.android.mms.views;

import com.android.mms.R;
import com.android.mms.presenters.VideoViewInterface;
import com.android.mms.ui.ScaleCenterDrawable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.view.Gravity;
import android.view.View;

public class VideoMessageView extends SelectedFilterImageView implements VideoViewInterface {

    public VideoMessageView(Context context) {
        super(context);
    }

    @Override
    public void setImage(Bitmap bitmap) {
        Drawable[] layers = new Drawable[3];
        layers[0] = new BitmapDrawable(getResources(), bitmap);
        layers[1] = new ColorDrawable(Color.parseColor("#55000000"));
        Drawable attachment = getResources().getDrawable(R.drawable.ic_video_attachment_play);
        // Construct this once and only update layer one. OPTIMIZATIONS !!!
        ScaleCenterDrawable scaleDrawable = new ScaleCenterDrawable(getContext(), attachment, 0.45f);
        layers[2] = scaleDrawable;
        LayerDrawable layerDrawable = new LayerDrawable(layers);
        setImageDrawable(layerDrawable);
    }

    @Override
    public View getView() {
        return this;
    }
}
