/*
 * Copyright (C) 2012 Adam K
 * Modifications Copyright (C) 2012 the CyanogenMod Project
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

package com.android.mms.quickmessage;

import android.content.Context;
import android.os.PowerManager;

public class ManageWakeLock {
    private static String LOG_TAG = "ManageWakelock";

    private static volatile PowerManager.WakeLock sWakeLock = null;
    private static volatile  PowerManager.WakeLock sPartialWakeLock = null;
    private static final int TIMEOUT = 30;

    public static synchronized void acquireFull(Context context) {
        if (sWakeLock != null) {
            return;
        }

        ManageKeyguard.disableKeyguard();

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        sWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP, LOG_TAG + ".full");
        sWakeLock.setReferenceCounted(false);
        sWakeLock.acquire();

        // Set a receiver to remove all locks in "timeout" seconds
        ClearAllReceiver.setCancel(context, TIMEOUT);
    }

    public static synchronized void pokeWakeLock(Context context) {
        if (sWakeLock == null) {
            return;
        }
        // Reset the receiver to remove all locks in "timeout" seconds
        ClearAllReceiver.setCancel(context, TIMEOUT);
    }

    public static synchronized void acquirePartial(Context mContext) {
        if (sPartialWakeLock != null) {
            return;
        }

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        sPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG + ".partial");
        sPartialWakeLock.setReferenceCounted(false);
        sPartialWakeLock.acquire(300000); // set timeout to 5 mins
    }

    public static synchronized void releaseFull() {
        if (sWakeLock != null) {
            sWakeLock.release();
            sWakeLock = null;
        }
    }

    public static synchronized void releasePartial() {
        if (sPartialWakeLock != null && sPartialWakeLock.isHeld()) {
            sPartialWakeLock.release();
            sPartialWakeLock = null;
        }
    }

    public static synchronized void releaseAll() {
        releaseFull();
        releasePartial();
    }
}