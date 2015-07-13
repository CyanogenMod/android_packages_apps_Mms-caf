package com.android.mms.presenters;

import android.graphics.Color;
import android.os.Handler;

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
import android.view.ViewGroup;

import com.android.mms.R;

public class SimpleAttachmentPresenter extends RecyclePresenter<SimpleAttachmentView, SimplePresenterModel>
        implements OnClickListener {

    private final Handler mHandler;
    private final int mTitleColor, mSubTitleColor;

    public SimpleAttachmentPresenter(Context context, SimplePresenterModel model) {
        super(context, model);
        mHandler = new Handler(Looper.getMainLooper());
        mTitleColor = context.getResources().getColor(R.color.simple_attachment_title_color);
        mSubTitleColor = context.getResources().getColor(R.color.simple_attachment_subtitle_color);
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
    protected SimpleAttachmentView inflateView(final PresenterOptions presenterOptions) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final SimpleAttachmentView view = (SimpleAttachmentView) inflater
                .inflate(R.layout.simple_attachment_view, null);
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
    protected SimpleAttachmentView inflateAttachmentView(AttachmentPresenterOptions presenterOptions) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final SimpleAttachmentView view = (SimpleAttachmentView) inflater
                .inflate(R.layout.simple_pending_attachment_view, null);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                presenterOptions.getAttachmentWidth(),
                presenterOptions.getAttachmentHeight());
        view.setLayoutParams(layoutParams);
        view.setOnClickListener(this);
        return view;
    }

    @Override
    protected void bindView(final SimpleAttachmentView viewInterface, final PresenterOptions presenterOptions) {
        boolean isIncomingMsg = presenterOptions.isIncomingMessage();
        viewInterface.setTitleColor(isIncomingMsg ? Color.WHITE : mTitleColor);
        viewInterface.setSubTitleColor(isIncomingMsg ? Color.WHITE : mSubTitleColor);

        Drawable placeHolder = getModel().getPlaceHolder();
        if (placeHolder != null) {
            placeHolder = placeHolder.mutate();
            placeHolder.setTint(isIncomingMsg ? Color.WHITE : mTitleColor);
            viewInterface.setIconDrawable(placeHolder);
        }

        MessageUtils.tintBackground(viewInterface, presenterOptions.getAccentColor());

        getModel().loadData(new ItemLoadedCallback<SimpleAttachmentLoaded>() {
            @Override
            public void onItemLoaded(final SimpleAttachmentLoaded result, Throwable exception) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result.drawable != null) {
                            viewInterface.setIconDrawable(result.drawable);
                        } else if (result.icon != null) {
                            viewInterface.setIconBitmap(result.icon);
                        }
                        viewInterface.setTitle(result.title);
                        viewInterface.setSubTitle(result.subtitle);
                        presenterOptions.donePresenting(getStaticHeight(getContext()));
                        viewInterface.requestLayout();
                    }
                });
            }
        });
    }

    @Override
    protected void bindAttachmentView(final SimpleAttachmentView view,
                                      AttachmentPresenterOptions presenterOptions) {
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
