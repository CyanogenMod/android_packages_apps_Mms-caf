package com.android.mms.presenters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
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
        if (presenterOptions.getAccentColor() != -1) {
            LayerDrawable background = (LayerDrawable) textView.getBackground();
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
