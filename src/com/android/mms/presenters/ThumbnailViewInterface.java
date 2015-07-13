package com.android.mms.presenters;

import android.graphics.drawable.Drawable;

public interface ThumbnailViewInterface extends RecyclePresenter.RecyclePresenterInterface {
    void setSelectColor(int darkerColor);
    void setImage(Drawable drawable);
}
