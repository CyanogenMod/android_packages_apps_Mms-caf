/*
 * Copyright (C) 2014 Anthony W.
 * Modifications Copyright (C) 2012 The CyanogenMod Project
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
package com.android.mms.quickmessage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.RemoteInput;
import android.util.Log;
import android.widget.Toast;

import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.SmsMessageSender;


public class QuickMessageWear extends Activity {
    private static boolean DEBUG = false;
    private static String TAG = "QmWear";
    private Context mContext;
    private WakeLock mWakeLock;
    public static final String SMS_SENDER = "com.android.mms.SMS_SENDER";
    public static final String SMS_THEAD_ID = "com.android.mms.SMS_THREAD_ID";
    public static final String SMS_CONATCT = "com.android.mms.CONTACT";
    public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        i = getIntent();
        parseIntent(i.getExtras());
        //Get partial Wakelock so that we can send the message even if phone is locked
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.acquire();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (DEBUG)
            Log.d(TAG, "onNewIntent() called");
        // Set new intent
        setIntent(intent);
        // Send new SMS from voice
        parseIntent(intent);
    }

    @Override
    protected void onDestroy(){
        mWakeLock.release();
        super.onDestroy();
    }

    private void parseIntent(Intent i) {
        if (i == null) {
            return;
        }
        //parse the remote input into a message that can be sent
        Bundle remoteInput = RemoteInput.getResultsFromIntent(i);
        CharSequence message = remoteInput.getCharSequence(EXTRA_VOICE_REPLY);
        String sender = i.getStringExtra(SMS_SENDER);
        String contactName = i.getStringExtra(SMS_CONATCT);
        long tId = i.getLongExtra(SMS_THEAD_ID, -1);
        //Only send if we have a valid thread id
        if (tId != -1) {
            String[] dest = new String[]{
                    sender
            };
            SmsMessageSender smsMessageSender = new SmsMessageSender(getBaseContext(), dest,
                    message.toString(), tId);
            try {
                smsMessageSender.sendMessage(tId);
                Toast.makeText(mContext, getString(R.string.qm_wear_sending_message, contactName),
                        Toast.LENGTH_LONG).show();
            } catch (MmsException e) {
                Log.e(TAG, "Mms Exception thrown as follows: " + e);
                Toast.makeText(mContext, getString(R.string.qm_wear_messaged_failed, contactName),
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(mContext, getString(R.string.qm_wear_messaged_failed, contactName),
                    Toast.LENGTH_LONG).show();
        }
        //gotta mark as read even if it doesn't send since we read
        // the message and tried to respond to it
        Conversation con = Conversation.get(mContext, tId, true);
        if (con != null) {
            con.markAsRead();
            if (DEBUG)
                Log.d(TAG, "markAllMessagesRead(): Marked message " + tId + " as read");
        }
        finish();
    }
}


