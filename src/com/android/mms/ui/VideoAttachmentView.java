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

import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.model.SlideModel;
// TODO: remove dependency for SDK build

/**
 * This class provides an embedded editor/viewer of video attachment.
 */
public class VideoAttachmentView extends AttachmentView {
    private static final String TAG = LogTag.TAG;

    private ImageView mThumbnailView;

    public VideoAttachmentView(SlideModel slide, Context context) {
        super(slide, context);
    }

    @Override
    public void setupView(ViewGroup root) {
        View.inflate(getContext(), R.layout.video_attachment_view, root);
        mThumbnailView = (ImageView) findViewById(R.id.video_thumbnail);
    }

    @Override
    public int getViewMessageCode() {
        return AttachmentEditor.MSG_PLAY_VIDEO;
    }

    public void setVideo(String name, Uri video) {
        try {
            Bitmap bitmap = createVideoThumbnail(mContext, video);
            if (null == bitmap) {
                bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_missing_thumbnail_video);
            }
            setVideoThumbnail(null, bitmap);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setVideo: out of memory: ", e);
        }
    }

    public void setVideoThumbnail(String name, Bitmap thumbnail) {
        RoundedBitmapDrawable bitmapDrawable = RoundedBitmapDrawableFactory
                .create(getResources(), thumbnail);
        bitmapDrawable.setAntiAlias(true);
        bitmapDrawable.setCornerRadius(MmsConfig.getMmsCornerRadius());
        mThumbnailView.setImageDrawable(bitmapDrawable);
    }

    public static Bitmap createVideoThumbnail(Context context, Uri uri) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            bitmap = retriever.getFrameAtTime(-1);
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }
        return bitmap;
    }

}
