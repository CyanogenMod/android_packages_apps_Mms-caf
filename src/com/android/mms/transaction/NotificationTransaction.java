/*
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

import static com.android.mms.transaction.TransactionState.FAILED;
import static com.android.mms.transaction.TransactionState.INITIALIZED;
import static com.android.mms.transaction.TransactionState.SUCCESS;
import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF;
import static com.google.android.mms.pdu.PduHeaders.STATUS_DEFERRED;
import static com.google.android.mms.pdu.PduHeaders.STATUS_RETRIEVED;
import static com.google.android.mms.pdu.PduHeaders.STATUS_UNRECOGNIZED;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Inbox;
import android.provider.Telephony.Threads;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.Recycler;
import com.android.mms.widget.MmsWidgetProvider;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.NotifyRespInd;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * The NotificationTransaction is responsible for handling multimedia
 * message notifications (M-Notification.ind).  It:
 *
 * <ul>
 * <li>Composes the notification response (M-NotifyResp.ind).
 * <li>Sends the notification response to the MMSC server.
 * <li>Stores the notification indication.
 * <li>Notifies the TransactionService about succesful completion.
 * </ul>
 *
 * NOTE: This MMS client handles all notifications with a <b>deferred
 * retrieval</b> response.  The transaction service, upon succesful
 * completion of this transaction, will trigger a retrieve transaction
 * in case the client is in immediate retrieve mode.
 */
public class NotificationTransaction extends Transaction implements Runnable {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    private Uri mUri;
    private NotificationInd mNotificationInd;
    private String mContentLocation;

    public NotificationTransaction(
            Context context, int serviceId,
            TransactionSettings connectionSettings, String uriString, int subId) {
        super(context, serviceId, connectionSettings, subId);

        mUri = Uri.parse(uriString);

        try {
            mNotificationInd = (NotificationInd)
                    PduPersister.getPduPersister(context).load(mUri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load NotificationInd from: " + uriString, e);
            throw new IllegalArgumentException();
        }

        mContentLocation = new String(mNotificationInd.getContentLocation());
        mId = mContentLocation;

        // Attach the transaction to the instance of RetryScheduler.
        attach(RetryScheduler.getInstance(context));
    }

    /**
     * This constructor is only used for test purposes.
     */
    public NotificationTransaction(
            Context context, int serviceId,
            TransactionSettings connectionSettings, NotificationInd ind, int subId) {
        super(context, serviceId, connectionSettings, subId);

        try {
            // Save the pdu. If we can start downloading the real pdu immediately, don't allow
            // persist() to create a thread for the notificationInd because it causes UI jank.
            mUri = PduPersister.getPduPersister(context).persist(
                        ind, Inbox.CONTENT_URI, !allowAutoDownload(),
                        MessagingPreferenceActivity.getIsGroupMmsEnabled(context), null);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to save NotificationInd in constructor.", e);
            throw new IllegalArgumentException();
        }

        mNotificationInd = ind;
        mId = new String(mNotificationInd.getContentLocation());
    }

    /*
     * (non-Javadoc)
     * @see com.google.android.mms.pdu.Transaction#process()
     */
    @Override
    public void process() {
        new Thread(this, "NotificationTransaction").start();
    }

    public static boolean allowAutoDownload() {
        DownloadManager downloadManager = DownloadManager.getInstance();
        boolean autoDownload = downloadManager.isAuto();
        boolean dataSuspended = (MmsApp.getApplication().getTelephonyManager().getDataState() ==
                TelephonyManager.DATA_SUSPENDED);
        return autoDownload && !dataSuspended;
    }

    public static boolean isMmsSizeTooLarge(NotificationInd nInd) {
        int currentMmsSize = (int) nInd.getMessageSize();
        int maxSize = MmsConfig.getMaxMessageSize();
        return currentMmsSize > maxSize;
    }

    public void run() {
        DownloadManager downloadManager = DownloadManager.getInstance();
        boolean autoDownload = allowAutoDownload();
        boolean isMemoryFull = MessageUtils.isMmsMemoryFull();
        boolean isTooLarge = isMmsSizeTooLarge(mNotificationInd);
        try {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Notification transaction launched: " + this);
            }

            // By default, we set status to STATUS_DEFERRED because we
            // should response MMSC with STATUS_DEFERRED when we cannot
            // download a MM immediately.
            int status = STATUS_DEFERRED;
            // Don't try to download when data is suspended, as it will fail, so defer download
            if (!autoDownload) {
                downloadManager.markState(mUri, DownloadManager.STATE_UNSTARTED);
                sendNotifyRespInd(status);
                return;
            }

            if (isMemoryFull || isTooLarge) {
                downloadManager.markState(mUri, DownloadManager.STATE_TRANSIENT_FAILURE);
                sendNotifyRespInd(status);
                return;
            }

            downloadManager.markState(mUri, DownloadManager.STATE_DOWNLOADING);

            if (LOCAL_LOGV) {
                Log.v(TAG, "Content-Location: " + mContentLocation);
            }

            byte[] retrieveConfData = null;
            // We should catch exceptions here to response MMSC
            // with STATUS_DEFERRED.
            try {
                retrieveConfData = getPdu(mContentLocation);
            } catch (IOException e) {
                mTransactionState.setState(FAILED);
            }

            if (retrieveConfData != null) {
                GenericPdu pdu = new PduParser(
                        retrieveConfData, PduParserUtil.shouldParseContentDisposition()).parse();
                if ((pdu == null) || (pdu.getMessageType() != MESSAGE_TYPE_RETRIEVE_CONF)) {
                    Log.e(TAG, "Invalid M-RETRIEVE.CONF PDU. " +
                            (pdu != null ? "message type: " + pdu.getMessageType() : "null pdu"));
                    mTransactionState.setState(FAILED);
                    status = STATUS_UNRECOGNIZED;
                } else {

                    /**
                     * Modifying group messaging parsing. This has to be done here as we can't
                     * modify the framework nor external dependencies for this release - we are
                     * updating just the app through the Updater. Pdu parsing utilities are in
                     * framework/opt and are hence subject to the limitations outlined earlier.
                     *
                     * 1. MessagingPreferenceActivity#getIsGroupMmsEnabled 's behavior has been
                     *    modified to not check whether the sim holds its phone number. Instead,
                     *    the phone number for the sim should be obtained from
                     *    MessageUtils#getPhoneNumber.
                     *
                     * 2. The number of address entries in PduHeaders.TO and PduHeaders.CC *MUST* be
                     *    greater than or equal to 2 - artificially boost the address field count
                     *    if need be. This is to circumvent the shortcut in
                     *    PduPersister#loadRecipients, wherein an incorrect assumption is made, that
                     *    if the address field only contains 1 entry, then it is the current user's
                     *    phone number.
                     */

                    Method getHeaderMethod = GenericPdu.class.getDeclaredMethod("getPduHeaders");
                    Method getEncodedStringValuesMethod = PduHeaders.class.
                            getDeclaredMethod("getEncodedStringValues", int.class);
                    Method setEncodedStringValuesMethod = PduHeaders.class.
                            getDeclaredMethod("setEncodedStringValues", EncodedStringValue[].class,
                                    int.class);

                    getHeaderMethod.setAccessible(true);
                    getEncodedStringValuesMethod.setAccessible(true);
                    setEncodedStringValuesMethod.setAccessible(true);

                    String simPhoneNumber = MessageUtils.getPhoneNumber(mContext, mSubId);
                    PduHeaders pduHeaders = (PduHeaders) getHeaderMethod.invoke(pdu);

                    EncodedStringValue[] toAddresses = (EncodedStringValue[])
                            getEncodedStringValuesMethod.invoke(pduHeaders, PduHeaders.TO);
                    EncodedStringValue[] ccAddresses = (EncodedStringValue[])
                            getEncodedStringValuesMethod.invoke(pduHeaders, PduHeaders.CC);
                    EncodedStringValue[] modifiedAddresses;

                    int numOfAddresses = (toAddresses != null ? toAddresses.length : 0) +
                            (ccAddresses != null ? ccAddresses.length : 0);

                    if (numOfAddresses > 1) {   // could be a group msg

                        // filter TO addresses
                        ArrayList<EncodedStringValue> filteredAddresses = removeSelfFromAddressList(
                                toAddresses, simPhoneNumber);

                        if (filteredAddresses != null) {
                            // *need* to do this , see comment 2 in the comment block above
                            if (filteredAddresses.size() <= 1) filteredAddresses.add(pdu.getFrom());
                            modifiedAddresses = filteredAddresses.toArray(
                                    new EncodedStringValue[filteredAddresses.size()]);
                            // add the "new" addresses to the pdu header
                            setEncodedStringValuesMethod.invoke(pduHeaders, modifiedAddresses,
                                    PduHeaders.TO);
                        }

                        // filter CC addresses
                        filteredAddresses = removeSelfFromAddressList(ccAddresses, simPhoneNumber);
                        if (filteredAddresses != null) {
                            // *need* to do this , see comment 2 in the comment block above
                            if (filteredAddresses.size() <= 1) filteredAddresses.add(pdu.getFrom());
                            modifiedAddresses = filteredAddresses.toArray(
                                    new EncodedStringValue[filteredAddresses.size()]);
                            // add the "new" addresses to the pdu header
                            setEncodedStringValuesMethod.invoke(pduHeaders, modifiedAddresses,
                                    PduHeaders.CC);
                        }
                    }

                    // Save the received PDU (must be a M-RETRIEVE.CONF).
                    PduPersister p = PduPersister.getPduPersister(mContext);
                    Uri uri = p.persist(pdu, Inbox.CONTENT_URI, true,
                            MessagingPreferenceActivity.getIsGroupMmsEnabled(mContext), null);

                    // Use local time instead of PDU time
                    ContentValues values = new ContentValues(4);
                    values.put(Mms.DATE, System.currentTimeMillis() / 1000L);
                    values.put(Mms.SUBSCRIPTION_ID, mSubId);
                    values.put(Mms.PHONE_ID, SubscriptionManager.getPhoneId(mSubId));

                    // Update Message Size for Original MMS.
                    values.put(Mms.MESSAGE_SIZE, mNotificationInd.getMessageSize());
                    SqliteWrapper.update(mContext, mContext.getContentResolver(),
                            uri, values, null, null);

                    // We have successfully downloaded the new MM. Delete the
                    // M-NotifyResp.ind from Inbox.
                    SqliteWrapper.delete(mContext, mContext.getContentResolver(),
                                         mUri, null, null);
                    Log.v(TAG, "NotificationTransaction received new mms message: " + uri);
                    // Delete obsolete threads
                    SqliteWrapper.delete(mContext, mContext.getContentResolver(),
                            Threads.OBSOLETE_THREADS_URI, null, null);

                    // Notify observers with newly received MM.
                    mUri = uri;
                    status = STATUS_RETRIEVED;
                }
            }

            if (LOCAL_LOGV) {
                Log.v(TAG, "status=0x" + Integer.toHexString(status));
            }

            // Check the status and update the result state of this Transaction.
            switch (status) {
                case STATUS_RETRIEVED:
                    mTransactionState.setState(SUCCESS);
                    break;
                case STATUS_DEFERRED:
                    // STATUS_DEFERRED, may be a failed immediate retrieval.
                    if (mTransactionState.getState() == INITIALIZED) {
                        mTransactionState.setState(SUCCESS);
                    }
                    break;
            }

            sendNotifyRespInd(status);

            // Make sure this thread isn't over the limits in message count.
            Recycler.getMmsRecycler().deleteOldMessagesInSameThreadAsMessage(mContext, mUri);
            MmsWidgetProvider.notifyDatasetChanged(mContext);
        } catch (Throwable t) {
            Log.e(TAG, Log.getStackTraceString(t));
        } finally {
            mTransactionState.setContentUri(mUri);
            if (!autoDownload || isMemoryFull || isTooLarge) {
                // Always mark the transaction successful for deferred
                // download since any error here doesn't make sense.
                mTransactionState.setState(SUCCESS);
            }
            if (mTransactionState.getState() != SUCCESS) {
                mTransactionState.setState(FAILED);
                Log.e(TAG, "NotificationTransaction failed.");
            }
            notifyObservers();
        }
    }

    private ArrayList<EncodedStringValue> removeSelfFromAddressList(EncodedStringValue[] addressList
            , String myNumber) {
        if (addressList == null) return null;
        ArrayList<EncodedStringValue> modifiedAddressList = new ArrayList<>();
        for (EncodedStringValue encodedStringValue : addressList) {
            if (!encodedStringValue.getString().equals(myNumber)) {
                modifiedAddressList.add(encodedStringValue);
            }
        }

        return modifiedAddressList;
    }

    private void sendNotifyRespInd(int status) throws MmsException, IOException {
        // Create the M-NotifyResp.ind
        NotifyRespInd notifyRespInd = new NotifyRespInd(
                PduHeaders.CURRENT_MMS_VERSION,
                mNotificationInd.getTransactionId(),
                status);

        // Pack M-NotifyResp.ind and send it
        if(MmsConfig.getNotifyWapMMSC()) {
            sendPdu(new PduComposer(mContext, notifyRespInd).make(), mContentLocation);
        } else {
            sendPdu(new PduComposer(mContext, notifyRespInd).make());
        }
    }

    @Override
    public void abort() {
        Log.d(TAG, "markFailed = " + this);
        DownloadManager downloadManager = DownloadManager.getInstance();

        downloadManager.markState(mUri, DownloadManager.STATE_SKIP_RETRYING);
        notifyObservers();
    }

    @Override
    public int getType() {
        return NOTIFICATION_TRANSACTION;
    }
}
