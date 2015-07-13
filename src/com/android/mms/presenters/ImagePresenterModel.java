package com.android.mms.presenters;

import android.net.Uri;

import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ItemLoadedFuture;

public interface ImagePresenterModel {
    ItemLoadedFuture loadThumbnailBitmap(ItemLoadedCallback callback, int maxWidth);
    void cancelThumbnailLoading();
    int getHeight();
    int getWidth();
    String getContentType();
    Uri getUri();
}
