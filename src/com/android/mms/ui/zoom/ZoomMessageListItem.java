/*
* Copyright (C) 2014 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.mms.ui.zoom;

import android.content.Context;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ZoomMessageListItem
 * <pre>
 *     Zoom logic bits
 * </pre>
 *
 * @see {@link MessageListItem}
 */
public class ZoomMessageListItem extends LinearLayout {

    // Log tag
    private static final String TAG = ZoomMessageListItem.class.getSimpleName();

    // Members
    private final List<TextView> mZoomableTextViewList = new ArrayList<TextView>();
    private final Map<TextView, Float> mOriginalTextSizes = new HashMap<>();

    /**
     * Constructor
     *
     * @param context {@link android.content.Context}
     */
    public ZoomMessageListItem(Context context) {
        super(context);
    }

    /**
     * Constructor
     *
     * @param context {@link android.content.Context}
     * @param attrs   {@link android.util.AttributeSet}
     */
    public ZoomMessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Add a text view to be zoomed
     *
     * @param textView {@link android.widget.TextView}
     */
    public void addZoomableTextView(TextView textView) {
        if (textView == null) {
            return;
        }
        if (!mZoomableTextViewList.contains(textView)) {
            mZoomableTextViewList.add(textView);
            mOriginalTextSizes.put(textView, textView.getTextSize());
        }
    }

    /**
     * Accept the font size to use and handle "zooming" the text to the given scale
     *
     * @param fontSize {@link java.lang.Integer}
     */
    public void setZoomScale(final float scale) {
        Handler handler = getHandler();
        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    for (TextView textView : mZoomableTextViewList) {
                        float origTextSize = mOriginalTextSizes.get(textView);
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, origTextSize * scale);
                    }
                }
            });
        }
    }
}
