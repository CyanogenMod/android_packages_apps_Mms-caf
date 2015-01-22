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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.android.mms.R;
import com.android.mms.data.ContactList;

import java.util.List;

/**
 * A subclass of AlertDialog that will prompt the user to select a SIM card to send a message with.
 */
public class MsimDialog extends AlertDialog {
    public static final String TAG = "MsimDialog";
    View mLayout;
    OnSimButtonClickListener mOnSimClickListener;
    ContactList mRecipients;

    public MsimDialog(Context context,
                      OnSimButtonClickListener onSimClickListener,
                      ContactList recipients) {
        super(context);
        mOnSimClickListener = onSimClickListener;
        mRecipients = recipients;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayout = getLayoutInflater().inflate(R.layout.multi_sim_sms_sender, null);
        setView(mLayout);

        super.onCreate(savedInstanceState);

        initDialog();
    }

    private void initDialog() {
        setOnKeyListener(new DialogInterface.OnKeyListener() {
                             public boolean onKey(DialogInterface dialog, int keyCode,
                                                  KeyEvent event) {
                                 switch (keyCode) {
                                     case KeyEvent.KEYCODE_BACK: {
                                         dismiss();
                                         return true;
                                     }
                                     case KeyEvent.KEYCODE_SEARCH: {
                                         return true;
                                     }
                                 }
                                 return false;
                             }
                         });

        setButton(AlertDialog.BUTTON_NEGATIVE,
                  getContext().getResources().getString(R.string.no),
                  new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int which) {
                          dismiss();
                      }
                  });

        if (mRecipients != null && mRecipients.size() > 0) {
            setTitle(getContext().getResources().getString(R.string.to_address_label)
                     + mRecipients.formatNamesAndNumbers(","));
        }

        setCanceledOnTouchOutside(true);

        int[] smsBtnIds = {R.id.BtnSimOne, R.id.BtnSimTwo, R.id.BtnSimThree};
        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        Button[] smsBtns = new Button[phoneCount];

        for (int i = 0; i < phoneCount; i++) {
            final int phoneId = i;
            smsBtns[i] = (Button) mLayout.findViewById(smsBtnIds[i]);
            smsBtns[i].setVisibility(View.VISIBLE);
            List<SubInfoRecord> sir = SubscriptionManager.getSubInfoUsingSlotId(phoneId);

            String displayName = ((sir != null) && (sir.size() > 0)) ?
                    sir.get(0).displayName : "SIM " + (i + 1);

            smsBtns[i].setText(displayName);
            smsBtns[i].setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        if (mOnSimClickListener != null) {
                            mOnSimClickListener.onSimButtonClick(phoneId);
                        }
                }
            });
        }
    }

    public interface OnSimButtonClickListener {
        public void onSimButtonClick(int phoneId);
    }
}
