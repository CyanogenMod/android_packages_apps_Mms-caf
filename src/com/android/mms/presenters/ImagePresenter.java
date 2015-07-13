package com.android.mms.presenters;

import com.android.mms.ui.ImageAttachmentView;
import com.android.mms.ui.Presenter;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ImageMessageView;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ImagePresenter extends Presenter<ImagePresenterModel> implements OnClickListener {

    public ImagePresenter(Context context, ImagePresenterModel model) {
        super(context, model);
    }

    @Override
    public void present(ViewGroup v, int accentColor) {
        final ImageViewInterface mThumbnail;
        if (v.getChildAt(0) instanceof ImageViewInterface) {
            // View was recycled, lets re-use it
            mThumbnail = (ImageViewInterface) v.getChildAt(0);
        } else {
            v.removeAllViews();
            final ImageMessageView view = new ImageMessageView(getContext());
            view.setOnClickListener(this);
            v.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400));
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mThumbnail = view;
        }

//        View view = (View) mThumbnail;
//        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
//        layoutParams.width = getModel().getWidth();
//        layoutParams.height = getModel().getHeight();
//        view.setLayoutParams(layoutParams);
        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                if (exception != null) {
                    exception.printStackTrace();
                }
                mThumbnail.setImage(result.mBitmap);
            }
        });
    }

    @Override
    public void presentThumbnail(ViewGroup v) {
        System.out.println("presentThumbnail");
//        final ImageAttachmentView view = new ImageAttachmentView(getContext());
//        v.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
//            @Override
//            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
//                view.set(result.mBitmap);
//            }
//        });
        present(v, -1);
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
