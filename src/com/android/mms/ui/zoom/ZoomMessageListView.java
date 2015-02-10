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
import android.view.View;
import android.widget.ListView;
import com.android.mms.ui.MessageListItem;

/**
 * ZoomMessageListView
 * <pre>
 *     Extension to handle a call for resizing its children
 * </pre>
 *
 * @see {@link android.widget.ListView}
 */
public class ZoomMessageListView extends ListView {

    public static final int MIN_FONT_SIZE = 14;  //sp
    public static final int MAX_FONT_SIZE = 72;  //sp.0f;
    private int mCurrentFontSize = MIN_FONT_SIZE;

    public ZoomMessageListView(Context context) {
        super(context);
    }

    public ZoomMessageListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ZoomMessageListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ZoomMessageListView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * This function iterates the existing child views and delegates the zooming work onto the view
     * itself.
     *
     * @param scale {@link java.lang.Float}
     */
    public void handleZoomWithScale(float scale) {
        updateZoomViewByScale(scale);
        int viewCount = getChildCount();
        for (int i = 0; i < viewCount; i++) {
            View view = getChildAt(i);
            if (view instanceof ZoomMessageListItem) {
                ((ZoomMessageListItem) view).setZoomFontSize(mCurrentFontSize);
            }
        }
    }

    /**
     * This will "zoom" the text by changing the font size based ont he given scale for the given
     * view
     *
     * @param scale   {@link java.lang.Float}
     */
    public void updateZoomViewByScale(float scale) {
        // getTextSize() returns absolute pixels
        // convert to scaled for proper math flow
        //float currentTextSize = pixelsToSp(context, view.getTextSize());

        // Calculate based on the scale (1.1 and 0.95 in this case)
        float calculatedSize = mCurrentFontSize * scale;
        // Limit max and min
        if (calculatedSize > MAX_FONT_SIZE) {
            mCurrentFontSize = MAX_FONT_SIZE;
        } else if (calculatedSize < MIN_FONT_SIZE) {
            mCurrentFontSize = MIN_FONT_SIZE;
        } else {
            // Specify the calculated if we are within the reasonable bounds
            mCurrentFontSize = (int)calculatedSize;
        }
    }

    /**
     * Get the currently set font size for the current zoom level.
     * @return The currently set font size.
     */
    public int getZoomFontSize() {
        return mCurrentFontSize;
    }
}
