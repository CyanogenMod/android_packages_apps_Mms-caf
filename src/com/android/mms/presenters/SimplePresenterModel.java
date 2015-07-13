package com.android.mms.presenters;

import com.android.mms.presenters.SimpleAttachmentPresenter.SimpleAttachmentLoaded;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ItemLoadedFuture;

import android.net.Uri;

public interface SimplePresenterModel {
    ItemLoadedFuture loadThumbnailBitmap(ItemLoadedCallback callback);

    int getHeight();
    int getWidth();

    String getContentType();

    String getLookupUri();

    void loadData(ItemLoadedCallback<SimpleAttachmentLoaded> itemLoadedCallback);
}
