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
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
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
import android.preference.Preference.OnPreferenceClickListener;
import android.provider.SearchRecentSuggestions;
import android.provider.Settings;
import android.telephony.MSimSmsManager;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.util.BlacklistUtils;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.templates.TemplatesListActivity;
import com.android.mms.transaction.TransactionService;
import com.android.mms.util.Recycler;
import java.util.ArrayList;

import static com.android.internal.telephony.MSimConstants.MAX_PHONE_COUNT_DUAL_SIM;

/**
 * With this activity, users can set preferences for MMS and SMS and
 * can access and manipulate SMS messages stored on the SIM.
 */
public class MessagingPreferenceActivity extends PreferenceActivity
            implements OnPreferenceChangeListener {
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

    // Expiry of MMS
    private final static String EXPIRY_ONE_WEEK = "604800"; // 7 * 24 * 60 * 60
    private final static String EXPIRY_TWO_DAYS = "172800"; // 2 * 24 * 60 * 60

    private final static int MAX_SIGNATURE_LENGTH = 64;
    public static final String ENABLE_EMOTICONS         = "pref_key_enable_emoticons";

    // Unicode
    public static final String UNICODE_STRIPPING            = "pref_key_unicode_stripping";
    public static final String UNICODE_STRIPPING_VALUE      = "pref_key_unicode_stripping_value";
    public static final int UNICODE_STRIPPING_LEAVE_INTACT  = 0;
    public static final int UNICODE_STRIPPING_NON_DECODABLE = 1;

    // Split sms
    public static final String SMS_SPLIT_COUNTER        = "pref_key_sms_split_counter";

    // Templates
    public static final String MANAGE_TEMPLATES         = "pref_key_templates_manage";
    public static final String SHOW_GESTURE             = "pref_key_templates_show_gesture";
    public static final String GESTURE_SENSITIVITY      = "pref_key_templates_gestures_sensitivity";
    public static final String GESTURE_SENSITIVITY_VALUE = "pref_key_templates_gestures_sensitivity_value";

    // Timestamps
    public static final String FULL_TIMESTAMP            = "pref_key_mms_full_timestamp";
    public static final String SENT_TIMESTAMP            = "pref_key_mms_use_sent_timestamp";

    // Vibrate pattern
    public static final String NOTIFICATION_VIBRATE_PATTERN =
            "pref_key_mms_notification_vibrate_pattern";

    // Privacy mode
    public static final String PRIVACY_MODE_ENABLED = "pref_key_enable_privacy_mode";

    // Keyboard input type
    public static final String INPUT_TYPE                = "pref_key_mms_input_type";

    // QuickMessage
    public static final String QUICKMESSAGE_ENABLED      = "pref_key_quickmessage";
    public static final String QM_LOCKSCREEN_ENABLED     = "pref_key_qm_lockscreen";
    public static final String QM_CLOSE_ALL_ENABLED      = "pref_key_close_all";
    public static final String QM_DARK_THEME_ENABLED     = "pref_dark_theme";

    // Blacklist
    public static final String BLACKLIST                 = "pref_blacklist";

    private static final String TAG = "MessagingPreferenceActivity";
    // Menu entries
    private static final int MENU_RESTORE_DEFAULTS    = 1;

    // Preferences for enabling and disabling SMS
    private Preference mSmsDisabledPref;
    private Preference mSmsEnabledPref;

    private PreferenceCategory mStoragePrefCategory;
    private PreferenceCategory mSmsPrefCategory;
    private PreferenceCategory mMmsPrefCategory;
    private PreferenceCategory mNotificationPrefCategory;

    // Delay send
    public static final String SEND_DELAY_DURATION       = "pref_key_send_delay";

    private ListPreference mMessageSendDelayPref;
    private Preference mSmsLimitPref;
    private Preference mSmsDeliveryReportPref;
    private Preference mSmsDeliveryReportPrefSub1;
    private Preference mSmsDeliveryReportPrefSub2;
    private CheckBoxPreference mSmsSplitCounterPref;
    private Preference mMmsLimitPref;
    private Preference mMmsDeliveryReportPref;
    private Preference mMmsGroupMmsPref;
    private Preference mMmsReadReportPref;
    private Preference mManageSimPref;
    private Preference mClearHistoryPref;
    private CheckBoxPreference mVibratePref;
    private CheckBoxPreference mEnableNotificationsPref;
    private CheckBoxPreference mEnablePrivacyModePref;
    private CheckBoxPreference mEnableEmoticonsPref;
    private CheckBoxPreference mMmsAutoRetrievialPref;
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
    private CheckBoxPreference mSmsSignaturePref;
    private EditTextPreference mSmsSignatureEditPref;
    private PreferenceCategory mSmscPrefCate;
    private ArrayList<Preference> mSmscPrefList = new ArrayList<Preference>();
    private static final int CONFIRM_CLEAR_SEARCH_HISTORY_DIALOG = 3;

    private static final String TARGET_PACKAGE = "com.android.mms";
    private static final String TARGET_CLASS = "com.android.mms.ui.ManageSimMessages";
    // Templates
    private Preference mManageTemplate;
    private ListPreference mGestureSensitivity;
    private ListPreference mUnicodeStripping;
    private CharSequence[] mUnicodeStrippingEntries;

    // Keyboard input type
    private ListPreference mInputTypePref;
    private CharSequence[] mInputTypeEntries;
    private CharSequence[] mInputTypeValues;

    // Whether or not we are currently enabled for SMS. This field is updated in onResume to make
    // sure we notice if the user has changed the default SMS app.
    private boolean mIsSmsEnabled;
    private static final String SMSC_DIALOG_TITLE = "title";
    private static final String SMSC_DIALOG_NUMBER = "smsc";
    private static final String SMSC_DIALOG_SUB = "sub";
    private static final String COMMAND_GET_SMSC    = "com.android.smsc.cmd.get";
    private static final String COMMAND_SET_SMSC    = "com.android.smsc.cmd.set";
    private static final String NOTIFY_SMSC_UPDATE  = "com.android.smsc.notify.update";
    private static final String NOTIFY_SMSC_ERROR   = "com.android.smsc.notify.error";
    private static final String NOTIFY_SMSC_SUCCESS = "com.android.smsc.notify.success";

    private BroadcastReceiver mReceiver = null;

    // QuickMessage
    private CheckBoxPreference mEnableQuickMessagePref;
    private CheckBoxPreference mEnableQmLockscreenPref;
    private CheckBoxPreference mEnableQmCloseAllPref;
    private CheckBoxPreference mEnableQmDarkThemePref;

    // Blacklist
    private PreferenceScreen mBlacklist;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

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

    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
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
        mSmsLimitPref = findPreference("pref_key_sms_delete_limit");
        mSmsDeliveryReportPref = findPreference("pref_key_sms_delivery_reports");
        mSmsDeliveryReportPrefSub1 = findPreference("pref_key_sms_delivery_reports_slot1");
        mSmsDeliveryReportPrefSub2 = findPreference("pref_key_sms_delivery_reports_slot2");
        mSmsSplitCounterPref = (CheckBoxPreference) findPreference("pref_key_sms_split_counter");
        mMmsDeliveryReportPref = findPreference("pref_key_mms_delivery_reports");
        mMmsGroupMmsPref = findPreference("pref_key_mms_group_mms");
        mMmsReadReportPref = findPreference("pref_key_mms_read_reports");
        mMmsLimitPref = findPreference("pref_key_mms_delete_limit");
        mClearHistoryPref = findPreference("pref_key_mms_clear_history");
        mEnableNotificationsPref = (CheckBoxPreference) findPreference(NOTIFICATION_ENABLED);
        mMmsAutoRetrievialPref = (CheckBoxPreference) findPreference(AUTO_RETRIEVAL);
        mMmsExpiryPref = (ListPreference) findPreference("pref_key_mms_expiry");
        mMmsExpiryCard1Pref = (ListPreference) findPreference("pref_key_mms_expiry_slot1");
        mMmsExpiryCard2Pref = (ListPreference) findPreference("pref_key_mms_expiry_slot2");
        mEnablePrivacyModePref = (CheckBoxPreference) findPreference(PRIVACY_MODE_ENABLED);
        mVibratePref = (CheckBoxPreference) findPreference(NOTIFICATION_VIBRATE);
        mSmsSignaturePref = (CheckBoxPreference) findPreference("pref_key_enable_signature");
        mSmsSignatureEditPref = (EditTextPreference) findPreference("pref_key_edit_signature");
        mRingtonePref = (RingtonePreference) findPreference(NOTIFICATION_RINGTONE);
        mSmsStorePref = (ListPreference) findPreference("pref_key_sms_store");
        mSmsStoreCard1Pref = (ListPreference) findPreference("pref_key_sms_store_card1");
        mSmsStoreCard2Pref = (ListPreference) findPreference("pref_key_sms_store_card2");
        mSmsTemplate = findPreference("pref_key_message_template");
        mSmscPrefCate = (PreferenceCategory) findPreference("pref_key_smsc");
        mSmsValidityPref = (ListPreference) findPreference("pref_key_sms_validity_period");
        mSmsValidityCard1Pref
            = (ListPreference) findPreference("pref_key_sms_validity_period_slot1");
        mSmsValidityCard2Pref
            = (ListPreference) findPreference("pref_key_sms_validity_period_slot2");

        mManageTemplate = findPreference(MANAGE_TEMPLATES);
        mGestureSensitivity = (ListPreference) findPreference(GESTURE_SENSITIVITY);
        mUnicodeStripping = (ListPreference) findPreference(UNICODE_STRIPPING);
        mUnicodeStrippingEntries = getResources().getTextArray(R.array.pref_unicode_stripping_entries);

        // QuickMessage
        mEnableQuickMessagePref = (CheckBoxPreference) findPreference(QUICKMESSAGE_ENABLED);
        mEnableQmLockscreenPref = (CheckBoxPreference) findPreference(QM_LOCKSCREEN_ENABLED);
        mEnableQmCloseAllPref = (CheckBoxPreference) findPreference(QM_CLOSE_ALL_ENABLED);
        mEnableQmDarkThemePref = (CheckBoxPreference) findPreference(QM_DARK_THEME_ENABLED);

        // Keyboard input type
        mInputTypePref = (ListPreference) findPreference(INPUT_TYPE);
        mInputTypeEntries = getResources().getTextArray(R.array.pref_entries_input_type);
        mInputTypeValues = getResources().getTextArray(R.array.pref_values_input_type);

        // Blacklist screen - Needed for setting summary
        mBlacklist = (PreferenceScreen) findPreference(BLACKLIST);

        // Remove the Blacklist item if we are not running on CyanogenMod
        // This allows the app to be run on non-blacklist enabled roms (including Stock)
        if (!MessageUtils.isCyanogenMod(this)) {
            PreferenceCategory extraCategory = (PreferenceCategory) findPreference("pref_key_extra_settings");
            extraCategory.removePreference(mBlacklist);
            mBlacklist = null;
        }

        // SMS Sending Delay
        mMessageSendDelayPref = (ListPreference) findPreference(SEND_DELAY_DURATION);
        mMessageSendDelayPref.setSummary(mMessageSendDelayPref.getEntry());

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
        registerListeners();

        // NOTE: After restoring preferences, the auto delete function (i.e. message recycler)
        // will be turned off by default. However, we really want the default to be turned on.
        // Because all the prefs are cleared, that'll cause:
        // ConversationList.runOneTimeStorageLimitCheckForLegacyMessages to get executed the
        // next time the user runs the Messaging app and it will either turn on the setting
        // by default, or if the user is over the limits, encourage them to turn on the setting
        // manually.
    }

    private void setMessagePreferences() {
        setMessagePriorityPref();
        updateSignatureStatus();
        showSmscPref();

        if (!MmsApp.getApplication().getTelephonyManager().hasIccCard()) {
            // No SIM card, remove the SIM-related prefs
            mSmsPrefCategory.removePreference(mManageSimPref);
        }

        if (!MmsConfig.getSMSDeliveryReportsEnabled()) {
            mSmsPrefCategory.removePreference(mSmsDeliveryReportPref);
            mSmsPrefCategory.removePreference(mSmsDeliveryReportPrefSub1);
            mSmsPrefCategory.removePreference(mSmsDeliveryReportPrefSub2);
            if (!MmsApp.getApplication().getTelephonyManager().hasIccCard()) {
                getPreferenceScreen().removePreference(mSmsPrefCategory);
            }
        }

        if (!MmsConfig.getSplitSmsEnabled()) {
            // SMS Split disabled, remove SplitCounter pref
            PreferenceCategory smsCategory =
            (PreferenceCategory)findPreference("pref_key_sms_settings");
            smsCategory.removePreference(mSmsSplitCounterPref);
        }

        if (!MmsConfig.getMmsEnabled()) {
            // No Mms, remove all the mms-related preferences
            getPreferenceScreen().removePreference(mMmsPrefCategory);

            mStoragePrefCategory.removePreference(findPreference("pref_key_mms_delete_limit"));
        } else {
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                mSmsPrefCategory.removePreference(mSmsDeliveryReportPref);
            } else {
                mSmsPrefCategory.removePreference(mSmsDeliveryReportPrefSub1);
                mSmsPrefCategory.removePreference(mSmsDeliveryReportPrefSub2);
            }
        }

        setMmsRelatedPref();

        setEnabledNotificationsPref();

        setSmsPreferStoragePref();
        setSmsValidityPeriodPref();

        // Privacy mode
        setEnabledPrivacyModePref();

        // QuickMessage
        setEnabledQuickMessagePref();
        setEnabledQmLockscreenPref();
        setEnabledQmCloseAllPref();
        setEnabledQmDarkThemePref();

        // If needed, migrate vibration setting from the previous tri-state setting stored in
        // NOTIFICATION_VIBRATE_WHEN to the boolean setting stored in NOTIFICATION_VIBRATE.
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
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

        mManageTemplate.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(MessagingPreferenceActivity.this,
                        TemplatesListActivity.class);
                startActivity(intent);
                return false;
            }
        });

        String gestureSensitivity = String.valueOf(sharedPreferences.getInt(GESTURE_SENSITIVITY_VALUE, 3));
        mGestureSensitivity.setSummary(gestureSensitivity);
        mGestureSensitivity.setValue(gestureSensitivity);
        mGestureSensitivity.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int value = Integer.parseInt((String) newValue);
                sharedPreferences.edit().putInt(GESTURE_SENSITIVITY_VALUE, value).commit();
                mGestureSensitivity.setSummary(String.valueOf(value));
                return true;
            }
        });

        int unicodeStripping = sharedPreferences.getInt(UNICODE_STRIPPING_VALUE, UNICODE_STRIPPING_LEAVE_INTACT);
        mUnicodeStripping.setValue(String.valueOf(unicodeStripping));
        mUnicodeStripping.setSummary(mUnicodeStrippingEntries[unicodeStripping]);
        mUnicodeStripping.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int value = Integer.parseInt((String) newValue);
                sharedPreferences.edit().putInt(UNICODE_STRIPPING_VALUE, value).commit();
                mUnicodeStripping.setSummary(mUnicodeStrippingEntries[value]);
                return true;
            }
        });

        mSmsRecycler = Recycler.getSmsRecycler();
        mMmsRecycler = Recycler.getMmsRecycler();

        // Fix up the recycler's summary with the correct values
        setSmsDisplayLimit();
        setMmsDisplayLimit();
        setMmsExpiryPref();

        String soundValue = sharedPreferences.getString(NOTIFICATION_RINGTONE, null);
        setRingtoneSummary(soundValue);

        // Read the input type value and set the summary
        String inputType = sharedPreferences.getString(MessagingPreferenceActivity.INPUT_TYPE,
                Integer.toString(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE));
        mInputTypePref.setValue(inputType);
        adjustInputTypeSummary(mInputTypePref.getValue());
        mInputTypePref.setOnPreferenceChangeListener(this);

        mMessageSendDelayPref.setOnPreferenceChangeListener(this);
    }

    public static long getMessageSendDelayDuration(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Long.valueOf(prefs.getString(SEND_DELAY_DURATION, "0"));
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

        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            if(MessageUtils.getActivatedIccCardCount() < MSimConstants.MAX_PHONE_COUNT_DUAL_SIM) {
                int preferredSmsSub = MSimSmsManager.getDefault()
                        .getPreferredSmsSubscription();
                mManageSimPref.setSummary(
                        getString(R.string.pref_summary_manage_sim_messages_slot,
                                preferredSmsSub + 1));
            } else {
                mManageSimPref.setSummary(
                        getString(R.string.pref_summary_manage_sim_messages));
            }
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

    private void setSmsPreferStoragePref() {
        if (getResources().getBoolean(R.bool.config_savelocation)) {
            PreferenceCategory storageOptions =
                    (PreferenceCategory)findPreference("pref_key_storage_settings");
            if (MessageUtils.isMultiSimEnabledMms()) {
                storageOptions.removePreference(mSmsStorePref);

                if (!MessageUtils.hasIccCard(MSimConstants.SUB1)) {
                    storageOptions.removePreference(mSmsStoreCard1Pref);
                } else {
                    setSmsPreferStoreSummary(MSimConstants.SUB1);
                }
                if (!MessageUtils.hasIccCard(MSimConstants.SUB2)) {
                    storageOptions.removePreference(mSmsStoreCard2Pref);
                } else {
                    setSmsPreferStoreSummary(MSimConstants.SUB2);
                }
            } else {
                storageOptions.removePreference(mSmsStoreCard1Pref);
                storageOptions.removePreference(mSmsStoreCard2Pref);

                if (!MessageUtils.hasIccCard()) {
                    storageOptions.removePreference(mSmsStorePref);
                } else {
                    setSmsPreferStoreSummary();
                }
            }
        } else {
            PreferenceCategory storageOptions =
                    (PreferenceCategory)findPreference("pref_key_storage_settings");
            storageOptions.removePreference(mSmsStorePref);
            storageOptions.removePreference(mSmsStoreCard1Pref);
            storageOptions.removePreference(mSmsStoreCard2Pref);
        }
    }

    private void setSmsValidityPeriodPref() {
        PreferenceCategory storageOptions =
                (PreferenceCategory)findPreference("pref_key_sms_settings");
        if (getResources().getBoolean(R.bool.config_sms_validity)) {
            if (MessageUtils.isMultiSimEnabledMms()) {
                storageOptions.removePreference(mSmsValidityPref);
                setSmsPreferValiditySummary(MSimConstants.SUB1);
                setSmsPreferValiditySummary(MSimConstants.SUB2);
            } else {
                storageOptions.removePreference(mSmsValidityCard1Pref);
                storageOptions.removePreference(mSmsValidityCard2Pref);
                setSmsPreferValiditySummary(MSimConstants.INVALID_SUBSCRIPTION);
            }
        } else {
            storageOptions.removePreference(mSmsValidityPref);
            storageOptions.removePreference(mSmsValidityCard1Pref);
            storageOptions.removePreference(mSmsValidityCard2Pref);
        }
    }

    private void setRingtoneSummary(String soundValue) {
        Uri soundUri = TextUtils.isEmpty(soundValue) ? null : Uri.parse(soundValue);
        Ringtone tone = soundUri != null ? RingtoneManager.getRingtone(this, soundUri) : null;
        mRingtonePref.setSummary(tone != null ? tone.getTitle(this)
                : getResources().getString(R.string.silent_ringtone));
    }

    private void showSmscPref() {
        int count = MSimTelephonyManager.getDefault().getPhoneCount();
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
        registerReceiver();
    }

    private boolean isAirPlaneModeOn() {
        return Settings.System.getInt(getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    private String getSMSCDialogTitle(int count, int index) {
        String title = MSimTelephonyManager.getDefault().isMultiSimEnabled()
                ? getString(R.string.pref_more_smcs, index + 1)
                : getString(R.string.pref_one_smcs);
        return title;
    }

    private void setSmsPreferStoreSummary() {
        mSmsStorePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String summary = newValue.toString();
                int index = mSmsStorePref.findIndexOfValue(summary);
                mSmsStorePref.setSummary(mSmsStorePref.getEntries()[index]);
                mSmsStorePref.setValue(summary);
                return true;
            }
        });
        mSmsStorePref.setSummary(mSmsStorePref.getEntry());
    }

    private void setSmsPreferStoreSummary(int subscription) {
        if (MSimConstants.SUB1 == subscription) {
            mSmsStoreCard1Pref.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final String summary = newValue.toString();
                    int index = mSmsStoreCard1Pref.findIndexOfValue(summary);
                    mSmsStoreCard1Pref.setSummary(mSmsStoreCard1Pref.getEntries()[index]);
                    mSmsStoreCard1Pref.setValue(summary);
                    return false;
                }
            });
            mSmsStoreCard1Pref.setSummary(mSmsStoreCard1Pref.getEntry());
        } else {
            mSmsStoreCard2Pref.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final String summary = newValue.toString();
                    int index = mSmsStoreCard2Pref.findIndexOfValue(summary);
                    mSmsStoreCard2Pref.setSummary(mSmsStoreCard2Pref.getEntries()[index]);
                    mSmsStoreCard2Pref.setValue(summary);
                    return false;
                }
            });
            mSmsStoreCard2Pref.setSummary(mSmsStoreCard2Pref.getEntry());
        }
    }

    private void setSmsPreferValiditySummary(int subscription) {
        switch (subscription) {
            case MSimConstants.INVALID_SUBSCRIPTION:
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
                break;
            case MSimConstants.SUB1:
                mSmsValidityCard1Pref.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final String summary = newValue.toString();
                        int index = mSmsValidityCard1Pref.findIndexOfValue(summary);
                        mSmsValidityCard1Pref.setSummary(mSmsValidityCard1Pref.getEntries()[index]);
                        mSmsValidityCard1Pref.setValue(summary);
                        return true;
                    }
                });
                mSmsValidityCard1Pref.setSummary(mSmsValidityCard1Pref.getEntry());
                break;
            case MSimConstants.SUB2:
                mSmsValidityCard2Pref.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final String summary = newValue.toString();
                        int index = mSmsValidityCard2Pref.findIndexOfValue(summary);
                        mSmsValidityCard2Pref.setSummary(mSmsValidityCard2Pref.getEntries()[index]);
                        mSmsValidityCard2Pref.setValue(summary);
                        return true;
                    }
                });
                mSmsValidityCard2Pref.setSummary(mSmsValidityCard2Pref.getEntry());
                break;
            default:
                break;
        }
    }

    private void setEnabledNotificationsPref() {
        // The "enable notifications" setting is really stored in our own prefs. Read the
        // current value and set the checkbox to match.
        mEnableNotificationsPref.setChecked(getNotificationEnabled(this));
    }

    private void setEnabledPrivacyModePref() {
        // The "enable privacy mode" setting is really stored in our own prefs. Read the
        // current value and set the checkbox to match.
        boolean isPrivacyModeEnabled = getPrivacyModeEnabled(this);
        mEnablePrivacyModePref.setChecked(isPrivacyModeEnabled);

        // Enable/Disable the "enable quickmessage" setting according to
        // the "enable privacy mode" setting state
        mEnableQuickMessagePref.setEnabled(!isPrivacyModeEnabled);

        // Enable/Disable the "enable dark theme" setting according to
        // the "enable privacy mode" setting state
        mEnableQmDarkThemePref.setEnabled(!isPrivacyModeEnabled);
    }

    private void setEnabledQuickMessagePref() {
        // The "enable quickmessage" setting is really stored in our own prefs. Read the
        // current value and set the checkbox to match.
        mEnableQuickMessagePref.setChecked(getQuickMessageEnabled(this));
    }

    private void setEnabledQmLockscreenPref() {
        // The "enable quickmessage on lock screen " setting is really stored in our own prefs. Read the
        // current value and set the checkbox to match.
        mEnableQmLockscreenPref.setChecked(getQmLockscreenEnabled(this));
    }

    private void setEnabledQmCloseAllPref() {
        // The "enable close all" setting is really stored in our own prefs. Read the
        // current value and set the checkbox to match.
        mEnableQmCloseAllPref.setChecked(getQmCloseAllEnabled(this));
    }

    private void setEnabledQmDarkThemePref() {
        // The "Use dark theme" setting is really stored in our own prefs. Read the
        // current value and set the checkbox to match.
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
            setMmsExpirySummary(MSimConstants.SUB1);
            setMmsExpirySummary(MSimConstants.SUB2);
        } else {
            mmsSettings.removePreference(mMmsExpiryCard1Pref);
            mmsSettings.removePreference(mMmsExpiryCard2Pref);
            setMmsExpirySummary(MSimConstants.INVALID_SUBSCRIPTION);
        }
    }

    private void setMmsExpirySummary(int subscription) {
        switch (subscription) {
            case MSimConstants.INVALID_SUBSCRIPTION:
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
                mMmsExpiryPref.setSummary(mMmsExpiryPref.getEntry());
                break;
            case MSimConstants.SUB1:
                mMmsExpiryCard1Pref.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final String value = newValue.toString();
                        int index = mMmsExpiryCard1Pref.findIndexOfValue(value);
                        mMmsExpiryCard1Pref.setValue(value);
                        mMmsExpiryCard1Pref.setSummary(mMmsExpiryCard1Pref.getEntries()[index]);
                        return false;
                    }
                });
                mMmsExpiryCard1Pref.setSummary(mMmsExpiryCard1Pref.getEntry());
                break;
            case MSimConstants.SUB2:
                mMmsExpiryCard2Pref.setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final String value = newValue.toString();
                        int index = mMmsExpiryCard2Pref.findIndexOfValue(value);
                        mMmsExpiryCard2Pref.setValue(value);
                        mMmsExpiryCard2Pref.setSummary(mMmsExpiryCard2Pref.getEntries()[index]);
                        return false;
                    }
                });
                mMmsExpiryCard2Pref.setSummary(mMmsExpiryCard2Pref.getEntry());
                break;
            default:
                break;
        }
    }

    private void updateSignatureStatus() {
        // If the signature CheckBox is checked, we should set the signature EditText
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
                    R.string.pref_title_sms_delete).show();

        } else if (preference == mMmsLimitPref) {
            new NumberPickerDialog(this,
                    mMmsLimitListener,
                    mMmsRecycler.getMessageLimit(this),
                    mMmsRecycler.getMessageMinLimit(),
                    mMmsRecycler.getMessageMaxLimit(),
                    R.string.pref_title_mms_delete).show();
        } else if (preference == mManageSimPref) {
            // If one SIM card valid, display the valid subcription messages.
            // If two SIM card valid, display select subcription activity.
            if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()
                    || MessageUtils.getActivatedIccCardCount() < 2) {
                Intent intent = new Intent(this, ManageSimMessages.class);
                intent.putExtra(MSimConstants.SUBSCRIPTION_KEY, getSubscriptionKey());
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, SelectSubscription.class);
                intent.putExtra(SelectSubscription.PACKAGE, TARGET_PACKAGE);
                intent.putExtra(SelectSubscription.TARGET_CLASS, TARGET_CLASS);
                startActivity(intent);
            }
        } else if (preference == mClearHistoryPref) {
            showDialog(CONFIRM_CLEAR_SEARCH_HISTORY_DIALOG);
            return true;

        } else if (preference == mEnableNotificationsPref) {
            // Update the actual "enable notifications" value that is stored in secure settings.
            enableNotifications(mEnableNotificationsPref.isChecked(), this);
        } else if (preference == mSmsSignaturePref) {
            updateSignatureStatus();

        } else if (preference == mEnablePrivacyModePref) {
            // Update the actual "enable private mode" value that is stored in secure settings.
            enablePrivacyMode(mEnablePrivacyModePref.isChecked(), this);

            // Update "enable quickmessage" checkbox state
            mEnableQuickMessagePref.setEnabled(!mEnablePrivacyModePref.isChecked());

            // Update "enable dark theme" checkbox state
            mEnableQmDarkThemePref.setEnabled(!mEnablePrivacyModePref.isChecked());

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

    private int getSubscriptionKey(){
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            return MessageUtils.isIccCardActivated(MessageUtils.SUB1)
                    ? MessageUtils.SUB1: MessageUtils.SUB2;
        }
        return MessageUtils.SUB_INVALID;
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

    public static boolean getPrivacyModeEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean privacyModeEnabled =
            prefs.getBoolean(MessagingPreferenceActivity.PRIVACY_MODE_ENABLED, false);
        return privacyModeEnabled;
    }

    public static void enablePrivacyMode(boolean enabled, Context context) {
        // Store the value of private mode in SharedPreferences
        SharedPreferences.Editor editor =
            PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(MessagingPreferenceActivity.PRIVACY_MODE_ENABLED, enabled);
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

    private void registerListeners() {
        mRingtonePref.setOnPreferenceChangeListener(this);
        mSmsSignatureEditPref.getEditText().addTextChangedListener(mTextEditorWatcher);
    }

    private final TextWatcher mTextEditorWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s != null && s.length() == MAX_SIGNATURE_LENGTH) {
                showToast(R.string.signature_full_toast);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean result = false;
        if (preference == mRingtonePref) {
            setRingtoneSummary((String)newValue);
            result = true;
        } else if (preference == mInputTypePref) {
            adjustInputTypeSummary((String)newValue);
            result = true;
        } else if (preference == mMessageSendDelayPref) {
            String value = (String) newValue;
            mMessageSendDelayPref.setValue(value);
            mMessageSendDelayPref.setSummary(mMessageSendDelayPref.getEntry());
            result = true;
        }
        return result;
    }

    private void registerReceiver() {
        if (mReceiver != null) return;
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                    updateSMSCPref();
                } else if (NOTIFY_SMSC_ERROR.equals(action)) {
                    showToast(R.string.set_smsc_error);
                } else if (NOTIFY_SMSC_SUCCESS.equals(action)) {
                    showToast(R.string.set_smsc_success);
                } else if (NOTIFY_SMSC_UPDATE.equals(action)) {
                    int sub = intent.getIntExtra(SMSC_DIALOG_SUB, 0);
                    String summary = intent.getStringExtra(SMSC_DIALOG_NUMBER);
                    mSmscPrefList.get(sub).setSummary(summary);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(NOTIFY_SMSC_ERROR);
        filter.addAction(NOTIFY_SMSC_SUCCESS);
        filter.addAction(NOTIFY_SMSC_UPDATE);
        registerReceiver(mReceiver, filter);
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
    private void updateSMSCPref() {
        if (mSmscPrefList == null || mSmscPrefList.size() == 0) {
            return;
        }
        int count = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < count; i++) {
            setSMSCPrefState(i, !isAirPlaneModeOn() &&
                    (MSimTelephonyManager.getDefault().isMultiSimEnabled()
                    ? MSimTelephonyManager.getDefault().hasIccCard(i)
                    : TelephonyManager.getDefault().hasIccCard()));
        }
    }

    private void setSMSCPrefState(int id, boolean prefEnabled) {
        // We need update the preference summary.
        if (prefEnabled) {
            Intent get = new Intent();
            get.setAction(COMMAND_GET_SMSC);
            get.putExtra(SMSC_DIALOG_SUB, id);
            startService(get);
        } else {
            mSmscPrefList.get(id).setSummary(null);
        }
        mSmscPrefList.get(id).setEnabled(prefEnabled);
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

            return new AlertDialog.Builder(mActivity)
                    .setIcon(android.R.drawable.ic_dialog_alert).setMessage(
                            R.string.set_smsc_confirm_message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Intent intent = new Intent();
                            intent.setAction(COMMAND_SET_SMSC);
                            intent.putExtra(SMSC_DIALOG_SUB, sub);
                            intent.putExtra(SMSC_DIALOG_NUMBER, displayedSMSC);
                            mActivity.startService(intent);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(true)
                    .create();
        }
    }

    private void adjustInputTypeSummary(String value) {
        int len = mInputTypeValues.length;
        for (int i = 0; i < len; i++) {
            if (mInputTypeValues[i].equals(value)) {
                mInputTypePref.setSummary(mInputTypeEntries[i]);
                return;
            }
        }
        mInputTypePref.setSummary(R.string.pref_keyboard_unknown);
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
