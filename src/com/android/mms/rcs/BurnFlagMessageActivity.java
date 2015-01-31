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

import com.suntek.mway.rcs.client.api.ClientInterfaceIntents;
import com.suntek.mway.rcs.client.api.constant.BroadcastConstants;
import com.suntek.mway.rcs.client.api.exception.OperatorException;
import com.suntek.mway.rcs.client.api.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.api.provider.model.ChatMessage;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.util.log.LogHelper;

import com.android.mms.R;
import com.android.mms.RcsApiManager;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.HashMap;
import android.util.Log;

public class BurnFlagMessageActivity extends Activity {

    public static final String ACTION_REGISTER_STATUS_CHANGED = "com.suntek.mway.rcs.ACTION_REGISTER_STATUS_CHANGED";

    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";

    public static final String MESSAGE_STATE_CHANGED="com.suntek.mway.rcs.ACTION_UI_MESSAGE_STATUS_CHANGE_NOTIFY";

    public static String ACTION_SIM_STATE_CHANGED ="android.intent.action.SIM_STATE_CHANGED";

    private static final int BURN_TIME_REFRESH = 1;

    private static final int AUDIO_TIME_REFRESH = 2;

    private static final int VIDEO_TIME_REFRESH = 3;

    public static final String VIDEO_HEAD = "[video]";

    public static final String SPLIT = "-";

    private ImageView mImage;

    private VideoView mVideo;

    private TextView mAudio;

    private TextView mText;

    private TextView mTime;

    private TextView mVideoLen;

    private TextView mProgressText;

    private ImageView mAudioIcon;

    private long mTempType;

    private MediaPlayer mMediaPlayer;

    private int mMsgType;

    private String mFilePath;

    private int mLen = 0;

    private long mLastProgress = 0;

    private ChatMessage mMsg;

    private TelephonyManager mTelManager;

    private long mSmsId;

    private BroadcastReceiver simStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == ACTION_SIM_STATE_CHANGED) {
                if (TelephonyManager.SIM_STATE_ABSENT == mTelManager.getSimState()) {
                    burnMessage(mSmsId);
                }
            }
        }
    };

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            String messageId = intent
                    .getStringExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_MESSAGE_ID);
            long sessionId = intent.getLongExtra(
                    BroadcastConstants.BC_VAR_TRANSFER_PRG_SESSION_ID, -1);
            long start = intent.getLongExtra(
                    BroadcastConstants.BC_VAR_TRANSFER_PRG_START, -1);
            long end = intent.getLongExtra(
                    BroadcastConstants.BC_VAR_TRANSFER_PRG_END, -1);
            long total = intent.getLongExtra(
                    BroadcastConstants.BC_VAR_TRANSFER_PRG_TOTAL, -1);
            String notifyMessageId = intent
                    .getStringExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_MESSAGE_ID);
            LogHelper.trace("messageId =" + messageId + ",sessionId ="
                    + sessionId + "start =" + start + ";end =" + end
                    + ";total =" + total);
            if (notifyMessageId != null && notifyMessageId.equals(messageId)
                    && start == end) {
                LogHelper.trace("loadImage");
                mProgressText.setVisibility(View.GONE);
                if (mMsgType == SuntekMessageData.MSG_TYPE_IMAGE) {
                    loadImage();
                } else if (mMsgType == SuntekMessageData.MSG_TYPE_VIDEO) {
                    loadVideo();
                }
            }
            if (notifyMessageId != null && notifyMessageId.equals(messageId)
                    && total != 0) {
                long temp = end * 100 / total;
                LogHelper.trace("file tranfer progress = " + temp
                        + "% ; lastprogress = " + mLastProgress + "% .");
                if (mLastProgress == 0 || temp - mLastProgress >= 5) {
                    mLastProgress = temp;
                    mProgressText.setText(String
                            .format(getString(R.string.image_downloading),
                                    mLastProgress));
                }
            }

            String action = intent.getAction();
            if (action.equals(ACTION_REGISTER_STATUS_CHANGED)) {
                int status = intent.getIntExtra("status", -1);
                if (ClientInterfaceIntents.REGISTER_FAILED == status) {
                    finish();
                }
            } else if (action.equals(MESSAGE_STATE_CHANGED)) {
                int status = intent.getIntExtra("status", -11);
                if (SuntekMessageData.MSG_BURN_HAS_BEEN_BURNED == status
                        && mMsg.getSendReceive() == SuntekMessageData.MSG_SEND) {
                    Toast.makeText(getBaseContext(), R.string.message_is_burnd, Toast.LENGTH_SHORT)
                            .show();
                    finish();
                }
            } else if (ALARM_ALERT_ACTION.equals(intent.getAction())) {
                finish();
            }
        }

    };

    public static void start(Context context, String messageId) {
        Intent intent = new Intent(context, BurnFlagMessageActivity.class);
        intent.putExtra("smsId", messageId);
        context.startActivity(intent);
    }

    private Runnable refresh = new Runnable() {

        @Override
        public void run() {
            mTempType = mTempType - 1000;
            mTime.setText(mTempType / 1000 + "");
            if (mTempType != 0) {
                handler.postDelayed(this, 1000);
            } else {
                Toast.makeText(getBaseContext(), R.string.message_is_burnd,
                        Toast.LENGTH_SHORT).show();
                finish();
            }

        }
    };

    private Runnable refreshAudio = new Runnable() {

        @Override
        public void run() {
            mLen = mLen - 1000;
            mAudio.setText(getString(R.string.audio_length) + mLen / 1000 + "\'");

            if (mLen != 0) {
                handler.postDelayed(this, 1000);
            } else {
                Toast.makeText(getBaseContext(), R.string.message_is_play_over,
                        Toast.LENGTH_SHORT).show();
                mAudio.setVisibility(View.GONE);
                finish();
            }

        }
    };

    private Runnable refreshvideo = new Runnable() {

        @Override
        public void run() {
            mLen = mLen - 1000;
            mVideoLen.setText(getString(R.string.video_length) + mLen / 1000
                    + "\'");
            if (mLen != 0) {
                handler.postDelayed(this, 1000);
            } else {
                Toast.makeText(getBaseContext(), R.string.message_is_play_over,
                        Toast.LENGTH_SHORT).show();
                finish();
            }

        }
    };

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case BURN_TIME_REFRESH:
                    mTime.setText(mTempType / 1000 + "");
                    handler.postDelayed(refresh, 1000);
                    break;
                case AUDIO_TIME_REFRESH:
                    mLen = mLen * 1000;
                    mAudio.setText(getString(R.string.audio_length) + mLen / 1000
                            + "\"");
                    handler.postDelayed(refreshAudio, 1000);
                    break;
                case VIDEO_TIME_REFRESH:
                    mLen = mLen * 1000;
                    mVideoLen.setText(getString(R.string.video_length) + mLen / 1000
                            + "\"");
                    handler.postDelayed(refreshvideo, 1000);
                    break;
                default:
                    break;
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.burn_message_activity);
        getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

        WindowManager mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mTelManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(BroadcastConstants.UI_DOWNLOADING_FILE_CHANGE);
        filter.addAction(ACTION_REGISTER_STATUS_CHANGED);
        filter.addAction(ALARM_ALERT_ACTION);
        filter.addAction(MESSAGE_STATE_CHANGED);
        registerReceiver(receiver, filter);

        IntentFilter simOutFilter = new IntentFilter();
        simOutFilter.addAction(ACTION_SIM_STATE_CHANGED);
        registerReceiver(simStateReceiver,simOutFilter);

        mSmsId = getIntent().getLongExtra("smsId", -1);
        mMsg = RcsChatMessageUtils.getChatMessageOnSMSDB(this, mSmsId);

        if (mMsg == null) {
            finish();
        }
        findView();
        initView();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            if (receiver != null) {
                unregisterReceiver(receiver);
            }
            if (simStateReceiver != null) {
                unregisterReceiver(simStateReceiver);
            }
        } catch (Exception e) {

        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
        if (mMsg.getSendReceive() == SuntekMessageData.MSG_RECEIVE) {
            burnMessage(mSmsId);
            finish();
        }
    }

    private void loadVideo() {
        String filepath = null;
        try {
            filepath = RcsChatMessageUtils.getFilePath(mMsg);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
        if (RcsChatMessageUtils.isFileDownload(filepath, mMsg.getFilesize())) {
            mVideo.setVisibility(View.VISIBLE);
            mVideo.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer arg0) {
                    finish();
                }
            });
            mVideoLen.setVisibility(View.VISIBLE);
            mVideoLen.setText(getString(R.string.video_length) + mLen / 1000
                    + "\"");
            mVideo.setVideoURI(Uri.parse(filepath));
            mVideo.start();
            if (mMsg.getSendReceive() == SuntekMessageData.MSG_RECEIVE) {
                burnMessage(mSmsId);
            }
            handler.sendEmptyMessage(VIDEO_TIME_REFRESH);
        } else {
            mVideo.setVisibility(View.GONE);
            mVideoLen.setVisibility(View.GONE);
            acceptFile();
        }
    }

    private void loadImage() {

        try {
            mFilePath = RcsChatMessageUtils.getFilePath(mMsg);

        } catch (ServiceDisconnectedException e1) {
            e1.printStackTrace();
        }
        if (RcsChatMessageUtils.isFileDownload(mFilePath, mMsg.getFilesize())) {
            Bitmap imageBm = ImageUtils.getBitmap(mFilePath);
            mImage.setImageBitmap(imageBm);
            if (mMsg.getSendReceive() == SuntekMessageData.MSG_RECEIVE) {
                burnMessage(mSmsId);
            }
            mProgressText.setVisibility(View.GONE);
        } else {
            acceptFile();
            mProgressText.setVisibility(View.VISIBLE);
        }
    }

    private void acceptFile() {
        try {
            RcsApiManager.getMessageApi().acceptFile(mMsg);
        } catch (OperatorException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.image_msg_load_fail_tip,
                    Toast.LENGTH_SHORT).show();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    private void initView() {
        mMsgType = mMsg.getMsgType();
        switch (mMsgType) {
            case SuntekMessageData.MSG_TYPE_TEXT:
                mText.setVisibility(View.VISIBLE);
                mText.setText(mMsg.getData());
                if (mMsg.getSendReceive() == SuntekMessageData.MSG_RECEIVE) {
                    burnMessage(mSmsId);
                }
                break;
            case SuntekMessageData.MSG_TYPE_IMAGE:
                mImage.setVisibility(View.VISIBLE);
                loadImage();
                break;
            case SuntekMessageData.MSG_TYPE_AUDIO:
                mAudio.setVisibility(View.VISIBLE);
                mAudioIcon.setVisibility(View.VISIBLE);
                mAudioIcon.setBackgroundResource(R.anim.burn_message_audio_icon);
                final AnimationDrawable animaition = (AnimationDrawable) mAudioIcon.getBackground();
                animaition.setOneShot(false);
                mMediaPlayer = new MediaPlayer();

                try {
                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                        mMediaPlayer.release();
                    }
                    String filePath = RcsApiManager.getMessageApi()
                            .getFilepath(mMsg);

                    mMediaPlayer.setDataSource(filePath);
                    mMediaPlayer.prepare();
                    mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                        mLen = mMediaPlayer.getDuration() / 1000;
                        Log.i("RCS_UI","AUDIO MELN="+mLen);
                        }
                    });
                    mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {

                        @Override
                        public void onCompletion(MediaPlayer arg0) {
                            animaition.stop();
                            finish();
                        }
                    });
                } catch (Exception e) {

                    e.printStackTrace();

                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                    }
                    Toast.makeText(this, R.string.open_file_fail,
                            Toast.LENGTH_SHORT).show();
                    // return;
                    finish();
                }
                mMediaPlayer.start();
                if (mMsg.getSendReceive() == SuntekMessageData.MSG_RECEIVE) {
                    burnMessage(mSmsId);
                }
                animaition.start();
                handler.sendEmptyMessage(AUDIO_TIME_REFRESH);
                break;

            case SuntekMessageData.MSG_TYPE_VIDEO:
                mVideo.setVisibility(View.VISIBLE);
                mVideoLen.setVisibility(View.VISIBLE);
                mVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mLen = mVideo.getDuration();
                        Log.i("RCS_UI", "mLen=" + mLen);
                    }
                });
                loadVideo();
                break;
            default:
                break;
        }

    }

    private void findView() {
        mProgressText = (TextView) findViewById(R.id.progress_text);
        mImage = (ImageView) findViewById(R.id.image);
        mVideo = (VideoView) findViewById(R.id.video);
        mAudio = (TextView) findViewById(R.id.audio);
        mText = (TextView) findViewById(R.id.text);
        mTime = (TextView) findViewById(R.id.burn_time);
        mVideoLen = (TextView) findViewById(R.id.video_len);
        mAudioIcon = (ImageView) findViewById(R.id.audio_icon);
    }

    public static int getVideoLength(String message) {
        if (message.startsWith(VIDEO_HEAD)) {
            return Integer.parseInt(message.substring(VIDEO_HEAD.length())
                    .split(SPLIT)[0]);
        }
        return 0;
    }

    private void burnMessage(long id) {
        String smsId = String.valueOf(id);
        try {
            if (mMsg != null) {
                RcsApiManager.getMessageApi().burnMessageAtOnce(smsId);
            }
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
        ContentValues values = new ContentValues();
        values.put("rcs_is_burn", 1);
        getContentResolver().update(Uri.parse("content://sms/"), values, " _id = ? ", new String[] {
                smsId
        });
    }

}
