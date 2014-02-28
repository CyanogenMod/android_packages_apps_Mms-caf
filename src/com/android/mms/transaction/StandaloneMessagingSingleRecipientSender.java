/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.mms.transaction;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.mms.data.Conversation;
import com.google.android.mms.MmsException;

public class StandaloneMessagingSingleRecipientSender extends SmsMessageSender {
    private static final boolean DEBUG = false;
    private static final String TAG = "StandaloneMessagingSingleRecipientSender";

    private static boolean isServiceAvailable = false;

    private final boolean mRequestDeliveryReport;
    private String mDest;
    private Uri mUri;
    private boolean isExpectMore;

    public StandaloneMessagingSingleRecipientSender(Context context, String dest, String msgText,
            long threadId, boolean requestDeliveryReport, Uri uri, int subscription,
            boolean expectMore) {
        super(context, null, msgText, threadId, subscription);
        mRequestDeliveryReport = requestDeliveryReport;
        mDest = dest;
        mUri = uri;
        isExpectMore = isExpectMore;
        if (DEBUG) {
            Log.d(TAG, "created with dest=" + dest + ",msgtext=" + msgText + ",theadId=" + threadId
                    + ",requestDeliveryRepost=" + requestDeliveryReport + ",uri=" + uri.toString()
                    + ",subscription=" + subscription);
        }
    }

    public boolean sendMessage(long token) throws MmsException {
        if (!isServiceAvailable) {
            Log.i(TAG, "Message sent via SMS");
            SmsSingleRecipientSender sender = new SmsSingleRecipientSender(mContext, mDest,
                    mMessageText, mThreadId, mRequestDeliveryReport, mUri, mSubscription,
                    isExpectMore);
            return sender.sendMessage(token);
        }

        Log.i(TAG, "Message sent via RCS SM");
        if (mMessageText == null) {
            throw new MmsException("Null message body or have multiple destinations.");
        }

        boolean moved = Sms.moveMessageToFolder(mContext, mUri, Sms.MESSAGE_TYPE_OUTBOX, 0);
        if (!moved) {
            throw new MmsException("SmsMessageSender.sendMessage: couldn't move message " +
                    "to outbox: " + mUri);
        }

        mDest = PhoneNumberUtils.stripSeparators(mDest);
        mDest = Conversation.verifySingleRecipient(mContext, mThreadId, mDest);

        Intent intent = new Intent(StandaloneMessagingReceiver.ACTION_SEND_MESSAGE,
                null, mContext, StandaloneMessagingReceiver.class);
        intent.putExtra("content", mMessageText);
        intent.putExtra("request_delivery_report", mRequestDeliveryReport ? 1 : 0);
        intent.putExtra("dest", mDest);
        intent.putExtra("uri", mUri.toString());
        if (DEBUG) {
            Log.d(TAG, "mDest=" + mDest + ",content=" + mMessageText + ",uri=" + mUri);
        }

        mContext.sendBroadcast(intent);
        return false;
    }

    public static void setServiceAvailability(boolean available) {
        isServiceAvailable = available;
    }
}
