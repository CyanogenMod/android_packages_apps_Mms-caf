package com.android.mms.presenters;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import com.android.mms.presenters.SimpleAttachmentPresenter.SimpleAttachmentLoaded;
import com.android.mms.util.ItemLoadedCallback;

public interface SimplePresenterModel {
    Drawable getPlaceHolder();
    void loadData(ItemLoadedCallback<SimpleAttachmentLoaded> itemLoadedCallback);
    Intent getIntent();
    void cancelBackgroundLoading();
    Uri getUri();
}
