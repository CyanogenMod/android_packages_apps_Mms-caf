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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony.Mms;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.ui.MessagingPreferenceActivity;

/**
 * MmsSystemEventReceiver receives the
 * {@link android.content.intent.ACTION_BOOT_COMPLETED},
 * {@link com.android.internal.telephony.TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED}
 * and performs a series of operations which may include:
 * <ul>
 * <li>Show/hide the icon in notification area which is used to indicate
 * whether there is new incoming message.</li>
 * <li>Resend the MM's in the outbox.</li>
 * </ul>
 */
public class MmsSystemEventReceiver extends BroadcastReceiver {
    private static final String TAG = LogTag.TAG;
    private static ConnectivityManager mConnMgr = null;

    public static void wakeUpService(Context context) {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "wakeUpService: start transaction service ...");
        }

        context.startService(new Intent(context, TransactionService.class));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.v(TAG, "Intent received: " + intent);
        }

        String action = intent.getAction();
        if (action.equals(Mms.Intents.CONTENT_CHANGED_ACTION)) {
            Uri changed = (Uri) intent.getParcelableExtra(Mms.Intents.DELETED_CONTENTS);
            MmsApp.getApplication().getPduLoaderManager().removePdu(changed);
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            if (mConnMgr == null) {
                mConnMgr = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
            }
            boolean autoEnableData = isAutoEnableData(context) && !isAirplaneModeOn(context);
            if (!mConnMgr.getMobileDataEnabled() && !autoEnableData) {
                if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                    Log.v(TAG, "mobile data unavailable, bailing");
                }
                return;
            }
            NetworkInfo mmsNetworkInfo = mConnMgr
                    .getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);
            if (mmsNetworkInfo == null) {
                return;
            }
            boolean available = mmsNetworkInfo.isAvailable();
            boolean isConnected = mmsNetworkInfo.isConnected();

            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                Log.v(TAG, "TYPE_MOBILE_MMS available = " + available +
                           ", isConnected = " + isConnected);
            }

            if ((available || autoEnableData) && !isConnected) {
                wakeUpService(context);
            }
        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            // We should check whether there are unread incoming
            // messages in the Inbox and then update the notification icon.
            // Called on the UI thread so don't block.
            MessagingNotification.nonBlockingUpdateNewMessageIndicator(
                    context, MessagingNotification.THREAD_NONE, false);

            // Scan and send pending Mms once after boot completed since
            // ACTION_ANY_DATA_CONNECTION_STATE_CHANGED wasn't registered in a whole life cycle
            wakeUpService(context);
        } else if (action.equals(TelephonyIntents.ACTION_SUBSCRIPTION_SET_UICC_RESULT)) {

            int status = intent.getIntExtra(TelephonyIntents.EXTRA_RESULT, PhoneConstants.FAILURE);
            int state = intent.getIntExtra(TelephonyIntents.EXTRA_NEW_SUB_STATE,
                    SubscriptionManager.INACTIVE);
            int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, PhoneConstants.PHONE_ID1);
            Log.d(TAG, "ACTION_SUBSCRIPTION_SET_UICC_RESULT status = " + status + ", state = "
                    + state + " phoneId: " + phoneId);
            // Scan and send pending Mms if subscription is activated
            if (status == PhoneConstants.SUCCESS && state == SubscriptionManager.ACTIVE) {
                wakeUpService(context);
            }
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            // Wake up transact service upon leaving airplane mode if auto-enable data
            if (isAutoEnableData(context) && !isAirplaneModeOn(context)) {
                wakeUpService(context);
            }
        }
    }

    private boolean isAutoEnableData(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(MessagingPreferenceActivity.AUTO_ENABLE_DATA, false);
    }

    private boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
}
