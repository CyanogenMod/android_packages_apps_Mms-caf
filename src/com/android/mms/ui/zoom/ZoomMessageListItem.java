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

    // "Zooming" constants
    private static final int MIN_FONT_SIZE = 14;  //sp
    private static final int MAX_FONT_SIZE = 72;  //sp

    // Members
    private final List<TextView> mZoomableTextViewList = new ArrayList<TextView>();

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
     * Accept the scale to use and handle "zooming" the text to the given scale
     *
     * @param scale {@link java.lang.Float}
     */
    public void handleZoomWithScale(final float scale) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                for (TextView textView : mZoomableTextViewList) {
                    zoomViewByScale(getContext(), textView, scale);
                }
            }
        });
    }

    /**
     * This will "zoom" the text by changing the font size based ont he given scale for the given
     * view
     *
     * @param context {@link android.content.Context}
     * @param view    {@link android.widget.TextView}
     * @param scale   {@link java.lang.Float}
     */
    public static void zoomViewByScale(Context context, TextView view, float scale) {
        if (view == null) {
            Log.w(TAG, "'view' is null!");
            return;
        }
        // getTextSize() returns absolute pixels
        // convert to scaled for proper math flow
        float currentTextSize = pixelsToSp(context, view.getTextSize());
        // Calculate based on the scale (1.1 and 0.95 in this case)
        float calculatedSize = currentTextSize * scale;
        // Limit max and min
        if (calculatedSize > MAX_FONT_SIZE) {
            currentTextSize = MAX_FONT_SIZE;
        } else if (calculatedSize < MIN_FONT_SIZE) {
            currentTextSize = MIN_FONT_SIZE;
        } else {
            // Specify the calculated if we are within the reasonable bounds
            currentTextSize = calculatedSize;
        }
        // Cast to int in order to normalize it
        // setTextSize takes a Scaled Pixel value
        view.setTextSize((int) currentTextSize);

    }

    /**
     * Convert absolute pixels to scaled pixels based on density
     *
     * @param px {@link java.lang.Float}
     *
     * @return {@link java.lang.Float}
     */
    private static float pixelsToSp(Context context, float px) {
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        return px / scaledDensity;
    }
}
