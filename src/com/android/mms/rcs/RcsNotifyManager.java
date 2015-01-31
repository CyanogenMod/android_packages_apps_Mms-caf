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

import com.suntek.mway.rcs.client.api.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.api.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.text.TextUtils;
import java.util.ArrayList;
import com.android.mms.R;

public class RcsNotifyManager {

    private static RcsNotifyManager instance;

    private static NotificationManager notifManager;

    private static ArrayList<String> contactMsgIdMap;

    private Context mContext;

    private RcsNotifyManager(Context context) {
        this.mContext = context;
        notifManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        contactMsgIdMap = new ArrayList<String>();
    }

    public static RcsNotifyManager getInstance(Context context) {
        if (instance == null)
            instance = new RcsNotifyManager(context);
        return instance;
    }

    public void showNewMessageNotif(int state, String messageId, boolean shouldPlaySound) {
        if (notifManager == null)
            return;
        if (state != SuntekMessageData.MSG_STATE_SEND_FAIL && contactMsgIdMap.size() == 0)
            return;
        if (state != SuntekMessageData.MSG_STATE_SEND_FAIL)
            contactMsgIdMap.remove(messageId);
        else
            contactMsgIdMap.add(messageId);
        final Notification notification = new Notification(R.drawable.stat_notify_sms_failed,
                mContext.getString(R.string.send_manage_fail_count, contactMsgIdMap.size()),
                System.currentTimeMillis());
        notification.setLatestEventInfo(mContext, mContext.getString(R.string.send_manage_fail),
                mContext.getString(R.string.send_manage_fail_count, contactMsgIdMap.size()),
                null);
        if (state != SuntekMessageData.MSG_STATE_SEND_FAIL)
            notification.defaults = Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE;
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notifManager.notify(0, notification);
    }

}
