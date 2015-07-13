package com.android.mms.presenters;

import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.Presenter;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ImageMessageView;
import com.android.mms.views.SimpleAttachmentView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.mms.R;
public class SimpleAttachmentPresenter extends Presenter<SimplePresenterModel> implements OnClickListener {

    public SimpleAttachmentPresenter(Context context, SimplePresenterModel model) {
        super(context, model);
    }

    public static class SimpleAttachmentLoaded {
        public String title, subtitle;
        public Bitmap icon;
        public Drawable drawable;
    }

    @Override
    public void present(ViewGroup v, PresenterOptions presenterOptions) {
        final SimpleAttachmentView mThumbnail;
        if (v.getChildAt(0) instanceof SimpleViewInterface) {
            // View was recycled, lets re-use it
            mThumbnail = (SimpleAttachmentView) v.getChildAt(0);
        } else {
            // Cleanup any previous views
            v.removeAllViews();

            LayoutInflater inflater = LayoutInflater.from(getContext());
            final SimpleAttachmentView view = (SimpleAttachmentView) inflater
                    .inflate(R.layout.simple_attachment_view, v, false);
            view.setOnClickListener(this);
            v.addView(view);

            mThumbnail = view;
        }
        if (presenterOptions != null) {
            mThumbnail.setTitleColor(presenterOptions.isIncomingMessage() ? Color.WHITE : Color.BLACK);
            mThumbnail.setSubTitleColor(presenterOptions.isIncomingMessage() ? Color.WHITE : Color.BLACK);
        }
        if (presenterOptions != null && presenterOptions.getAccentColor() != -1) {
            LayerDrawable background = (LayerDrawable) mThumbnail.getBackground();
            Drawable base = background.findDrawableByLayerId(R.id.base_layer);
            if (base instanceof StateListDrawable) {
                StateListDrawable sld = (StateListDrawable) base;
                base = sld.getStateDrawable(sld.getStateDrawableIndex(null));

                // amend selector color
                Drawable selector = sld.getStateDrawable(sld.getStateDrawableIndex(
                        new int[] { android.R.attr.state_selected }));
                selector.setTint(MessageUtils.getDarkerColor(presenterOptions.getAccentColor()));
            }
            if (base != null) {
                base.setTint(presenterOptions.getAccentColor());
            }
        }
        getModel().loadData(new ItemLoadedCallback<SimpleAttachmentLoaded>() {
            @Override
            public void onItemLoaded(final SimpleAttachmentLoaded result, Throwable exception) {
                mThumbnail.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result.drawable != null) {
                            mThumbnail.setIconDrawable(result.drawable);
                        } else {
                            mThumbnail.setIconBitmap(result.icon);
                        }
                        mThumbnail.setTitle(result.title);
                        mThumbnail.setSubTitle(result.subtitle);
                    }
                });
            }
        });
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
        //contentType = getModel().getContentType();
//        intent.setDataAndType(getModel().getUri(), contentType);
        getContext().startActivity(intent);
    }
}
