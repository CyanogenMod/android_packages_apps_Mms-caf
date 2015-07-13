package com.android.mms.presenters;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.VideoMessageView;

public class VideoPresenter extends RecyclePresenter<VideoViewInterface, VideoPresenterModel> implements OnClickListener {

    public VideoPresenter(Context context, VideoPresenterModel model) {
        super(context, model);
    }

    @Override
    protected VideoViewInterface inflateView(PresenterOptions presenterOptions) {
        final VideoMessageView view = new VideoMessageView(getContext());
        view.setOnClickListener(this);
        view.setLayoutParams(new ViewGroup.LayoutParams(500, 500));
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        return view;
    }

    @Override
    protected void bindView(final VideoViewInterface view, PresenterOptions presenterOptions) {
        if (presenterOptions != null) {
            int color = MessageUtils.getDarkerColor(presenterOptions.getAccentColor());
            color = (color & 0x00FFFFFF) | 0x80000000;
            view.setSelectColor(color);
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
}
