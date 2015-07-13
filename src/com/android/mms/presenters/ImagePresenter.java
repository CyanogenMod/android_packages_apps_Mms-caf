package com.android.mms.presenters;

import com.android.mms.MmsConfig;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.Presenter;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ImageMessageView;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

public class ImagePresenter extends RecyclePresenter<ImageViewInterface, ImagePresenterModel> implements OnClickListener {

    public ImagePresenter(Context context, ImagePresenterModel model) {
        super(context, model);
    }

    @Override
    protected ImageViewInterface inflateView(final PresenterOptions presenterOptions) {
        ImageMessageView view = getView();
        view.setElevation(5);
        if (presenterOptions != null) {
            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    // TODO Leaked reference ?
                    presenterOptions.onItemLongClick();
                    return true;
                }
            });
        }
        return view;
    }

    @Override
    protected void bindView(final ImageViewInterface viewInterface, final PresenterOptions presenterOptions) {
        if (presenterOptions != null) {
            int color = MessageUtils.getDarkerColor(presenterOptions.getAccentColor());
            color = (color & 0x00FFFFFF) | 0x80000000;
            viewInterface.setSelectColor(color);
        }
        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                viewInterface.setImage(result.mBitmap);
                presenterOptions.donePresenting(result.mBitmap.getHeight());
            }
        }, presenterOptions.getMaxWidth());
    }

    private ImageMessageView getView() {
        ImageMessageView view = new ImageMessageView(getContext());
        view.setOnClickListener(this);
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(layoutParams);
        return view;
    }

    @Override
    protected ImageViewInterface inflateAttachmentView(AttachmentPresenterOptions presenterOptions) {
        return getView();
    }

    @Override
    protected void bindAttachmentView(final ImageViewInterface view, AttachmentPresenterOptions presenterOptions) {
        view.setSelectColor(0);
        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                view.setImage(result.mBitmap);
            }
        }, ThumbnailManager.THUMBNAIL_SIZE);
    }

    @Override
    public boolean hideArrowHead() {
        return true;
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra("SingleItemOnly", true); // So we don't see "surrounding" images in Gallery

        String contentType;
        contentType = getModel().getContentType();
        intent.setDataAndType(getModel().getUri(), contentType);
        getContext().startActivity(intent);
    }

    @Override
    public void cancelBackgroundLoading() {
        getModel().cancelThumbnailLoading();
    }

    public static int getStaticHeight() {
        return 640;
    }
}
