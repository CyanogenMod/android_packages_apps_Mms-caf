package com.android.mms.presenters;

import com.android.mms.R;
import com.android.mms.views.ThumbnailMessageView;

import android.content.Context;
import android.content.res.Resources;

public class ImagePresenter extends ThumbnailPresenter<ThumbnailMessageView, ImagePresenterModel> {

    public ImagePresenter(Context context, ImagePresenterModel model) {
        super(context, model);
    }

    @Override
    public ThumbnailMessageView inflateThumbnail(PresenterOptions presenterOptions) {
        return new ThumbnailMessageView(getContext());
    }

    @Override
    public ThumbnailMessageView inflateAttachmentThumbnail(AttachmentPresenterOptions presenterOptions) {
        return inflateThumbnail(null);
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
