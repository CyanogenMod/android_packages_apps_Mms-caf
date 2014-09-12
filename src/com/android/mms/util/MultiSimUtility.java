/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.util;

import android.content.Context;
import android.content.Intent;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import com.android.internal.telephony.MSimConstants;

/**
 * The MultiSimActivity is responsible for getting current data subscription.
 */
public class MultiSimUtility {
    private static final String TAG = "MultiSimUtility";
    public static final String ORIGIN_SUB_ID = "origin_sub_id";

    public static int getCurrentDataSubscription(Context mContext) {

        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimTelephonyManager mtmgr = (MSimTelephonyManager)
                    mContext.getSystemService (Context.MSIM_TELEPHONY_SERVICE);
            return mtmgr.getPreferredDataSubscription();
        } else {
            return 0;
        }
    }

    public static int getDefaultDataSubscription(Context mContext) {

        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimTelephonyManager mtmgr = (MSimTelephonyManager)
                    mContext.getSystemService (Context.MSIM_TELEPHONY_SERVICE);
            return mtmgr.getDefaultDataSubscription();
        } else {
            return 0;
        }
    }

    public static void startSelectMmsSubsciptionServ(Context mContext, Intent svc) {
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            Log.d(TAG, "MMS silent transaction");
            Intent silentIntent = new Intent(mContext,
                    com.android.mms.ui.SelectMmsSubscription.class);
            silentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            silentIntent.putExtras(svc); //copy all extras
            mContext.startService(silentIntent);

        } else {
            mContext.startService(svc);
        }
    }

    /**
     * Returns the preference name for the given subscription
     *
     * @param baseKey Name of the base preference key
     * @param subscription subId or -1 for no sub
     * @return preference key string
     */
    public static String getPreferenceKey(String baseKey, int subscription) {
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled() ||
                subscription == MSimConstants.INVALID_SUBSCRIPTION) {
            return baseKey;
        } else {
            return baseKey + "_slot" + (subscription+1);
        }
    }
}
