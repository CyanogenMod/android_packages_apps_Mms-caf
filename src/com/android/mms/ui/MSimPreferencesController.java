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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.view.WindowManager;
import android.widget.EditText;
import com.android.internal.telephony.MSimConstants;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.util.MultiSimUtility;

public class MSimPreferencesController {

    private Activity mActivity;
    private PreferenceHolder mPreferenceHolder;
    private PreferenceManager mPreferenceManager;
    private int mSubscription;

    private PreferenceScreen mPrefRoot;

    private PreferenceCategory mSmsPrefCategory;
    private PreferenceCategory mStorageCategory;
    private PreferenceCategory mMmsPrefCategory;
    private PreferenceCategory mSmscPrefCategory;

    private CheckBoxPreference mSmsDeliveryReportsPref;
    private ListPreference mSmsValidityPref;
    private Preference mManageSimPref;
    private CheckBoxPreference mMmsDeliveryReportPref;
    private ListPreference mMmsExpiryPref;
    private Preference mSmscPref;

    private static final String SMSC_DIALOG_TITLE = "title";
    public static final String SMSC_DIALOG_NUMBER = "smsc";
    public static final String SMSC_DIALOG_SUB = "sub";
    private static final String COMMAND_GET_SMSC    = "com.android.smsc.cmd.get";
    private static final String COMMAND_SET_SMSC    = "com.android.smsc.cmd.set";

    public MSimPreferencesController(Activity activity,
            PreferenceManager manager, int subscription) {
        mActivity = activity;
        mPreferenceManager = manager;
        mSubscription = subscription;
    }

    private void findPreferences() {
        mPrefRoot = (PreferenceScreen)mPreferenceHolder.findPreference("pref_key_root");

        // Categories
        mSmsPrefCategory =
                (PreferenceCategory)mPreferenceHolder.findPreference("pref_key_sms_settings");
        mStorageCategory =
                (PreferenceCategory)mPreferenceHolder.findPreference("pref_key_sms_settings");
        mMmsPrefCategory =
                (PreferenceCategory)mPreferenceHolder.findPreference("pref_key_mms_settings");
        mSmscPrefCategory =
                (PreferenceCategory)mPreferenceHolder.findPreference("pref_key_smsc_category");

        mSmsDeliveryReportsPref =
                (CheckBoxPreference)mPreferenceHolder.findPreference(
                        MessagingPreferenceActivity.SMS_DELIVERY_REPORT_MODE);
        mSmsValidityPref =
                (ListPreference)mPreferenceHolder.findPreference("pref_key_sms_validity_period");
        mManageSimPref = mPreferenceHolder.findPreference("pref_key_manage_sim_messages");
        mMmsDeliveryReportPref =
                (CheckBoxPreference)mPreferenceHolder.findPreference(
                        MessagingPreferenceActivity.MMS_DELIVERY_REPORT_MODE);
        mMmsExpiryPref = (ListPreference)mPreferenceHolder.findPreference("pref_key_mms_expiry");
        mSmscPref = mPreferenceHolder.findPreference("pref_key_smsc_number");
    }

    public void setupPreferences() {
        findPreferences();

        // SMS delivery reports
        updateKeyName(mSmsDeliveryReportsPref);
        if (!MmsConfig.getSMSDeliveryReportsEnabled()) {
            mSmsPrefCategory.removePreference(mSmsDeliveryReportsPref);
        }

        // SMS validity period
        updateKeyName(mSmsValidityPref);
        if (mActivity.getResources().getBoolean(R.bool.config_sms_validity)) {
            mSmsValidityPref.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            final String summary = newValue.toString();
                            int index = mSmsValidityPref.findIndexOfValue(summary);
                            mSmsValidityPref.setSummary(mSmsValidityPref.getEntries()[index]);
                            mSmsValidityPref.setValue(summary);
                            return true;
                        }
                    });
            mSmsValidityPref.setSummary(mSmsValidityPref.getEntry());
        } else {
            mStorageCategory.removePreference(mSmsValidityPref);
        }

        // Manage SIM card messages
        if (!MmsApp.getApplication().getTelephonyManager().hasIccCard()) {
            // No SIM card, remove the SIM-related prefs
            mSmsPrefCategory.removePreference(mManageSimPref);
        }
        mManageSimPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(mActivity, ManageSimMessages.class);
                intent.putExtra(MSimConstants.SUBSCRIPTION_KEY, mSubscription);
                mActivity.startActivity(intent);
                return true;
            }
        });

        // MMS Delivery Reports
        updateKeyName(mMmsDeliveryReportPref);
        if (!MmsConfig.getMMSDeliveryReportsEnabled()) {
            mMmsPrefCategory.removePreference(mMmsDeliveryReportPref);
        }

        // MMS validity period
        updateKeyName(mMmsExpiryPref);
        mMmsExpiryPref.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final String value = newValue.toString();
                        int index = mMmsExpiryPref.findIndexOfValue(value);
                        mMmsExpiryPref.setValue(value);
                        mMmsExpiryPref.setSummary(mMmsExpiryPref.getEntries()[index]);
                        return false;
                    }
                });

        // SMSC
        mSmscPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SmscEditDialogFragment dialog = new SmscEditDialogFragment(
                        mActivity,
                        preference.getTitle(),
                        preference.getSummary());
                dialog.show(mActivity.getFragmentManager(), "dialog");
                return true;
            }
        });
        refreshSmsc();
    }

    void removePreferences() {
        findPreferences();
        mSmsPrefCategory.removePreference(mSmsDeliveryReportsPref);
        mSmsPrefCategory.removePreference(mSmsValidityPref);
        mSmsPrefCategory.removePreference(mManageSimPref);
        mMmsPrefCategory.removePreference(mMmsDeliveryReportPref);
        mMmsPrefCategory.removePreference(mMmsExpiryPref);
        mPrefRoot.removePreference(mSmscPrefCategory);
    }

    public void refreshSmsc() {
        boolean isPrefEnabled =
                !isAirPlaneModeOn() && (
                        MSimTelephonyManager.getDefault().isMultiSimEnabled() ?
                                MSimTelephonyManager.getDefault().hasIccCard(mSubscription) :
                                TelephonyManager.getDefault().hasIccCard()
                );

        if (isPrefEnabled) {
            Intent get = new Intent();
            get.setAction(COMMAND_GET_SMSC);
            get.putExtra(SMSC_DIALOG_SUB, mSubscription);
            mActivity.startService(get);
        } else {
            mSmscPref.setSummary(null);
        }
        mSmscPref.setEnabled(isPrefEnabled);
    }

    public void updateSmscSummary(String summary) {
        mSmscPref.setSummary(summary);
    }

    public int getSubscription() {
        return mSubscription;
    }

    // SMSC UI Classes
    public class SmscEditDialogFragment extends DialogFragment {
        private Activity mActivity;

        public SmscEditDialogFragment(Activity activity,
                                      CharSequence title, CharSequence smsc) {
            mActivity = activity;

            Bundle args = new Bundle();
            args.putCharSequence(SMSC_DIALOG_TITLE, title);
            args.putCharSequence(SMSC_DIALOG_NUMBER, smsc);
            setArguments(args);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (null == mActivity) {
                mActivity = (MessagingPreferenceActivity) getActivity();
                dismiss();
            }
            final EditText edit = new EditText(mActivity);
            edit.setPadding(15, 15, 15, 15);
            edit.setText(getArguments().getCharSequence(SMSC_DIALOG_NUMBER));

            Dialog alert = new AlertDialog.Builder(mActivity)
                    .setTitle(getArguments().getCharSequence(SMSC_DIALOG_TITLE))
                    .setView(edit)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            SmscAlertDialogFragment newFragment = new SmscAlertDialogFragment(
                                    mActivity, edit.getText().toString());
                            newFragment.show(getFragmentManager(), "dialog");
                            dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(true)
                    .create();
            alert.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            return alert;
        }
    }

    public class SmscAlertDialogFragment extends DialogFragment {
        private Activity mActivity;

        public SmscAlertDialogFragment(Activity activity,
                                       String smsc) {
            mActivity = activity;

            Bundle args = new Bundle();
            args.putString(SMSC_DIALOG_NUMBER, smsc);
            setArguments(args);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String displayedSMSC = getArguments().getString(SMSC_DIALOG_NUMBER);

            // When framework re-instantiate this fragment by public empty
            // constructor and call onCreateDialog(Bundle savedInstanceState) ,
            // we should make sure mActivity not null.
            if (null == mActivity) {
                mActivity = (MessagingPreferenceActivity) getActivity();
            }

            return new AlertDialog.Builder(mActivity)
                    .setIcon(android.R.drawable.ic_dialog_alert).setMessage(
                            R.string.set_smsc_confirm_message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Intent intent = new Intent();
                            intent.setAction(COMMAND_SET_SMSC);
                            intent.putExtra(SMSC_DIALOG_SUB, mSubscription);
                            intent.putExtra(SMSC_DIALOG_NUMBER, displayedSMSC);
                            mActivity.startService(intent);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(true)
                    .create();
        }
    }

    // Abstract away whether we are accessing preferences from an activity (legacy)
    // or fragment.
    private interface PreferenceHolder {
        Preference findPreference(String prefKey);
    }

    public void setPreferenceHolder(final PreferenceActivity activity) {
        mPreferenceHolder = new PreferenceHolder() {
            @Override
            public Preference findPreference(String prefKey) {
                return activity.findPreference(prefKey);
            }
        };
    }

    public void setPreferenceHolder(final PreferenceFragment fragment) {
        mPreferenceHolder = new PreferenceHolder() {
            @Override
            public Preference findPreference(String prefKey) {
                return fragment.findPreference(prefKey);
            }
        };
    }

    // updateKeyName methods:
    //      We need to update they key names of each preference in order to store settings
    //      on a per-SIM basis.  Since setKey happens after the initial value of each
    //      preference is set, we need this hack to reset the preference value based on
    //      the value persisted under the real key.
    private void updateKeyName(CheckBoxPreference pref) {
        String newKey = MultiSimUtility.getPreferenceKey(pref.getKey(), mSubscription);
        pref.setKey(newKey);

        // make sure preferences contain this key so we don't set the wrong default
        if (mPreferenceManager.getSharedPreferences().contains(newKey)) {
            boolean isChecked =
                    mPreferenceManager.getSharedPreferences().getBoolean(newKey, false);
            pref.setChecked(isChecked);
        }
    }

    private void updateKeyName(ListPreference pref) {
        String newKey = MultiSimUtility.getPreferenceKey(pref.getKey(), mSubscription);
        pref.setKey(newKey);

        String value =
                mPreferenceManager.getSharedPreferences().getString(newKey, null);
        if (value != null) {
            pref.setValue(value);
        }
        pref.setSummary(pref.getEntry());
    }

    private boolean isAirPlaneModeOn() {
        return Settings.System.getInt(mActivity.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }
}
