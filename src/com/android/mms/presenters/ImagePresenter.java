package com.android.mms.presenters;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.mms.R;
import com.android.mms.views.ThumbnailMessageView;

public class ImagePresenter extends ThumbnailPresenter<ThumbnailMessageView, ImagePresenterModel> {

    public ImagePresenter(Context context, ImagePresenterModel model) {
        super(context, model);
    }

    @Override
    protected boolean isRecyclable(Class viewClass) {
        return viewClass.equals(ThumbnailMessageView.class);
    }

    @Override
    public ThumbnailMessageView getView(PresenterOptions presenterOptions) {
        ThumbnailMessageView view = new ThumbnailMessageView(getContext());
        view.setAdjustViewBounds(true);
        view.setScaleType(presenterOptions.isIncomingMessage() ?
                ImageView.ScaleType.FIT_START : ImageView.ScaleType.FIT_END);
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(layoutParams);
        return view;
    }

    @Override
    public ThumbnailMessageView getAttachmentView(AttachmentPresenterOptions presenterOptions) {
        ThumbnailMessageView view = new ThumbnailMessageView(getContext());
        view.setAdjustViewBounds(true);
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(layoutParams);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return view;
    }

    public static int getStaticHeight(Context context) {
        Resources res = context.getResources();
        return res.getDimensionPixelSize(R.dimen.thumbnail_attachment_static_height);
    }
}
