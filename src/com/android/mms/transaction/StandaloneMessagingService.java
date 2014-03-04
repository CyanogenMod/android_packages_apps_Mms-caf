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

import java.util.HashMap;

import static android.content.Intent.ACTION_BOOT_COMPLETED;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.text.TextUtils;
import android.util.Log;

import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.Recycler;
import com.android.mms.widget.MmsWidgetProvider;
import com.qualcomm.rcsservice.CDInfo;
import com.qualcomm.rcsservice.IQCDListener;
import com.qualcomm.rcsservice.IQCDService;
import com.qualcomm.rcsservice.IQRCSService;
import com.qualcomm.rcsservice.QRCSInt;
import com.qualcomm.rcsservice.QRCSString;
import com.qualcomm.rcsservice.StatusCode;
import com.qualcomm.standalone.IQSMService;
import com.qualcomm.standalone.IQSMListener;
import com.qualcomm.standalone.SmCommonGroupInfo;
import com.qualcomm.standalone.SmContent;
import com.qualcomm.standalone.SmDispositionNotiConfig;
import com.qualcomm.standalone.SmId;
import com.qualcomm.standalone.SmIncomingContactinfo;
import com.qualcomm.standalone.SmMessageInfo;
import com.qualcomm.standalone.SmMessageStatus;
import com.qualcomm.standalone.SmServiceEvent;
import com.qualcomm.standalone.SmServiceHandleInfo;

public class StandaloneMessagingService extends Service {
    private static final boolean DEBUG = false;

    private static final String TAG = "StandaloneMessagingService";

    private static final String ONE_SHOT_FEATURE_TAG = "+g.oma.sip-im";


    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    private int mResultCode;

    private BroadcastReceiver mRcsAvailableReceiver;
    private boolean mIsRcsServiceBound = false;
    private IQRCSService mRcsService = null;

    private int mCdServiceHandle;
    private IQCDService mCdService;
    private QRCSInt mCdListenerHandle = new QRCSInt();

    private int mOneShotServiceHandle;
    private IQSMService mOneShotService;
    private QRCSInt mOneShotListenerHandle = new QRCSInt();

    private HashMap<String, String> mMessageUriTable = new HashMap<String, String>();

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mResultCode = intent != null ? intent.getIntExtra("result", 0) : 0;

        if (mResultCode != 0 && DEBUG) {
            Log.d(TAG, "onStart: #" + startId + " mResultCode: " + mResultCode);
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mRcsAvailableReceiver);
        unbindService(mServiceConnection);
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int serviceId = msg.arg1;
            Intent intent = (Intent) msg.obj;
            if (intent != null) {
                String action = intent.getAction();
                int error = intent.getIntExtra("errorCode", 0);

                if (ACTION_BOOT_COMPLETED.equals(action)) {
                    handleBootCompleted();
                } else if (StandaloneMessagingReceiver.ACTION_SEND_MESSAGE.equals(action)) {
                    handleSendMessage(intent);
                }
            }
            StandaloneMessagingReceiver.finishStartingService(
                    StandaloneMessagingService.this, serviceId);
        }
    }

    private void handleBootCompleted() {
        bindRcsService();
        registerForServiceAvailableBroadcast();
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mIsRcsServiceBound = true;
            mRcsService = IQRCSService.Stub.asInterface(service);
            if (mRcsService != null) {
                startRcsServices();
                setMyStatus("", "");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mIsRcsServiceBound = false;
            mRcsService = null;
        }
    };

    private void bindRcsService() {
        if (!mIsRcsServiceBound) {
            final Intent intent = new Intent("com.qualcomm.rcsservice.QRCSService");
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void registerForServiceAvailableBroadcast() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction("RCS_SERVICE_AVAILABLE");

        mRcsAvailableReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mRcsService != null) {
                    startRcsServices();
                    setMyStatus("", "");
                }
            }
        };

        registerReceiver(mRcsAvailableReceiver, filter);
    }

    private final IQCDListener mCdListener = new IQCDListener.Stub() {
        public void IQCDListener_ServiceUnAvailable(int handle, StatusCode status)
            throws RemoteException {
        }

        public void IQCDListener_ServiceAvailable(int handle, StatusCode status)
            throws RemoteException {
        }

        public void IQCDListener_GetVersion(int handle, QRCSString version, QRCSInt versionLen)
            throws RemoteException {
        }

        public void IQCDListener_CDUpdate(int handle, StatusCode status, QRCSString uri,
                CDInfo info) throws RemoteException {
        }

        public void IQCDListener_CDStatusUpdate(int handle, StatusCode status, QRCSString uri,
                QRCSString statusString, QRCSString customString, QRCSInt responseCode)
            throws RemoteException {
        }
    };

    private final IQSMListener mSmListener = new IQSMListener.Stub() {
        public void QSMListener_ServiceUnavailable(int handle, StatusCode code)
                throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "QSMListener_ServiceUnavailable");
            }
            StandaloneMessagingSingleRecipientSender.setServiceAvailability(false);
        }

        public void QSMListener_ServiceAvailable(int handle, StatusCode code)
               throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "QSMListener_ServiceAvailable");
            }
            StandaloneMessagingSingleRecipientSender.setServiceAvailability(true);
        }

        public void QSMListener_GetVersion(int handle, QRCSString version,
                QRCSInt versionLen) throws RemoteException {
        }

        public void QSMListener_HandleServiceInitStatus(int handle, SmServiceEvent event,
                SmServiceHandleInfo info) throws RemoteException {
            String featureTag = info.getFeatureTag();
            if (ONE_SHOT_FEATURE_TAG.equals(featureTag)) {
                mOneShotServiceHandle = info.getServiceHandle();
            }
        }

        public void QSMListener_HandleIncomingMessage(int handle, SmMessageInfo message,
                SmIncomingContactinfo contact, SmCommonGroupInfo group) throws RemoteException {
            final SmId id = message.getSmContentId();
            final String conversationId = id.getConversationId();
            final String contributionId = id.getContributionID();
            final String inReply = id.getInReplyToContributionId();
            Log.i(TAG, "incoming RCS SM message, conversationId=" + conversationId
                    + ",contributionId=" + contributionId
                    + ",inReply=" + inReply);

            final String from = contact.getFrom();
            final String content = message.getContentInfo().getContent();
            if (DEBUG) {
                Log.d(TAG, "from=" + from + ",content=" + content);
            }

            final Context _this = StandaloneMessagingService.this;

            final Uri messageUri = storeMessage(_this, conversationId, contributionId, inReply,
                    from, content);
            MessageUtils.checkIsPhoneMessageFull(_this);
            if (messageUri != null) {
                if (DEBUG) {
                    Log.d(TAG, "messageUri=" + messageUri);
                }
                long threadId = MessagingNotification.getSmsThreadId(_this, messageUri);
                if (DEBUG) {
                    Log.d(TAG, "threadId=" + threadId);
                }
                MessagingNotification.blockingUpdateNewMessageIndicator(_this, threadId, false);
            }
        }

        public void QSMListener_ServiceHandleSmSendSuccess(int handle, SmId id)
                throws RemoteException {
            final String contributionId = id.getContributionID();
            final String conversationId = id.getConversationId();
            final String inReply = id.getInReplyToContributionId();
            Log.i(TAG, "RCS SM message sent successfully, conversationId=" + conversationId
                    + ",contributionId=" + contributionId
                    + ",inReply=" + inReply);
            final Uri uri = getMessageUri(contributionId);
            if (uri != null) {
                if (DEBUG) {
                    Log.d(TAG, "uri=" + uri);
                }
                sendBroadcast(new Intent(SmsReceiverService.MESSAGE_SENT_ACTION,
                            uri, StandaloneMessagingService.this, SmsReceiver.class));
            }
        }

        public void QSMListener_HandleSmError(int handle, SmId id, int error)
            throws RemoteException {
            if (DEBUG) {
                Log.d(TAG, "QSMListener_HandleSmError: error=" + error);
            }
        }

        public void QSMListener_HandleMessageDispositionStatus(int handle, SmMessageInfo message,
                SmMessageStatus status) throws RemoteException {
            final SmId id = message.getSmContentId();
            final String contributionId = id.getContributionID();
            Log.i(TAG, "RCS SM message acknowledged, contributionId=" + contributionId);
            final Uri uri = getMessageUri(contributionId);
            if (uri != null) {
                if (DEBUG) {
                    Log.d(TAG, "uri=" + uri);
                }
                sendBroadcast(new Intent(MessageStatusReceiver.MESSAGE_STATUS_RECEIVED_ACTION,
                            uri, StandaloneMessagingService.this, MessageStatusReceiver.class));
            }
        }
    };

    private void startRcsServices() {
        boolean status = false;
        try {
            status = mRcsService.getServiceStatus();
            if (DEBUG) {
                Log.d(TAG, "status=" + status);
            }
            if (status) {
                mCdServiceHandle = mRcsService.QRCSCreateCDService(mCdListener,
                        mCdListenerHandle);
                mCdService = mRcsService.getCDService();
                if (DEBUG) {
                    Log.d(TAG, "mCdServiceHandle=" + mCdServiceHandle
                            + ",mCdService=" + mCdService);
                }

                mRcsService.QRCSCreateSMService(ONE_SHOT_FEATURE_TAG, mSmListener,
                        mOneShotListenerHandle);
                mOneShotService = mRcsService.getSMService();
                if (DEBUG) {
                    Log.d(TAG, "mOneShotListenerHandle=" + mOneShotListenerHandle
                            + ",mOneShotService=" + mOneShotService);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void setMyStatus(final String status, final String msg) {
        if (mCdService != null) {
            try {
                mCdService.QCDService_SetMyCdStatusInfo(mCdServiceHandle, status, msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleSendMessage(final Intent intent) {
        final Bundle extras = intent.getExtras();
        final String content = extras.getString("content");
        int requestDeliveryReport = extras.getInt("request_delivery_report");
        final String dest = extras.getString("dest");
        final String msgUri = extras.getString("uri");

        SmMessageInfo msg = new SmMessageInfo();

        SmDispositionNotiConfig noti = new SmDispositionNotiConfig();
        if (requestDeliveryReport != 0) {
            noti.setSmDispositionNotiConfigEnum(SmDispositionNotiConfig
                    .QRCS_SM_DISPOSITION_NOTIFICATION_CONFIG_ENUM
                    .QRCS_SM_BOTH_DELIVERY_DISPLAY_NOTIFICATION_ON.ordinal());
        } else {
            noti.setSmDispositionNotiConfigEnum(SmDispositionNotiConfig
                    .QRCS_SM_DISPOSITION_NOTIFICATION_CONFIG_ENUM
                    .QRCS_SM_BOTH_DELIVERY_DISPLAY_NOTIFICATION_OFF.ordinal());
        }
        msg.setSmDispositionNotiConfig(noti);

        // expiry header
        msg.setExpiresHeaderValue(0);
        // todo
        msg.setRemoteContact("sip:" + dest + "@ims.cingularme.com");

        SmContent smContent = new SmContent();
        smContent.setContent(content);
        smContent.setContentLength(content.getBytes().length);
        smContent.setContentType("text/plain");
        msg.setContentInfo(smContent);

        SmId smId = new SmId();
        smId.setContributionID(null);
        smId.setConversationId(null);
        smId.setInReplyToContributionId(null);
        msg.setSmContentId(smId);

        try {
            String contributionId = mOneShotService.QSMService_SendPagerModeSMMessage(
                    mOneShotServiceHandle, msg);
            if (DEBUG) {
                Log.d(TAG, "SendPagerModeSMMessage contributionId=" + contributionId);
            }
            storeContributionId(msgUri, contributionId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void storeContributionId(final String msgUri, final String contributionId) {
        mMessageUriTable.put(contributionId, msgUri);
    }

    private final Uri getMessageUri(final String contributionId) {
        final String s = mMessageUriTable.get(contributionId);
        return s == null ? null : Uri.parse(s);
    }

    private Uri storeMessage(Context context, final String conversationId,
            final String contributionId, final String inReply, String address,
            final String content) {
        ContentValues values = new ContentValues();
        values.put(Sms.ERROR_CODE, 0);
        values.put(Inbox.BODY, content);

        if (!TextUtils.isEmpty(address)) {
            Contact cacheContact = Contact.get(address, true);
            if (cacheContact != null) {
                address = cacheContact.getNumber();
            }
        } else {
            address = getString(R.string.unknown_sender);
        }
        values.put(Sms.ADDRESS, address);
        values.put(Inbox.ADDRESS, address);

        Long threadId = null;
        /*Long threadId = values.getAsLong(Sms.THREAD_ID);*/
        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            threadId = Conversation.getOrCreateThreadId(context, address);
            values.put(Sms.THREAD_ID, threadId);
        }

        values.put(Inbox.READ, 0);
        values.put(Inbox.SEEN, 0);

        // seems there is no timestamp in the incoming message
        long now = System.currentTimeMillis();
        Long nowLong = new Long(now);
        values.put(Inbox.DATE, nowLong);
        values.put(Inbox.DATE_SENT, nowLong);

        // some fake values
        values.put(Inbox.PROTOCOL, 0);
        values.put(Inbox.REPLY_PATH_PRESENT, 0);
        values.put(Inbox.SERVICE_CENTER, (String) null);

        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);
        MmsWidgetProvider.notifyDatasetChanged(context);

        return insertedUri;
    }
}
