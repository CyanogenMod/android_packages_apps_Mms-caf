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
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.model.MediaModel;

import com.android.mms.model.SlideshowModel;

/**
 * This is an embedded editor/view to add photos and sound/video clips
 * into a multimedia message.
 */
public class AttachmentEditor extends LinearLayout {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;

    static final int MSG_REMOVE_ATTACHMENT = 1;
    private static final int KILOBYTE = 1024;
    private final Presenter.AttachmentPresenterOptions mPresenterOptions;

    private Handler mHandler;

    public AttachmentEditor(Context context, AttributeSet attr) {
        super(context, attr);
        final int itemHeight = context.getResources()
                .getDimensionPixelSize(R.dimen.attachment_editor_item_height);
        mPresenterOptions = new Presenter.AttachmentPresenterOptions() {
            @Override
            public int getAttachmentWidth() {
                return itemHeight;
            }
            @Override
            public int getAttachmentHeight() {
                return itemHeight;
            }
        };
    }

    public boolean removeAttachment(MediaModel model) {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view.getTag() == model) {
                removeView(view);
                break;
            }
        }
        return getChildCount() > 0;
    }

    public void addAttachment(MediaModel model) {
        View v = createMediaView(model);
        Presenter p = model.getPresenter();
        if (p != null) {
            ViewGroup thumbnailRoot = (ViewGroup) v.findViewById(R.id.thumbnail_root);
            p.presentThumbnail(thumbnailRoot, mPresenterOptions);
        }
    }

    public void removeAllAttachments() {
        removeAllViews();
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    @SuppressWarnings("ConstantConditions")
    private View createMediaView(final MediaModel slide) {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View view = layoutInflater.inflate(R.layout.attachment_view, this, false);
        View remove = view.findViewById(R.id.remove_attachment);
        remove.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHandler != null) {
                    Message msg = mHandler.obtainMessage(AttachmentEditor.MSG_REMOVE_ATTACHMENT);
                    msg.obj = slide;
                    msg.sendToTarget();
                }
            }
        });
        addView(view);
        view.setTag(slide);
        return view;
    }

    public boolean canAddTextForMms(CharSequence s, SlideshowModel mSlideshow) {
        int totalSize = 0;
        int textSize = s.toString().getBytes().length;
        int remainSize = MmsConfig.getMaxMessageSize();
        int mediaSize = mSlideshow.getTotalMessageSize();
        if (mediaSize != 0) {
            // The AttachmentEditor only can edit text if there only one silde.
            // Here mSlideshow already includes text size, need to recalculate the totalsize.
            int totalTextSize = mSlideshow.getTotalTextMessageSize();
            remainSize = mSlideshow.getRemainMessageSize();
            if (DEBUG) {
                Log.v(TAG,"onTextChangeForMms totalTextSize = "+totalTextSize);
            }
            if (textSize != 0 && mSlideshow.size() == 1) {
                totalSize = mediaSize - totalTextSize + textSize;
                remainSize = remainSize + totalTextSize - textSize;
            } else {
                totalSize = mediaSize + textSize;
                remainSize = remainSize - textSize;
            }
            remainSize = remainSize -  mSlideshow.getSubjectSize();
        }

        if (DEBUG) {
            Log.v(TAG,"onTextChangeForMms textSize = " + textSize
                    + ", mediaSize = " + mediaSize
                    + ", totalSize = " + totalSize
                    + ", remainSize = "  + remainSize
                    );
        }

        if (DEBUG) {
            int currentSize = getSizeWithOverHead(totalSize + mSlideshow.getSubjectSize());
            if (remainSize < 0) {
                currentSize = MmsConfig.getMaxMessageSize() / KILOBYTE;
            }
            Log.v(TAG,"onTextChangeForMms currentSize = " + currentSize
                    + ", totalSize = " + totalSize
                    + ", subject size is "+mSlideshow.getSubjectSize());
        }
        return remainSize >= 0;
    }

    private int getSizeWithOverHead(int size) {
        return (size + KILOBYTE -1) / KILOBYTE + 1;
    }
}
