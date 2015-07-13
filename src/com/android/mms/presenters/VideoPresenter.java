package com.android.mms.presenters;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.mms.R;
import com.android.mms.ui.Presenter;
import com.android.mms.views.ThumbnailMessageView;
import com.android.mms.views.VideoMessageView;

public class VideoPresenter extends ThumbnailPresenter<VideoMessageView, ThumbnailPresenterModel> implements OnClickListener {

    public VideoPresenter(Context context, ThumbnailPresenterModel model) {
        super(context, model);
    }

    @Override
    public VideoMessageView inflateThumbnail(PresenterOptions presenterOptions) {
        return new VideoMessageView(getContext());
    }

    @Override
    public VideoMessageView inflateAttachmentThumbnail(AttachmentPresenterOptions presenterOptions) {
        return inflateThumbnail(null);
    }

    @Override
    protected Class<VideoMessageView> getViewClass() {
        return VideoMessageView.class;
    }

    public static int getStaticHeight(Context context) {
        Resources res = context.getResources();
        return res.getDimensionPixelSize(R.dimen.thumbnail_attachment_static_height);
    }
}
