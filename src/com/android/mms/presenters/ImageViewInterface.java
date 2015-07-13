package com.android.mms.presenters;

import android.graphics.Bitmap;

public interface ImageViewInterface extends RecyclePresenter.RecyclePresenterInterface {
    void setImage(Bitmap bitmap);
    void setSelectColor(int darkerColor);
}
