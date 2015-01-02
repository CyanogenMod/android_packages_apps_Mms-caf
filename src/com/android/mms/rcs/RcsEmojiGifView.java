/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.mms.rcs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import java.io.ByteArrayInputStream;

public class RcsEmojiGifView extends ImageView implements OnClickListener {

    private Movie mGifMovie;

    private long mStartPlayTime;

    private int mGifWidth;

    private int mGifHeight;

    private boolean isGifPlaying;

    private boolean isAutoPlay = true;

    public RcsEmojiGifView(Context context) {
        super(context);
        setOnClickListener(this);
    }

    public RcsEmojiGifView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        setOnClickListener(this);
    }

    public RcsEmojiGifView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOnClickListener(this);
    }

    public void setGifData(byte[] data) {
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        mGifMovie = Movie.decodeStream(is);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        mGifWidth = bitmap.getWidth();
        mGifHeight = bitmap.getHeight();
        bitmap.recycle();
        setMeasuredDimension(mGifWidth, mGifHeight);
    }

    public void setAutoPlay(boolean isAutoPlay) {
        this.isAutoPlay = isAutoPlay;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == getId()) {
            isGifPlaying = true;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mGifMovie == null) {
            super.onDraw(canvas);
        } else {
            if (isAutoPlay) {
                playGifMovie(canvas);
                invalidate();
            } else {
                if (isGifPlaying) {
                    if (playGifMovie(canvas)) {
                        isGifPlaying = false;
                    }
                    invalidate();
                } else {
                    mGifMovie.setTime(0);
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mGifMovie != null) {
            setMeasuredDimension(mGifWidth, mGifHeight);
        }
    }

    private boolean playGifMovie(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        if (mStartPlayTime == 0) {
            mStartPlayTime = now;
        }
        int duration = mGifMovie.duration();
        if (duration == 0) {
            duration = 1000;
        }
        int relTime = (int) ((now - mStartPlayTime) % duration);
        mGifMovie.setTime(relTime);
        mGifMovie.draw(canvas, 0, 0);
        if ((now - mStartPlayTime) >= duration) {
            mStartPlayTime = 0;
            return true;
        }
        return false;
    }

}