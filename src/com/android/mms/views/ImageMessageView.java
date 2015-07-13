package com.android.mms.views;

import com.android.mms.presenters.ImageViewInterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.widget.ImageView;

public class ImageMessageView extends SelectedFilterImageView implements ImageViewInterface {

    public ImageMessageView(Context context) {
        super(context);
    }

    @Override
    public void setImage(Bitmap bitmap) {
        setImageBitmap(bitmap);
    }
}
