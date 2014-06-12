package com.android.mms.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.*;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;

import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import com.android.mms.R;
import com.android.mms.data.cm.CMConversationSettings;

public class ConversationOptionsActivity extends Activity {
    private static final String TAG = "ConversationOptionsActivity";

    private static final boolean DEBUG = true;

    private long mThreadId;
    private PreferenceFragment mFragment;

    public class ConversationOptionsFragment extends PreferenceFragment
        implements OnPreferenceChangeListener, OnPreferenceClickListener {
        private static final String KEY_NOTIFICATION_ENABLED = "convopt_key_notification_enabled";
        private static final String KEY_NOTIFICATION_RINGTONE = "convopt_key_notification_ringtone";
        private static final String KEY_VIBRATE_ENABLED = "convopt_key_vibrate";
        private static final String KEY_VIBRATE_PATTERN = "convopt_key_vibrate_pattern";
        private static final String KEY_NOTIFICATIONS_CATEGORY = "convopt_key_notifications_category";
        private static final String KEY_RESET_TO_DEFAULT = "convopt_key_reset_to_default";

        private CheckBoxPreference mNotificationEnabled;
        private RingtonePreference mNotificationRingtone;
        private CheckBoxPreference mVibrateEnabled;
        private ListPreference mVibratePattern;
        private Preference mResetToDefault;
        private PreferenceCategory mNotificationsCategory;
        private CMConversationSettings mConversationSetting;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            new AsyncTask<Long, Void, CMConversationSettings>() {
                @Override
                protected CMConversationSettings doInBackground(Long... params) {
                    if (DEBUG)
                        Log.d(TAG, "Getting conversation setting of " + params[0]);
                    return CMConversationSettings.getOrNew(getActivity(), params[0].longValue());
                }

                @Override
                protected void onPostExecute(CMConversationSettings conversationSettings) {
                    if (DEBUG) {
                        Log.d(TAG, "Conversation setting:"
                            + " notificationEnabled=" +
                            conversationSettings.getNotificationEnabled()
                            + " notificationTone=" + conversationSettings.getNotificationTone()
                            + " vibrateEnabled=" + conversationSettings.getVibrateEnabled()
                            + " vibratePattern=" + conversationSettings.getVibratePattern());
                    }
                    mConversationSetting = conversationSettings;

                    updateUI();
                }
            }.execute(mThreadId);
            addPreferencesFromResource(R.xml.conversation_options);

            mNotificationsCategory = (PreferenceCategory) findPreference(
                KEY_NOTIFICATIONS_CATEGORY);
            mNotificationEnabled = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_ENABLED);
            mNotificationRingtone = (RingtonePreference) findPreference(KEY_NOTIFICATION_RINGTONE);
            mVibrateEnabled = (CheckBoxPreference) findPreference(KEY_VIBRATE_ENABLED);
            mVibratePattern = (ListPreference) findPreference(KEY_VIBRATE_PATTERN);
            mResetToDefault = findPreference(KEY_RESET_TO_DEFAULT);

            mResetToDefault.setOnPreferenceClickListener(this);

            mNotificationEnabled.setOnPreferenceChangeListener(this);
            mNotificationRingtone.setOnPreferenceChangeListener(this);
            mVibrateEnabled.setOnPreferenceChangeListener(this);
            mVibratePattern.setOnPreferenceChangeListener(this);

            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (!vibrator.hasVibrator()) {
                mNotificationsCategory.removePreference(mVibrateEnabled);
                mNotificationsCategory.removePreference(mVibratePattern);
                mVibrateEnabled = null;
                mVibratePattern = null;
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == mNotificationEnabled) {
                setNotificationEnabled((Boolean) newValue);
            } else if (preference == mNotificationRingtone) {
                setNotificationTone((String) newValue);
            } else if (preference == mVibrateEnabled) {
                setVibrateEnabled((Boolean) newValue);
            } else if (preference == mVibratePattern) {
                setVibratePattern((String) newValue);
            }
            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == mResetToDefault) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        if (DEBUG)
                            Log.d(TAG, "Resetting to default for " + mConversationSetting.getThreadId());

                        mConversationSetting.resetToDefault();
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void v){
                        updateUI();
                    }
                }.execute();
            }
            return false;
        }

        private void updateUI() {
            if (getActivity().isFinishing()) return;

            mNotificationEnabled.setEnabled(true);
            mNotificationRingtone.setEnabled(true);
            mResetToDefault.setEnabled(true);

            mNotificationEnabled.setChecked(mConversationSetting.getNotificationEnabled());
            setNotificationToneUI(mConversationSetting.getNotificationTone());

            if (mVibrateEnabled != null) {
                mVibrateEnabled.setEnabled(true);
                mVibratePattern.setEnabled(true);

                mVibrateEnabled.setChecked(mConversationSetting.getVibrateEnabled());
                mVibratePattern.setValue(mConversationSetting.getVibratePattern());
            }
        }

        private void setNotificationEnabled(final boolean enabled) {
            if (DEBUG)
                Log.d(TAG, "Set notification enabled: " + enabled);
            new AsyncTask<Void, Void, Void>() {
                @Override
                public Void doInBackground(Void... params) {
                    mConversationSetting.setNotificationEnabled(enabled);
                    return null;
                }

                @Override
                public void onPostExecute(Void v) {
                    mNotificationEnabled.setChecked(enabled);
                }
            }.execute();
        }

        private void setNotificationTone(final String uri) {
            if (DEBUG)
                Log.d(TAG, "Set notification tone: " + uri);
            new AsyncTask<Void, Void, Void>() {
                @Override
                public Void doInBackground(Void... params) {
                    mConversationSetting.setNotificationTone(uri);
                    return null;
                }

                @Override
                public void onPostExecute(Void v) {
                    setNotificationToneUI(uri);
                }
            }.execute();
        }

        private void setNotificationToneUI(String uri) {
            Activity activity = getActivity();
            Ringtone ringtone;
            if (uri.length() > 0) {
                ringtone = RingtoneManager.getRingtone(activity, Uri.parse(uri));
            } else {
                ringtone = RingtoneManager
                    .getRingtone(activity, Settings.System.DEFAULT_NOTIFICATION_URI);
            }
            mNotificationRingtone.setSummary(ringtone.getTitle(activity));
        }

        private void setVibrateEnabled(final boolean enabled) {
            if (DEBUG)
                Log.d(TAG, "Set vibrate tone: " + enabled);
            new AsyncTask<Void, Void, Void>() {
                @Override
                public Void doInBackground(Void... params) {
                    mConversationSetting.setVibrateEnabled(enabled);
                    return null;
                }

                @Override
                public void onPostExecute(Void v) {
                    mVibrateEnabled.setChecked(enabled);
                }
            }.execute();
        }

        private void setVibratePattern(final String pattern) {
            if (DEBUG)
                Log.d(TAG, "Set vibrate pattern: " + pattern);
            new AsyncTask<Void, Void, Void>() {
                @Override
                public Void doInBackground(Void... params) {
                    mConversationSetting.setVibratePattern(pattern);
                    return null;
                }

                @Override
                public void onPostExecute(Void v) {
                    int index = mVibratePattern.findIndexOfValue(pattern);
                    mVibratePattern.setSummary(mVibratePattern.getEntries()[index]);
                }
            }.execute();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mThreadId = savedInstanceState.getLong(ComposeMessageActivity.THREAD_ID);
        } else {
            mThreadId = getIntent().getLongExtra(ComposeMessageActivity.THREAD_ID, 0);
        }
        if (mThreadId == 0) {
            Log.e(TAG, "No thread id, exiting...");
            finish();
        }

        mFragment = new ConversationOptionsFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content, mFragment).commit();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putLong(ComposeMessageActivity.THREAD_ID, mThreadId);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = ComposeMessageActivity.createIntent(this, mThreadId);
                startActivity(intent);
                break;
        }
        return true;
    }
}

