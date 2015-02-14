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

import com.android.mms.transaction.MessagingNotification;
import com.suntek.mway.rcs.client.aidl.constant.BroadcastConstants;
import com.suntek.mway.rcs.client.aidl.provider.model.ChatMessage;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.aidl.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.api.im.impl.MessageApi;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RcsMessageStatusService extends IntentService {
    private static String LOG_TAG = "RCS_UI";
    private static ThreadPoolExecutor pool;
    private static final int NUMBER_OF_CORES; // Number of cores.
    private static final int MAXIMUM_POOL_SIZE; // Max size of the thread pool.
    private static int runningCount = 0;
    private static int runningId = 0;
    private static long taskCount = 0;

    static {
        NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
        MAXIMUM_POOL_SIZE = Math.max(NUMBER_OF_CORES, 16);

        pool = new ThreadPoolExecutor(
                NUMBER_OF_CORES,
                MAXIMUM_POOL_SIZE,
                1,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public RcsMessageStatusService() {
        // Class name will be the thread name.
        super(RcsMessageStatusService.class.getName());

        // Intent should be redelivered if the process gets killed before
        // completing the job.
        setIntentRedelivery(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        taskCount++;
        Log.w(LOG_TAG, "onStartCommand: taskCount=" + taskCount);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final int currentRunningId = ++runningId;

        pool.execute(new Runnable() {
            public void run() {
                runningCount++;
                Log.w(LOG_TAG, "runningId=" + currentRunningId + ", countOfRunning=" + runningCount
                        + ", taskCount=" + taskCount + ", Begin");

                String action = intent.getAction();
                RcsUtils.dumpIntent(intent);
                if (BroadcastConstants.UI_MESSAGE_ADD_DATABASE.equals(action)) {
                    final ChatMessage chatMessage = intent.getParcelableExtra("chatMessage");
                    if(chatMessage.getChatType() == SuntekMessageData.CHAT_TYPE_PUBLIC)
                        return;

                    long threadId = copyRcsMsgToSmsProvider(
                            RcsMessageStatusService.this, chatMessage);

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
                        MessagingNotification.blockingUpdateNewMessageIndicator(
                                RcsMessageStatusService.this, threadId, true);
                    }
                } else if (BroadcastConstants.UI_SHOW_GROUP_MESSAGE_NOTIFY.equals(action)) {
                    long rcsThreadId = intent.getLongExtra("threadId", -1);
                    MessageApi messageApi = RcsApiManager.getMessageApi();
                    try {
                        GroupChatModel model = messageApi.getGroupChatByThreadId(rcsThreadId);
                        if (model != null) {
                            int msgNotifyType = model.getRemindPolicy();
                            int rcsId = model.getId();
                            long threadId = RcsUtils.getThreadIdByGroupId(
                                    RcsMessageStatusService.this, String.valueOf(rcsId));
                            if (msgNotifyType == 0) {
                                // not in group chat
                                if (threadId != MessagingNotification
                                        .getCurrentlyDisplayedThreadId()) {
                                    MessagingNotification.blockingUpdateNewMessageIndicator(
                                            RcsMessageStatusService.this, threadId, true);
                                }
                            } else {
                                MessagingNotification.blockingUpdateNewMessageIndicator(
                                        RcsMessageStatusService.this, threadId, false);
                            }
                        }
                    } catch (ServiceDisconnectedException e) {
                        Log.i(LOG_TAG, "GroupChatMessage" + e);
                    }
                } else if (BroadcastConstants.UI_MESSAGE_STATUS_CHANGE_NOTIFY.equals(action)) {
                    String id = intent.getStringExtra("id");
                    int status = intent.getIntExtra("status", -11);
                    Log.i(LOG_TAG, BroadcastConstants.UI_MESSAGE_STATUS_CHANGE_NOTIFY
                            + id + status);
                    RcsUtils.updateState(RcsMessageStatusService.this, id, status);
                    RcsNotifyManager.sendMessageFailNotif(RcsMessageStatusService.this,
                            status, id, true);
                } else if (BroadcastConstants.UI_DOWNLOADING_FILE_CHANGE.equals(action)) {
                    String rcs_message_id = intent
                            .getStringExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_MESSAGE_ID);
                    long start = intent.getLongExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_START,
                            -1);
                    long end = intent.getLongExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_END, -1);
                    if (start == end) {
                        RcsUtils.updateFileDownloadState(RcsMessageStatusService.this,
                                rcs_message_id);
                    }
                } else if (BroadcastConstants.UI_SHOW_RECV_REPORT_INFO.equals(action)) {
                    String id = intent.getStringExtra("messageId");
                    String statusString = intent.getStringExtra("status");
                    int status = 99;
                    if ("delivered".equals(statusString)) {
                        status = 99;
                    } else if ("displayed".equals(statusString)) {
                        status = 100;
                    }
                    String number = intent.getStringExtra("original-recipient");
                    RcsUtils.updateManyState(RcsMessageStatusService.this, id, number, status);
                } else if("com.suntek.mway.rcs.ACTION_UI_MESSAGE_TRANSFER_SMS".equals(action)){
                    Log.i(LOG_TAG,"rcs message to sms="+action);
                    long messageId = intent.getLongExtra("id",-1);
                    RcsUtils.deleteMessageById(RcsMessageStatusService.this, messageId);
                }

                Log.w(LOG_TAG, "runningId=" + currentRunningId + ", countOfRunning="
                        + runningCount + ", taskCount=" + taskCount + ", End");
                runningCount--;
                taskCount--;
            };
        });
    }

    public static long copyRcsMsgToSmsProvider(Context context, ChatMessage chatMessage) {
        try {
            return RcsUtils.rcsInsert(context, chatMessage);
        } catch (ServiceDisconnectedException e) {
            Log.w(LOG_TAG, e);
            return 0;
        }
    }
}
