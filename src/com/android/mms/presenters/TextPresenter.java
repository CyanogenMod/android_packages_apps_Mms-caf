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

public class TextPresenter extends Presenter<TextModel> {

    public TextPresenter(Context context, TextModel modelInterface) {
        super(context, modelInterface);
    }

    @Override
    public View present(ViewGroup v, final PresenterOptions presenterOptions) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final TextView textView = (TextView) inflater
                .inflate(R.layout.text_attachment_view, v, false);
        v.addView(textView);
        textView.setText(getModel().getText());
        textView.setTextColor(presenterOptions.isIncomingMessage() ? Color.WHITE : Color.BLACK);
        MessageUtils.tintBackground(textView, presenterOptions.getAccentColor());
        textView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                textView.getViewTreeObserver().removeOnPreDrawListener(this);
                presenterOptions.donePresenting(textView.getMeasuredHeight());
                return true;
            }
        });
        return textView;
    }
}
