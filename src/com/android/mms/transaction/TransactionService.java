/*
 * Copyright (c) 2012-2014 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.mms.transaction;

import java.io.IOException;
import java.lang.Long;
import java.util.ArrayList;

import android.app.NotificationManager;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.database.DatabaseUtils;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.LinkProperties;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Mms.Sent;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.RateController;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;

/**
 * The TransactionService of the MMS Client is responsible for handling requests
 * to initiate client-transactions sent from:
 * <ul>
 * <li>The Proxy-Relay (Through Push messages)</li>
 * <li>The composer/viewer activities of the MMS Client (Through intents)</li>
 * </ul>
 * The TransactionService runs locally in the same process as the application.
 * It contains a HandlerThread to which messages are posted from the
 * intent-receivers of this application.
 * <p/>
 * <b>IMPORTANT</b>: This is currently the only instance in the system in
 * which simultaneous connectivity to both the mobile data network and
 * a Wi-Fi network is allowed. This makes the code for handling network
 * connectivity somewhat different than it is in other applications. In
 * particular, we want to be able to send or receive MMS messages when
 * a Wi-Fi connection is active (which implies that there is no connection
 * to the mobile data network). This has two main consequences:
 * <ul>
 * <li>Testing for current network connectivity ({@link android.net.NetworkInfo#isConnected()} is
 * not sufficient. Instead, the correct test is for network availability
 * ({@link android.net.NetworkInfo#isAvailable()}).</li>
 * <li>If the mobile data network is not in the connected state, but it is available,
 * we must initiate setup of the mobile data connection, and defer handling
 * the MMS transaction until the connection is established.</li>
 * </ul>
 */
public class TransactionService extends Service implements Observer {
    private static final String TAG = LogTag.TAG;

    /**
     * Used to identify notification intents broadcasted by the
     * TransactionService when a Transaction is completed.
     */
    public static final String TRANSACTION_COMPLETED_ACTION =
            "android.intent.action.TRANSACTION_COMPLETED_ACTION";

    /**
     * Action for the Intent which is sent by Alarm service to launch
     * TransactionService.
     */
    public static final String ACTION_ONALARM = "android.intent.action.ACTION_ONALARM";

    /**
     * Action for the Intent which is sent when the user turns on the auto-retrieve setting.
     * This service gets started to auto-retrieve any undownloaded messages.
     */
    public static final String ACTION_ENABLE_AUTO_RETRIEVE
            = "android.intent.action.ACTION_ENABLE_AUTO_RETRIEVE";

    /**
     * Used as extra key in notification intents broadcasted by the TransactionService
     * when a Transaction is completed (TRANSACTION_COMPLETED_ACTION intents).
     * Allowed values for this key are: TransactionState.INITIALIZED,
     * TransactionState.SUCCESS, TransactionState.FAILED.
     */
    public static final String STATE = "state";

    /**
     * Used as extra key in notification intents broadcasted by the TransactionService
     * when a Transaction is completed (TRANSACTION_COMPLETED_ACTION intents).
     * Allowed values for this key are any valid content uri.
     */
    public static final String STATE_URI = "uri";

    private static final int EVENT_TRANSACTION_REQUEST = 1;
    private static final int EVENT_CONTINUE_MMS_CONNECTIVITY = 3;
    private static final int EVENT_HANDLE_NEXT_PENDING_TRANSACTION = 4;
    private static final int EVENT_NEW_INTENT = 5;
    private static final int EVENT_QUIT = 100;

    private static final int TOAST_MSG_QUEUED = 1;
    private static final int TOAST_DOWNLOAD_LATER = 2;
    private static final int TOAST_NO_APN = 3;
    private static final int TOAST_NONE = -1;

    // How often to extend the use of the MMS APN while a transaction
    // is still being processed.
    private static final int APN_EXTENSION_WAIT = 30 * 1000;

    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    private final ArrayList<Transaction> mProcessing  = new ArrayList<Transaction>();
    private final ArrayList<Transaction> mPending  = new ArrayList<Transaction>();
    private ConnectivityManager mConnMgr;

    private PowerManager.WakeLock mWakeLock;

    private ConnectivityManager.NetworkCallback mMmsNetworkCallback = null;
    private NetworkRequest mMmsNetworkRequest = null;


    private ConnectivityManager.NetworkCallback  getNetworkCallback(String subId) {
        final String mSubId = subId;

        return new ConnectivityManager.NetworkCallback() {
            @Override
            public void onPreCheck(Network network) {
                Log.d(TAG, "sub:" + mSubId + "NetworkCallback.onPrecheck: network=" + network);
            }
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "sub:" + mSubId + "NetworkCallback.onAvailable: network=" + network);
                onMmsPdpConnected(mSubId);

            }
            @Override
            public void onLosing(Network network, int timeToLive) {
                Log.d(TAG, "sub:" + mSubId + "NetworkCallback.onLosing: network="
                        + network + ", maxTimeToLive= " + timeToLive);
            }
            @Override
            public void onLost(Network network) {
                Log.d(TAG, "sub:" + mSubId + "NetworkCallback.onLost: network=" + network);
            }
            @Override
            public void onUnavailable() {
                Log.d(TAG,"sub:" + mSubId + "NetworkCallback.onUnavailable");

            }
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                Log.d(TAG, "sub:" + mSubId + "NetworkCallback.onCapabilitiesChanged: network="
                        + network + ", Cap = " + nc);
            }
            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
                Log.d(TAG, "sub:" + mSubId + "NetworkCallback.onLinkPropertiesChanged: network="
                        + network + ", LP = " + lp);
            }

        };
    }

    private void onMmsPdpConnected(String subId) {
        Log.d(TAG, "onMmsPdpConnected");

        NetworkInfo mmsNetworkInfo = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);

        // Check availability of the mobile network.
        if (mmsNetworkInfo == null) {
            Log.e(TAG, "mms type is null or mobile data is turned off, bail");
        } else {
            // This is a very specific fix to handle the case where the phone receives an
            // incoming call during the time we're trying to setup the mms connection.
            // When the call ends, restart the process of mms connectivity.
            if (Phone.REASON_VOICE_CALL_ENDED.equals(mmsNetworkInfo.getReason())) {
                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "   reason is " + Phone.REASON_VOICE_CALL_ENDED +
                            ", retrying mms connectivity");
                }
                renewMmsConnectivity();
                return;
            }

            if (mmsNetworkInfo.isConnected()) {
                TransactionSettings settings = new TransactionSettings(
                        TransactionService.this, mmsNetworkInfo.getExtraInfo(),
                        Long.parseLong(subId));
                // If this APN doesn't have an MMSC, mark everything as failed and bail.
                if (TextUtils.isEmpty(settings.getMmscUrl())) {
                    Log.v(TAG, "   empty MMSC url, bail");
                    mToastHandler.sendEmptyMessage(TOAST_NO_APN);
                    mServiceHandler.markAllPendingTransactionsAsFailed();
                    endMmsConnectivity();
                    return;
                }
                mServiceHandler.processPendingTransaction(null, settings);
            } else {
                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "   TYPE_MOBILE_MMS not connected, bail");
                }

                // Retry mms connectivity once it's possible to connect
                if (mmsNetworkInfo.isAvailable()) {
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "   retrying mms connectivity for it's available");
                    }
                    renewMmsConnectivity();
                }
            }

        }
    }

    private NetworkRequest buildNetworkRequest() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
            .build();

        return networkRequest;
    }

    public Handler mToastHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String str = null;

            if (msg.what == TOAST_MSG_QUEUED) {
                str = getString(R.string.message_queued);
            } else if (msg.what == TOAST_DOWNLOAD_LATER) {
                str = getString(R.string.download_later);
            } else if (msg.what == TOAST_NO_APN) {
                str = getString(R.string.no_apn);
            }

            if (str != null) {
                Toast.makeText(TransactionService.this, str,
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    public void onCreate() {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "Creating TransactionService");
        }

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        HandlerThread thread = new HandlerThread("TransactionService");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Message msg = mServiceHandler.obtainMessage(EVENT_NEW_INTENT);
            msg.arg1 = startId;
            msg.obj = intent;
            mServiceHandler.sendMessage(msg);
        }
        return Service.START_NOT_STICKY;
    }

    private int getSubIdFromDb(Uri uri) {
        int phoneId = 0;
        Cursor c = getApplicationContext().getContentResolver().query(uri,
                null, null, null, null);
        Log.d(TAG, "Cursor= " + DatabaseUtils.dumpCursorToString(c));
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    phoneId = c.getInt(c.getColumnIndex(Mms.PHONE_ID));
                    Log.d(TAG, "phoneId in db = " + phoneId );
                    c.close();
                    c = null;
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }
        return phoneId;
    }

    public void onNewIntent(Intent intent, int serviceId) {
        mConnMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (mConnMgr == null || !MmsConfig.isSmsEnabled(getApplicationContext())) {
            endMmsConnectivity();
            stopSelf(serviceId);
            return;
        }
        boolean noNetwork = false;

        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "onNewIntent: serviceId: " + serviceId + ": " + intent.getExtras() +
                    " intent=" + intent);
        }

        Bundle extras = intent.getExtras();
        String action = intent.getAction();
        if ((ACTION_ONALARM.equals(action) || ACTION_ENABLE_AUTO_RETRIEVE.equals(action) ||
                    (extras == null)) || ((extras != null) && !extras.containsKey("uri"))) {

            //We hit here when either the Retrymanager triggered us or there is
            //send operation in which case uri is not set. For rest of the
            //cases(MT MMS) we hit "else" case.

            // Scan database to find all pending operations.
            Cursor cursor = PduPersister.getPduPersister(this).getPendingMessages(
                    System.currentTimeMillis());
            Log.d(TAG, "Cursor= " + DatabaseUtils.dumpCursorToString(cursor));
            if (cursor != null) {
                try {
                    int count = cursor.getCount();

                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "onNewIntent: cursor.count=" + count + " action=" + action);
                    }

                    if (count == 0) {
                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "onNewIntent: no pending messages. Stopping service.");
                        }
                        RetryScheduler.setRetryAlarm(this);
                        stopSelfIfIdle(serviceId);
                        return;
                    }

                    int columnIndexOfMsgId = cursor.getColumnIndexOrThrow(PendingMessages.MSG_ID);
                    int columnIndexOfMsgType = cursor.getColumnIndexOrThrow(
                            PendingMessages.MSG_TYPE);

                    while (cursor.moveToNext()) {
                        int msgType = cursor.getInt(columnIndexOfMsgType);
                        int transactionType = getTransactionType(msgType);
                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "onNewIntent: msgType=" + msgType + " transactionType=" +
                                    transactionType);
                        }
                        if (noNetwork) {
                            onNetworkUnavailable(serviceId, transactionType);
                            return;
                        }
                        switch (transactionType) {
                            case -1:
                                break;
                            case Transaction.RETRIEVE_TRANSACTION:
                                // If it's a transiently failed transaction,
                                // we should retry it in spite of current
                                // downloading mode. If the user just turned on the auto-retrieve
                                // option, we also retry those messages that don't have any errors.
                                int failureType = cursor.getInt(
                                        cursor.getColumnIndexOrThrow(
                                                PendingMessages.ERROR_TYPE));
                                DownloadManager downloadManager = DownloadManager.getInstance();
                                boolean autoDownload = downloadManager.isAuto();
                                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                                    Log.v(TAG, "onNewIntent: failureType=" + failureType +
                                            " action=" + action + " isTransientFailure:" +
                                            isTransientFailure(failureType) + " autoDownload=" +
                                            autoDownload);
                                }
                                if (!autoDownload) {
                                    // If autodownload is turned off, don't process the
                                    // transaction.
                                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                                        Log.v(TAG, "onNewIntent: skipping - autodownload off");
                                    }
                                    // Re-enable "download" button if auto-download is off
                                    Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI,
                                            cursor.getLong(columnIndexOfMsgId));
                                    downloadManager.markState(uri,
                                            DownloadManager.STATE_SKIP_RETRYING);
                                    break;
                                }
                                // Logic is twisty. If there's no failure or the failure
                                // is a non-permanent failure, we want to process the transaction.
                                // Otherwise, break out and skip processing this transaction.
                                if (!(failureType == MmsSms.NO_ERROR ||
                                        isTransientFailure(failureType))) {
                                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                                        Log.v(TAG, "onNewIntent: skipping - permanent error");
                                    }
                                    break;
                                }
                                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                                    Log.v(TAG, "onNewIntent: falling through and processing");
                                }
                               // fall-through
                            default:
                                Uri uri = ContentUris.withAppendedId(
                                        Mms.CONTENT_URI,
                                        cursor.getLong(columnIndexOfMsgId));

                                int destPhoneId = getSubIdFromDb(uri);
                                long [] subId = SubscriptionManager.getSubId(destPhoneId);
                                Log.d(TAG, "Destination Phone Id = " + destPhoneId +
                                        "destination Sub Id = " + subId[0]);

                                TransactionBundle args = new TransactionBundle(transactionType,
                                        uri.toString(), subId[0]);
                                // FIXME: We use the same startId for all MMs.
                                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                                    Log.v(TAG, "onNewIntent: launchTransaction uri=" + uri);
                                }
                                // FIXME: We use the same serviceId for all MMs.
                                launchTransaction(serviceId, args, false);
                                break;
                        }
                    }
                } finally {
                    cursor.close();
                }
            } else {
                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "onNewIntent: no pending messages. Stopping service.");
                }
                RetryScheduler.setRetryAlarm(this);
                stopSelfIfIdle(serviceId);
            }
        } else {
            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "onNewIntent: launch transaction...");
            }

            String uriStr = intent.getStringExtra("uri");
            Uri uri = Uri.parse(uriStr);

            int destPhoneId = getSubIdFromDb(uri);
            long [] subId = SubscriptionManager.getSubId(destPhoneId);
            Log.d(TAG, "Destination Phone Id = " + destPhoneId +
                    "destination Sub Id = " + subId[0]);

            Bundle bundle = intent.getExtras();
            bundle.putLong(PhoneConstants.SUBSCRIPTION_KEY, subId[0]);
            // For launching NotificationTransaction and test purpose.
            TransactionBundle args = new TransactionBundle(bundle);
            launchTransaction(serviceId, args, noNetwork);
        }
    }

    private void stopSelfIfIdle(int startId) {
        synchronized (mProcessing) {
            if (mProcessing.isEmpty() && mPending.isEmpty()) {
                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "stopSelfIfIdle: STOP!");
                }

                stopSelf(startId);
            }
        }
    }

    private static boolean isTransientFailure(int type) {
        return type >= MmsSms.NO_ERROR && type < MmsSms.ERR_TYPE_GENERIC_PERMANENT;
    }

    private int getTransactionType(int msgType) {
        switch (msgType) {
            case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                return Transaction.RETRIEVE_TRANSACTION;
            case PduHeaders.MESSAGE_TYPE_READ_REC_IND:
                return Transaction.READREC_TRANSACTION;
            case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                return Transaction.SEND_TRANSACTION;
            default:
                Log.w(TAG, "Unrecognized MESSAGE_TYPE: " + msgType);
                return -1;
        }
    }

    private void launchTransaction(int serviceId, TransactionBundle txnBundle, boolean noNetwork) {
        if (noNetwork) {
            Log.w(TAG, "launchTransaction: no network error!");
            onNetworkUnavailable(serviceId, txnBundle.getTransactionType());
            return;
        }
        Message msg = mServiceHandler.obtainMessage(EVENT_TRANSACTION_REQUEST);
        msg.arg1 = serviceId;
        msg.obj = txnBundle;

        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "launchTransaction: sending message " + msg);
        }
        mServiceHandler.sendMessage(msg);
    }

    private void onNetworkUnavailable(int serviceId, int transactionType) {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "onNetworkUnavailable: sid=" + serviceId + ", type=" + transactionType);
        }

        int toastType = TOAST_NONE;
        if (transactionType == Transaction.RETRIEVE_TRANSACTION) {
            toastType = TOAST_DOWNLOAD_LATER;
        } else if (transactionType == Transaction.SEND_TRANSACTION) {
            toastType = TOAST_MSG_QUEUED;
        }
        if (toastType != TOAST_NONE) {
            mToastHandler.sendEmptyMessage(toastType);
        }
        stopSelf(serviceId);
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "Destroying TransactionService");
        }
        if (!mPending.isEmpty()) {
            Log.w(TAG, "TransactionService exiting with transaction still pending");
        }

        releaseWakeLock();

        mServiceHandler.sendEmptyMessage(EVENT_QUIT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Handle status change of Transaction (The Observable).
     */
    public void update(Observable observable) {
        Transaction transaction = (Transaction) observable;
        int serviceId = transaction.getServiceId();

        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "update transaction " + serviceId);
        }

       releaseNetworkRequest();

        try {
            synchronized (mProcessing) {
                mProcessing.remove(transaction);
                if (mPending.size() > 0) {
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "update: handle next pending transaction...");
                    }
                    Message msg = mServiceHandler.obtainMessage(
                            EVENT_HANDLE_NEXT_PENDING_TRANSACTION,
                            transaction.getConnectionSettings());
                    mServiceHandler.sendMessage(msg);
                }
                else if (mProcessing.isEmpty()) {
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "update: endMmsConnectivity");
                    }
                    endMmsConnectivity();
                } else {
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "update: mProcessing is not empty");
                    }
                }
            }

            Intent intent = new Intent(TRANSACTION_COMPLETED_ACTION);
            TransactionState state = transaction.getState();
            int result = state.getState();
            intent.putExtra(STATE, result);

            switch (result) {
                case TransactionState.SUCCESS:
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Transaction complete: " + serviceId);
                    }

                    intent.putExtra(STATE_URI, state.getContentUri());

                    // Notify user in the system-wide notification area.
                    switch (transaction.getType()) {
                        case Transaction.NOTIFICATION_TRANSACTION:
                        case Transaction.RETRIEVE_TRANSACTION:
                            // We're already in a non-UI thread called from
                            // NotificationTransacation.run(), so ok to block here.
                            long threadId = MessagingNotification.getThreadId(
                                    this, state.getContentUri());
                            MessagingNotification.blockingUpdateNewMessageIndicator(this,
                                    threadId,
                                    false);
                            MessagingNotification.updateDownloadFailedNotification(this);
                            break;
                        case Transaction.SEND_TRANSACTION:
                            RateController.getInstance().update();
                            break;
                    }
                    break;
                case TransactionState.FAILED:
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Transaction failed: " + serviceId);
                    }
                    break;
                default:
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Transaction state unknown: " +
                                serviceId + " " + result);
                    }
                    break;
            }

            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "update: broadcast transaction result " + result);
            }
            // Broadcast the result of the transaction.
            sendBroadcast(intent);
        } finally {
            transaction.detach(this);
            stopSelfIfIdle(serviceId);
        }
    }

    private synchronized void createWakeLock() {
        // Create a new wake lock if we haven't made one yet.
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS Connectivity");
            mWakeLock.setReferenceCounted(false);
        }
    }

    private void acquireWakeLock() {
        // It's okay to double-acquire this because we are not using it
        // in reference-counted mode.
        Log.v(TAG, "mms acquireWakeLock");
        mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        // Don't release the wake lock if it hasn't been created and acquired.
        if (mWakeLock != null && mWakeLock.isHeld()) {
            Log.v(TAG, "mms releaseWakeLock");
            mWakeLock.release();
        }
    }

    /* MMS app initiates one transaction at a time. Next transaction is started only on the
       completion(success/failure) of previous one.
    */
    protected int beginMmsConnectivity(String subId) throws IOException {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "beginMmsConnectivity for subId = " + subId);
        }
        // Take a wake lock so we don't fall asleep before the message is downloaded.
        createWakeLock();

        int result = mConnMgr.startUsingNetworkFeatureForSubscription(
                ConnectivityManager.TYPE_MOBILE, Phone.FEATURE_ENABLE_MMS, subId);

        if (mMmsNetworkRequest == null) {
            mMmsNetworkRequest = buildNetworkRequest();
            mMmsNetworkCallback = getNetworkCallback(subId);

            mConnMgr.registerNetworkCallback(mMmsNetworkRequest, mMmsNetworkCallback);
        }

        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "beginMmsConnectivity: result=" + result);
        }

        switch (result) {
            case PhoneConstants.APN_ALREADY_ACTIVE:
            case PhoneConstants.APN_REQUEST_STARTED:
                acquireWakeLock();
                return result;
        }

        throw new IOException("Cannot establish MMS connectivity");
    }

    private void releaseNetworkRequest() {
        if (mMmsNetworkCallback != null) {
            Log.d(TAG, "releaseNetworkRequest");

            mConnMgr.unregisterNetworkCallback(mMmsNetworkCallback);
            mMmsNetworkRequest = null;
            mMmsNetworkCallback = null;
        }
    }

    protected void endMmsConnectivity() {
        long subId = SubscriptionManager.getOnDemandDataSubId();
        endMmsConnectivity(Long.toString(subId));
    }

    protected void endMmsConnectivity(String subId) {
        try {
            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "endMmsConnectivity for subId = " + subId);
            }

            // cancel timer for renewal of lease
            mServiceHandler.removeMessages(EVENT_CONTINUE_MMS_CONNECTIVITY);
            if (mConnMgr != null) {
                releaseNetworkRequest();

                mConnMgr.stopUsingNetworkFeatureForSubscription(
                        ConnectivityManager.TYPE_MOBILE,
                        Phone.FEATURE_ENABLE_MMS, subId);
            }
        } finally {
            releaseWakeLock();
        }
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        private String decodeMessage(Message msg) {
            if (msg.what == EVENT_QUIT) {
                return "EVENT_QUIT";
            } else if (msg.what == EVENT_CONTINUE_MMS_CONNECTIVITY) {
                return "EVENT_CONTINUE_MMS_CONNECTIVITY";
            } else if (msg.what == EVENT_TRANSACTION_REQUEST) {
                return "EVENT_TRANSACTION_REQUEST";
            } else if (msg.what == EVENT_HANDLE_NEXT_PENDING_TRANSACTION) {
                return "EVENT_HANDLE_NEXT_PENDING_TRANSACTION";
            } else if (msg.what == EVENT_NEW_INTENT) {
                return "EVENT_NEW_INTENT";
            }
            return "unknown message.what";
        }

        private String decodeTransactionType(int transactionType) {
            if (transactionType == Transaction.NOTIFICATION_TRANSACTION) {
                return "NOTIFICATION_TRANSACTION";
            } else if (transactionType == Transaction.RETRIEVE_TRANSACTION) {
                return "RETRIEVE_TRANSACTION";
            } else if (transactionType == Transaction.SEND_TRANSACTION) {
                return "SEND_TRANSACTION";
            } else if (transactionType == Transaction.READREC_TRANSACTION) {
                return "READREC_TRANSACTION";
            }
            return "invalid transaction type";
        }

        /**
         * Handle incoming transaction requests.
         * The incoming requests are initiated by the MMSC Server or by the
         * MMS Client itself.
         */
        @Override
        public void handleMessage(Message msg) {
            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "Handling incoming message: " + msg + " = " + decodeMessage(msg));
            }

            Transaction transaction = null;

            switch (msg.what) {
                case EVENT_NEW_INTENT:
                    onNewIntent((Intent)msg.obj, msg.arg1);
                    break;

                case EVENT_QUIT:
                    getLooper().quit();
                    return;

                case EVENT_CONTINUE_MMS_CONNECTIVITY:
                    synchronized (mProcessing) {
                        if (mProcessing.isEmpty()) {
                            return;
                        }
                    }

                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "handle EVENT_CONTINUE_MMS_CONNECTIVITY event...");
                    }

                    try {
                        long subId = SubscriptionManager.getOnDemandDataSubId();
                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "renew PDP connection for subscription: " + subId);
                        }
                        int result = beginMmsConnectivity(Long.toString(subId));
                        if (result != PhoneConstants.APN_ALREADY_ACTIVE) {
                            Log.v(TAG, "Extending MMS connectivity returned " + result +
                                    " instead of APN_ALREADY_ACTIVE");
                            // Just wait for connectivity startup without
                            // any new request of APN switch.
                            return;
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "Attempt to extend use of MMS connectivity failed");
                        return;
                    }

                    // Restart timer
                    renewMmsConnectivity();
                    return;

                case EVENT_TRANSACTION_REQUEST:
                    int serviceId = msg.arg1;
                    try {
                        TransactionBundle args = (TransactionBundle) msg.obj;
                        TransactionSettings transactionSettings;

                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "EVENT_TRANSACTION_REQUEST MmscUrl=" +
                                    args.getMmscUrl() + " proxy port: " + args.getProxyAddress());
                        }

                        // Set the connection settings for this transaction.
                        // If these have not been set in args, load the default settings.
                        String mmsc = args.getMmscUrl();
                        if (mmsc != null) {
                            transactionSettings = new TransactionSettings(
                                    mmsc, args.getProxyAddress(), args.getProxyPort());
                        } else {
                            transactionSettings = new TransactionSettings(
                                                    TransactionService.this, null,
                                                    args.getSubId());
                        }

                        int transactionType = args.getTransactionType();

                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "handle EVENT_TRANSACTION_REQUEST: transactionType=" +
                                    transactionType + " " + decodeTransactionType(transactionType));
                        }

                        // Create appropriate transaction
                        switch (transactionType) {
                            case Transaction.NOTIFICATION_TRANSACTION:
                                String uri = args.getUri();
                                if (uri != null) {
                                    transaction = new NotificationTransaction(
                                            TransactionService.this, serviceId,
                                            transactionSettings, uri);
                                } else {
                                    // Now it's only used for test purpose.
                                    byte[] pushData = args.getPushData();
                                    PduParser parser = new PduParser(pushData);
                                    GenericPdu ind = parser.parse();

                                    int type = PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
                                    if ((ind != null) && (ind.getMessageType() == type)) {
                                        transaction = new NotificationTransaction(
                                                TransactionService.this, serviceId,
                                                transactionSettings, (NotificationInd) ind);
                                    } else {
                                        Log.e(TAG, "Invalid PUSH data.");
                                        transaction = null;
                                        return;
                                    }
                                }
                                break;
                            case Transaction.RETRIEVE_TRANSACTION:
                                transaction = new RetrieveTransaction(
                                        TransactionService.this, serviceId,
                                        transactionSettings, args.getUri());
                                break;
                            case Transaction.SEND_TRANSACTION:
                                transaction = new SendTransaction(
                                        TransactionService.this, serviceId,
                                        transactionSettings, args.getUri());
                                break;
                            case Transaction.READREC_TRANSACTION:
                                transaction = new ReadRecTransaction(
                                        TransactionService.this, serviceId,
                                        transactionSettings, args.getUri());
                                break;
                            default:
                                Log.w(TAG, "Invalid transaction type: " + serviceId);
                                transaction = null;
                                return;
                        }
                        //copy the subId from TransactionBundle to transaction obj.
                        transaction.setSubId(args.getSubId());

                        if (!processTransaction(transaction)) {
                            transaction = null;
                            return;
                        }

                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "Started processing of incoming message: " + msg);
                        }
                    } catch (Exception ex) {
                        Log.w(TAG, "Exception occurred while handling message: " + msg, ex);

                        if (transaction != null) {
                            try {
                                transaction.detach(TransactionService.this);
                                if (mProcessing.contains(transaction)) {
                                    synchronized (mProcessing) {
                                        mProcessing.remove(transaction);
                                    }
                                }
                            } catch (Throwable t) {
                                Log.e(TAG, "Unexpected Throwable.", t);
                            } finally {
                                // Set transaction to null to allow stopping the
                                // transaction service.
                                transaction = null;
                            }
                        }
                    } finally {
                        if (transaction == null) {
                            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                                Log.v(TAG, "Transaction was null. Stopping self: " + serviceId);
                            }
                            endMmsConnectivity();
                            stopSelf(serviceId);
                        }
                    }
                    return;
                case EVENT_HANDLE_NEXT_PENDING_TRANSACTION:
                    processPendingTransaction(transaction, (TransactionSettings) msg.obj);
                    return;
                default:
                    Log.w(TAG, "what=" + msg.what);
                    return;
            }
        }

        public void markAllPendingTransactionsAsFailed() {
            synchronized (mProcessing) {
                while (mPending.size() != 0) {
                    Transaction transaction = mPending.remove(0);
                    transaction.mTransactionState.setState(TransactionState.FAILED);
                    if (transaction instanceof SendTransaction) {
                        Uri uri = ((SendTransaction)transaction).mSendReqURI;
                        transaction.mTransactionState.setContentUri(uri);
                        int respStatus = PduHeaders.RESPONSE_STATUS_ERROR_NETWORK_PROBLEM;
                        ContentValues values = new ContentValues(1);
                        values.put(Mms.RESPONSE_STATUS, respStatus);

                        SqliteWrapper.update(TransactionService.this,
                                TransactionService.this.getContentResolver(),
                                uri, values, null, null);
                    }
                    transaction.notifyObservers();
                }
            }
        }

        public void processPendingTransaction(Transaction transaction,
                                               TransactionSettings settings) {

            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "processPendingTxn: transaction=" + transaction);
            }

            int numProcessTransaction = 0;
            synchronized (mProcessing) {
                if (mPending.size() != 0) {
                    transaction = mPending.remove(0);
                }
                numProcessTransaction = mProcessing.size();
            }

            if (transaction != null) {
                if (settings != null) {
                    transaction.setConnectionSettings(settings);
                }

                /*
                 * Process deferred transaction
                 */
                try {
                    int serviceId = transaction.getServiceId();

                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "processPendingTxn: process " + serviceId);
                    }

                    if (processTransaction(transaction)) {
                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "Started deferred processing of transaction  "
                                    + transaction);
                        }
                    } else {
                        transaction = null;
                        stopSelf(serviceId);
                    }
                } catch (IOException e) {
                    Log.w(TAG, e.getMessage(), e);
                }
            } else {
                if (numProcessTransaction == 0) {
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "processPendingTxn: no more transaction, endMmsConnectivity");
                    }
                    endMmsConnectivity();
                }
            }
        }

        /**
         * Internal method to begin processing a transaction.
         * @param transaction the transaction. Must not be {@code null}.
         * @return {@code true} if process has begun or will begin. {@code false}
         * if the transaction should be discarded.
         * @throws IOException if connectivity for MMS traffic could not be
         * established.
         */
        private boolean processTransaction(Transaction transaction) throws IOException {
            // Check if transaction already processing
            synchronized (mProcessing) {
                for (Transaction t : mPending) {
                    if (t.isEquivalent(transaction)) {
                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "Transaction already pending: " +
                                    transaction.getServiceId());
                        }
                        return true;
                    }
                }
                for (Transaction t : mProcessing) {
                    if (t.isEquivalent(transaction)) {
                        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                            Log.v(TAG, "Duplicated transaction: " + transaction.getServiceId());
                        }
                        return true;
                    }
                }

                long subId = transaction.getSubId();
                /*
                * Make sure that the network connectivity necessary
                * for MMS traffic is enabled. If it is not, we need
                * to defer processing the transaction until
                * connectivity is established.
                */
                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "processTransaction: call beginMmsConnectivity on subId = " + subId);
                }
                int connectivityResult = beginMmsConnectivity(Long.toString(subId));
                if (connectivityResult == PhoneConstants.APN_REQUEST_STARTED) {
                    mPending.add(transaction);
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "processTransaction: connResult=APN_REQUEST_STARTED, " +
                                "defer transaction pending MMS connectivity");
                    }
                    return true;
                }
                // If there is already a transaction in processing list, because of the previous
                // beginMmsConnectivity call and there is another transaction just at a time,
                // when the pdp is connected, there will be a case of adding the new transaction
                // to the Processing list. But Processing list is never traversed to
                // resend, resulting in transaction not completed/sent.
                if (mProcessing.size() > 0) {
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Adding transaction to 'mPending' list: " + transaction);
                    }
                    mPending.add(transaction);
                    return true;
                } else {
                    if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "Adding transaction to 'mProcessing' list: " + transaction);
                    }
                    mProcessing.add(transaction);
                }
            }

            // Set a timer to keep renewing our "lease" on the MMS connection
            sendMessageDelayed(obtainMessage(EVENT_CONTINUE_MMS_CONNECTIVITY),
                               APN_EXTENSION_WAIT);

            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "processTransaction: starting transaction " + transaction);
            }

            // Attach to transaction and process it
            transaction.attach(TransactionService.this);
            transaction.process();
            return true;
        }
    }

    private void renewMmsConnectivity() {
        // Set a timer to keep renewing our "lease" on the MMS connection
        mServiceHandler.sendMessageDelayed(
                mServiceHandler.obtainMessage(EVENT_CONTINUE_MMS_CONNECTIVITY),
                           APN_EXTENSION_WAIT);
    }
}
