package com.android.mms.presenters;

import android.content.res.Resources;
import android.widget.ImageView;

import com.android.mms.R;
import com.android.mms.ui.MessageUtils;
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
        view.setScaleType(presenterOptions.isIncomingMessage() ? ImageView.ScaleType.FIT_START :
                ImageView.ScaleType.FIT_END);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // TODO Leaked reference ?
                presenterOptions.onItemLongClick();
                return true;
            }
        });
        return view;
    }

    @Override
    protected void bindView(final ImageViewInterface viewInterface, final PresenterOptions presenterOptions) {
        if (presenterOptions != null) {
            int color = MessageUtils.getDarkerColor(presenterOptions.getAccentColor());
            color = MessageUtils.getColorAtAlpha(color, 36);
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
        return view;
    }

    @Override
    protected ImageViewInterface inflateAttachmentView(AttachmentPresenterOptions presenterOptions) {
        ImageMessageView view = getView();
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                presenterOptions.getAttachmentWidth(), presenterOptions.getAttachmentHeight());
        view.setLayoutParams(layoutParams);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return view;
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

    public static int getStaticHeight(Context context) {
        Resources res = context.getResources();
        return res.getDimensionPixelSize(R.dimen.thumbnail_attachment_static_height);
    }
}
