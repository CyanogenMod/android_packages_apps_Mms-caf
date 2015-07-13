package com.android.mms.presenters;

import android.os.Handler;
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

import java.util.concurrent.Future;

public class SimpleAttachmentPresenter extends RecyclePresenter<SimpleViewInterface, SimplePresenterModel> implements OnClickListener {

    private final Handler mHandler;

    public SimpleAttachmentPresenter(Context context, SimplePresenterModel model) {
        super(context, model);
        mHandler = new Handler();
    }

    public static class SimpleAttachmentLoaded {
        public String title, subtitle;
        public Bitmap icon;
        public Drawable drawable;
    }

    @Override
    protected SimpleViewInterface inflateView(PresenterOptions presenterOptions) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final SimpleAttachmentView view = (SimpleAttachmentView) inflater
                .inflate(R.layout.simple_attachment_view, null, false);
        if (presenterOptions.isThumbnail()) {
            view.setBackgroundColor(Color.parseColor("#1a00b0f0"));
        }
        view.setOnClickListener(this);
        return view;
    }

    @Override
    protected void bindView(final SimpleViewInterface view, final PresenterOptions presenterOptions) {
        if (presenterOptions != null) {
            view.setTitleColor(presenterOptions.isIncomingMessage() ? Color.WHITE : Color.BLACK);
            view.setSubTitleColor(presenterOptions.isIncomingMessage() ? Color.WHITE : Color.BLACK);
        }
        if (!presenterOptions.isThumbnail() && presenterOptions.getAccentColor() != -1) {
            LayerDrawable background = (LayerDrawable) view.getBackground();
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
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result.drawable != null) {
                            view.setIconDrawable(result.drawable);
                        } else {
                            view.setIconBitmap(result.icon);
                        }
                        view.setTitle(result.title);
                        view.setSubTitle(result.subtitle);
                        if (presenterOptions.isThumbnail()) {
                            view.setIconTint(Color.parseColor("#1a00b0f0"));
                        }
                    }
                });
            }
        });
    }

    @Override
    public void cancelBackgroundLoading() {
        getModel().cancelBackgroundLoading();
    }

    @Override
    public void onClick(View v) {
        Intent intent = getModel().getIntent();
        getContext().startActivity(intent);
    }
}
