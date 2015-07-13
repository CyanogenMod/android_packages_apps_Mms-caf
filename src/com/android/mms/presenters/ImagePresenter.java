package com.android.mms.presenters;

import com.android.mms.R;
import com.android.mms.views.ThumbnailMessageView;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;

public class ImagePresenter extends ThumbnailPresenter<ThumbnailMessageView, ImagePresenterModel> {

    public ImagePresenter(Context context, ImagePresenterModel model) {
        super(context, model);
    }

    @Override
    public int getMessageAttachmentLayoutId() {
        return R.layout.image_attachment_view;
    }

    @Override
    public int getPreviewAttachmentLayoutId() {
        return R.layout.image_attachment_view;
    }

    @Override
    protected Class<ThumbnailMessageView> getViewClass() {
        return ThumbnailMessageView.class;
    }

    public static int getStaticHeight(Context context) {
        Resources res = context.getResources();
        return res.getDimensionPixelSize(R.dimen.thumbnail_attachment_static_height);
    }
}
