package com.android.mms.views;

import android.view.View;
import com.android.mms.R;
import com.android.mms.presenters.VideoViewInterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.Gravity;
import android.widget.ImageView;

public class VideoMessageView extends SelectedFilterImageView implements VideoViewInterface {

    public VideoMessageView(Context context) {
        super(context);
    }

    @Override
    public void setImage(Bitmap bitmap) {
        Drawable[] layers = new Drawable[3];
        layers[0] = new BitmapDrawable(bitmap);
        layers[1] = new ColorDrawable(Color.parseColor("#55000000"));
        Drawable attachment = getResources().getDrawable(R.drawable.ic_video_attachment_play);
        // Construct this once and only update layer one. OPTIMIZATIONS !!!
        ScaleDrawable scaleDrawable = new ScaleDrawable(attachment, Gravity.CENTER, 1.0f, 1.0f);
        scaleDrawable.setLevel(4500);
        layers[2] = scaleDrawable;
        LayerDrawable layerDrawable = new LayerDrawable(layers);
        setImageDrawable(layerDrawable);
    }

    @Override
    public View getView() {
        return this;
    }
}
