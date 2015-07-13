package com.android.mms.presenters;

import android.graphics.Color;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.view.ViewGroup;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.views.SimpleAttachmentView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.mms.R;

public class SimpleAttachmentPresenter extends RecyclePresenter<SimpleViewInterface, SimplePresenterModel>
        implements OnClickListener {

    private final Handler mHandler;

    public SimpleAttachmentPresenter(Context context, SimplePresenterModel model) {
        super(context, model);
        mHandler = new Handler(Looper.getMainLooper());
    }

    public static int getStaticHeight(Context context) {
        return context.getResources().getDimensionPixelSize(
                R.dimen.simple_attachment_height);
    }

    public static class SimpleAttachmentLoaded {
        public String title, subtitle;
        public Bitmap icon;
        public Drawable drawable;
    }

    @Override
    protected SimpleViewInterface inflateView(final PresenterOptions presenterOptions) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final SimpleAttachmentView view = (SimpleAttachmentView) inflater
                .inflate(R.layout.simple_attachment_view, null, false);
        view.setOnClickListener(this);
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
    protected SimpleViewInterface inflateAttachmentView(ViewGroup parent, AttachmentPresenterOptions presenterOptions) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final SimpleAttachmentView view = (SimpleAttachmentView) inflater
                .inflate(R.layout.simple_pending_attachment_view, parent, false);
        view.setOnClickListener(this);
        return view;
    }

    @Override
    protected void bindView(final SimpleViewInterface view, final PresenterOptions presenterOptions) {
        boolean isIncomingMsg = presenterOptions.isIncomingMessage();
        view.setTitleColor(isIncomingMsg ? Color.WHITE : Color.parseColor("#5f6060"));
        view.setSubTitleColor(isIncomingMsg ? Color.WHITE : Color.parseColor("#707070"));

        Drawable placeHolder = getModel().getPlaceHolder();
        if (placeHolder != null) {
            placeHolder = placeHolder.mutate();
            placeHolder.setTint(isIncomingMsg ? Color.WHITE : Color.parseColor("#5f6060"));
            view.setIconDrawable(placeHolder);
        }

        if (presenterOptions.getAccentColor() != -1) {
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
                        } else if (result.icon != null) {
                            view.setIconBitmap(result.icon);
                        }
                        view.setTitle(result.title);
                        view.setSubTitle(result.subtitle);
                        presenterOptions.donePresenting(getStaticHeight(getContext()));
                    }
                });
            }
        });
    }

    @Override
    protected void bindAttachmentView(final SimpleViewInterface view, AttachmentPresenterOptions presenterOptions) {
        Drawable placeHolder = getModel().getPlaceHolder();
        view.setIconDrawable(placeHolder);
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
