package com.android.mms.presenters;

import android.content.Context;
import android.graphics.Color;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;
import com.android.mms.model.TextModel;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.Presenter;

import com.android.mms.R;
import com.android.mms.views.ThumbnailMessageView;

import java.lang.ref.WeakReference;

public class TextPresenter extends RecyclePresenter<TextView, TextModel> implements OnClickListener {

    private ViewTreeObserver.OnPreDrawListener mObserver;

    private final int mIncomingWhite;
    private final int mIncomingBlack;
    private Context mContext;

    public TextPresenter(Context context, TextModel modelInterface) {
        super(context, modelInterface);
        mContext = context;
        mIncomingWhite = context.getResources().getColor(R.color.incoming_color_white);
        mIncomingBlack = context.getResources().getColor(R.color.incoming_color_black);
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
    protected void bindMessageAttachmentView(final TextView textView, final PresenterOptions presenterOptions) {
        textView.setText(getModel().getText());

        textView.setTextColor(
                presenterOptions.isIncomingMessage() ? mIncomingWhite : mIncomingBlack);

        if (presenterOptions.isIncomingMessage()) {
            textView.setLinkTextColor(mIncomingWhite);
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


        textView.setClickable(true);
        textView.setOnClickListener(this);


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

    @Override
    public void onClick(View v) {
        TextView textView = (TextView)v;

        // Check for links. If none, do nothing; if 1, open it; if >1, ask user to pick one
        URLSpan[] spans = textView.getUrls();
        if (spans.length != 0) {
            MessageUtils.onMessageContentClick(mContext, textView);
        }
    }
}
