package com.android.mms.presenters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import com.android.mms.model.TextModel;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.Presenter;

import com.android.mms.R;
import com.android.mms.views.ThumbnailMessageView;

import java.lang.ref.WeakReference;

public class TextPresenter extends RecyclePresenter<TextView, TextModel> {

    private ViewTreeObserver.OnPreDrawListener mObserver;

    public TextPresenter(Context context, TextModel modelInterface) {
        super(context, modelInterface);
    }

    @Override
    protected Class<TextView> getViewClass() {
        return TextView.class;
    }

    @Override
    public int getMessageAttachmentLayoutId() {
        return R.layout.text_attachment_view;
    }

    @Override
    protected void bindMessageAttachmentView(TextView textView, final PresenterOptions presenterOptions) {
        textView.setText(getModel().getText());
        textView.setTextColor(presenterOptions.isIncomingMessage() ? Color.WHITE : Color.BLACK);

        if (presenterOptions.isIncomingMessage()) {
            textView.setLinkTextColor(Color.WHITE);
        }

        MessageUtils.tintBackground(textView, presenterOptions.getAccentColor());
        final TextView finalTextView = textView;
        mObserver = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                finalTextView.getViewTreeObserver().removeOnPreDrawListener(this);
                presenterOptions.donePresenting(finalTextView.getMeasuredHeight());
                return true;
            }
        };
        textView.getViewTreeObserver().addOnPreDrawListener(mObserver);
    }

    @Override
    public int getPreviewAttachmentLayoutId() {
        throw new UnsupportedOperationException("TextPresenter not valid for preview attachments");
    }

    @Override
    protected void bindPreviewAttachmentView(TextView view, AttachmentPresenterOptions presenterOptions) {
        throw new UnsupportedOperationException("TextPresenter not valid for preview attachments");
    }

    @Override
    public void unbindView(TextView textView) {
        if (mObserver != null) {
            textView.getViewTreeObserver().removeOnPreDrawListener(mObserver);
            mObserver = null;
        }
    }
}
