package com.android.mms.data.cm;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;
import com.android.mms.ui.MessagingPreferenceActivity;

public class CMConversationSettings {
    private static final String TAG = "CMConversationSettings";

    private Context mContext;
    /* package */
    long mThreadId;
    int mNotificationEnabled;
    String mNotificationTone;
    int mVibrateEnabled;
    String mVibratePattern;

    private static final int DEFAULT_NOTIFICATION_ENABLED = CMMmsDatabaseHelper.DEFAULT;
    private static final String DEFAULT_NOTIFICATION_TONE = "";
    private static final int DEFAULT_VIBRATE_ENABLED = CMMmsDatabaseHelper.DEFAULT;
    private static final String DEFAULT_VIBRATE_PATTERN = "";

    private CMConversationSettings(Context context, long threadId, int notificationEnabled,
        String notificationTone, int vibrateEnabled, String vibratePattern) {
        mContext = context;
        mThreadId = threadId;
        mNotificationEnabled = notificationEnabled;
        mNotificationTone = notificationTone;
        mVibrateEnabled = vibrateEnabled;
        mVibratePattern = vibratePattern;
    }

    public static CMConversationSettings getOrNew(Context context, long threadId) {
        CMMmsDatabaseHelper helper = CMMmsDatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.query(CMMmsDatabaseHelper.CONVERSATIONS_TABLE,
            CMMmsDatabaseHelper.CONVERSATIONS_COLUMNS,
            " thread_id = ?",
            new String[] { String.valueOf(threadId) },
            null, null, null, null);

        // we should only have one result
        int count = cursor.getCount();
        CMConversationSettings convSetting;
        if (cursor != null && count == 1) {
            cursor.moveToFirst();
            convSetting = new CMConversationSettings(context,
                threadId,
                cursor.getInt(1),
                cursor.getString(2),
                cursor.getInt(3),
                cursor.getString(4)
            );
        } else if (count > 1) {
            Log.wtf(TAG, "More than one settings with the same thread id is returned!");
            return null;
        } else {
            convSetting = new CMConversationSettings(context, threadId,
                DEFAULT_NOTIFICATION_ENABLED, DEFAULT_NOTIFICATION_TONE,
                DEFAULT_VIBRATE_ENABLED, DEFAULT_VIBRATE_PATTERN);

            helper.insertCMConversationSettings(convSetting);
        }

        return convSetting;
    }

    public static void delete(Context context, long threadId) {
        CMMmsDatabaseHelper helper = CMMmsDatabaseHelper.getInstance(context);
        helper.deleteCMConversationSettings(threadId);
    }

    public static void deleteAll(Context context) {
        CMMmsDatabaseHelper helper = CMMmsDatabaseHelper.getInstance(context);
        helper.deleteAllCMConversationSettings();
    }

    public long getThreadId() {
        return mThreadId;
    }

    public boolean getNotificationEnabled() {
        if (mNotificationEnabled == CMMmsDatabaseHelper.DEFAULT) {
            SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);
            return sharedPreferences.getBoolean(MessagingPreferenceActivity.NOTIFICATION_ENABLED,
                DEFAULT_NOTIFICATION_ENABLED == CMMmsDatabaseHelper.TRUE);
        }
        return mNotificationEnabled == CMMmsDatabaseHelper.TRUE;
    }

    public void setNotificationEnabled(boolean enabled) {
        mNotificationEnabled = enabled ? CMMmsDatabaseHelper.TRUE : CMMmsDatabaseHelper.FALSE;
        setNotificationEnabled(mNotificationEnabled);
    }

    public void setNotificationEnabled(int enabled) {
        CMMmsDatabaseHelper helper = CMMmsDatabaseHelper.getInstance(mContext);
        helper.updateCMConversationSettingsField(mThreadId,
            CMMmsDatabaseHelper.CONVERSATIONS_NOTIFICATION_ENABLED, enabled);
    }

    public String getNotificationTone() {
        if (mNotificationTone.equals("")) {
            SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);
            return sharedPreferences.getString(MessagingPreferenceActivity.NOTIFICATION_RINGTONE,
                null);
        }
        return mNotificationTone;
    }

    public void setNotificationTone(String tone) {
        mNotificationTone = tone;
        CMMmsDatabaseHelper helper = CMMmsDatabaseHelper.getInstance(mContext);
        helper.updateCMConversationSettingsField(mThreadId,
            CMMmsDatabaseHelper.CONVERSATIONS_NOTIFICATION_TONE, tone);
    }

    public boolean getVibrateEnabled() {
        if (mVibrateEnabled == CMMmsDatabaseHelper.DEFAULT) {
            SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);
            return sharedPreferences.getBoolean(MessagingPreferenceActivity.NOTIFICATION_VIBRATE,
                DEFAULT_VIBRATE_ENABLED == CMMmsDatabaseHelper.TRUE);
        }
        return mVibrateEnabled == CMMmsDatabaseHelper.TRUE;
    }

    public void setVibrateEnabled(boolean enabled) {
        mVibrateEnabled = enabled ? CMMmsDatabaseHelper.TRUE : CMMmsDatabaseHelper.FALSE;
        setVibrateEnabled(mVibrateEnabled);
    }

    public void setVibrateEnabled(int enabled) {
        CMMmsDatabaseHelper helper = CMMmsDatabaseHelper.getInstance(mContext);
        helper.updateCMConversationSettingsField(mThreadId,
            CMMmsDatabaseHelper.CONVERSATIONS_VIBRATE_ENABLED, enabled);
    }

    public String getVibratePattern() {
        if (mVibratePattern.equals("")) {
            SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(mContext);
            return sharedPreferences.getString(MessagingPreferenceActivity.NOTIFICATION_VIBRATE_PATTERN,
                "0,1200");
        }
        return mVibratePattern;
    }

    public void setVibratePattern(String pattern) {
        mVibratePattern = pattern;
        CMMmsDatabaseHelper helper = CMMmsDatabaseHelper.getInstance(mContext);
        helper.updateCMConversationSettingsField(mThreadId,
            CMMmsDatabaseHelper.CONVERSATIONS_VIBRATE_PATTERN, pattern);
    }

    public void resetToDefault() {
        mNotificationEnabled = DEFAULT_NOTIFICATION_ENABLED;
        mNotificationTone = DEFAULT_NOTIFICATION_TONE;
        mVibrateEnabled = DEFAULT_VIBRATE_ENABLED;
        mVibratePattern = DEFAULT_VIBRATE_PATTERN;
        CMMmsDatabaseHelper helper = CMMmsDatabaseHelper.getInstance(mContext);
        helper.updateCMConversationSettings(this);
    }
}
