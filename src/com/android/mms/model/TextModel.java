/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.model;

import java.io.UnsupportedEncodingException;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import com.android.mms.ui.MessageUtils;
import com.android.mms.views.SimpleAttachmentView;
import org.w3c.dom.events.Event;
import org.w3c.dom.smil.ElementTime;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.mms.R;
import com.android.mms.LogTag;
import com.android.mms.dom.smil.SmilMediaElementImpl;
import com.android.mms.ui.Presenter;

import com.google.android.mms.pdu.CharacterSets;

public class TextModel extends RegionMediaModel {
    private static final String TAG = LogTag.TAG;

    private CharSequence mText;
    private final int mCharset;

    public TextModel(Context context, String contentType, String src, RegionModel region) {
        this(context, contentType, src, CharacterSets.UTF_8, new byte[0], region);
    }

    public TextModel(Context context, String contentType, String src,
            int charset, byte[] data, RegionModel region) {
        super(context, SmilHelper.ELEMENT_TAG_TEXT, contentType, src,
                data != null ? data : new byte[0], region);

        if (charset == CharacterSets.ANY_CHARSET) {
            // By default, we use ISO_8859_1 to decode the data
            // which character set wasn't set.
            charset = CharacterSets.ISO_8859_1;
        }
        mCharset = charset;
        mText = extractTextFromData(data);
        mSize = mText.toString().getBytes().length;
    }

    private CharSequence extractTextFromData(byte[] data) {
        if (data != null) {
            try {
                if (CharacterSets.ANY_CHARSET == mCharset) {
                    return new String(data); // system default encoding.
                } else {
                    String name = CharacterSets.getMimeName(mCharset);
                    return new String(data, name);
                }
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Unsupported encoding: " + mCharset, e);
                return new String(data); // system default encoding.
            }
        }
        return "";
    }

    public String getText() {
        if (mText == null) {
            mText = extractTextFromData(getData());
        }

        // If our internal CharSequence is not already a String,
        // re-save it as a String so subsequent calls to getText will
        // be less expensive.
        if (!(mText instanceof String)) {
            mText = mText.toString();
        }

        return mText.toString();
    }

    public void setText(CharSequence text) {
        mText = text;
        mSize = text.toString().getBytes().length;
        notifyModelChanged(true);
    }

    public void cloneText() {
        mText = new String((mText != null ? mText.toString() : ""));
    }

    public int getCharset() {
        return mCharset;
    }

    // EventListener Interface
    public void handleEvent(Event evt) {
        if (evt.getType().equals(SmilMediaElementImpl.SMIL_MEDIA_START_EVENT)) {
            mVisible = true;
        } else if (mFill != ElementTime.FILL_FREEZE) {
            mVisible = false;
        }

        notifyModelChanged(false);
    }

    @Override
    public Presenter getPresenter() {
        //noinspection unchecked
        return new Presenter(mContext, null) {
            @Override
            public void present(ViewGroup v, PresenterOptions presenterOptions) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                TextView textView = (TextView) inflater
                        .inflate(R.layout.text_attachment_view, v, false);
                v.addView(textView);
                textView.setText(getText());
                if (presenterOptions != null) {
                    textView.setTextColor(presenterOptions.isIncomingMessage() ? Color.WHITE : Color.BLACK);
                }
                if (presenterOptions != null && presenterOptions.getAccentColor() != -1) {
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
            }
        };
    }
}
