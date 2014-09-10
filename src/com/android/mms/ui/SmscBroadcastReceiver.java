/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package com.android.mms.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;
import com.android.mms.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Handle broadcasts from SMSC component
 */
public class SmscBroadcastReceiver extends BroadcastReceiver {

    private static final String NOTIFY_SMSC_UPDATE  = "com.android.smsc.notify.update";
    private static final String NOTIFY_SMSC_ERROR   = "com.android.smsc.notify.error";
    private static final String NOTIFY_SMSC_SUCCESS = "com.android.smsc.notify.success";

    private Activity mActivity;

    private List<MSimPreferencesController> mControllers =
            new ArrayList<MSimPreferencesController>();

    public SmscBroadcastReceiver(Activity activity) {
        mActivity = activity;
    }

    public void registerController(MSimPreferencesController controller) {
        mControllers.add(controller);
    }

    public void unregisterController(MSimPreferencesController controller) {
        mControllers.remove(controller);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
            for (MSimPreferencesController controller : mControllers) {
                controller.refreshSmsc();
            }
        } else if (NOTIFY_SMSC_ERROR.equals(action)) {
            Toast.makeText(mActivity, R.string.set_smsc_error, Toast.LENGTH_SHORT).show();
        } else if (NOTIFY_SMSC_SUCCESS.equals(action)) {
            Toast.makeText(mActivity, R.string.set_smsc_success, Toast.LENGTH_SHORT).show();
        } else if (NOTIFY_SMSC_UPDATE.equals(action)) {
            int sub = intent.getIntExtra(MSimPreferencesController.SMSC_DIALOG_SUB, 0);
            String summary = intent.getStringExtra(MSimPreferencesController.SMSC_DIALOG_NUMBER);
            for (MSimPreferencesController controller : mControllers) {
                if (sub == controller.getSubscription()) {
                    controller.updateSmscSummary(summary);
                }
            }
        }
    }

    public IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(NOTIFY_SMSC_ERROR);
        filter.addAction(NOTIFY_SMSC_SUCCESS);
        filter.addAction(NOTIFY_SMSC_UPDATE);
        return filter;
    }
}
