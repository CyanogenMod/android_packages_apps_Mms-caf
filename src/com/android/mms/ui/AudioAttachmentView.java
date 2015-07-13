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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.model.SlideModel;

/**
 * This class provides an embedded editor/viewer of audio attachment.
 */
public class AudioAttachmentView extends IconAttachmentView {
    private static final String TAG = LogTag.TAG;

    private Uri mAudioUri;
    private MediaPlayer mMediaPlayer;
    private boolean mIsPlaying;

    public AudioAttachmentView(SlideModel slide, Context context) {
        super(slide, context);
    }

    @Override
    public void setIcon() {
        getIcon().setImageResource(R.drawable.ic_audio_attachment_play);
    }

    @Override
    public int getViewMessageCode() {
        return AttachmentEditor.MSG_PLAY_AUDIO;
    }

    private void onPlaybackError() {
        Log.e(TAG, "Error occurred while playing audio.");
        stopAudio();
    }

    private void cleanupMediaPlayer() {
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
                mMediaPlayer.release();
            } finally {
                mMediaPlayer = null;
            }
        }
    }

    synchronized public void startAudio() {
        if (!mIsPlaying && (mAudioUri != null)) {
            mMediaPlayer = MediaPlayer.create(mContext, mAudioUri);
            if (mMediaPlayer != null) {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                    public void onCompletion(MediaPlayer mp) {
                        stopAudio();
                    }
                });
                mMediaPlayer.setOnErrorListener(new OnErrorListener() {
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        onPlaybackError();
                        return true;
                    }
                });

                mIsPlaying = true;
                mMediaPlayer.start();
            }
        }
    }

    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        synchronized (this) {
            mAudioUri = audio;
        }
    }

    synchronized public void stopAudio() {
        try {
            cleanupMediaPlayer();
        } finally {
            mIsPlaying = false;
        }
    }

    public void reset() {
        synchronized (this) {
            if (mIsPlaying) {
                stopAudio();
            }
        }
    }
}
