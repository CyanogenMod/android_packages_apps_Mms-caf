package com.android.mms.presenters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.Presenter;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ImageMessageView;

public class ImagePresenter extends RecyclePresenter<ImageViewInterface, ImagePresenterModel> implements OnClickListener {

    public ImagePresenter(Context context, ImagePresenterModel model) {
        super(context, model);
    }

    @Override
    protected ImageViewInterface inflateView(PresenterOptions presenterOptions) {
        ImageMessageView view = new ImageMessageView(getContext());
        view.setBackgroundColor(Color.WHITE);
        view.setElevation(5);
        view.setOnClickListener(this);
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 600);
        view.setLayoutParams(layoutParams);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return view;
    }

    @Override
    protected void bindView(final ImageViewInterface view, PresenterOptions presenterOptions) {
        if (presenterOptions != null) {
            view.setSelectColor(MessageUtils.getDarkerColor(presenterOptions.getAccentColor()));
        }
        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                view.setImage(result.mBitmap);
            }
        });
    }

    @Override
    public boolean showsArrowHead() {
        return false;
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
}
