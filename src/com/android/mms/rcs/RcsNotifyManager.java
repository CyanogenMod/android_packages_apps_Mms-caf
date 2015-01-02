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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.ConversationList;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.util.Log;
import android.text.SpannableString;
import android.text.TextUtils;
import com.android.mms.R;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.suntek.mway.rcs.client.api.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.api.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;

public class RcsNotifyManager {
    public static final int RCS_MESSAGE_FAILED_NOTIFICATION_ID = 789;

    private final static String RCS_UNDELIVERED_FLAG = "undelivered_flag";

    private static final Uri RCS_UNDELIVERED_URI = Sms.CONTENT_URI;

    public static void sendMessageFailNotif(Context context, int state,
            String messageId, boolean shouldPlaySound) {
        // TODO factor out common code for creating notifications
        boolean bNotifEnabled = MessagingPreferenceActivity
                .getNotificationEnabled(context);
        if (!bNotifEnabled || state != SuntekMessageData.MSG_STATE_SEND_FAIL) {
            return;
        }
        int totalFailedCount = getRcsUndeliveredMessageCount(context, state);
        Intent sendFailedIntent;
        Notification notification = new Notification();
        String title = context.getString(R.string.send_manage_fail);

        String description = context.getString(R.string.send_manage_fail_count,
                totalFailedCount);

        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);

        sendFailedIntent = getRcsFailedIntentFromConversationMode(context,
                false, Integer.valueOf(messageId), state);

        taskStackBuilder.addNextIntent(sendFailedIntent);
        notification.icon = R.drawable.stat_notify_sms_failed;
        notification.tickerText = title;

        notification.setLatestEventInfo(context, title, description,
                taskStackBuilder.getPendingIntent(0,
                        PendingIntent.FLAG_UPDATE_CURRENT));

        notification.defaults = Notification.DEFAULT_SOUND
                | Notification.DEFAULT_VIBRATE;

        notification.flags = Notification.FLAG_AUTO_CANCEL;

        NotificationManager rcsNotificationMgr = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        rcsNotificationMgr.notify(RCS_MESSAGE_FAILED_NOTIFICATION_ID,
                notification);
    }

    // Query the DB and return the number of rcsundelivered messages (total for
    // both SMS and MMS)
    private static int getRcsUndeliveredMessageCount(Context context, int state) {
        Cursor rcsundeliveredCursor = SqliteWrapper.query(context,
                context.getContentResolver(), RCS_UNDELIVERED_URI, null,
                "rcs_msg_state = " + state, null, null);
        if (rcsundeliveredCursor == null) {
            return 0;
        }
        int count = rcsundeliveredCursor.getCount();
        rcsundeliveredCursor.close();
        return count;
    }

    /**
     * Return the pending intent for failed messages in conversation mode.
     *
     * @param context
     *            The context
     * @param isDownload
     *            Whether the message is failed to download
     * @param threadId
     *            The thread if of the message failed to download
     * @param state
     *            The send fail state
     */
    private static Intent getRcsFailedIntentFromConversationMode(
            Context context, boolean isDownload, long threadId, int state) {
        Cursor cursor = SqliteWrapper.query(context,
                context.getContentResolver(), RCS_UNDELIVERED_URI, null,
                "rcs_msg_state = " + state, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            Intent rcsfailedIntent;
            if (isRcsFailedMessagesInSameThread(cursor)) {
                rcsfailedIntent = new Intent(context,
                        ComposeMessageActivity.class);
                // For send failed case, get the thread id from the cursor.
                rcsfailedIntent.putExtra(RCS_UNDELIVERED_FLAG, true);
                rcsfailedIntent.putExtra(ComposeMessageActivity.THREAD_ID,
                        getRcsUndeliveredMessageThreadId(cursor));
            } else {
                rcsfailedIntent = new Intent(context, ConversationList.class);
            }
            return rcsfailedIntent;
        } finally {
            cursor.close();
        }
    }

    // Get the box id of the first rcsundelivered message
    private static long getRcsUndeliveredMessageThreadId(Cursor cursor) {
        if (cursor.moveToFirst()) {
            return cursor.getLong(cursor.getColumnIndexOrThrow("thread_id"));
        } else {
            return 0;
        }
    }

    // Whether all the rcsundelivered messages belong to the same thread.
    private static boolean isRcsFailedMessagesInSameThread(Cursor cursor) {
        long firstThreadId = getRcsUndeliveredMessageThreadId(cursor);

        boolean isSame = true;
        while (cursor.moveToNext()) {
            Log.d("RCS_UI",
                    "ThreadId: "
                            + cursor.getLong(cursor
                                    .getColumnIndexOrThrow("thread_id")));
            if (cursor.getLong(cursor.getColumnIndexOrThrow("thread_id")) != firstThreadId) {
                isSame = false;
                break;
            }
        }
        return isSame;
    }
}
