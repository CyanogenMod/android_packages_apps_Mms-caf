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

package com.android.mms.ui;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.data.SlideViewInterfaceFactory;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;

/**
 * This is an embedded editor/view to add photos and sound/video clips
 * into a multimedia message.
 */
public class AttachmentEditor extends LinearLayout {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;
    static final int MSG_EDIT_SLIDESHOW   = 1;
    static final int MSG_SEND_SLIDESHOW   = 2;
    static final int MSG_PLAY_SLIDESHOW   = 3;
    static final int MSG_REPLACE_IMAGE    = 4;
    static final int MSG_REPLACE_VIDEO    = 5;
    static final int MSG_REPLACE_AUDIO    = 6;
    static final int MSG_PLAY_VIDEO       = 7;
    static final int MSG_PLAY_AUDIO       = 8;
    static final int MSG_VIEW_IMAGE       = 9;
    static final int MSG_REMOVE_ATTACHMENT = 10;
    static final int MSG_VIEW_VCARD        = 11;
    static final int MSG_REPLACE_VCARD     = 12;
    static final int MSG_VIEW_VCAL        = 13;
    static final int MSG_REPLACE_VCAL     = 14;

    private static final int KILOBYTE = 1024;

    private final Context mContext;
    private Handler mHandler;

    private Presenter mPresenter;
    private boolean mCanSend;
    private Button mSendButton;
    private int mMediaSize = 0;

    public AttachmentEditor(Context context, AttributeSet attr) {
        super(context, attr);
        mContext = context;
        mPresenter = PresenterFactory.getPresenter(
                "MmsThumbnailPresenter", mContext);
    }

    public boolean remove(WorkingMessage msg, SlideModel model) {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view.getTag() == model) {
                removeView(view);
                break;
            }
        }
        return getChildCount() > 0;
    }

    public void add(SlideModel model) {
        SlideViewInterface v = createMediaView(model);
        mPresenter.present(v, model);
    }

    public void removeAll() {
        removeAllViews();
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    public void setCanSend(boolean enable) {
        if (mCanSend != enable) {
            mCanSend = enable;
            updateSendButton();
        }
    }

    private void updateSendButton() {
        if (null != mSendButton) {
            mSendButton.setEnabled(mCanSend);
            mSendButton.setFocusable(mCanSend);
        }
    }

    private SlideViewInterface createMediaView(SlideModel slide) {
        AttachmentView view = SlideViewInterfaceFactory.getViewForSlide(slide, mContext);
        view.setHandler(mHandler);
        view.setTag(slide);
        addView(view);
        return view;
    }

    public void hideSlideshowSendButton() {
        if (mSendButton != null) {
            mSendButton.setVisibility(View.GONE);
        }
    }

    public boolean canAddTextForMms(CharSequence s, SlideshowModel mSlideshow) {
        int totalSize = 0;
        int textSize = s.toString().getBytes().length;
        int remainSize = MmsConfig.getMaxMessageSize();
        if (mMediaSize != 0) {
            // The AttachmentEditor only can edit text if there only one silde.
            // Here mSlideshow already includes text size, need to recalculate the totalsize.
            int totalTextSize = mSlideshow.getTotalTextMessageSize();
            remainSize = mSlideshow.getRemainMessageSize();
            if (DEBUG) {
                Log.v(TAG,"onTextChangeForMms totalTextSize = "+totalTextSize);
            }
            if (textSize != 0 && mSlideshow.size() == 1) {
                remainSize = remainSize + totalTextSize - textSize;
            } else {
                remainSize = remainSize - textSize;
            }
            remainSize = remainSize -  mSlideshow.getSubjectSize();
        }

        if (DEBUG) {
            Log.v(TAG,"onTextChangeForMms textSize = " + textSize
                    + ", mMediaSize = " + mMediaSize
                    + ", totalSize = " + totalSize
                    + ", remainSize = "  + remainSize
                    );
        }
        return remainSize >= 0;
    }

    private int getSizeWithOverHead(int size) {
        return (size + KILOBYTE -1) / KILOBYTE + 1;
    }
}
