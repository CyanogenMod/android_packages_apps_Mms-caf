package com.android.mms.presenters;

import android.content.Intent;
import com.android.mms.presenters.SimpleAttachmentPresenter.SimpleAttachmentLoaded;
import com.android.mms.util.ItemLoadedCallback;

public interface SimplePresenterModel {
    void loadData(ItemLoadedCallback<SimpleAttachmentLoaded> itemLoadedCallback);
    Intent getIntent();
    void cancelBackgroundLoading();
}
