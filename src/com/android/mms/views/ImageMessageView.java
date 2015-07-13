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

public class ImageMessageView extends ImageView implements ImageViewInterface {

    public ImageMessageView(Context context) {
        super(context);
    }

    @Override
    public void setImage(Bitmap bitmap) {
        Drawable[] drawables = new Drawable[2];
        Drawable first = getDrawable();
        if (first == null) {
            first = new ColorDrawable(Color.WHITE);
        }
        drawables[0] = first;
        drawables[1] = new BitmapDrawable(bitmap);
        TransitionDrawable transitionDrawable = new TransitionDrawable(drawables);
        setImageDrawable(transitionDrawable);
        transitionDrawable.startTransition(200);
    }
}
