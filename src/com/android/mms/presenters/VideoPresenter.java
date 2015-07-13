package com.android.mms.presenters;

import com.android.mms.ui.Presenter;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ImageMessageView;
import com.android.mms.views.VideoMessageView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

public class VideoPresenter extends Presenter<VideoPresenterModel> implements OnClickListener {

    public VideoPresenter(Context context, VideoPresenterModel model) {
        super(context, model);
    }

    @Override
    public void present(ViewGroup v, PresenterOptions presenterOptions) {
        final VideoViewInterface mThumbnail;
        if (v.getChildAt(0) instanceof VideoViewInterface) {
            // View was recycled, lets re-use it
            mThumbnail = (VideoViewInterface) v.getChildAt(0);
        } else {
            v.removeAllViews();
            v.removeAllViewsInLayout();
            final VideoMessageView view = new VideoMessageView(getContext());
            view.setOnClickListener(this);
            view.setLayoutParams(new ViewGroup.LayoutParams(500, 500));
            v.addView(view);
            view.setScaleType(ImageView.ScaleType.FIT_XY);
            mThumbnail = view;
        }

//        View view = (View) mThumbnail;
//        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
//        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
//        view.setLayoutParams(layoutParams);

        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                mThumbnail.setImage(result.mBitmap);
            }
        });
    }

    @Override
    public boolean showsArrowHead() {
        return false;
    }

    @Override
    public void presentThumbnail(ViewGroup v) {
        present(v, null);
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
