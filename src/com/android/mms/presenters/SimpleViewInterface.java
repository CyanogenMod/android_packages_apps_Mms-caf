package com.android.mms.presenters;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public interface SimpleViewInterface {
    void setImage(Bitmap bitmap);
    void setImage(Drawable drawable);
    void setLabel1(String label);
    void setLabel2(String label);
}
