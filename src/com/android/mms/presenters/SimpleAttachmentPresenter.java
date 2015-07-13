package com.android.mms.presenters;

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
        public String label1, label2;
        public Bitmap icon;
        public Drawable drawable;
    }

    @Override
    public void present(ViewGroup v, int accentColor) {
        final SimpleAttachmentView mThumbnail;
        if (v.getChildAt(0) instanceof SimpleViewInterface) {
            // View was recycled, lets re-use it
            mThumbnail = (SimpleAttachmentView) v.getChildAt(0);
        } else {
            v.removeAllViews();
            final SimpleAttachmentView view = (SimpleAttachmentView) LayoutInflater.from(getContext()).inflate(R.layout.simple_attachment_view, v, false);
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            view.setOnClickListener(this);
            if (accentColor == -1) {
                view.setBackgroundColor(Color.parseColor("#1a00b0f0"));
            }
            v.addView(view);
            mThumbnail = view;
        }
        if (accentColor != -1) {
            LayerDrawable background = (LayerDrawable) mThumbnail.getBackground();
            Drawable base = background.findDrawableByLayerId(R.id.base_layer);
            if (base instanceof StateListDrawable) {
                StateListDrawable sld = (StateListDrawable) base;
                base = sld.getStateDrawable(sld.getStateDrawableIndex(null));

                // amend selector color
                Drawable selector = sld.getStateDrawable(sld.getStateDrawableIndex(
                        new int[] { android.R.attr.state_selected }));
                selector.setTint(darkerColor(accentColor));
            }
            if (base != null) {
                base.setTint(accentColor);
            }
        }
        getModel().loadData(new ItemLoadedCallback<SimpleAttachmentLoaded>() {
            @Override
            public void onItemLoaded(SimpleAttachmentLoaded result, Throwable exception) {
                if (result.drawable != null) {
                    mThumbnail.setImage(result.drawable);
                } else {
                    mThumbnail.setImage(result.icon);
                }
                mThumbnail.setLabel1(result.label1);
                mThumbnail.setLabel2(result.label2);
            }
        });
    }
    private int darkerColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] =  Math.max(0f, hsv[2] - hsv[2] * 0.5f);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }
    @Override
    public void presentThumbnail(ViewGroup v) {
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
//        intent.setDataAndType(getModel().getUri(), contentType);
        getContext().startActivity(intent);
    }
}
