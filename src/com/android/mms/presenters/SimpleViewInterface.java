package com.android.mms.presenters;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public interface SimpleViewInterface {
    void setIconBitmap(Bitmap bitmap);
    void setIconDrawable(Drawable drawable);
    void setIconTint(int color);
    void setTitle(String label);
    void setTitleColor(int color);
    void setSubTitle(String label);
    void setSubTitleColor(int color);
    Drawable getBackground();

    int getWidth();
    int getHeight();
}
