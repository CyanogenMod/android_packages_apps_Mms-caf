package com.android.mms.presenters;

import android.net.Uri;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ItemLoadedFuture;

public interface ThumbnailPresenterModel {
    ItemLoadedFuture loadThumbnailBitmap(ItemLoadedCallback callback, int maxWidth);
    void cancelThumbnailLoading();
    String getContentType();
    Uri getUri();
}
