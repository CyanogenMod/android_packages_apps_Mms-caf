/*
* Copyright (C) 2015 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.mms.ui.next;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.provider.Telephony.Threads;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toolbar;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.SmsRejectedReceiver;
import com.android.mms.ui.ConversationListAdapter;
import com.android.mms.ui.ConversationListAdapter.OnContentChangedListener;
import com.android.mms.ui.MailBoxMessageList;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.android.mms.util.DraftCache;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

/**
 * NextConversationListActivity
 * <pre>
 *
 * </pre>
 */
public class NextConversationListActivity extends Activity implements
        DraftCache.OnDraftChangedListener, OnContentChangedListener, OnKeyListener {

    // Constants
    private static final String TAG = LogTag.TAG;

    /* package */
    static void logd(String format, Object... args) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            String s = String.format(format, args);
            Log.d(TAG, "[" + Thread.currentThread().getId() + "] " + s);
        }
    }

    /* package */
    static void loge(String format, Object... args) {
        String s = String.format(format, args);
        Log.e(TAG, "[" + Thread.currentThread().getId() + "] " + s);
    }

    // IDs of the context menu items for the list of conversations.
    public static final int MENU_DELETE = 0;
    public static final int MENU_VIEW = 1;
    public static final int MENU_VIEW_CONTACT = 2;
    public static final int MENU_ADD_TO_CONTACTS = 3;

    // Members
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private ThreadListQueryHandler mQueryHandler;
    private NextConversationListAdapter mListAdapter;
    private ProgressDialog mProgressDialog;
    private Integer mFilterSubId = null;

    // Toolbar Views
    private Spinner mFilterSpinner;
    private TextView mUnreadConvCount;
    private SearchView mSearchView;

    // Views
    private StickyListHeadersListView mListView;
    private TextView mEmptyView;
    private View mSmsPromoBannerView;

    // Flags
    private boolean mIsRunning;
    private boolean mIsSmsEnabled;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.next_conversation_list_screen);
        if (MessageUtils.isMailboxMode()) {
            Intent modeIntent = new Intent(this, MailBoxMessageList.class);
            startActivityIfNeeded(modeIntent, -1);
            finish();
            return;
        }

        initSmsPromoBanner();

        setTitle(R.string.app_label);
        setupActionBar();
        setupUi(); // Including list view
        initListAdapter();

        mQueryHandler = new ThreadListQueryHandler(getContentResolver(), mListAdapter);

    }

    @Override
    public void onStart() {
        super.onStart();

        MessagingNotification.cancelNotification(getApplicationContext(),
                SmsRejectedReceiver.SMS_REJECTED_NOTIFICATION_ID);

        DraftCache.getInstance().addOnDraftChangedListener(this);

        startAsyncQuery();

        // We used to refresh the DraftCache here, but
        // refreshing the DraftCache each time we go to the ConversationList seems overly
        // aggressive. We already update the DraftCache when leaving CMA in onStop() and
        // onNewIntent(), and when we delete threads or delete all in CMA or this activity.
        // I hope we don't have to do such a heavy operation each time we enter here.

        // we invalidate the contact cache here because we want to get updated presence
        // and any contact changes. We don't invalidate the cache by observing presence and contact
        // changes (since that's too untargeted), so as a tradeoff we do it here.
        // If we're in the middle of the app initialization where we're loading the conversation
        // threads, don't invalidate the cache because we're in the process of building it.
        // TODO: think of a better way to invalidate cache more surgically or based on actual
        // TODO: changes we care about
        if (!Conversation.loadingThreads()) {
            Contact.invalidateCache();
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        if (isSmsEnabled != mIsSmsEnabled) {
            mIsSmsEnabled = isSmsEnabled;
            invalidateOptionsMenu();
        }

        // Show or hide the SMS promo banner
        if (mIsSmsEnabled || MmsConfig.isSmsPromoDismissed(this)) {
            mSmsPromoBannerView.setVisibility(View.GONE);
        } else {
            initSmsPromoBanner();
            mSmsPromoBannerView.setVisibility(View.VISIBLE);
        }

        mIsRunning = true;
    }

    @Override
    public void onPause() {
        mIsRunning = false;
        super.onPause();
    }

    @Override
    public void onStop() {
        stopAsyncQuery();
        DraftCache.getInstance().removeOnDraftChangedListener(this);
        if (mListView != null) {
            mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

            if (mListAdapter != null) {
                Cursor cursor = mListAdapter.getCursor();
                if (cursor != null && !cursor.isClosed()) {
                    cursor.close();
                }
            }

        }

        if (mListAdapter != null) {
            mListAdapter.changeCursor(null);
        }
        super.onStop();
    }

    private void setupActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        ActionBar actionBar = getActionBar();

        ViewGroup v = (ViewGroup) LayoutInflater.from(this)
                .inflate(R.layout.conversation_list_actionbar, null);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(v,
                new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        mUnreadConvCount = (TextView) v.findViewById(R.id.unread_conv_count);
    }

    private void setupUi() {
        mListView = (StickyListHeadersListView) findViewById(R.id.mms_list);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setOnKeyListener(this);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mEmptyView = (TextView) findViewById(R.id.empty);
        mListView.setEmptyView(mEmptyView);
    }

    private void initSmsPromoBanner() {
        final PackageManager packageManager = getPackageManager();
        final String smsAppPackage = Telephony.Sms.getDefaultSmsPackage(this);

        // Get all the data we need about the default app to properly render the promo banner. We
        // try to show the icon and name of the user's selected SMS app and have the banner link
        // to that app. If we can't read that information for any reason we leave the fallback
        // text that links to Messaging settings where the user can change the default.
        Drawable smsAppIcon = null;
        ApplicationInfo smsAppInfo = null;
        try {
            smsAppIcon = packageManager.getApplicationIcon(smsAppPackage);
            smsAppInfo = packageManager.getApplicationInfo(smsAppPackage, 0);
        } catch (NameNotFoundException e) {
            loge("Could not retrieve current sms appinfo");
        }
        final Intent smsAppIntent = packageManager.getLaunchIntentForPackage(smsAppPackage);

        mSmsPromoBannerView = findViewById(R.id.banner_sms_promo);

        // If we got all the info we needed
        if (smsAppIcon != null && smsAppInfo != null && smsAppIntent != null) {
            ImageView defaultSmsAppIconImageView =
                    (ImageView) mSmsPromoBannerView.findViewById(R.id.banner_sms_default_app_icon);
            defaultSmsAppIconImageView.setImageDrawable(smsAppIcon);
            TextView smsPromoBannerTitle =
                    (TextView) mSmsPromoBannerView.findViewById(R.id.banner_sms_promo_title);
            String message = getResources().getString(R.string.banner_sms_promo_title_application,
                    smsAppInfo.loadLabel(packageManager));
            smsPromoBannerTitle.setText(message);

            mSmsPromoBannerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(smsAppIntent);
                }
            });
        } else {
            // Otherwise the banner will be left alone and will launch settings
            mSmsPromoBannerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Launch settings
                    Intent settingsIntent = new Intent(NextConversationListActivity.this,
                            MessagingPreferenceActivity.class);
                    startActivityIfNeeded(settingsIntent, -1);
                }
            });
        }
    }

    private void initListAdapter() {
        mListAdapter = new NextConversationListAdapter(this, null);
        mListAdapter.setOnContentChangedListener(this);
        mListView.setAdapter(mListAdapter);
    }

    private void startAsyncQuery() {
        try {
            mEmptyView.setText(R.string.loading_conversations);

            Conversation.startQueryForAll(mQueryHandler, ThreadListQueryHandler
                    .THREAD_LIST_QUERY_TOKEN, mFilterSubId);
            Conversation.startQuery(mQueryHandler,
                    ThreadListQueryHandler.UNREAD_THREADS_QUERY_TOKEN, Threads.READ + "=0",
                    mFilterSubId);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }

    }

    private void stopAsyncQuery() {
        if (mQueryHandler != null) {
            // for (token : currentRunningTokens) { cancel(token); }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo
            menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        Cursor cursor = mListAdapter.getCursor();
        if (cursor == null || cursor.getPosition() < 0) {
            return;
        }
        Conversation conv = Conversation.from(this, cursor);
        ContactList recipients = conv.getRecipients();
        menu.setHeaderTitle(recipients.formatNames(","));

        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) menuInfo;
        menu.add(0, MENU_VIEW, 0, R.string.menu_view);

        // Only show if there's a single recipient
        if (recipients.size() == 1) {
            // do we have this recipient in contacts?
            if (recipients.get(0).existsInDatabase()) {
                menu.add(0, MENU_VIEW_CONTACT, 0, R.string.menu_view_contact);
            } else {
                menu.add(0, MENU_ADD_TO_CONTACTS, 0, R.string.menu_add_to_contacts_cm);
            }
        }
        if (mIsSmsEnabled) {
            menu.add(0, MENU_DELETE, 0, R.string.delete);
        }
    }

    @Override
    public void onDraftChanged(long threadId, boolean hasDraft) {

    }

    @Override
    public void onContentChanged(ConversationListAdapter adapter) {
        startAsyncQuery();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DEL: {
                    long id = mListView.getWrappedList().getSelectedItemId();
                    if (id > 0) {
                        ThreadUtil.confirmDeleteThread(id, mQueryHandler);
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
