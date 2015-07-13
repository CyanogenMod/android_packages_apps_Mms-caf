package com.android.mms.presenters;

import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ItemLoadedFuture;

import android.net.Uri;

public interface VideoPresenterModel {
    ItemLoadedFuture loadThumbnailBitmap(ItemLoadedCallback callback);



    String getContentType();

    Uri getUri();
}
