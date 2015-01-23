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
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.mms.ui.MessageListItem;

import java.util.ArrayList;
import java.util.List;

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

    protected int mZoomFontSize = ZoomMessageListView.MIN_FONT_SIZE;

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
        }


    }

    /**
     * Accept the font size to use and handle "zooming" the text to the given scale
     *
     * @param fontSize {@link java.lang.Integer}
     */
    public void setZoomFontSize(final int fontSize) {
        mZoomFontSize = fontSize;
        handleZoomFontSize();
    }

    /**
     * "Zoom" the font size to mZoomFontSize, if a handler is attached to this view.
     */
    private void handleZoomFontSize() {
        Handler handler = getHandler();
        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    for (TextView textView : mZoomableTextViewList) {
                        textView.setTextSize(mZoomFontSize);
                    }
                }
            });
        }
    }
}
