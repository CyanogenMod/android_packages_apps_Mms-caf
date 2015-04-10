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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import android.content.Intent;

import com.android.mms.MmsApp;

import com.suntek.mway.rcs.client.aidl.constant.BroadcastConstants;
import com.suntek.mway.rcs.client.api.im.impl.MessageApi;
import com.suntek.mway.rcs.client.aidl.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.aidl.provider.model.ChatMessage;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import android.content.Context;
import com.android.mms.transaction.MessagingNotification;
import android.util.Log;

public class RcsMessageThread extends Thread {

    private static final int WAITING_TIME_TO_GET_FIRST_RECORD = 3;
    private static final int MESSAGE_STATUS_DELIVERED = 99;
    private static final int MESSAGE_STATUS_DISPLAYED = 100;
    private static final long COPY_RCS_MESSAGE_FAIL = 0;

    private static final String ACTION_UI_SHOW_GROUP_MESSAGE_NOTIFY =
            "com.suntek.mway.rcs.ACTION_UI_SHOW_GROUP_MESSAGE_NOTIFY";

    private volatile boolean mIsExit = false;

    private int mIndex;

    private BlockingQueue<Intent> mReportQueue;

    public RcsMessageThread(int index) {
        this.setName("MessageReport-" + index);
        this.mIndex = index;

        mReportQueue = new LinkedBlockingQueue<Intent>();
    }

    public boolean addReport(Intent reportOption) {
        return mReportQueue.add(reportOption);
    }

    public void notifyExit() {
        mIsExit = true;
    }

    @Override
    public void run() {

        while (!mIsExit) {
            try {
                Intent reportOption = mReportQueue.poll(WAITING_TIME_TO_GET_FIRST_RECORD,
                        TimeUnit.SECONDS);
                if (reportOption != null) {
                    rcsBroadcastDeal(reportOption);
                }
            } catch (Exception ex) {

            }
        }
    }

    public void rcsBroadcastDeal(Intent intent) {

        String action = intent.getAction();
        RcsUtils.dumpIntent(intent);
        if (BroadcastConstants.UI_MESSAGE_ADD_DATABASE.equals(action)) {
            final ChatMessage chatMessage = intent.getParcelableExtra("chatMessage");
            if (chatMessage == null) {
                return;
            }
            if (chatMessage.getChatType() == SuntekMessageData.CHAT_TYPE_PUBLIC) {
                // if chat type is public, it means it is a publicAccount message and does
                // not need to notify.
                return;
            }

            long threadId = copyRcsMsgToSmsProvider(MmsApp.getApplication(), chatMessage);

            boolean notify;
            int chatType = chatMessage.getChatType();
            int sendReceive = chatMessage.getSendReceive();
            if (chatMessage == null) {
                notify = false;
            } else if (chatType == SuntekMessageData.CHAT_TYPE_PUBLIC) {
                notify = false;
            } else {
                notify = ((chatType != SuntekMessageData.CHAT_TYPE_GROUP)
                        && (sendReceive == SuntekMessageData.MSG_RECEIVE));
            }
            if (notify) {
                MessagingNotification.blockingUpdateNewMessageIndicator(MmsApp.getApplication(),
                        threadId, true);
            }
            if ((chatType == SuntekMessageData.CHAT_TYPE_GROUP)
                    && (sendReceive == SuntekMessageData.MSG_RECEIVE)
                    && chatMessage.getMsgType() != SuntekMessageData.MSG_TYPE_NOTIFICATION) {
                Intent groupNotifyIntent = new Intent(ACTION_UI_SHOW_GROUP_MESSAGE_NOTIFY);
                groupNotifyIntent.putExtra("id", (long)chatMessage.getId());
                groupNotifyIntent.putExtra("threadId", chatMessage.getThreadId());
                MmsApp.getApplication().sendBroadcast(groupNotifyIntent);
            }
        } else if (BroadcastConstants.UI_MESSAGE_STATUS_CHANGE_NOTIFY.equals(action)) {
            String id = intent.getStringExtra("id");
            int status = intent.getIntExtra("status", -11);
            Log.i("RCS_UI", "com.suntek.mway.rcs.ACTION_UI_MESSAGE_STATUS_CHANGE_NOTIFY" + id
                    + status);
            RcsUtils.updateState(MmsApp.getApplication(), id, status);
            RcsNotifyManager.sendMessageFailNotif(MmsApp.getApplication(), status, id, true);
        } else if (BroadcastConstants.UI_SHOW_RECV_REPORT_INFO.equals(action)) {
            String id = intent.getStringExtra("messageId");
            String statusString = intent.getStringExtra("status");
            int status = MESSAGE_STATUS_DELIVERED;
            if ("delivered".equals(statusString)) {
                status = MESSAGE_STATUS_DELIVERED;
            } else if ("displayed".equals(statusString)) {
                status = MESSAGE_STATUS_DISPLAYED;
            }
            String number = intent.getStringExtra("original-recipient");
            RcsUtils.updateManyState(MmsApp.getApplication(), id, number, status);
        }

    }

    public static class MessageThreadOption {
        public String messageId;

        public String conversationId;

        public String number;

        private MessageThreadOption() {
        }

        public static MessageThreadOption createByMessageId(String messageId,
                String conversationId, String number) {
            MessageThreadOption option = new MessageThreadOption();
            option.messageId = messageId;
            option.conversationId = conversationId;
            option.number = number;
            return option;
        }
    }

    public static long copyRcsMsgToSmsProvider(Context context, ChatMessage chatMessage) {
        try {
            return RcsUtils.rcsInsert(context, chatMessage);
        } catch (ServiceDisconnectedException e) {
            Log.e("RCS_UI", e.toString());
            return COPY_RCS_MESSAGE_FAIL;
        }
    }

}
