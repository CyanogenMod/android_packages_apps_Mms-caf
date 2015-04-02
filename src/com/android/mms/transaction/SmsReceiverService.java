/*
 * Copyright (C) 2010-2014, The Linux Foundation. All rights reserved.
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

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.provider.Telephony.Sms.Intents.SMS_DELIVER_ACTION;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.provider.Telephony.Sms.Intents;
import android.provider.Telephony.Sms.Outbox;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.util.BlacklistUtils;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;
import com.android.mms.ui.ClassZeroActivity;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.android.mms.util.Recycler;
import com.android.mms.util.SendingProgressTokenManager;
import com.android.mms.widget.MmsWidgetProvider;
import com.google.android.mms.MmsException;

/**
 * This service essentially plays the role of a "worker thread", allowing us to store
 * incoming messages to the database, update notifications, etc. without blocking the
 * main thread that SmsReceiver runs on.
 */
public class SmsReceiverService extends Service {
    private static final String TAG = LogTag.TAG;
    private final static String SMS_PRIORITY = "priority";
    private static final boolean DEBUG = false;

    private ServiceHandler mServiceHandler;
    private Looper mServiceLooper;
    private boolean mSending;

    public static final String MESSAGE_SENT_ACTION =
        "com.android.mms.transaction.MESSAGE_SENT";

    // Indicates next message can be picked up and sent out.
    public static final String EXTRA_MESSAGE_SENT_SEND_NEXT ="SendNextMsg";

    public static final String ACTION_SEND_MESSAGE =
            "com.android.mms.transaction.SEND_MESSAGE";
    public static final String ACTION_SEND_INACTIVE_MESSAGE =
            "com.android.mms.transaction.SEND_INACTIVE_MESSAGE";

    // This must match the column IDs below.
    private static final String[] SEND_PROJECTION = new String[] {
        Sms._ID,        //0
        Sms.THREAD_ID,  //1
        Sms.ADDRESS,    //2
        Sms.BODY,       //3
        Sms.STATUS,     //4
        Sms.SUBSCRIPTION_ID, //5
        SMS_PRIORITY,   //6
    };

    public Handler mToastHandler = new Handler();

    // This must match SEND_PROJECTION.
    private static final int SEND_COLUMN_ID         = 0;
    private static final int SEND_COLUMN_THREAD_ID  = 1;
    private static final int SEND_COLUMN_ADDRESS    = 2;
    private static final int SEND_COLUMN_BODY       = 3;
    private static final int SEND_COLUMN_STATUS     = 4;
    private static final int SEND_COLUMN_SUB_ID     = 5;
    private static final int SEND_COLUMN_PRIORITY   = 6;

    // SMS sending delay
    private static Uri sCurrentSendingUri = Uri.EMPTY;
    public static final String ACTION_SEND_COUNTDOWN ="com.android.mms.transaction.SEND_COUNTDOWN";
    public static final String DATA_COUNTDOWN = "DATA_COUNTDOWN";
    public static final String DATA_MESSAGE_URI = "DATA_MESSAGE_URI";
    private static final long TIMER_DURATION = 1000;

    // Blacklist support
    private static final String REMOVE_BLACKLIST = "com.android.mms.action.REMOVE_BLACKLIST";
    private static final String EXTRA_NUMBER = "number";
    private static final String EXTRA_FROM_NOTIFICATION = "fromNotification";
    private static final int BLACKLISTED_MESSAGE_NOTIFICATION = 119911;

    // Used to track blacklisted messages
    private static class BlacklistedMessageInfo {
        String number;
        long date;
        int matchType;

        BlacklistedMessageInfo(String number, long date, int matchType) {
            this.number = number;
            this.date = date;
            this.matchType = matchType;
        }
    };
    private ArrayList<BlacklistedMessageInfo> mBlacklistedMessages =
            new ArrayList<BlacklistedMessageInfo>();

    @Override
    public void onCreate() {
        // Temporarily removed for this duplicate message track down.
//        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
//            Log.v(TAG, "onCreate");
//        }

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!MmsConfig.isSmsEnabled(this)) {
            Log.d(TAG, "SmsReceiverService: is not the default sms app");
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            SmsReceiver.finishStartingService(SmsReceiverService.this, startId);
            return Service.START_NOT_STICKY;
        }
        // Temporarily removed for this duplicate message track down.

        int resultCode = intent != null ? intent.getIntExtra("result", 0) : 0;

        if (resultCode != 0) {
            Log.v(TAG, "onStart: #" + startId + " resultCode: " + resultCode +
                    " = " + translateResultCode(resultCode));
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        return Service.START_NOT_STICKY;
    }

    private static String translateResultCode(int resultCode) {
        switch (resultCode) {
            case Activity.RESULT_OK:
                return "Activity.RESULT_OK";
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                return "SmsManager.RESULT_ERROR_GENERIC_FAILURE";
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return "SmsManager.RESULT_ERROR_RADIO_OFF";
            case SmsManager.RESULT_ERROR_NULL_PDU:
                return "SmsManager.RESULT_ERROR_NULL_PDU";
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return "SmsManager.RESULT_ERROR_NO_SERVICE";
            case SmsManager.RESULT_ERROR_LIMIT_EXCEEDED:
                return "SmsManager.RESULT_ERROR_LIMIT_EXCEEDED";
            case SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE:
                return "SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE";
            default:
                return "Unknown error code";
        }
    }

    @Override
    public void onDestroy() {
        // Temporarily removed for this duplicate message track down.
//        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
//            Log.v(TAG, "onDestroy");
//        }
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

        /**
         * Handle incoming transaction requests.
         * The incoming requests are initiated by the MMSC Server or by the MMS Client itself.
         */
        @Override
        public void handleMessage(Message msg) {
            int serviceId = msg.arg1;
            Intent intent = (Intent)msg.obj;
            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "handleMessage serviceId: " + serviceId + " intent: " + intent);
            }
            if (intent != null && MmsConfig.isSmsEnabled(getApplicationContext())) {
                String action = intent.getAction();

                int error = intent.getIntExtra("errorCode", 0);

                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "handleMessage action: " + action + " error: " + error);
                }

                if (MESSAGE_SENT_ACTION.equals(intent.getAction())) {
                    handleSmsSent(intent, error);
                } else if (SMS_DELIVER_ACTION.equals(action)) {
                    handleSmsReceived(intent, error);
                } else if (ACTION_BOOT_COMPLETED.equals(action)) {
                    handleBootCompleted();
                } else if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                    handleServiceStateChanged(intent);
                } else if (ACTION_SEND_MESSAGE.endsWith(action)) {
                    handleSendMessage();
                } else if (ACTION_SEND_INACTIVE_MESSAGE.equals(action)) {
                    handleSendInactiveMessage();
                } else if (REMOVE_BLACKLIST.equals(action)) {
                    if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
                        // Dismiss the notification that brought us here
                        cancelBlacklistedMessageNotification();
                        BlacklistUtils.addOrUpdate(SmsReceiverService.this,
                                intent.getStringExtra(EXTRA_NUMBER),
                                0, BlacklistUtils.BLOCK_MESSAGES);
                    }
                }
            }
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            SmsReceiver.finishStartingService(SmsReceiverService.this, serviceId);
        }
    }

    public static void cancelSendingMessage(Uri messageUri) {
        synchronized (sCurrentSendingUri) {
            if (sCurrentSendingUri.equals(messageUri)) {
                sCurrentSendingUri.notifyAll();
            }
        }
    }

    private void handleServiceStateChanged(Intent intent) {
        // If service just returned, start sending out the queued messages
        ServiceState serviceState = ServiceState.newFromBundle(intent.getExtras());
        long subId = intent.getLongExtra(PhoneConstants.SUBSCRIPTION_KEY, 0);
        long prefSubId = SubscriptionManager.getDefaultSmsSubId();
        // if service state is IN_SERVICE & current subscription is same as
        // preferred SMS subscription.i.e.as set under SIM Settings, then
        // sendFirstQueuedMessage.
        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE &&
            subId == prefSubId) {
            sendFirstQueuedMessage();
        }
    }

    private void handleSendMessage() {
        if (!mSending) {
            sendFirstQueuedMessage();
        }
    }

        private boolean maybeDelaySendingAndCheckForCancel(Uri msgUri) {
            long sendDelay = MessagingPreferenceActivity.getMessageSendDelayDuration(
                    getApplicationContext());
            if (sendDelay <= 0) {
                return false;
            }

            boolean oldSending = mSending;
            boolean sendingCancelled = false;

            try {
                sCurrentSendingUri = msgUri;
                mSending = true;

                int countDown = (int) sendDelay / 1000;
                while (countDown >= 0 && !sendingCancelled) {
                    Intent intent = new Intent(SmsReceiverService.ACTION_SEND_COUNTDOWN);
                    intent.putExtra(DATA_COUNTDOWN, countDown);
                    intent.putExtra(DATA_MESSAGE_URI, msgUri);
                    sendBroadcast(intent);

                    if (countDown > 0) {
                        long start = System.currentTimeMillis();
                        synchronized (sCurrentSendingUri) {
                            sCurrentSendingUri.wait(SmsReceiverService.TIMER_DURATION);
                        }
                        long end = System.currentTimeMillis();
                        if (end - start < SmsReceiverService.TIMER_DURATION) {
                            sendingCancelled = true;
                        }
                        Log.d(TAG, "Delayed send: wait returned after " + (end - start) + " ms");
                    }
                    countDown--;
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "sendFirstQueuedMessage: user cancelled sending " + msgUri);
                sendingCancelled = true;
            } finally {
                sCurrentSendingUri = Uri.EMPTY;
            }

            mSending = oldSending && !sendingCancelled;
            if (sendingCancelled) {
                messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                unRegisterForServiceStateChanges();
                return true;
            }

            return false;
        }

    private void handleSendInactiveMessage() {
        // Inactive messages includes all messages in outbox and queued box.
        moveOutboxMessagesToQueuedBox();
        sendFirstQueuedMessage();
    }

    public synchronized void sendFirstQueuedMessage() {
        boolean success = true;
        // get all the queued messages from the database
        final Uri uri = Uri.parse("content://sms/queued");
        ContentResolver resolver = getContentResolver();
        Cursor c = SqliteWrapper.query(this, resolver, uri,
                        SEND_PROJECTION, null, null, "date ASC");   // date ASC so we send out in
                                                                    // same order the user tried
                                                                    // to send messages.
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String msgText = c.getString(SEND_COLUMN_BODY);
                    String address = c.getString(SEND_COLUMN_ADDRESS);
                    int threadId = c.getInt(SEND_COLUMN_THREAD_ID);
                    int status = c.getInt(SEND_COLUMN_STATUS);

                    int msgId = c.getInt(SEND_COLUMN_ID);
                    int subId = c.getInt(SEND_COLUMN_SUB_ID);
                    int priority = c.getInt(SEND_COLUMN_PRIORITY);
                    Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);

                    if (maybeDelaySendingAndCheckForCancel(msgUri)) {
                        return;
                    }

                    SmsMessageSender sender = new SmsSingleRecipientSender(this,
                            address, msgText, threadId, status == Sms.STATUS_PENDING,
                            msgUri, subId);

                    if(priority != -1){
                        ((SmsSingleRecipientSender)sender).setPriority(priority);
                    }

                    if (LogTag.DEBUG_SEND ||
                            LogTag.VERBOSE ||
                            Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                        Log.v(TAG, "sendFirstQueuedMessage " + msgUri +
                                ", address: " + address +
                                ", threadId: " + threadId);
                    }

                    try {
                        sender.sendMessage(SendingProgressTokenManager.NO_TOKEN);;
                        mSending = true;
                    } catch (MmsException e) {
                        Log.e(TAG, "sendFirstQueuedMessage: failed to send message " + msgUri
                                + ", caught ", e);
                        mSending = false;
                        messageFailedToSend(msgUri, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
                        success = false;
                        // Sending current message fails. Try to send more pending messages
                        // if there is any.
                        sendBroadcast(new Intent(SmsReceiverService.ACTION_SEND_MESSAGE,
                                null,
                                this,
                                SmsReceiver.class));
                    }
                }
            } finally {
                c.close();
            }
        }
        if (success) {
            // We successfully sent all the messages in the queue. We don't need to
            // be notified of any service changes any longer.
            unRegisterForServiceStateChanges();
        }
    }

    private void handleSmsSent(Intent intent, int error) {
        Uri uri = intent.getData();
        int resultCode = intent.getIntExtra("result", 0);
        mSending = false;
        boolean sendNextMsg = intent.getBooleanExtra(EXTRA_MESSAGE_SENT_SEND_NEXT, false);

        if (LogTag.DEBUG_SEND) {
            Log.v(TAG, "handleSmsSent uri: " + uri + " sendNextMsg: " + sendNextMsg +
                    " resultCode: " + resultCode +
                    " = " + translateResultCode(resultCode) + " error: " + error);
        }

        if (resultCode == Activity.RESULT_OK) {
            if (sendNextMsg) {
                if (LogTag.DEBUG_SEND || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "handleSmsSent: move message to sent folder uri: " + uri);
                }
                if (!Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_SENT, error)) {
                    Log.e(TAG, "handleSmsSent: failed to move message " + uri + " to sent folder");
                }
                sendFirstQueuedMessage();
            }

            // Update the notification for failed messages since they may be deleted.
            MessagingNotification.nonBlockingUpdateSendFailedNotification(this);
        } else if ((resultCode == SmsManager.RESULT_ERROR_RADIO_OFF) ||
                (resultCode == SmsManager.RESULT_ERROR_NO_SERVICE)) {
            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "handleSmsSent: no service, queuing message w/ uri: " + uri);
            }
            // We got an error with no service or no radio. Register for state changes so
            // when the status of the connection/radio changes, we can try to send the
            // queued up messages.
            registerForServiceStateChanges();
            // We couldn't send the message, put in the queue to retry later.
            Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_QUEUED, error);
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, getString(R.string.message_queued),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else if (resultCode == SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE) {
            messageFailedToSend(uri, resultCode);
            mToastHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SmsReceiverService.this, getString(R.string.fdn_check_failure),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            messageFailedToSend(uri, error);
            if (sendNextMsg) {
                sendFirstQueuedMessage();
            }
        }
    }

    private void messageFailedToSend(Uri uri, int error) {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "messageFailedToSend msg failed uri: " + uri + " error: " + error);
        }
        Sms.moveMessageToFolder(this, uri, Sms.MESSAGE_TYPE_FAILED, error);
        MessagingNotification.notifySendFailed(getApplicationContext(), true);
    }

    private void handleSmsReceived(Intent intent, int error) {
        SmsMessage[] msgs = Intents.getMessagesFromIntent(intent);
        String format = intent.getStringExtra("format");

        // Because all sub id have been changed to phone id in Mms,
        // so also change it here.
        int saveLoc = MessageUtils.getSmsPreferStoreLocation(this,
                SubscriptionManager.getPhoneId(msgs[0].getSubId()));
        if (getResources().getBoolean(R.bool.config_savelocation)
                && saveLoc == MessageUtils.PREFER_SMS_STORE_CARD) {
            for (int i = 0; i < msgs.length; i++) {
                SmsMessage sms = msgs[i];
                boolean saveSuccess = saveMessageToIcc(sms);
                if (saveSuccess) {
                    int subId = TelephonyManager.getDefault().isMultiSimEnabled()
                            ? sms.getSubId() : MessageUtils.SUB_INVALID;
                    String address = MessageUtils.convertIdp(this,
                            sms.getDisplayOriginatingAddress());
                    MessagingNotification.blockingUpdateNewIccMessageIndicator(
                            this, address, sms.getDisplayMessageBody(), subId,
                            sms.getTimestampMillis());
                    int phoneId = SubscriptionManager.getPhoneId(subId);
                    getContentResolver().notifyChange(
                            MessageUtils.getIccUriBySlot(phoneId), null);
                } else {
                    mToastHandler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.pref_sim_card_full_save_to_phone),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    // save message to phone if failed save to icc.
                    saveMessageToPhone(msgs, error, format);
                    break;
                }
            }
        } else {
            saveMessageToPhone(msgs, error, format);
        }
    }

    private void saveMessageToPhone(SmsMessage[] msgs, int error, String format){
        Uri messageUri = insertMessage(this, msgs, error, format);

        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            SmsMessage sms = msgs[0];
            Log.v(TAG, "handleSmsReceived" + (sms.isReplace() ? "(replace)" : "") +
                    " messageUri: " + messageUri +
                    ", address: " + sms.getOriginatingAddress() +
                    ", body: " + sms.getMessageBody());
        }

        MessageUtils.checkIsPhoneMessageFull(this);

        if (messageUri != null) {
            long threadId = MessagingNotification.getSmsThreadId(this, messageUri);
            // Called off of the UI thread so ok to block.
            Log.d(TAG, "handleSmsReceived messageUri: " + messageUri + " threadId: " + threadId);
            MessagingNotification.blockingUpdateNewMessageIndicator(this, threadId, false);
        }

    }

    private void handleBootCompleted() {
        // Some messages may get stuck in the outbox. At this point, they're probably irrelevant
        // to the user, so mark them as failed and notify the user, who can then decide whether to
        // resend them manually.
        int numMoved = moveOutboxMessagesToFailedBox();
        if (numMoved > 0) {
            MessagingNotification.notifySendFailed(getApplicationContext(), true);
        }

        // Send any queued messages that were waiting from before the reboot.
        sendFirstQueuedMessage();

        // Called off of the UI thread so ok to block.
        MessagingNotification.blockingUpdateNewMessageIndicator(
                this, MessagingNotification.THREAD_ALL, false);
    }

    /**
     * Move all messages that are in the outbox to the queued state
     * @return The number of messages that were actually moved
     */
    private int moveOutboxMessagesToQueuedBox() {
        ContentValues values = new ContentValues(1);

        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_QUEUED);

        int messageCount = SqliteWrapper.update(
                getApplicationContext(), getContentResolver(), Outbox.CONTENT_URI,
                values, "type = " + Sms.MESSAGE_TYPE_OUTBOX, null);
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "moveOutboxMessagesToQueuedBox messageCount: " + messageCount);
        }
        return messageCount;
    }

    /**
     * Move all messages that are in the outbox to the failed state and set them to unread.
     * @return The number of messages that were actually moved
     */
    private int moveOutboxMessagesToFailedBox() {
        ContentValues values = new ContentValues(3);

        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_FAILED);
        values.put(Sms.ERROR_CODE, SmsManager.RESULT_ERROR_GENERIC_FAILURE);
        values.put(Sms.READ, Integer.valueOf(0));

        int messageCount = SqliteWrapper.update(
                getApplicationContext(), getContentResolver(), Outbox.CONTENT_URI,
                values, "type = " + Sms.MESSAGE_TYPE_OUTBOX, null);
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "moveOutboxMessagesToFailedBox messageCount: " + messageCount);
        }
        return messageCount;
    }

    public static final String CLASS_ZERO_BODY_KEY = "CLASS_ZERO_BODY";

    // This must match the column IDs below.
    private final static String[] REPLACE_PROJECTION = new String[] {
        Sms._ID,
        Sms.ADDRESS,
        Sms.PROTOCOL
    };

    // This must match REPLACE_PROJECTION.
    private static final int REPLACE_COLUMN_ID = 0;

    /**
     * If the message is a class-zero message, display it immediately
     * and return null.  Otherwise, store it using the
     * <code>ContentResolver</code> and return the
     * <code>Uri</code> of the thread containing this message
     * so that we can use it for notification.
     */
    private Uri insertMessage(Context context, SmsMessage[] msgs, int error, String format) {
        // Build the helper classes to parse the messages.
        SmsMessage sms = msgs[0];

        if (sms.getMessageClass() == SmsMessage.MessageClass.CLASS_0) {
            displayClassZeroMessage(context, sms, format);
            return null;
        } else if (sms.isReplace()) {
            return replaceMessage(context, msgs, error);
        } else if (isBlacklisted(context, sms.getOriginatingAddress(), sms.getTimestampMillis())) {
            return null;
        } else {
            return storeMessage(context, msgs, error);
        }
    }

    private boolean isBlacklisted(Context context, String number, long date) {
        if (DEBUG) {
            Log.d(TAG, "isBlacklisted(). number: " + number
                + ", date: " + date + " is being checked against the blacklist");
        }

        int listType = BlacklistUtils.isListed(context, number, BlacklistUtils.BLOCK_MESSAGES);
        if (listType != BlacklistUtils.MATCH_NONE) {
            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
                Log.v(TAG, "Incoming message from " + number + " blocked.");
            }
            showBlacklistNotification(context, number, date, listType);
            return true;
        }
        return false;
    }

    private void showBlacklistNotification(Context context, String number, long date, int matchType) {
        if (!BlacklistUtils.isBlacklistNotifyEnabled(context)) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "notifyBlacklistedCall(). number: " + number
                + ", match type: " + matchType + ", date: " + date);
        }

        // Keep track of the message, keeping list sorted from newest to oldest
        mBlacklistedMessages.add(0, new BlacklistedMessageInfo(number, date, matchType));

        // Get the intent to open Blacklist settings if user taps on content ready
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings", "com.android.settings.Settings$BlacklistSettingsActivity");
        PendingIntent blSettingsIntent = PendingIntent.getActivity(context, 0, intent, 0);

        // Start building the notification
        Notification.Builder builder = new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_block_message_holo_dark)
                .setContentIntent(blSettingsIntent)
                .setContentTitle(context.getString(R.string.blacklist_title))
                .setColor(context.getResources().getColor(R.color.mms_theme_color))
                .setWhen(date);

        // Add the 'Remove block' notification action only for MATCH_LIST items since
        // MATCH_REGEX and MATCH_PRIVATE items does not have an associated specific number
        // to unblock, and MATCH_UNKNOWN unblock for a single number does not make sense.
        boolean addUnblockAction = true;

        if (mBlacklistedMessages.size() == 1) {
            String message;
            switch (matchType) {
                case BlacklistUtils.MATCH_PRIVATE:
                    message = context.getString(R.string.blacklist_notification_private_number);
                    break;
                case BlacklistUtils.MATCH_UNKNOWN:
                    message = context.getString(R.string.blacklist_notification_unknown_number, number);
                    break;
                default:
                    message = context.getString(R.string.blacklist_notification, number);
            }
            builder.setContentText(message);

            if (matchType != BlacklistUtils.MATCH_LIST) {
                addUnblockAction = false;
            }
        } else {
            String message = context.getString(R.string.blacklist_notification_multiple,
                    mBlacklistedMessages.size());

            builder.setContentText(message)
                    .setNumber(mBlacklistedMessages.size());

            Notification.InboxStyle style = new Notification.InboxStyle(builder);

            for (BlacklistedMessageInfo info : mBlacklistedMessages) {
                // Takes care of displaying "Private" instead of an empty string
                String numberString = TextUtils.isEmpty(info.number)
                        ? context.getString(R.string.blacklist_notification_list_private)
                        : info.number;
                style.addLine(formatSingleCallLine(numberString, info.date));

                if (!TextUtils.equals(number, info.number)) {
                    addUnblockAction = false;
                } else if (info.matchType != BlacklistUtils.MATCH_LIST) {
                    addUnblockAction = false;
                }
            }
            style.setBigContentTitle(message);
            style.setSummaryText(" ");
            builder.setStyle(style);
        }

        if (addUnblockAction) {
            CharSequence action = context.getText(R.string.unblock_number);
            builder.addAction(R.drawable.ic_unblock_message_holo_dark,
                    context.getString(R.string.unblock_number),
                    getUnblockNumberFromNotificationPendingIntent(context, number));
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(BLACKLISTED_MESSAGE_NOTIFICATION, builder.getNotification());
    }

    private void cancelBlacklistedMessageNotification() {
        mBlacklistedMessages.clear();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(BLACKLISTED_MESSAGE_NOTIFICATION);
    }

    private PendingIntent getUnblockNumberFromNotificationPendingIntent(Context context, String number) {
        Intent intent = new Intent(REMOVE_BLACKLIST);
        intent.putExtra(EXTRA_NUMBER, number);
        intent.putExtra(EXTRA_FROM_NOTIFICATION, true);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static final RelativeSizeSpan TIME_SPAN = new RelativeSizeSpan(0.7f);

    private CharSequence formatSingleCallLine(String caller, long date) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        if (!DateUtils.isToday(date)) {
            flags |= DateUtils.FORMAT_SHOW_WEEKDAY;
        }

        SpannableStringBuilder lineBuilder = new SpannableStringBuilder();
        lineBuilder.append(caller);
        lineBuilder.append("  ");

        int timeIndex = lineBuilder.length();
        lineBuilder.append(DateUtils.formatDateTime(getApplicationContext(), date, flags));
        lineBuilder.setSpan(TIME_SPAN, timeIndex, lineBuilder.length(), 0);

        return lineBuilder;
    }

    /**
     * This method is used if this is a "replace short message" SMS.
     * We find any existing message that matches the incoming
     * message's originating address and protocol identifier.  If
     * there is one, we replace its fields with those of the new
     * message.  Otherwise, we store the new message as usual.
     *
     * See TS 23.040 9.2.3.9.
     */
    private Uri replaceMessage(Context context, SmsMessage[] msgs, int error) {
        SmsMessage sms = msgs[0];
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Inbox.BODY, replaceFormFeeds(sms.getDisplayMessageBody()));
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                if (sms.mWrappedSmsMessage != null) {
                    body.append(sms.getDisplayMessageBody());
                }
            }
            values.put(Inbox.BODY, replaceFormFeeds(body.toString()));
        }

        ContentResolver resolver = context.getContentResolver();
        String originatingAddress = MessageUtils.convertIdp(this, sms.getOriginatingAddress());
        int protocolIdentifier = sms.getProtocolIdentifier();
        String selection;
        String[] selectionArgs;

        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, " SmsReceiverService: replaceMessage:");
        }
        selection = Sms.ADDRESS + " = ? AND " +
                    Sms.PROTOCOL + " = ? AND " +
                    Sms.SUBSCRIPTION_ID +  " = ? ";
        selectionArgs = new String[] {
                originatingAddress, Integer.toString(protocolIdentifier),
                Integer.toString(sms.getSubId())
            };

        Cursor cursor = SqliteWrapper.query(context, resolver, Inbox.CONTENT_URI,
                            REPLACE_PROJECTION, selection, selectionArgs, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long messageId = cursor.getLong(REPLACE_COLUMN_ID);
                    Uri messageUri = ContentUris.withAppendedId(
                            Sms.CONTENT_URI, messageId);

                    SqliteWrapper.update(context, resolver, messageUri,
                                        values, null, null);
                    return messageUri;
                }
            } finally {
                cursor.close();
            }
        }
        return storeMessage(context, msgs, error);
    }

    public static String replaceFormFeeds(String s) {
        // Some providers send formfeeds in their messages. Convert those formfeeds to newlines.
        return s == null ? "" : s.replace('\f', '\n');
    }

//    private static int count = 0;

    private Uri storeMessage(Context context, SmsMessage[] msgs, int error) {
        // Check to see whether short message count is up to 2000 for cmcc
        if (MessageUtils.checkIsPhoneMessageFull(this)) {
            return null;
        }

        SmsMessage sms = msgs[0];
        int subId = sms.getSubId();

        // Store the message in the content provider.
        ContentValues values = extractContentValues(sms);
        values.put(Sms.ERROR_CODE, error);
        values.put(Sms.PHONE_ID, SubscriptionManager.getPhoneId(subId));
        values.put(Sms.SUBSCRIPTION_ID, subId);

        int pduCount = msgs.length;

        if (pduCount == 1) {
            // There is only one part, so grab the body directly.
            values.put(Inbox.BODY, replaceFormFeeds(sms.getDisplayMessageBody()));
        } else {
            // Build up the body from the parts.
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < pduCount; i++) {
                sms = msgs[i];
                if (sms.mWrappedSmsMessage != null) {
                    body.append(sms.getDisplayMessageBody());
                }
            }
            values.put(Inbox.BODY, replaceFormFeeds(body.toString()));
        }

        // Make sure we've got a thread id so after the insert we'll be able to delete
        // excess messages.
        Long threadId = values.getAsLong(Sms.THREAD_ID);
        String address = values.getAsString(Sms.ADDRESS);

        // Code for debugging and easy injection of short codes, non email addresses, etc.
        // See Contact.isAlphaNumber() for further comments and results.
//        switch (count++ % 8) {
//            case 0: address = "AB12"; break;
//            case 1: address = "12"; break;
//            case 2: address = "Jello123"; break;
//            case 3: address = "T-Mobile"; break;
//            case 4: address = "Mobile1"; break;
//            case 5: address = "Dogs77"; break;
//            case 6: address = "****1"; break;
//            case 7: address = "#4#5#6#"; break;
//        }

        if (!TextUtils.isEmpty(address)) {
            Contact cacheContact = Contact.get(address,true);
            if (cacheContact != null) {
                address = cacheContact.getNumber();
            }
        } else {
            address = getString(R.string.unknown_sender);
            values.put(Sms.ADDRESS, address);
        }

        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            threadId = Conversation.getOrCreateThreadId(context, address);
            values.put(Sms.THREAD_ID, threadId);
        }

        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        // Now make sure we're not over the limit in stored messages
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);
        MmsWidgetProvider.notifyDatasetChanged(context);

        return insertedUri;
    }

    /**
     * Extract all the content values except the body from an SMS
     * message.
     */
    private ContentValues extractContentValues(SmsMessage sms) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();

        values.put(Inbox.ADDRESS, MessageUtils.convertIdp(this, sms.getDisplayOriginatingAddress()));

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        // Check to make sure the system is giving us a non-bogus time.
        Calendar buildDate = new GregorianCalendar(2011, 8, 18);    // 18 Sep 2011
        Calendar nowDate = new GregorianCalendar();
        long now = System.currentTimeMillis();
        nowDate.setTimeInMillis(now);

        if (nowDate.before(buildDate)) {
            // It looks like our system clock isn't set yet because the current time right now
            // is before an arbitrary time we made this build. Instead of inserting a bogus
            // receive time in this case, use the timestamp of when the message was sent.
            now = sms.getTimestampMillis();
        }

        values.put(Inbox.DATE, new Long(now));
        values.put(Inbox.DATE_SENT, Long.valueOf(sms.getTimestampMillis()));
        values.put(Inbox.PROTOCOL, sms.getProtocolIdentifier());
        values.put(Inbox.READ, 0);
        values.put(Inbox.SEEN, 0);
        if (sms.getPseudoSubject().length() > 0) {
            values.put(Inbox.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Inbox.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);
        values.put(Inbox.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    /**
     * Displays a class-zero message immediately in a pop-up window
     * with the number from where it received the Notification with
     * the body of the message
     *
     */
    private void displayClassZeroMessage(Context context, SmsMessage sms, String format) {
        // Using NEW_TASK here is necessary because we're calling
        // startActivity from outside an activity.
        Intent smsDialogIntent = new Intent(context, ClassZeroActivity.class)
                .putExtra("pdu", sms.getPdu())
                .putExtra("format", format)
                .putExtra(PhoneConstants.SUBSCRIPTION_KEY, sms.getSubId())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                          | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        context.startActivity(smsDialogIntent);
    }

    private void registerForServiceStateChanges() {
        Context context = getApplicationContext();
        unRegisterForServiceStateChanges();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "registerForServiceStateChanges");
        }

        context.registerReceiver(SmsReceiver.getInstance(), intentFilter);
    }

    private void unRegisterForServiceStateChanges() {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE) || LogTag.DEBUG_SEND) {
            Log.v(TAG, "unRegisterForServiceStateChanges");
        }
        try {
            Context context = getApplicationContext();
            context.unregisterReceiver(SmsReceiver.getInstance());
        } catch (IllegalArgumentException e) {
            // Allow un-matched register-unregister calls
        }
    }

    private boolean saveMessageToIcc(SmsMessage sms) {
        boolean result = true;
        int subscription = sms.getSubId();
        String address = MessageUtils.convertIdp(this, sms.getOriginatingAddress());
        byte pdu[] = MessageUtils.getDeliveryPdu(null, address,
                sms.getMessageBody(), sms.getTimestampMillis(), subscription);
        result &= TelephonyManager.getDefault().isMultiSimEnabled()
                ? SmsManager.getSmsManagerForSubscriptionId(subscription)
                    .copyMessageToIcc(null, pdu, SmsManager.STATUS_ON_ICC_READ)
                : SmsManager.getDefault()
                    .copyMessageToIcc(null, pdu, SmsManager.STATUS_ON_ICC_READ);
        return result;
    }
}
