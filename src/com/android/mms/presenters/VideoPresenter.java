package com.android.mms.presenters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.Presenter;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ImageMessageView;
import com.android.mms.views.VideoMessageView;

public class VideoPresenter extends RecyclePresenter<VideoViewInterface, VideoPresenterModel> implements OnClickListener {

    public VideoPresenter(Context context, VideoPresenterModel model) {
        super(context, model);
    }

    @Override
    protected VideoViewInterface inflateView(final PresenterOptions presenterOptions) {
        VideoMessageView view = getView();
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
    protected void bindView(final VideoViewInterface view, final PresenterOptions presenterOptions) {
        if (presenterOptions != null) {
            int color = MessageUtils.getDarkerColor(presenterOptions.getAccentColor());
            color = (color & 0x00FFFFFF) | 0x80000000;
            view.setSelectColor(color);
        }
        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                view.setImage(result.mBitmap);
                presenterOptions.donePresenting(result.mBitmap.getHeight());
            }
        }, presenterOptions.getMaxWidth());
    }

    private VideoMessageView getView() {
        final VideoMessageView view = new VideoMessageView(getContext());
        view.setOnClickListener(this);
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(layoutParams);
        return view;
    }

    @Override
    protected VideoViewInterface inflateAttachmentView(AttachmentPresenterOptions presenterOptions) {
        return getView();
    }

    @Override
    protected void bindAttachmentView(final VideoViewInterface view, final AttachmentPresenterOptions presenterOptions) {
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

    public static int getStaticHeight() {
        return 640;
    }
}
