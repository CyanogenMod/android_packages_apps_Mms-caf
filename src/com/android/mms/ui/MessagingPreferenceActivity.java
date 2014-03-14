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

package com.android.mms.ui;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.os.Message;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.preference.SwitchPreference;
import android.provider.SearchRecentSuggestions;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.util.BlacklistUtils;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.transaction.TransactionService;
import com.android.mms.util.Recycler;
import com.android.mms.QTIBackupMMS;
import com.android.mms.QTIBackupSMS;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;


/**
 * With this activity, users can set preferences for MMS and SMS and
 * can access and manipulate SMS messages stored on the SIM.
 */
public class MessagingPreferenceActivity extends PreferenceActivity
            implements OnPreferenceChangeListener {
    private static final String TAG = "MessagingPreferenceActivity";
    // Symbolic names for the keys used for preference lookup
    public static final String MMS_DELIVERY_REPORT_MODE = "pref_key_mms_delivery_reports";
    public static final String EXPIRY_TIME              = "pref_key_mms_expiry";
    public static final String EXPIRY_TIME_SLOT1        = "pref_key_mms_expiry_slot1";
    public static final String EXPIRY_TIME_SLOT2        = "pref_key_mms_expiry_slot2";
    public static final String PRIORITY                 = "pref_key_mms_priority";
    public static final String READ_REPORT_MODE         = "pref_key_mms_read_reports";
    public static final String SMS_DELIVERY_REPORT_MODE = "pref_key_sms_delivery_reports";
    public static final String SMS_DELIVERY_REPORT_SUB1 = "pref_key_sms_delivery_reports_slot1";
    public static final String SMS_DELIVERY_REPORT_SUB2 = "pref_key_sms_delivery_reports_slot2";
    public static final String NOTIFICATION_ENABLED     = "pref_key_enable_notifications";
    public static final String NOTIFICATION_VIBRATE     = "pref_key_vibrate";
    public static final String NOTIFICATION_VIBRATE_WHEN= "pref_key_vibrateWhen";
    public static final String NOTIFICATION_RINGTONE    = "pref_key_ringtone";
    public static final String AUTO_RETRIEVAL           = "pref_key_mms_auto_retrieval";
    public static final String RETRIEVAL_DURING_ROAMING = "pref_key_mms_retrieval_during_roaming";
    public static final String AUTO_DELETE              = "pref_key_auto_delete";
    public static final String GROUP_MMS_MODE           = "pref_key_mms_group_mms";
    public static final String SMS_CDMA_PRIORITY        = "pref_key_sms_cdma_priority";

    // Unicode
    public static final String UNICODE_STRIPPING            = "pref_key_unicode_stripping_value";
    public static final String UNICODE_STRIPPING_LEAVE_INTACT  = "0";
    public static final String UNICODE_STRIPPING_NON_DECODABLE = "1";

    // Expiry of MMS
    private final static String EXPIRY_ONE_WEEK = "604800"; // 7 * 24 * 60 * 60
    private final static String EXPIRY_TWO_DAYS = "172800"; // 2 * 24 * 60 * 60

    // Smart Dialer
    public static final String SMART_DIALER_ENABLED = "pref_key_mms_smart_dialer";

    // QuickMessage
    public static final String QUICKMESSAGE_ENABLED      = "pref_key_quickmessage";
    public static final String QM_LOCKSCREEN_ENABLED     = "pref_key_qm_lockscreen";
    public static final String QM_CLOSE_ALL_ENABLED      = "pref_key_close_all";
    public static final String QM_DARK_THEME_ENABLED     = "pref_dark_theme";

    // Blacklist
    public static final String BLACKLIST                 = "pref_blacklist";

    // Timestamps
    public static final String FULL_TIMESTAMP            = "pref_key_mms_full_timestamp";
    public static final String SENT_TIMESTAMP            = "pref_key_mms_use_sent_timestamp";

    // Vibrate pattern
    public static final String NOTIFICATION_VIBRATE_PATTERN =
            "pref_key_mms_notification_vibrate_pattern";

    // Menu entries
    private static final int MENU_RESTORE_DEFAULTS    = 1;

    // Preferences for enabling and disabling SMS
    private Preference mSmsDisabledPref;
    private Preference mSmsEnabledPref;

    private PreferenceCategory mStoragePrefCategory;
    private PreferenceCategory mSmsPrefCategory;
    private PreferenceCategory mMmsPrefCategory;
    private PreferenceCategory mNotificationPrefCategory;
    private PreferenceCategory mSmscPrefCate;

    // Delay send
    public static final String SEND_DELAY_DURATION = "pref_key_send_delay";

    private ListPreference mMessageSendDelayPref;
    private Preference mSmsLimitPref;
    private Preference mSmsDeliveryReportPref;
    private Preference mSmsDeliveryReportPrefSub1;
    private Preference mSmsDeliveryReportPrefSub2;
    private Preference mMmsLimitPref;
    private Preference mMmsDeliveryReportPref;
    private Preference mMmsGroupMmsPref;
    private Preference mMmsReadReportPref;
    private Preference mManageSimPref;
    private Preference mManageSim1Pref;
    private Preference mManageSim2Pref;
    private Preference mManageSdcardSMSPref;
    private Preference mClearHistoryPref;
    private SwitchPreference mVibratePref;
    private SwitchPreference mEnableNotificationsPref;
    private SwitchPreference mMmsAutoRetrievialPref;
    private ListPreference mMmsExpiryPref;
    private ListPreference mMmsExpiryCard1Pref;
    private ListPreference mMmsExpiryCard2Pref;
    private RingtonePreference mRingtonePref;
    private ListPreference mSmsStorePref;
    private ListPreference mSmsStoreCard1Pref;
    private ListPreference mSmsStoreCard2Pref;
    private ListPreference mSmsValidityPref;
    private ListPreference mSmsValidityCard1Pref;
    private ListPreference mSmsValidityCard2Pref;
    private Recycler mSmsRecycler;
    private Recycler mMmsRecycler;
    private Preference mSmsTemplate;
    private SwitchPreference mSmsSignaturePref;
    private EditTextPreference mSmsSignatureEditPref;
    private ArrayList<Preference> mSmscPrefList = new ArrayList<Preference>();
    private static final int CONFIRM_CLEAR_SEARCH_HISTORY_DIALOG = 3;
    private ListPreference mUnicodeStripping;

    // Whether or not we are currently enabled for SMS. This field is updated in onResume to make
    // sure we notice if the user has changed the default SMS app.
    private boolean mIsSmsEnabled;
    private static final String SMSC_DIALOG_TITLE = "title";
    private static final String SMSC_DIALOG_NUMBER = "smsc";
    private static final String SMSC_DIALOG_SUB = "sub";
    private static final int EVENT_SET_SMSC_DONE = 0;
    private static final int EVENT_GET_SMSC_DONE = 1;
    private static final String EXTRA_EXCEPTION = "exception";
    private static SmscHandler mHandler = null;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                    PreferenceCategory smsCategory =
                            (PreferenceCategory)findPreference("pref_key_sms_settings");
                    if (smsCategory != null) {
                        updateSIMSMSPref();
                    }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                    updateSMSCPref();
            }
        }
    };

    // QuickMessage
    private SwitchPreference mEnableQuickMessagePref;
    private SwitchPreference mEnableQmLockscreenPref;
    private SwitchPreference mEnableQmCloseAllPref;
    private SwitchPreference mEnableQmDarkThemePref;

    // Blacklist
    private PreferenceScreen mBlacklist;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mHandler = new SmscHandler(this);
        loadPrefs();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        if (isSmsEnabled != mIsSmsEnabled) {
            mIsSmsEnabled = isSmsEnabled;
            invalidateOptionsMenu();
        }

        // Since the enabled notifications pref can be changed outside of this activity,
        // we have to reload it whenever we resume, including the blacklist summary
        setEnabledNotificationsPref();
        // Initialize the sms signature
        updateSignatureStatus();
        updateBlacklistSummary();
        registerListeners();
        updateSmsEnabledState();
        updateSMSCPref();
    }

    private void updateSmsEnabledState() {
        // Show the right pref (SMS Disabled or SMS Enabled)
        PreferenceScreen prefRoot = (PreferenceScreen)findPreference("pref_key_root");
        if (!mIsSmsEnabled) {
            prefRoot.addPreference(mSmsDisabledPref);
            prefRoot.removePreference(mSmsEnabledPref);
        } else {
            prefRoot.removePreference(mSmsDisabledPref);
            prefRoot.addPreference(mSmsEnabledPref);
        }

        // Enable or Disable the settings as appropriate
        mStoragePrefCategory.setEnabled(mIsSmsEnabled);
        mSmsPrefCategory.setEnabled(mIsSmsEnabled);
        mMmsPrefCategory.setEnabled(mIsSmsEnabled);
        mNotificationPrefCategory.setEnabled(mIsSmsEnabled);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    private void updateBlacklistSummary() {
        if (mBlacklist != null) {
            if (BlacklistUtils.isBlacklistEnabled(this)) {
                mBlacklist.setSummary(R.string.blacklist_summary);
            } else {
                mBlacklist.setSummary(R.string.blacklist_summary_disabled);
            }
        }
    }

    private void loadPrefs() {
        addPreferencesFromResource(R.xml.preferences);

        mSmsDisabledPref = findPreference("pref_key_sms_disabled");
        mSmsEnabledPref = findPreference("pref_key_sms_enabled");

        mStoragePrefCategory = (PreferenceCategory)findPreference("pref_key_storage_settings");
        mSmsPrefCategory = (PreferenceCategory)findPreference("pref_key_sms_settings");
        mMmsPrefCategory = (PreferenceCategory)findPreference("pref_key_mms_settings");
        mNotificationPrefCategory =
                (PreferenceCategory)findPreference("pref_key_notification_settings");

        mManageSimPref = findPreference("pref_key_manage_sim_messages");
        mManageSim1Pref = findPreference("pref_key_manage_sim_messages_slot1");
        mManageSim2Pref = findPreference("pref_key_manage_sim_messages_slot2");
        mManageSdcardSMSPref = findPreference("pref_key_manage_sdcard_messages");
        mSmsLimitPref = findPreference("pref_key_sms_delete_limit");
        mSmsDeliveryReportPref = findPreference("pref_key_sms_delivery_reports");
        mSmsDeliveryReportPrefSub1 = findPreference("pref_key_sms_delivery_reports_slot1");
        mSmsDeliveryReportPrefSub2 = findPreference("pref_key_sms_delivery_reports_slot2");
        mMmsDeliveryReportPref = findPreference("pref_key_mms_delivery_reports");
        mMmsGroupMmsPref = findPreference("pref_key_mms_group_mms");
        mMmsReadReportPref = findPreference("pref_key_mms_read_reports");
        mMmsLimitPref = findPreference("pref_key_mms_delete_limit");
        mClearHistoryPref = findPreference("pref_key_mms_clear_history");
        mEnableNotificationsPref = (SwitchPreference) findPreference(NOTIFICATION_ENABLED);
        mMmsAutoRetrievialPref = (SwitchPreference) findPreference(AUTO_RETRIEVAL);
        mMmsExpiryPref = (ListPreference) findPreference("pref_key_mms_expiry");
        mMmsExpiryCard1Pref = (ListPreference) findPreference("pref_key_mms_expiry_slot1");
        mMmsExpiryCard2Pref = (ListPreference) findPreference("pref_key_mms_expiry_slot2");
        mSmsSignaturePref = (SwitchPreference) findPreference("pref_key_enable_signature");
        mSmsSignatureEditPref = (EditTextPreference) findPreference("pref_key_edit_signature");
        mVibratePref = (SwitchPreference) findPreference(NOTIFICATION_VIBRATE);
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            if (mVibratePref != null) {
                mNotificationPrefCategory.removePreference(mVibratePref);
                mVibratePref = null;
            }

            Preference patternPref = findPreference(NOTIFICATION_VIBRATE_PATTERN);
            if (patternPref != null) {
                mNotificationPrefCategory.removePreference(patternPref);
            }
        }
        mRingtonePref = (RingtonePreference) findPreference(NOTIFICATION_RINGTONE);
        mSmsTemplate = findPreference("pref_key_message_template");
        mSmsStorePref = (ListPreference) findPreference("pref_key_sms_store");
        mSmsStoreCard1Pref = (ListPreference) findPreference("pref_key_sms_store_card1");
        mSmsStoreCard2Pref = (ListPreference) findPreference("pref_key_sms_store_card2");
        mSmsValidityPref = (ListPreference) findPreference("pref_key_sms_validity_period");
        mSmsValidityCard1Pref
            = (ListPreference) findPreference("pref_key_sms_validity_period_slot1");
        mSmsValidityCard2Pref
            = (ListPreference) findPreference("pref_key_sms_validity_period_slot2");

        // QuickMessage
        mEnableQuickMessagePref = (SwitchPreference) findPreference(QUICKMESSAGE_ENABLED);
        mEnableQmLockscreenPref = (SwitchPreference) findPreference(QM_LOCKSCREEN_ENABLED);
        mEnableQmCloseAllPref = (SwitchPreference) findPreference(QM_CLOSE_ALL_ENABLED);
        mEnableQmDarkThemePref = (SwitchPreference) findPreference(QM_DARK_THEME_ENABLED);

        // Unicode Stripping
        mUnicodeStripping = (ListPreference) findPreference(UNICODE_STRIPPING);

        // SMS Sending Delay
        mMessageSendDelayPref = (ListPreference) findPreference(SEND_DELAY_DURATION);
        mMessageSendDelayPref.setSummary(mMessageSendDelayPref.getEntry());

        // Blacklist screen - Needed for setting summary
        mBlacklist = (PreferenceScreen) findPreference(BLACKLIST);

        setMessagePreferences();
    }

    private void restoreDefaultPreferences() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().clear().apply();
        setPreferenceScreen(null);
        // Reset the SMSC preference.
        mSmscPrefList.clear();
        mSmscPrefCate.removeAll();
        loadPrefs();
        updateSmsEnabledState();

        // NOTE: After restoring preferences, the auto delete function (i.e. message recycler)
        // will be turned off by default. However, we really want the default to be turned on.
        // Because all the prefs are cleared, that'll cause:
        // ConversationList.runOneTimeStorageLimitCheckForLegacyMessages to get executed the
        // next time the user runs the Messaging app and it will either turn on the setting
        // by default, or if the user is over the limits, encourage them to turn on the setting
        // manually.
    }

    private void setMessagePreferences() {
        updateSignatureStatus();

        boolean showSmsc = getResources().getBoolean(R.bool.config_show_smsc_pref);
        mSmscPrefCate = (PreferenceCategory) findPreference("pref_key_smsc");
        if (showSmsc) {
            showSmscPref();
        } else if (mSmscPrefCate != null) {
            getPreferenceScreen().removePreference(mSmscPrefCate);
        }
        setMessagePriorityPref();
        // Set SIM card SMS management preference
        updateSIMSMSPref();

        if (!MmsConfig.getSMSDeliveryReportsEnabled()) {
            mSmsPrefCategory.removePreference(mSmsDeliveryReportPref);
            mSmsPrefCategory.removePreference(mSmsDeliveryReportPrefSub1);
            mSmsPrefCategory.removePreference(mSmsDeliveryReportPrefSub2);
            if (!MmsApp.getApplication().getTelephonyManager().hasIccCard()) {
                getPreferenceScreen().removePreference(mSmsPrefCategory);
            }
        } else {
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                mSmsPrefCategory.removePreference(mSmsDeliveryReportPref);
                if (!MessageUtils.isIccCardActivated(MessageUtils.SUB1)) {
                    mSmsDeliveryReportPrefSub1.setEnabled(false);
                }
                if (!MessageUtils.isIccCardActivated(MessageUtils.SUB2)) {
                    mSmsDeliveryReportPrefSub2.setEnabled(false);
                }
            } else {
                mSmsPrefCategory.removePreference(mSmsDeliveryReportPrefSub1);
                mSmsPrefCategory.removePreference(mSmsDeliveryReportPrefSub2);
            }
        }

        setMmsRelatedPref();

        setEnabledNotificationsPref();

        if (getResources().getBoolean(R.bool.config_savelocation)) {
            if (MessageUtils.isMultiSimEnabledMms()) {
                PreferenceCategory storageOptions =
                    (PreferenceCategory)findPreference("pref_key_storage_settings");
                storageOptions.removePreference(mSmsStorePref);

                if (!MessageUtils.isIccCardActivated(MessageUtils.SUB1)) {
                    mSmsStoreCard1Pref.setEnabled(false);
                }
                if (!MessageUtils.isIccCardActivated(MessageUtils.SUB2)) {
                    mSmsStoreCard2Pref.setEnabled(false);
                }
            } else {
                PreferenceCategory storageOptions =
                    (PreferenceCategory)findPreference("pref_key_storage_settings");
                storageOptions.removePreference(mSmsStoreCard1Pref);
                storageOptions.removePreference(mSmsStoreCard2Pref);

                if (!MessageUtils.hasIccCard()) {
                    mSmsStorePref.setEnabled(false);
                }
            }
        } else {
            PreferenceCategory storageOptions =
                    (PreferenceCategory)findPreference("pref_key_storage_settings");
            storageOptions.removePreference(mSmsStorePref);
            storageOptions.removePreference(mSmsStoreCard1Pref);
            storageOptions.removePreference(mSmsStoreCard2Pref);
        }
        setSmsValidityPeriodPref();

        // If needed, migrate vibration setting from the previous tri-state setting stored in
        // NOTIFICATION_VIBRATE_WHEN to the boolean setting stored in NOTIFICATION_VIBRATE.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.contains(NOTIFICATION_VIBRATE_WHEN)) {
            String vibrateWhen = sharedPreferences.
                    getString(MessagingPreferenceActivity.NOTIFICATION_VIBRATE_WHEN, null);
            boolean vibrate = "always".equals(vibrateWhen);
            SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
            prefsEditor.putBoolean(NOTIFICATION_VIBRATE, vibrate);
            prefsEditor.remove(NOTIFICATION_VIBRATE_WHEN);  // remove obsolete setting
            prefsEditor.apply();
            mVibratePref.setChecked(vibrate);
        }

        mSmsRecycler = Recycler.getSmsRecycler();
        mMmsRecycler = Recycler.getMmsRecycler();

        // Fix up the recycler's summary with the correct values
        setSmsDisplayLimit();
        setMmsDisplayLimit();
        setMmsExpiryPref();

        String soundValue = sharedPreferences.getString(NOTIFICATION_RINGTONE, null);
        setRingtoneSummary(soundValue);

        mMessageSendDelayPref.setOnPreferenceChangeListener(this);
    }

    private void setMmsRelatedPref() {
        if (!MmsConfig.getMmsEnabled()) {
            // No Mms, remove all the mms-related preferences
            getPreferenceScreen().removePreference(mMmsPrefCategory);

            mStoragePrefCategory.removePreference(findPreference("pref_key_mms_delete_limit"));
        } else {
            if (!MmsConfig.getMMSDeliveryReportsEnabled()) {
                mMmsPrefCategory.removePreference(mMmsDeliveryReportPref);
            }
            if (!MmsConfig.getMMSReadReportsEnabled()) {
                mMmsPrefCategory.removePreference(mMmsReadReportPref);
            }
            // If the phone's SIM doesn't know it's own number, disable group mms.
            if (!MmsConfig.getGroupMmsEnabled() ||
                    TextUtils.isEmpty(MessageUtils.getLocalNumber())) {
                mMmsPrefCategory.removePreference(mMmsGroupMmsPref);
            }
        }

        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            mMmsPrefCategory.removePreference(mMmsExpiryPref);
        } else {
            mMmsPrefCategory.removePreference(mMmsExpiryCard1Pref);
            mMmsPrefCategory.removePreference(mMmsExpiryCard2Pref);
        }
    }

    private void setMessagePriorityPref() {
        if (!getResources().getBoolean(R.bool.support_sms_priority)) {
            Preference priorotySettings = findPreference(SMS_CDMA_PRIORITY);
            PreferenceScreen prefSet = getPreferenceScreen();
            prefSet.removePreference(priorotySettings);
        }
    }

    private void setSmsValidityPeriodPref() {
        PreferenceCategory storageOptions =
                (PreferenceCategory)findPreference("pref_key_sms_settings");
        if (getResources().getBoolean(R.bool.config_sms_validity)) {
            if (MessageUtils.isMultiSimEnabledMms()) {
                storageOptions.removePreference(mSmsValidityPref);
            } else {
                storageOptions.removePreference(mSmsValidityCard1Pref);
                storageOptions.removePreference(mSmsValidityCard2Pref);
            }
        } else {
            storageOptions.removePreference(mSmsValidityPref);
            storageOptions.removePreference(mSmsValidityCard1Pref);
            storageOptions.removePreference(mSmsValidityCard2Pref);
        }

        // QuickMessage
        setEnabledQuickMessagePref();
        setEnabledQmLockscreenPref();
        setEnabledQmCloseAllPref();
        setEnabledQmDarkThemePref();
    }

    public static long getMessageSendDelayDuration(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Long.valueOf(prefs.getString(SEND_DELAY_DURATION, "0"));
    }

    private void setRingtoneSummary(String soundValue) {
        Uri soundUri = TextUtils.isEmpty(soundValue) ? null : Uri.parse(soundValue);
        Ringtone tone = soundUri != null ? RingtoneManager.getRingtone(this, soundUri) : null;
        mRingtonePref.setSummary(tone != null ? tone.getTitle(this)
                : getResources().getString(R.string.silent_ringtone));
    }

    private void showSmscPref() {
        int count = TelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < count; i++) {
            final Preference pref = new Preference(this);
            pref.setKey(String.valueOf(i));
            pref.setTitle(getSMSCDialogTitle(count, i));

            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    MyEditDialogFragment dialog = MyEditDialogFragment.newInstance(
                            MessagingPreferenceActivity.this,
                            preference.getTitle(),
                            preference.getSummary(),
                            Integer.valueOf(preference.getKey()));
                    dialog.show(getFragmentManager(), "dialog");
                    return true;
                }
            });

            mSmscPrefCate.addPreference(pref);
            mSmscPrefList.add(pref);
        }
        updateSMSCPref();
    }

    private void updateSIMSMSPref() {
        if (MessageUtils.isMultiSimEnabledMms()) {
            if (!MessageUtils.isIccCardActivated(MessageUtils.SUB1)) {
                mManageSim1Pref.setEnabled(false);
            }
            if (!MessageUtils.isIccCardActivated(MessageUtils.SUB2)) {
                mManageSim2Pref.setEnabled(false);
            }
            mSmsPrefCategory.removePreference(mManageSimPref);
        } else {
            if (!MessageUtils.hasIccCard()) {
                mManageSimPref.setEnabled(false);
            }
            mSmsPrefCategory.removePreference(mManageSim1Pref);
            mSmsPrefCategory.removePreference(mManageSim2Pref);
        }
    }

    private boolean isAirPlaneModeOn() {
        return Settings.System.getInt(getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    private String getSMSCDialogTitle(int count, int index) {
        String title = TelephonyManager.getDefault().isMultiSimEnabled()
                ? getString(R.string.pref_more_smcs, index + 1)
                : getString(R.string.pref_one_smcs);
        return title;
    }

    private void setEnabledNotificationsPref() {
        // The "enable notifications" setting is really stored in our own prefs. Read the
        // current value and set the Switch to match.
        mEnableNotificationsPref.setChecked(getNotificationEnabled(this));
    }

    private void setEnabledQuickMessagePref() {
        // The "enable quickmessage" setting is really stored in our own prefs. Read the
        // current value and set the Switch to match.
        mEnableQuickMessagePref.setChecked(getQuickMessageEnabled(this));
    }

    private void setEnabledQmLockscreenPref() {
        // The "enable quickmessage on lock screen " setting is really stored in our own prefs. Read the
        // current value and set the Switch to match.
        mEnableQmLockscreenPref.setChecked(getQmLockscreenEnabled(this));
    }

    private void setEnabledQmCloseAllPref() {
        // The "enable close all" setting is really stored in our own prefs. Read the
        // current value and set the Switch to match.
        mEnableQmCloseAllPref.setChecked(getQmCloseAllEnabled(this));
    }

    private void setEnabledQmDarkThemePref() {
        // The "Use dark theme" setting is really stored in our own prefs. Read the
        // current value and set the Switch to match.
        mEnableQmDarkThemePref.setChecked(getQmDarkThemeEnabled(this));
    }

    private void setSmsDisplayLimit() {
        mSmsLimitPref.setSummary(
                getString(R.string.pref_summary_delete_limit,
                        mSmsRecycler.getMessageLimit(this)));
    }

    private void setMmsDisplayLimit() {
        mMmsLimitPref.setSummary(
                getString(R.string.pref_summary_delete_limit,
                        mMmsRecycler.getMessageLimit(this)));
    }

    private void setMmsExpiryPref() {
        PreferenceCategory mmsSettings =
                (PreferenceCategory)findPreference("pref_key_mms_settings");
        if (MessageUtils.isMultiSimEnabledMms()) {
            mmsSettings.removePreference(mMmsExpiryPref);
            if (!MessageUtils.isIccCardActivated(MessageUtils.SUB1)) {
                mMmsExpiryCard1Pref.setEnabled(false);
            }
            if (!MessageUtils.isIccCardActivated(MessageUtils.SUB2)) {
                mMmsExpiryCard2Pref.setEnabled(false);
            }
        } else {
            mmsSettings.removePreference(mMmsExpiryCard1Pref);
            mmsSettings.removePreference(mMmsExpiryCard2Pref);
        }
    }

    private void updateSignatureStatus() {
        // If the signature Switch is checked, we should set the signature EditText
        // enable, and disable when it's not checked.
        boolean isChecked = mSmsSignaturePref.isChecked();
        mSmsSignatureEditPref.setEnabled(isChecked);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.clear();
        if (mIsSmsEnabled) {
            menu.add(0, MENU_RESTORE_DEFAULTS, 0, R.string.restore_default);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESTORE_DEFAULTS:
                restoreDefaultPreferences();
                return true;

            case android.R.id.home:
                // The user clicked on the Messaging icon in the action bar. Take them back from
                // wherever they came from
                finish();
                return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mSmsLimitPref) {
            new NumberPickerDialog(this,
                    mSmsLimitListener,
                    mSmsRecycler.getMessageLimit(this),
                    mSmsRecycler.getMessageMinLimit(),
                    mSmsRecycler.getMessageMaxLimit(),
                    R.string.pref_title_sms_delete,
                    R.string.pref_messages_to_save).show();
        } else if (preference == mMmsLimitPref) {
            new NumberPickerDialog(this,
                    mMmsLimitListener,
                    mMmsRecycler.getMessageLimit(this),
                    mMmsRecycler.getMessageMinLimit(),
                    mMmsRecycler.getMessageMaxLimit(),
                    R.string.pref_title_mms_delete,
                    R.string.pref_messages_to_save).show();
        } else if (preference == mSmsTemplate) {
            startActivity(new Intent(this, MessageTemplate.class));
        } else if (preference == mManageSimPref) {
            startActivity(new Intent(this, ManageSimMessages.class));
        } else if (preference == mManageSdcardSMSPref) {
            manageSMS();
        } else if (preference == mManageSim1Pref) {
            Intent intent = new Intent(this, ManageSimMessages.class);
            intent.putExtra(PhoneConstants.PHONE_KEY, MessageUtils.SUB1);
            startActivity(intent);
        } else if (preference == mManageSim2Pref) {
            Intent intent = new Intent(this, ManageSimMessages.class);
            intent.putExtra(PhoneConstants.PHONE_KEY, MessageUtils.SUB2);
            startActivity(intent);
        } else if (preference == mClearHistoryPref) {
            showDialog(CONFIRM_CLEAR_SEARCH_HISTORY_DIALOG);
            return true;
        } else if (preference == mEnableNotificationsPref) {
            // Update the actual "enable notifications" value that is stored in secure settings.
            enableNotifications(mEnableNotificationsPref.isChecked(), this);
        } else if (preference == mSmsSignaturePref) {
            updateSignatureStatus();
        } else if (preference == mEnableQuickMessagePref) {
            // Update the actual "enable quickmessage" value that is stored in secure settings.
            enableQuickMessage(mEnableQuickMessagePref.isChecked(), this);
        } else if (preference == mEnableQmLockscreenPref) {
            // Update the actual "enable quickmessage on lockscreen" value that is stored in secure settings.
            enableQmLockscreen(mEnableQmLockscreenPref.isChecked(), this);
        } else if (preference == mEnableQmCloseAllPref) {
            // Update the actual "enable close all" value that is stored in secure settings.
            enableQmCloseAll(mEnableQmCloseAllPref.isChecked(), this);
        } else if (preference == mEnableQmDarkThemePref) {
            // Update the actual "enable dark theme" value that is stored in secure settings.
            enableQmDarkTheme(mEnableQmDarkThemePref.isChecked(), this);
        } else if (preference == mMmsAutoRetrievialPref) {
            if (mMmsAutoRetrievialPref.isChecked()) {
                startMmsDownload();
            }
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private class BackupRestoreSMSTask extends AsyncTask<Void, Void, ArrayList<File>> {

        private File mRestoreFile;
        private ProgressDialog mDialog;
        private boolean mShouldBackup;

        public BackupRestoreSMSTask(File restoreFile) {
            mRestoreFile = restoreFile;
            mShouldBackup = restoreFile == null ? true : false;
        }

        @Override
        protected void onPreExecute() {
            String title = getString(mShouldBackup ? R.string.perform_backup_title :
                    R.string.perform_restore_title);
            String msg = getString(R.string.wait_progress_message);
            mDialog = ProgressDialog.show(MessagingPreferenceActivity.this, title, msg, true);
        }

        @Override
        protected ArrayList<File> doInBackground(Void... params) {
            File folder = new File(Environment.getExternalStorageDirectory(), BACKUP_FOLDER_NAME);
            if (!folder.exists()) {
                folder.mkdir();
            }
            File operationFile = null;
            File operationFileSMS = null;
            File operationFileMMS = null;
            String sysTime = String.valueOf(System.currentTimeMillis());

            if (mShouldBackup) {
                operationFileSMS = new File(folder, sysTime + "_SMS");
                operationFileMMS = new File(folder, sysTime + "_MMS");
            } else {
                try {
                    ArrayList<File> backupFiles = MessageUtils.unzipBackupFile(mRestoreFile,
                            folder.getAbsolutePath());

                    if (backupFiles != null && backupFiles.size() >= 1) {
                        // Find SMS file and MMS file backups
                        for (File f : backupFiles) {
                            if (f.getName().contains("SMS")) {
                                operationFileSMS = f;
                            }
                            if (f.getName().contains("MMS")) {
                                operationFileMMS = f;
                            }
                        }
                    }

                    if (operationFileSMS == null && operationFileMMS == null) {
                        return null;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            QTIBackupMMS mmsBackup = new QTIBackupMMS(getApplicationContext(), operationFileMMS);
            QTIBackupSMS smsBackup = new QTIBackupSMS(getApplicationContext(), operationFileSMS);
            ArrayList<File> files = new ArrayList<File>();
            if (mShouldBackup) {
                mmsBackup.performBackup();
                smsBackup.performBackup();

                // compress and return zip file
                String zipFileName = folder + "/" + sysTime + ".zip";

                try {
                    ArrayList<File> filesTemp = new ArrayList<File>();
                    if (operationFileSMS != null) {
                        filesTemp.add(operationFileSMS);
                    }
                    if (operationFileMMS != null) {
                        filesTemp.add(operationFileMMS);
                    }
                    MessageUtils.zipFile(filesTemp, zipFileName);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }

                if (operationFileSMS != null) {
                    operationFileSMS.delete();
                }
                if (operationFileMMS != null) {
                    operationFileMMS.delete();
                }
                operationFile = new File(zipFileName);
                files.add(operationFile);
            } else {
                mmsBackup.performRestore();
                smsBackup.performRestore();
                if (operationFileSMS != null) {
                    files.add(operationFileSMS);
                }
                if (operationFileMMS != null) {
                    files.add(operationFileMMS);
                }
            }
            return files;
        }

        @Override
        protected void onPostExecute(final ArrayList<File> files) {
            mDialog.cancel();

            if (mShouldBackup) {
                if (files == null || files.size() == 0) {
                    Toast.makeText(MessagingPreferenceActivity.this,
                            R.string.export_sms_failed_toast,
                            Toast.LENGTH_SHORT).show();

                    return;
                }

                final File file = files.get(0);
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(MessagingPreferenceActivity.this);
                builder.setMessage(R.string.export_sms_toast)
                        .setMessage(String.format(getString(R.string.backup_file_name_message,
                                file.getAbsolutePath())))
                        .setPositiveButton(android.R.string.ok, null)
                        .setNegativeButton(R.string.message_share,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                        shareIntent.putExtra(Intent.EXTRA_STREAM,
                                                Uri.fromFile(file));
                                        shareIntent.setType("application/zip");
                                        startActivity(shareIntent);
                                    }
                                });
                builder.show();
            } else {
                boolean success = false;
                if (files != null) {
                    for (File f : files) {
                        success = true;
                        f.delete();
                    }
                }
                Toast.makeText(MessagingPreferenceActivity.this, !success ?
                        R.string.import_sms_failed_toast : R.string.import_sms_toast,
                        Toast.LENGTH_SHORT).show();
            }
        }

    }

    private class DeleteSMSTask extends AsyncTask<File, Void, Void> {

        @Override
        protected Void doInBackground(File... params) {
            params[0].delete();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Toast.makeText(MessagingPreferenceActivity.this, R.string.delete_sms_toast,
                    Toast.LENGTH_SHORT).show();
        }

    }

    private class RestoreDeleteListFilesTask extends AsyncTask<Void, Void, File[]> {

        private boolean mShouldDelete;

        public RestoreDeleteListFilesTask(boolean shouldDelete) {
            mShouldDelete = shouldDelete;
        }

        @Override
        protected File[] doInBackground(Void... params) {
            File folder = new File(Environment.getExternalStorageDirectory(), BACKUP_FOLDER_NAME);
            return folder.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File directory, String fileName) {
                    return fileName.toLowerCase().endsWith(".zip");
                }
            });
        }

        @Override
        protected void onPostExecute(File[] files) {
            if (files == null || files.length == 0) {
                Toast.makeText(MessagingPreferenceActivity.this, R.string.no_backups_found,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            final RestoreFileListAdapter adapter = new RestoreFileListAdapter(files);
            AlertDialog.Builder builder = new AlertDialog.Builder(MessagingPreferenceActivity.this);
            builder.setTitle(R.string.pick_sms_backup_title)
                    .setAdapter(adapter, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (mShouldDelete) {
                                new DeleteSMSTask().execute(adapter.getFile(which));
                            } else {
                                new BackupRestoreSMSTask(adapter.getFile(which)).execute();
                            }

                        }
                    });
            builder.show();
        }

    }

    private class RestoreFileListAdapter extends ArrayAdapter {

        private final File[] mItems;

        public RestoreFileListAdapter(File[] objects) {
            super(MessagingPreferenceActivity.this, android.R.layout.simple_list_item_1, objects);
            mItems = objects;
        }

        @Override
        public String getItem(int position) {
            String file = mItems[position].getName();
            int lastIndex = file.indexOf('.') != -1 ? file.indexOf('.') : file.length();
            Date date = new Date(Long.parseLong(file.substring(0, lastIndex)));
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return format.format(date);
        }

        public File getFile(int index) {
            return mItems[index];
        }

    }

    private static final String BACKUP_FOLDER_NAME = "BackupSms";
    private static final int SMS_IMPORT = 0;
    private static final int SMS_DELETE = 1;
    private static final int SMS_EXPORT = 2;

    private boolean foundEntriesToExport() {
        ContentResolver cr = getContentResolver();
        Cursor c = null;
        boolean foundEntries = false;

        c = cr.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, null);
        if (c.getCount() > 0) {
            foundEntries = true;
        }
        if (!foundEntries) {
            c = cr.query(Telephony.Sms.Sent.CONTENT_URI, null, null, null, null);
            if (c.getCount() > 0) {
                foundEntries = true;
            }
        }
        if (!foundEntries) {
            c = cr.query(Telephony.Sms.Draft.CONTENT_URI, null, null, null, null);
            if (c.getCount() > 0) {
                foundEntries = true;
            }
        }
        if (!foundEntries) {
            c = cr.query(Telephony.Mms.Inbox.CONTENT_URI, null, null, null, null);
            if (c.getCount() > 0) {
                foundEntries = true;
            }
        }
        if (!foundEntries) {
            c = cr.query(Telephony.Mms.Sent.CONTENT_URI, null, null, null, null);
            if (c.getCount() > 0) {
                foundEntries = true;
            }
        }
        if (!foundEntries) {
            c = cr.query(Telephony.Mms.Draft.CONTENT_URI, null, null, null, null);
            if (c.getCount() > 0) {
                foundEntries = true;
            }
        }
        return foundEntries;
    }

    private void manageSMS() {
        final boolean smsExport = foundEntriesToExport();

        int arrayRes = smsExport ? R.array.manage_sms_entries :
                R.array.manage_sms_import_entries;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setItems(arrayRes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case SMS_IMPORT:
                        new RestoreDeleteListFilesTask(false).execute();
                        break;
                    case SMS_DELETE:
                        new RestoreDeleteListFilesTask(true).execute();
                        break;
                    case SMS_EXPORT:
                        new BackupRestoreSMSTask(null).execute();
                        break;
                }
        }});
        builder.show();
    }

    /**
     * Trigger the TransactionService to download any outstanding messages.
     */
    private void startMmsDownload() {
        startService(new Intent(TransactionService.ACTION_ENABLE_AUTO_RETRIEVE, null, this,
                TransactionService.class));
    }

    NumberPickerDialog.OnNumberSetListener mSmsLimitListener =
        new NumberPickerDialog.OnNumberSetListener() {
            public void onNumberSet(int limit) {
                mSmsRecycler.setMessageLimit(MessagingPreferenceActivity.this, limit);
                setSmsDisplayLimit();
            }
    };

    NumberPickerDialog.OnNumberSetListener mMmsLimitListener =
        new NumberPickerDialog.OnNumberSetListener() {
            public void onNumberSet(int limit) {
                mMmsRecycler.setMessageLimit(MessagingPreferenceActivity.this, limit);
                setMmsDisplayLimit();
            }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case CONFIRM_CLEAR_SEARCH_HISTORY_DIALOG:
                return new AlertDialog.Builder(MessagingPreferenceActivity.this)
                    .setTitle(R.string.confirm_clear_search_title)
                    .setMessage(R.string.confirm_clear_search_text)
                    .setPositiveButton(android.R.string.ok, new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            SearchRecentSuggestions recent =
                                ((MmsApp)getApplication()).getRecentSuggestions();
                            if (recent != null) {
                                recent.clearHistory();
                            }
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .create();
        }
        return super.onCreateDialog(id);
    }

    public static boolean getNotificationEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean notificationsEnabled =
            prefs.getBoolean(MessagingPreferenceActivity.NOTIFICATION_ENABLED, true);
        return notificationsEnabled;
    }

    public static void enableNotifications(boolean enabled, Context context) {
        // Store the value of notifications in SharedPreferences
        SharedPreferences.Editor editor =
            PreferenceManager.getDefaultSharedPreferences(context).edit();

        editor.putBoolean(MessagingPreferenceActivity.NOTIFICATION_ENABLED, enabled);

        editor.apply();
    }

    public static boolean getQuickMessageEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean quickMessageEnabled =
            prefs.getBoolean(MessagingPreferenceActivity.QUICKMESSAGE_ENABLED, false);
        return quickMessageEnabled;
    }

    public static void enableQuickMessage(boolean enabled, Context context) {
        // Store the value of notifications in SharedPreferences
        SharedPreferences.Editor editor =
            PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(MessagingPreferenceActivity.QUICKMESSAGE_ENABLED, enabled);
        editor.apply();
    }

    public static boolean getQmLockscreenEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean qmLockscreenEnabled =
            prefs.getBoolean(MessagingPreferenceActivity.QM_LOCKSCREEN_ENABLED, false);
        return qmLockscreenEnabled;
    }

    public static void enableQmLockscreen(boolean enabled, Context context) {
        SharedPreferences.Editor editor =
            PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(MessagingPreferenceActivity.QM_LOCKSCREEN_ENABLED, enabled);
        editor.apply();
    }

    public static boolean getQmCloseAllEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean qmCloseAllEnabled =
            prefs.getBoolean(MessagingPreferenceActivity.QM_CLOSE_ALL_ENABLED, false);
        return qmCloseAllEnabled;
    }

    public static void enableQmCloseAll(boolean enabled, Context context) {
        SharedPreferences.Editor editor =
            PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(MessagingPreferenceActivity.QM_CLOSE_ALL_ENABLED, enabled);
        editor.apply();
    }

    public static void enableQmDarkTheme(boolean enabled, Context context) {
        SharedPreferences.Editor editor =
            PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(MessagingPreferenceActivity.QM_DARK_THEME_ENABLED, enabled);
        editor.apply();
    }

    public static boolean getQmDarkThemeEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean qmDarkThemeEnabled =
            prefs.getBoolean(MessagingPreferenceActivity.QM_DARK_THEME_ENABLED, false);
        return qmDarkThemeEnabled;
    }

    public static boolean isSmartCallEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(SMART_DIALER_ENABLED, false);
    }

    private void registerListeners() {
        mRingtonePref.setOnPreferenceChangeListener(this);
        final IntentFilter intentFilter =
                new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mReceiver, intentFilter);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean result = false;
        if (preference == mRingtonePref) {
            setRingtoneSummary((String)newValue);
            result = true;
        } else if (preference == mMessageSendDelayPref) {
            String value = (String) newValue;
            mMessageSendDelayPref.setValue(value);
            mMessageSendDelayPref.setSummary(mMessageSendDelayPref.getEntry());
            result = true;
        }
        return result;
    }


    private void showToast(int id) {
        Toast.makeText(this, id, Toast.LENGTH_SHORT).show();
    }

    /**
     * Set the SMSC preference enable or disable.
     *
     * @param id  the subscription of the slot, if the value is ALL_SUB, update all the SMSC
     *            preference
     * @param airplaneModeIsOn  the state of the airplane mode
     */
    private void setSMSCPrefState(int id, boolean prefEnabled) {
        // We need update the preference summary.
        if (prefEnabled) {
            Log.d(TAG, "get SMSC from sub= " + id);
            final Message callback = mHandler.obtainMessage(EVENT_GET_SMSC_DONE);
            Bundle userParams = new Bundle();
            userParams.putInt(PhoneConstants.SLOT_KEY, id);
            callback.obj = userParams;
            MessageUtils.getSmscFromSub(this, id, callback);
        } else {
            mSmscPrefList.get(id).setSummary(null);
        }
        mSmscPrefList.get(id).setEnabled(prefEnabled);
    }

    private void updateSMSCPref() {
        if (mSmscPrefList == null || mSmscPrefList.size() == 0) {
            return;
        }
        int count = TelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < count; i++) {
            setSMSCPrefState(i, !isAirPlaneModeOn() &&
                    (TelephonyManager.getDefault().isMultiSimEnabled()
                    ? MessageUtils.isIccCardActivated(i)
                    : TelephonyManager.getDefault().hasIccCard()));
        }
    }

    private void updateSmscFromBundle(Bundle bundle) {
        if (bundle != null) {
            int sub = bundle.getInt(PhoneConstants.SLOT_KEY, -1);
            if (sub != -1) {
                String summary = bundle.getString(MessageUtils.EXTRA_SMSC, null);
                if (summary == null) {
                    return;
                }
                Log.d(TAG, "Update SMSC: sub= " + sub + " SMSC= " + summary);
                int end = summary.lastIndexOf("\"");
                mSmscPrefList.get(sub).setSummary(summary.substring(1, end));
            }
        }
    }

    private static final class SmscHandler extends Handler {
        MessagingPreferenceActivity mOwner;
        public SmscHandler(MessagingPreferenceActivity owner) {
            super(Looper.getMainLooper());
            mOwner = owner;
        }
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = (Bundle) msg.obj;
            if (bundle == null) {
                return;
            }
            Throwable exception = (Throwable)bundle.getSerializable(EXTRA_EXCEPTION);
            if (exception != null) {
                Log.d(TAG, "Error: " + exception);
                mOwner.showToast(R.string.set_smsc_error);
                return;
            }

            Bundle userParams = (Bundle)bundle.getParcelable("userobj");
            if (userParams == null) {
                Log.d(TAG, "userParams = null");
                return;
            }
            switch (msg.what) {
                case EVENT_SET_SMSC_DONE:
                    Log.d(TAG, "Set SMSC successfully");
                    mOwner.showToast(R.string.set_smsc_success);
                    mOwner.updateSmscFromBundle(userParams);
                    break;
                case EVENT_GET_SMSC_DONE:
                    Log.d(TAG, "Get SMSC successfully");
                    int sub = userParams.getInt(PhoneConstants.SLOT_KEY, -1);
                    if (sub != -1) {
                        bundle.putInt(PhoneConstants.SLOT_KEY, sub);
                        mOwner.updateSmscFromBundle(bundle);
                    }
                    break;
            }
        }
    }

    public static class MyEditDialogFragment extends DialogFragment {
        private MessagingPreferenceActivity mActivity;

        public static MyEditDialogFragment newInstance(MessagingPreferenceActivity activity,
                CharSequence title, CharSequence smsc, int sub) {
            MyEditDialogFragment dialog = new MyEditDialogFragment();
            dialog.mActivity = activity;

            Bundle args = new Bundle();
            args.putCharSequence(SMSC_DIALOG_TITLE, title);
            args.putCharSequence(SMSC_DIALOG_NUMBER, smsc);
            args.putInt(SMSC_DIALOG_SUB, sub);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final int sub = getArguments().getInt(SMSC_DIALOG_SUB);
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
                            MyAlertDialogFragment newFragment = MyAlertDialogFragment.newInstance(
                                    mActivity, sub, edit.getText().toString());
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

    /**
     * All subclasses of Fragment must include a public empty constructor. The
     * framework will often re-instantiate a fragment class when needed, in
     * particular during state restore, and needs to be able to find this
     * constructor to instantiate it. If the empty constructor is not available,
     * a runtime exception will occur in some cases during state restore.
     */
    public static class MyAlertDialogFragment extends DialogFragment {
        private MessagingPreferenceActivity mActivity;

        public static MyAlertDialogFragment newInstance(MessagingPreferenceActivity activity,
                                                        int sub, String smsc) {
            MyAlertDialogFragment dialog = new MyAlertDialogFragment();
            dialog.mActivity = activity;

            Bundle args = new Bundle();
            args.putInt(SMSC_DIALOG_SUB, sub);
            args.putString(SMSC_DIALOG_NUMBER, smsc);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final int sub = getArguments().getInt(SMSC_DIALOG_SUB);
            final String displayedSMSC = getArguments().getString(SMSC_DIALOG_NUMBER);

            // When framework re-instantiate this fragment by public empty
            // constructor and call onCreateDialog(Bundle savedInstanceState) ,
            // we should make sure mActivity not null.
            if (null == mActivity) {
                mActivity = (MessagingPreferenceActivity) getActivity();
            }

            final String actualSMSC = mActivity.adjustSMSC(displayedSMSC);

            return new AlertDialog.Builder(mActivity)
                    .setIcon(android.R.drawable.ic_dialog_alert).setMessage(
                            R.string.set_smsc_confirm_message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                           Log.d(TAG, "set SMSC from sub= " +sub + " SMSC= " + displayedSMSC);
                           final Message callback = mHandler.obtainMessage(EVENT_SET_SMSC_DONE);
                           Bundle userParams = new Bundle();
                           userParams.putInt(PhoneConstants.SLOT_KEY, sub);
                           userParams.putString(MessageUtils.EXTRA_SMSC,actualSMSC);
                           callback.obj = userParams;
                           MessageUtils.setSmscForSub(mActivity, sub, actualSMSC, callback);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(true)
                    .create();
        }
    }

    private String adjustSMSC(String smsc) {
        String actualSMSC = "\"" + smsc + "\"";
        return actualSMSC;
    }

    // For the group mms feature to be enabled, the following must be true:
    //  1. the feature is enabled in mms_config.xml (currently on by default)
    //  2. the feature is enabled in the mms settings page
    //  3. the SIM knows its own phone number
    public static boolean getIsGroupMmsEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean groupMmsPrefOn = prefs.getBoolean(
                MessagingPreferenceActivity.GROUP_MMS_MODE, true);
        return MmsConfig.getGroupMmsEnabled() &&
                groupMmsPrefOn &&
                !TextUtils.isEmpty(MessageUtils.getLocalNumber());
    }
}
