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

public class TextPresenter extends Presenter<TextModel> {

    public TextPresenter(Context context, TextModel modelInterface) {
        super(context, modelInterface);
    }

    @Override
    public View present(ViewGroup v, final PresenterOptions presenterOptions) {
        TextView textView = presenterOptions.getCachedView(TextView.class);
        if (textView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            textView = (TextView) inflater
                    .inflate(R.layout.text_attachment_view, v, false);
        } else {
            System.out.println("WOOT WE RECYCLED SOME TEXT !");
        }

        v.addView(textView);
        textView.setText(getModel().getText());
        textView.setTextColor(presenterOptions.isIncomingMessage() ? Color.WHITE : Color.BLACK);
        MessageUtils.tintBackground(textView, presenterOptions.getAccentColor());
        final TextView finalTextView = textView;
        textView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                finalTextView.getViewTreeObserver().removeOnPreDrawListener(this);
                presenterOptions.donePresenting(finalTextView.getMeasuredHeight());
                return true;
            }
        });
        return textView;
    }
}
