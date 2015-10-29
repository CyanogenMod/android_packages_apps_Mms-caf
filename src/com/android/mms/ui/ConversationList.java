/*
 * Copyright (C) 2008 Esmertec AG.
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

package com.android.mms.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Threads;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.mms.blacklist.BlockCallerDialogFragment;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.data.Conversation.ConversationQueryHandler;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.SmsRejectedReceiver;
import com.android.mms.util.DraftCache;
import com.android.mms.util.Recycler;
import com.android.mms.widget.MmsWidgetProvider;
//import com.cyanogen.ambient.callerinfo.CallerInfoServices;
//import com.cyanogen.ambient.common.api.AmbientApiClient;
//import com.cyanogen.ambient.common.api.PendingResult;
//import com.cyanogen.ambient.common.api.Result;
//import com.cyanogen.ambient.common.api.ResultCallback;
import com.google.android.mms.pdu.PduHeaders;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * This activity provides a list view of existing conversations.
 */
public class ConversationList extends Activity implements DraftCache.OnDraftChangedListener,
        OnItemClickListener {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;
    private static final boolean DEBUGCLEANUP = true;

    private static final int THREAD_LIST_QUERY_TOKEN        = 1701;
    private static final int UNREAD_THREADS_QUERY_TOKEN     = 1702;
    public static final int DELETE_CONVERSATION_TOKEN       = 1801;
    public static final int HAVE_LOCKED_MESSAGES_TOKEN      = 1802;
    private static final int DELETE_OBSOLETE_THREADS_TOKEN  = 1803;
    private static final int MARK_CONVERSATION_UNREAD_TOKEN = 1804;
    private static final int MARK_CONVERSATION_READ_TOKEN = 1805;

    // IDs of the context menu items for the list of conversations.
    public static final int MENU_DELETE               = 0;
    public static final int MENU_VIEW                 = 1;
    public static final int MENU_VIEW_CONTACT         = 2;
    public static final int MENU_ADD_TO_CONTACTS      = 3;

    public static class BlockInfo {
        private Collection<Long> mThreadIds;
        public BlockInfo(Collection<Long> threadIds) {
            this.mThreadIds = threadIds;
        }
        public Collection<Long> getThreadIds() {
            return this.mThreadIds;
        }
    }

    public static class DeleteInfo {

        private final MessageDeleteTypes mDeleteType;
        private final int mDeleteCount;
        private Collection<Long> threadIds;

        public MessageDeleteTypes getDeleteType() {
            return mDeleteType;
        }

        public int getDeleteCount() {
            return mDeleteCount;
        }

        public Collection<Long> getThreadIds() {
            return threadIds;
        }

        public void setThreadIds(Collection<Long> threadIds) {
            this.threadIds = threadIds;
        }

        public DeleteInfo(MessageDeleteTypes deleteType) {
            this(deleteType, -1);
        }

        public DeleteInfo(MessageDeleteTypes deleteType, int deleteCount) {
            mDeleteType = deleteType;
            mDeleteCount = deleteCount;
        }
    }

    public static enum MessageDeleteTypes {
        ALL,
        SMS,
        MMS
    }

    public static boolean mIsRunning;

    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private ListView mListView, mSearchListView;
    private StickyListHeadersListView mListHeadersListView;
    private SharedPreferences mPrefs;
    private Handler mHandler = new Handler();
    private boolean mDoOnceAfterFirstQuery;
    private MenuItem mSearchItem;
    private View mSmsPromoBannerView;
    private int mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
    private int mSavedFirstItemOffset;
    private ProgressDialog mProgressDialog;
    private Spinner mFilterSpinner;
    private Integer mFilterSubId = null;
    private LinearLayout mEmptyView;
    private TextView mEmptyTextView;
    private Toolbar mToolbar;
    private TextView mToolBarTitleView;
    private static String sAppTitle;
    private static String sAppTitleWithUnread;
    private SearchAdapter mSearchAdapter;
    private Menu mMenu;
    private TextView mSearchHint;
    private View mFabContainer, mSearchRoot;
    private boolean mActionContextMenuEnb;

    // keys for extras and icicles
    private final static String LAST_LIST_POS = "last_list_pos";
    private final static String LAST_LIST_OFFSET = "last_list_offset";

    static private final String CHECKED_MESSAGE_LIMITS = "checked_message_limits";
    private final static int DELAY_TIME = 500;

    // Whether or not we are currently enabled for SMS. This field is updated in onResume to make
    // sure we notice if the user has changed the default SMS app.
    private Boolean mIsSmsEnabled;
    private Toast mComposeDisabledToast;
    private static long mLastDeletedThread = -1;

    private View.OnClickListener mComposeClickHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mIsSmsEnabled) {
                createNewMessage();
            } else {
                // Display a toast letting the user know they can not compose.
                if (mComposeDisabledToast == null) {
                    mComposeDisabledToast = Toast.makeText(ConversationList.this,
                            R.string.compose_disabled_toast, Toast.LENGTH_SHORT);
                }
                mComposeDisabledToast.show();
            }
        }
    };

    private StickyListHeadersListView getListView() {
        return mListHeadersListView;
    }

    private ModeCallback mModeCallbackHandler = new ModeCallback();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.conversation_list_screen);
        if (MessageUtils.isMailboxMode()) {
            Intent modeIntent = new Intent(this, MailBoxMessageList.class);
            startActivityIfNeeded(modeIntent, -1);
            finish();
            return;
        }

        sAppTitle = getResources().getString(R.string.app_label);

        mSmsPromoBannerView = findViewById(R.id.banner_sms_promo);

        mQueryHandler = new ThreadListQueryHandler(getContentResolver());

        mListHeadersListView = (StickyListHeadersListView) findViewById
                (R.id.mms_list);
        mListHeadersListView.setOnItemClickListener(this);
        mListView = mListHeadersListView.getWrappedList();
        mListView.setOnCreateContextMenuListener(mConvListOnCreateContextMenuListener);
        mListView.setOnKeyListener(mThreadListKeyListener);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(mModeCallbackHandler);

        // Tell the list view which view to display when the list is empty
        mEmptyView = (LinearLayout) findViewById(R.id.ll_empty);
        mListView.setEmptyView(mEmptyView);
        mEmptyTextView = (TextView) mEmptyView.findViewById(R.id.tv_empty);

        mSearchRoot = findViewById(R.id.message_search_root);
        mFabContainer = findViewById(R.id.floating_action_button_container);

        initListAdapter();

        setupActionBar();

        mProgressDialog = createProgressDialog();

        setTitle(sAppTitle);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean checkedMessageLimits = mPrefs.getBoolean(CHECKED_MESSAGE_LIMITS, false);
        if (DEBUG) Log.v(TAG, "checkedMessageLimits: " + checkedMessageLimits);
        if (!checkedMessageLimits) {
            runOneTimeStorageLimitCheckForLegacyMessages();
        }

        if (savedInstanceState != null) {
            mSavedFirstVisiblePosition = savedInstanceState.getInt(LAST_LIST_POS,
                    AdapterView.INVALID_POSITION);
            mSavedFirstItemOffset = savedInstanceState.getInt(LAST_LIST_OFFSET, 0);
        } else {
            mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
            mSavedFirstItemOffset = 0;
        }

        setupFilterSpinner();

        View actionButton = findViewById(R.id.floating_action_button);
        actionButton.setOnClickListener(mComposeClickHandler);

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(LAST_LIST_POS, mSavedFirstVisiblePosition);
        outState.putInt(LAST_LIST_OFFSET, mSavedFirstItemOffset);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Don't listen for changes while we're paused.
        mListAdapter.setOnContentChangedListener(null);

        // Remember where the list is scrolled to so we can restore the scroll position
        // when we come back to this activity and *after* we complete querying for the
        // conversations.
        mSavedFirstVisiblePosition = mListHeadersListView.getFirstVisiblePosition();
        View firstChild = mListHeadersListView.getChildAt(0);
        mSavedFirstItemOffset = (firstChild == null) ? 0 : firstChild.getTop();
        mIsRunning = false;
        unregisterBlacklistObserver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        if (mIsSmsEnabled == null || isSmsEnabled != mIsSmsEnabled) {
            mIsSmsEnabled = isSmsEnabled;
            invalidateOptionsMenu();
            mListHeadersListView.setChoiceMode(mIsSmsEnabled ? ListView.CHOICE_MODE_MULTIPLE_MODAL :
                    ListView.CHOICE_MODE_NONE);
        }

        // Show or hide the SMS promo banner
        if (mIsSmsEnabled) {
            mSmsPromoBannerView.setVisibility(View.GONE);
        } else {
            initSmsPromoBanner();
            mSmsPromoBannerView.setVisibility(View.VISIBLE);
        }

        registerBlacklistObserver();
        mListAdapter.setOnContentChangedListener(mContentChangedListener);
        mIsRunning = true;
    }

    private ContentObserver mBlacklistObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            mListAdapter.notifyDataSetChanged();
        }
    };

    private void registerBlacklistObserver() {
        getContentResolver().registerContentObserver(
                Telephony.Blacklist.CONTENT_MESSAGE_URI,
                true, mBlacklistObserver);
    }

    private void unregisterBlacklistObserver() {
        getContentResolver().unregisterContentObserver(mBlacklistObserver);
    }

    private void setupActionBar() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(mToolbar);
    }

    private void setupFilterSpinner() {
        TelephonyManager mgr =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mFilterSpinner = (Spinner) findViewById(R.id.filterSpinner);

        boolean filterEnabled =
                getResources().getBoolean(R.bool.enable_filter_threads_by_sim);

        if (filterEnabled && mgr.isMultiSimEnabled()) {
            MSimSpinnerAdapter adapter = new MSimSpinnerAdapter(mFilterSpinner.getContext());
            mFilterSpinner.setAdapter(adapter);
            mFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view,
                        int position, long id) {
                    if (position == 0) {
                        mFilterSubId = null;
                    } else {
                        mFilterSubId = (int) id;
                    }
                    startAsyncQuery();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    mFilterSubId = null;
                    startAsyncQuery();
                }
            });

            getLoaderManager().initLoader(0, null,
                    new MSimSpinnerAdapter.LoaderCallbacks(adapter));
        } else {
            mFilterSpinner.setVisibility(View.GONE);
        }
    }

    private final ConversationListAdapter.OnContentChangedListener mContentChangedListener =
        new ConversationListAdapter.OnContentChangedListener() {
        @Override
        public void onContentChanged(ConversationListAdapter adapter) {
            startAsyncQuery();
        }
    };

    private void initListAdapter() {
        mListAdapter = new ConversationListAdapter(this, null);
        mListAdapter.setOnContentChangedListener(mContentChangedListener);
        mListHeadersListView.setAdapter(mListAdapter);
        getListView().getWrappedList().setRecyclerListener(mListAdapter);

        MmsApp.getApplication().addPhoneNumberLookupListener(mListAdapter);
    }

    @SuppressWarnings("ConstantConditions")
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
        }
        final Intent smsAppIntent = packageManager.getLaunchIntentForPackage(smsAppPackage);

        // If we got all the info we needed
        if (smsAppIcon != null && smsAppInfo != null && smsAppIntent != null) {
            ImageView defaultSmsAppIconImageView =
                    (ImageView)mSmsPromoBannerView.findViewById(R.id.banner_sms_default_app_icon);
            defaultSmsAppIconImageView.setImageDrawable(smsAppIcon);
            TextView smsPromoBannerTitle =
                    (TextView)mSmsPromoBannerView.findViewById(R.id.banner_sms_promo_title);
            String message = getResources().getString(R.string.banner_sms_promo_title_application,
                    smsAppInfo.loadLabel(packageManager));
            smsPromoBannerTitle.setText(message);
        }

        View yes = mSmsPromoBannerView.findViewById(R.id.yes);
        yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                i.setPackage("com.android.settings");
                startActivity(i);
            }
        });
        View no = mSmsPromoBannerView.findViewById(R.id.no);
        no.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSmsPromoBannerView.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Checks to see if the number of MMS and SMS messages are under the limits for the
     * recycler. If not, it will prompt the user with a message and point them to the setting to
     * manually turn on the recycler.
     */
    public synchronized void runOneTimeStorageLimitCheckForLegacyMessages() {
        if (Recycler.isAutoDeleteEnabled(this)) {
            if (DEBUG) Log.v(TAG, "recycler is already turned on");
            // The recycler is already turned on. We don't need to check anything or warn
            // the user, just remember that we've made the check.
            markCheckedMessageLimit();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (Recycler.checkForThreadsOverLimit(ConversationList.this)) {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit TRUE");
                    // Dang, one or more of the threads are over the limit. Show an activity
                    // that'll encourage the user to manually turn on the setting. Delay showing
                    // this activity until a couple of seconds after the conversation list appears.
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(ConversationList.this,
                                    WarnOfStorageLimitsActivity.class);
                            startActivity(intent);
                        }
                    }, 2000);
                } else {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit FALSE");
                }
                // Remember that we don't have to do the check anymore when starting MMS.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        markCheckedMessageLimit();
                    }
                });
            }
        }, "ConversationList.runOneTimeStorageLimitCheckForLegacyMessages").start();
    }

    /**
     * Mark in preferences that we've checked the user's message limits. Once checked, we'll
     * never check them again, unless the user wipe-data or resets the device.
     */
    private void markCheckedMessageLimit() {
        if (DEBUG) Log.v(TAG, "markCheckedMessageLimit");
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(CHECKED_MESSAGE_LIMITS, true);
        editor.apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Handle intents that occur after the activity has already been created.
        startAsyncQuery();
    }

    @Override
    protected void onStart() {
        super.onStart();

        MessagingNotification.cancelNotification(getApplicationContext(),
                SmsRejectedReceiver.SMS_REJECTED_NOTIFICATION_ID);

        DraftCache.getInstance().addOnDraftChangedListener(this);

        mDoOnceAfterFirstQuery = true;

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
    protected void onStop() {
        super.onStop();
        stopAsyncQuery();
        DraftCache.getInstance().removeOnDraftChangedListener(this);
        unbindListeners(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close the cursor in the ListAdapter if the activity stopped.
        Cursor cursor = mListAdapter.getCursor();

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        mListAdapter.changeCursor(null);

        MmsApp.getApplication().removePhoneNumberLookupListener(mListAdapter);
        MessageUtils.removeDialogs();
    }

    private void unbindListeners(final Collection<Long> threadIds) {
        for (int i = 0; i < getListView().getChildCount(); i++) {
            View view = getListView().getChildAt(i);
            if (view instanceof ConversationListItem) {
                ConversationListItem item = (ConversationListItem)view;
                if (threadIds == null) {
                    item.unbind();
                } else if (threadIds.contains(item.getConversation().getThreadId())) {
                    item.unbind();
                }
            }
        }
    }

    @Override
    public void onDraftChanged(final long threadId, final boolean hasDraft) {
        // Run notifyDataSetChanged() on the main thread.
        mQueryHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    log("onDraftChanged: threadId=" + threadId + ", hasDraft=" + hasDraft);
                }
                mListAdapter.notifyDataSetChanged();
            }
        });
    }

    private void startAsyncQuery() {
        try {
            mEmptyTextView.setText(R.string.loading_conversations);

            Conversation.startQueryForAll(mQueryHandler, THREAD_LIST_QUERY_TOKEN, mFilterSubId);
            Conversation.startQuery(mQueryHandler,
                    UNREAD_THREADS_QUERY_TOKEN, Threads.READ + "=0", mFilterSubId);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void stopAsyncQuery() {
        if (mQueryHandler != null) {
            mQueryHandler.cancelOperation(THREAD_LIST_QUERY_TOKEN);
            mQueryHandler.cancelOperation(UNREAD_THREADS_QUERY_TOKEN);
        }
    }

    SearchView.OnQueryTextListener mQueryTextListener = new SearchView.OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if (TextUtils.isEmpty(newText)) {
                mSearchAdapter.changeCursor(null);
                mSearchHint.setText(R.string.mms_search_image_hint);
            } else if (newText.length() > 1) {
                mSearchHint.setText(R.string.mms_search_no_results);
                mSearchAdapter.setQuery(newText);
                getLoaderManager().restartLoader(1, null, mLoaderCallbacks);
            }
            return true;
        }
    };

    private LoaderManager.LoaderCallbacks mLoaderCallbacks = new LoaderManager.LoaderCallbacks() {
        @Override
        public Loader onCreateLoader(int id, Bundle args) {
            // don't pass a projection since the search uri ignores it
            Uri uri = Telephony.MmsSms.SEARCH_URI.buildUpon()
                    .appendQueryParameter("pattern", mSearchAdapter.getQuery()).build();
            return new CursorLoader(ConversationList.this, uri, null, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader loader, Object data) {
            mSearchAdapter.swapCursor((Cursor) data);
        }

        @Override
        public void onLoaderReset(Loader loader) {
            mSearchAdapter.swapCursor(null);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //In folder mode, it will jump to MailBoxMessageList,finish current
        //activity, no need create option menu.
        if (MessageUtils.isMailboxMode()) {
            return true;
        }
        if (!mActionContextMenuEnb) {
            getMenuInflater().inflate(R.menu.conversation_list_menu, menu);
        } else {
            getMenuInflater().inflate(R.menu.conversation_multi_select_menu, menu);
        }

        mMenu = menu;

        if (!getResources().getBoolean(R.bool.config_classify_search) &&
                !mActionContextMenuEnb) {
            mSearchItem = menu.findItem(R.id.search);
            mSearchItem.setOnActionExpandListener(new OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    toggleSearchUi(true);
                    return true;
                }
                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    toggleSearchUi(false);
                    return true;
                }
            });
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            searchView.setOnQueryTextListener(mQueryTextListener);
            searchView.setQueryHint(getString(R.string.mms_search_message_hint));
            searchView.setIconifiedByDefault(true);
        }

        MenuItem item = menu.findItem(R.id.action_change_to_conversation_mode);
        if (item != null) {
            item.setVisible(false);
        }

        MenuItem cellBroadcastItem = menu.findItem(R.id.action_cell_broadcasts);
        if (cellBroadcastItem != null) {
            // Enable link to Cell broadcast activity depending on the value in config.xml.
            boolean isCellBroadcastAppLinkEnabled = this.getResources().getBoolean(
                    com.android.internal.R.bool.config_cellBroadcastAppLinks);
            try {
                if (isCellBroadcastAppLinkEnabled) {
                    PackageManager pm = getPackageManager();
                    if (pm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver")
                            == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        isCellBroadcastAppLinkEnabled = false;  // CMAS app disabled
                    }
                }
            } catch (IllegalArgumentException ignored) {
                isCellBroadcastAppLinkEnabled = false;  // CMAS app not installed
            }
            if (!isCellBroadcastAppLinkEnabled) {
                cellBroadcastItem.setVisible(false);
            }
        }
        return true;
    }

    private void toggleSearchUi(boolean showSearch) {
        // Inflates the stub if necessary
        mSearchRoot.setVisibility(showSearch ? View.VISIBLE : View.GONE);
        if (mSearchListView == null) {
            // Initialize the view/adapters
            mSearchListView = (ListView) findViewById(R.id.mms_search_list);
            mSearchListView.setOnItemClickListener(this);
            mSearchHint = (TextView) findViewById(R.id.message_search_hint);
            mSearchAdapter = new SearchAdapter(this);
            mSearchListView.setAdapter(mSearchAdapter);
            mSearchListView.setEmptyView(findViewById(R.id.ll_search_empty));
        }

        // Hide search icon hint if landscape
        if (showSearch) {
            updateSearchHintIconVisibility();
        }

        // Hide fab, menu button and conversation list if showing search
        mFabContainer.setVisibility(showSearch ? View.GONE : View.VISIBLE);
        mListHeadersListView.setVisibility(showSearch ? View.GONE : View.VISIBLE);
        mMenu.setGroupVisible(R.id.non_search_items, !showSearch);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //In folder mode, it will jump to MailBoxMessageList,finish current
        //activity, no need prepare option menu.
        if (MessageUtils.isMailboxMode()) {
            return true;
        }
        MenuItem item = menu.findItem(R.id.action_delete_all);
        if (item != null) {
            item.setVisible((mListAdapter.getCount() > 0) && mIsSmsEnabled);
        }

        if (!getResources().getBoolean(R.bool.config_mailbox_enable)) {
            item = menu.findItem(R.id.action_change_to_folder_mode);
            if (item != null) {
                item.setVisible(false);
            }
            item = menu.findItem(R.id.action_memory_status);
            if (item != null) {
                item.setVisible(false);
            }
        }

        item = menu.findItem(R.id.action_mark_all_as_unread);
        if (item != null) {
            item.setVisible((mListAdapter.getCount() > 0) && mIsSmsEnabled);
        }

        item = menu.findItem(R.id.action_mark_all_as_read);
        if (item != null) {
            item.setVisible((mListAdapter.getCount() > 0) && mIsSmsEnabled);
        }

        if (!LogTag.DEBUG_DUMP) {
            item = menu.findItem(R.id.action_debug_dump);
            if (item != null) {
                item.setVisible(false);
            }
        }
        return true;
    }

    @Override
    public boolean onSearchRequested() {
        if (getResources().getBoolean(R.bool.config_classify_search)) {
            // block search entirely (by simply returning false).
            return false;
        }

        if (mSearchItem != null) {
            mSearchItem.expandActionView();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // pass the Menu event to the ActionContext handler
        if (mActionContextMenuEnb) {
            mModeCallbackHandler.onOptionsItemSelected(item);
            return true;
        }
        switch(item.getItemId()) {
            case R.id.search:
                if (getResources().getBoolean(R.bool.config_classify_search)) {
                    Intent searchintent = new Intent(this, SearchActivityExtend.class);
                    startActivityIfNeeded(searchintent, -1);
                    break;
                }
                return true;
            case R.id.action_delete_all:
                // The invalid threadId of -1 means all threads here.
                confirmDeleteThread(-1L, mQueryHandler);
                break;
            case R.id.action_mark_all_as_unread:
                final MarkAsUnreadThreadListener listener = new MarkAsUnreadThreadListener(
                        null, mQueryHandler, this);
                confirmMarkAsUnreadDialog(listener, null, this);
                break;
            case R.id.action_mark_all_as_read:
                final MarkAsReadThreadListener r_listener = new MarkAsReadThreadListener(
                        null, mQueryHandler, this);
                confirmMarkAsReadDialog(r_listener, null, this);
                break;
            case R.id.action_settings:
                Intent intent = new Intent(this, MessagingPreferenceActivity.class);
                startActivityIfNeeded(intent, -1);
                break;
            case R.id.action_change_to_folder_mode:
                Intent modeIntent = new Intent(this, MailBoxMessageList.class);
                startActivityIfNeeded(modeIntent, -1);
                MessageUtils.setMailboxMode(true);
                finish();
                break;
            case R.id.action_debug_dump:
                LogTag.dumpInternalTables(this);
                break;
            case R.id.action_memory_status:
                MessageUtils.showMemoryStatusDialog(this);
                break;
            case R.id.action_cell_broadcasts:
                Intent cellBroadcastIntent = new Intent(Intent.ACTION_MAIN);
                cellBroadcastIntent.setComponent(new ComponentName(
                        "com.android.cellbroadcastreceiver",
                        "com.android.cellbroadcastreceiver.CellBroadcastListActivity"));
                cellBroadcastIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(cellBroadcastIntent);
                } catch (ActivityNotFoundException ignored) {
                    Log.e(TAG, "ActivityNotFoundException for CellBroadcastListActivity");
                }
                return true;
            default:
                return true;
        }
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> a, View v, int position, long id) {
        // Note: don't read the thread id data from the ConversationListItem view passed in.
        // It's unreliable to read the cached data stored in the view because the ListItem
        // can be recycled, and the same view could be assigned to a different position
        // if you click the list item fast enough. Instead, get the cursor at the position
        // clicked and load the data from the cursor.
        // (ConversationListAdapter extends CursorAdapter, so getItemAtPosition() should
        // return the cursor object, which is moved to the position passed in)
        Cursor cursor;
        long tid;
        if (mSearchItem.isActionViewExpanded()) {
            cursor = (Cursor) mSearchListView.getItemAtPosition(position);
            tid = cursor.getInt(SearchAdapter.THREAD_ID_INDEX);
        } else {
            cursor = (Cursor) getListView().getItemAtPosition(position);
            Conversation conv = Conversation.from(this, cursor);
            tid = conv.getThreadId();
        }

        if (LogTag.VERBOSE) {
            Log.d(TAG, "onListItemClick: pos=" + position + ", view=" + v + ", tid=" + tid);
        }

        long selectId = -1;
        String highlight = null;
        if (mSearchItem.isActionViewExpanded()) {
            selectId = cursor.getLong(cursor.getColumnIndex("_id"));
            highlight = mSearchAdapter.getQuery();
        }

        openThread(tid, selectId, highlight);
    }

    private void createNewMessage() {
        startActivity(ComposeMessageActivity.createIntent(this, 0));
    }

    private void openThread(long threadId) {
        openThread(threadId, -1, null);
    }

    private void openThread(long threadId, long selectId, String highlight) {
        Intent i = ComposeMessageActivity.createIntent(this, threadId);
        if (highlight != null) {
          i.putExtra("highlight", mSearchAdapter.getQuery());
        }
        if (selectId != -1) {
            i.putExtra("select_id", selectId);
        }
        startActivity(i);
    }

    public static Intent createAddContactIntent(String address) {
        // address must be a single recipient
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        if (Mms.isEmailAddress(address)) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, address);
        } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, address);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        return intent;
    }

    private final OnCreateContextMenuListener mConvListOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            Cursor cursor = mListAdapter.getCursor();
            if (cursor == null || cursor.getPosition() < 0) {
                return;
            }
            Conversation conv = Conversation.from(ConversationList.this, cursor);
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
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Cursor cursor = mListAdapter.getCursor();
        if (cursor != null && cursor.getPosition() >= 0) {
            Conversation conv = Conversation.from(ConversationList.this, cursor);
            long threadId = conv.getThreadId();
            switch (item.getItemId()) {
            case MENU_DELETE: {
                confirmDeleteThread(threadId, mQueryHandler);
                break;
            }
            case MENU_VIEW: {
                openThread(threadId);
                break;
            }
            case MENU_VIEW_CONTACT: {
                Contact contact = conv.getRecipients().get(0);
                Intent intent = new Intent(Intent.ACTION_VIEW, contact.getUri());
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                startActivity(intent);
                break;
            }
            case MENU_ADD_TO_CONTACTS: {
                String address = conv.getRecipients().get(0).getNumber();
                startActivity(createAddContactIntent(address));
                break;
            }
            default:
                break;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // We override this method to avoid restarting the entire
        // activity when the keyboard is opened (declared in
        // AndroidManifest.xml).  Because the only translatable text
        // in this activity is "New Message", which has the full width
        // of phone to work with, localization shouldn't be a problem:
        // no abbreviated alternate words should be needed even in
        // 'wide' languages like German or Russian.

        super.onConfigurationChanged(newConfig);
        if (DEBUG) Log.v(TAG, "onConfigurationChanged: " + newConfig);
        updateSearchHintIconVisibility();
    }

    private void updateSearchHintIconVisibility() {
        View icon = findViewById(R.id.message_search_hint_icon);
        if (icon != null) {
            boolean isLandscape = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            icon.setVisibility(isLandscape ? View.GONE : View.VISIBLE);
        }
    }

    public void confirmBlockUsersFromThreads(final String[] numbers) {
        BlockCallerDialogFragment f = new BlockCallerDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putStringArray(BlockCallerDialogFragment.NUMBERS_EXTRA, numbers);
        bundle.putInt(BlockCallerDialogFragment.ORIGIN_EXTRA, BlockCallerDialogFragment.ORIGIN_MMS);
        f.setArguments(bundle);
        f.show(this.getFragmentManager(), "block_caller");
    }

    /**
     * Start the process of putting up a dialog to confirm deleting a thread,
     * but first start a background query to see if any of the threads or thread
     * contain locked messages so we'll know how detailed of a UI to display.
     * @param threadId id of the thread to delete or -1 for all threads
     * @param handler query handler to do the background locked query
     */
    public static void confirmDeleteThread(long threadId, AsyncQueryHandler handler) {
        ArrayList<Long> threadIds = null;
        if (threadId != -1) {
            threadIds = new ArrayList<Long>();
            threadIds.add(threadId);
        }
        confirmDeleteThreads(threadIds, handler);
    }

    /**
     * Start the process of putting up a dialog to confirm deleting threads,
     * but first start a background query to see if any of the threads
     * contain locked messages so we'll know how detailed of a UI to display.
     * @param threadIds list of threadIds to delete or null for all threads
     * @param handler query handler to do the background locked query
     */
    public static void confirmDeleteThreads(Collection<Long> threadIds, AsyncQueryHandler handler) {
        DeleteInfo info = new DeleteInfo(MessageDeleteTypes.ALL);
        info.setThreadIds(threadIds);
        Conversation.startQueryHaveLockedMessages(handler, HAVE_LOCKED_MESSAGES_TOKEN, info);
    }

    /**
     * Build and show the proper delete thread dialog. The UI is slightly different
     * depending on whether there are locked messages in the thread(s) and whether we're
     * deleting single/multiple threads or all threads.
     * @param listener gets called when the delete button is pressed
     * @param threadIds the thread IDs to be deleted (pass null for all threads)
     * @param hasLockedMessages whether the thread(s) contain locked messages
     * @param context used to load the various UI elements
     */
    public static void confirmDeleteThreadDialog(final DeleteThreadListener listener,
            Collection<Long> threadIds,
            boolean hasLockedMessages,
            Context context) {
        View contents = View.inflate(context, R.layout.delete_thread_dialog_view, null);
        TextView msg = (TextView)contents.findViewById(R.id.message);

        if (threadIds == null) {
            msg.setText(R.string.confirm_delete_all_conversations);
        } else {
            // Show the number of threads getting deleted in the confirmation dialog.
            int cnt = 0;
            int resId = 0;
            if (listener.mInfo.getDeleteType() == MessageDeleteTypes.ALL) {
                resId = R.plurals.confirm_delete_conversation;
                cnt = threadIds.size();
            } else {
                resId = R.plurals.confirm_delete_messages;
                cnt = listener.mInfo.getDeleteCount();
            }
            msg.setText(context.getResources().getQuantityString(
                resId, cnt, cnt));
        }

        final CheckBox checkbox = (CheckBox)contents.findViewById(R.id.delete_locked);
        if (!hasLockedMessages) {
            checkbox.setVisibility(View.GONE);
        } else {
            listener.setDeleteLockedMessage(checkbox.isChecked());
            checkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.setDeleteLockedMessage(checkbox.isChecked());
                }
            });
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_dialog_title)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setCancelable(true)
            .setPositiveButton(R.string.delete, listener)
            .setNegativeButton(R.string.no, null)
            .setView(contents)
            .show();
    }


 /**
     * Build and show the proper mark as unread thread dialog. The UI is slightly different
     * depending on whether we're deleting single/multiple threads or all threads.
     * @param listener gets called when the delete button is pressed
     * @param threadIds the thread IDs to be deleted (pass null for all threads)
     * @param context used to load the various UI elements
     */
    private static void confirmMarkAsUnreadDialog(final MarkAsUnreadThreadListener listener,
            Collection<Long> threadIds,
            Context context) {
        View contents = View.inflate(context,R.layout.mark_unread_thread_dialog_view,null);
        TextView msg = (TextView)contents.findViewById(R.id.message);
        if (threadIds == null) {
            msg.setText(R.string.confirm_mark_unread_all_conversations);
        } else {
            // Show the number of threads getting marked as unread in the confirmation dialog.
            int cnt = threadIds.size();
            msg.setText(context.getResources().getQuantityString(
                R.plurals.confirm_mark_unread_conversation,cnt,cnt));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_mark_unread_dialog_title)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setCancelable(true)
            .setPositiveButton(R.string.menu_as_unread,listener)
            .setNegativeButton(R.string.no,null)
            .setView(contents)
            .show();
    }

    /**
     * Build and show the proper mark as read thread dialog. The UI is slightly different
     * depending on whether we're reading single/multiple threads or all threads.
     * @param listener gets called when the markAsRead button is pressed
     * @param threadIds the thread IDs to be read (pass null for all threads)
     * @param context used to load the various UI elements
     */
    private static void confirmMarkAsReadDialog(final MarkAsReadThreadListener listener,
            Collection<Long> threadIds,
            Context context) {
        View contents = View.inflate(context,R.layout.mark_read_thread_dialog_view,null);
        TextView msg = (TextView)contents.findViewById(R.id.message);
        if (threadIds == null) {
            msg.setText(R.string.confirm_mark_read_all_conversations);
        } else {
            // Show the number of threads getting marked as unread in the confirmation dialog.
            int cnt = threadIds.size();
            msg.setText(context.getResources().getQuantityString(
                    R.plurals.confirm_mark_read_conversation,cnt,cnt));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_mark_read_dialog_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(true)
                .setPositiveButton(R.string.menu_as_read,listener)
                .setNegativeButton(R.string.no,null)
                .setView(contents)
                .show();
    }

    private final OnKeyListener mThreadListKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DEL: {
                        long id = getListView().getWrappedList().getSelectedItemId();
                        if (id > 0) {
                            confirmDeleteThread(id, mQueryHandler);
                        }
                        return true;
                    }
                }
            }
            return false;
        }
    };


    public static class MarkAsUnreadThreadListener implements OnClickListener {
        private final Collection<Long> mThreadIds;
        private final ConversationQueryHandler mHandler;
        private final Context mContext;

        public MarkAsUnreadThreadListener(Collection<Long> threadIds,
                                          ConversationQueryHandler handler, Context context) {
            mThreadIds = threadIds;
            mHandler = handler;
            mContext = context;
        }

        @Override
        public void onClick(DialogInterface dialog, final int whichButton) {
            MessageUtils.handleReadReport(mContext, mThreadIds,
                    PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, new Runnable() {
                @Override
                public void run() {
                    int token = MARK_CONVERSATION_UNREAD_TOKEN;
                    if (mThreadIds == null) {
                        Conversation.startMarkAsUnreadAll(mContext,mHandler, token);
                        DraftCache.getInstance().refresh();
                    } else {
                        Conversation.startMarkAsUnread(mContext,mHandler, token, mThreadIds);
                    }
                }
            });
            dialog.dismiss();
        }
    }

    public static class MarkAsReadThreadListener implements OnClickListener {
        private final Collection<Long> mThreadIds;
        private final ConversationQueryHandler mHandler;
        private final Context mContext;

        public MarkAsReadThreadListener(Collection<Long> threadIds,
                                          ConversationQueryHandler handler, Context context) {
            mThreadIds = threadIds;
            mHandler = handler;
            mContext = context;
        }

        @Override
        public void onClick(DialogInterface dialog, final int whichButton) {
            MessageUtils.handleReadReport(mContext, mThreadIds,
                    PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, new Runnable() {
                        @Override
                        public void run() {
                            int token = MARK_CONVERSATION_READ_TOKEN;
                            if (mThreadIds == null) {
                                Conversation.startMarkAsReadAll(mContext, mHandler, token);
                                DraftCache.getInstance().refresh();
                            } else {
                                Conversation.startMarkAsRead(mContext, mHandler, token, mThreadIds);
                            }
                        }
                    });
            dialog.dismiss();
        }
    }


    public static class DeleteThreadListener implements OnClickListener {
        private final ConversationQueryHandler mHandler;
        private final Context mContext;
        private final DeleteInfo mInfo;
        private boolean mDeleteLockedMessages;
        private final Runnable mCallBack;

        public DeleteThreadListener(DeleteInfo info, ConversationQueryHandler handler,
                Runnable callBack, Context context) {
            mInfo = info;
            mHandler = handler;
            mContext = context;
            mCallBack = callBack;
        }

        public void setDeleteLockedMessage(boolean deleteLockedMessages) {
            mDeleteLockedMessages = deleteLockedMessages;
        }

        @Override
        public void onClick(DialogInterface dialog, final int whichButton) {
            MessageUtils.handleReadReport(mContext, mInfo.getThreadIds(),
                    PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, new Runnable() {
                @Override
                public void run() {
                    int token = DELETE_CONVERSATION_TOKEN;
                    if (mInfo.getDeleteType() != MessageDeleteTypes.ALL) {
                        token = ComposeMessageActivity.DELETE_MESSAGE_TOKEN;
                    }

                    if (mCallBack != null) {
                        mCallBack.run();
                    }
                    if (mContext instanceof ConversationList) {
                        ((ConversationList)mContext).unbindListeners(mInfo.getThreadIds());
                    }
                    if (mInfo.getThreadIds() == null) {
                        Conversation.startDeleteAll(mHandler, token, mDeleteLockedMessages);
                        DraftCache.getInstance().refresh();
                    } else {
                        int size = mInfo.getThreadIds().size();
                        if (size > 0 && mCallBack != null) {
                            // Save the last thread id.
                            // And cancel deleting dialog after this thread been deleted.
                            mLastDeletedThread = (mInfo.getThreadIds().toArray(new Long[size]))[size - 1];
                        }
                        Conversation.startDelete(mHandler, token, mDeleteLockedMessages,
                                mInfo.getThreadIds(), mInfo.getDeleteType());
                    }
                }
            });
            dialog.dismiss();
        }

    }

    private final Runnable mDeleteObsoleteThreadsRunnable = new Runnable() {
        @Override
        public void run() {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                LogTag.debug("mDeleteObsoleteThreadsRunnable getSavingDraft(): " +
                        DraftCache.getInstance().getSavingDraft());
            }
            if (DraftCache.getInstance().getSavingDraft()) {
                // We're still saving a draft. Try again in a second. We don't want to delete
                // any threads out from under the draft.
                if (DEBUGCLEANUP) {
                    LogTag.debug("mDeleteObsoleteThreadsRunnable saving draft, trying again");
                }
                mHandler.postDelayed(mDeleteObsoleteThreadsRunnable, 1000);
            } else {
                if (DEBUGCLEANUP) {
                    LogTag.debug("mDeleteObsoleteThreadsRunnable calling " +
                            "asyncDeleteObsoleteThreads");
                }
                Conversation.asyncDeleteObsoleteThreads(mQueryHandler,
                        DELETE_OBSOLETE_THREADS_TOKEN);
            }
        }
    };

    private final class ThreadListQueryHandler extends ConversationQueryHandler {
        public ThreadListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        // Test code used for various scenarios where its desirable to insert a delay in
        // responding to query complete. To use, uncomment out the block below and then
        // comment out the @Override and onQueryComplete line.
//        @Override
//        protected void onQueryComplete(final int token, final Object cookie, final Cursor cursor) {
//            mHandler.postDelayed(new Runnable() {
//                public void run() {
//                    myonQueryComplete(token, cookie, cursor);
//                    }
//            }, 2000);
//        }
//
//        protected void myonQueryComplete(int token, Object cookie, Cursor cursor) {

        @Override
        protected void onQueryComplete(int token, Object cookie, final Cursor cursor) {
            switch (token) {
            case THREAD_LIST_QUERY_TOKEN:
                mListAdapter.changeCursor(cursor);

                if (mListAdapter.getCount() == 0) {
                    mEmptyTextView.setText(R.string.mms_next_empty_no_messages);
                }

                if (mDoOnceAfterFirstQuery) {
                    mDoOnceAfterFirstQuery = false;
                    // Delay doing a couple of DB operations until we've initially queried the DB
                    // for the list of conversations to display. We don't want to slow down showing
                    // the initial UI.

                    // 1. Delete any obsolete threads. Obsolete threads are threads that aren't
                    // referenced by at least one message in the pdu or sms tables.
                    mHandler.post(mDeleteObsoleteThreadsRunnable);

                    // 2. Mark all the conversations as seen.
                    Conversation.markAllConversationsAsSeen(getApplicationContext());
                }
                if (mSavedFirstVisiblePosition != AdapterView.INVALID_POSITION) {
                    // Restore the list to its previous position.
                    getListView().setSelectionFromTop(mSavedFirstVisiblePosition,
                            mSavedFirstItemOffset);
                    mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
                }
                break;

            case UNREAD_THREADS_QUERY_TOKEN:
                // iterate through all conversations, summing up unread_message_count
                // if "unread_message_count" column does not exist - due to database not
                // properly updated (shouldn't happen?) then simply return the number
                // of conversations with unread messages (pre MMS-104 implementation)

                // iterate in the background to avoid UI Thread blocking when there are
                // many conversations
                new AsyncTask<Void, Void, Integer>() {

                    @Override
                    protected Integer doInBackground(Void... params) {
                        int unreadMessageCount = 0;
                        if (cursor != null) {
                            try {
                                int columnIndex = cursor.getColumnIndexOrThrow(
                                  Threads.UNREAD_MESSAGE_COUNT);

                                while (cursor.moveToNext()) {

                                    unreadMessageCount += cursor.getInt(columnIndex);

                                }
                            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                                unreadMessageCount = cursor.getCount();
                            } finally {
                                cursor.close();
                            }
                        }
                        return unreadMessageCount;
                    }

                    @Override
                    protected void onPostExecute(Integer unreadMessageCount) {
                        String titleString = sAppTitle;
                        if (unreadMessageCount > 0) {
                            sAppTitleWithUnread = getResources().getQuantityString(R.plurals
                                    .app_label_with_unread, unreadMessageCount);
                            titleString = String.format(sAppTitleWithUnread, unreadMessageCount);
                        }
                        setTitle(titleString);
                    }
                }.execute();

                break;

            case HAVE_LOCKED_MESSAGES_TOKEN:
                if (ConversationList.this.isFinishing()) {
                    Log.w(TAG, "ConversationList is finished, do nothing ");
                    if (cursor != null) {
                        cursor.close();
                    }
                    return ;
                }
                @SuppressWarnings("unchecked")
                DeleteInfo info = (DeleteInfo) cookie;
                confirmDeleteThreadDialog(new DeleteThreadListener(info, mQueryHandler,
                        mDeletingRunnable, ConversationList.this), info.getThreadIds(),
                        cursor != null && cursor.getCount() > 0,
                        ConversationList.this);
                if (cursor != null) {
                    cursor.close();
                }
                break;

            default:
                Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onDeleteComplete(token, cookie, result);
            switch (token) {
            case DELETE_CONVERSATION_TOKEN:
                long threadId = cookie != null ? (Long)cookie : -1;     // default to all threads
                if (threadId < 0 || threadId == mLastDeletedThread) {
                    mHandler.removeCallbacks(mShowProgressDialogRunnable);
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                    }
                    mLastDeletedThread = -1;
                }
                if (threadId == -1) {
                    // Rebuild the contacts cache now that all threads and their associated unique
                    // recipients have been deleted.
                    Contact.init(ConversationList.this);
                } else {
                    // Remove any recipients referenced by this single thread from the
                    // contacts cache. It's possible for two or more threads to reference
                    // the same contact. That's ok if we remove it. We'll recreate that contact
                    // when we init all Conversations below.
                    Conversation conv = Conversation.get(ConversationList.this, threadId, false);
                    if (conv != null) {
                        ContactList recipients = conv.getRecipients();
                        for (Contact contact : recipients) {
                            contact.removeFromCache();
                        }
                    }
                }
                // Make sure the conversation cache reflects the threads in the DB.
                Conversation.init(ConversationList.this);

                // Update the notification for new messages since they
                // may be deleted.
                MessagingNotification.nonBlockingUpdateNewMessageIndicator(ConversationList.this,
                        MessagingNotification.THREAD_NONE, false);
                // Update the notification for failed messages since they
                // may be deleted.
                MessagingNotification.nonBlockingUpdateSendFailedNotification(ConversationList.this);

                // Make sure the list reflects the delete
                startAsyncQuery();

                MmsWidgetProvider.notifyDatasetChanged(getApplicationContext());
                break;

            case DELETE_OBSOLETE_THREADS_TOKEN:
                if (DEBUGCLEANUP) {
                    LogTag.debug("onQueryComplete finished DELETE_OBSOLETE_THREADS_TOKEN");
                }
                break;
            }
        }
    }

    private ProgressDialog createProgressDialog() {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.setMessage(getText(R.string.deleting_threads));
        return dialog;
    }

    private Runnable mDeletingRunnable = new Runnable() {
        @Override
        public void run() {
            mHandler.postDelayed(mShowProgressDialogRunnable, DELAY_TIME);
        }
    };

    private Runnable mShowProgressDialogRunnable = new Runnable() {
        @Override
        public void run() {
            if (mProgressDialog != null) {
                mProgressDialog.show();
            }
        }
    };

    public void checkAll() {
        int count = getListView().getCount();

        for (int i = 0; i < count; i++) {
            getListView().setItemChecked(i, true);
        }
        mListAdapter.notifyDataSetChanged();
    }

    public void unCheckAll() {
        int count = getListView().getCount();

        for (int i = 0; i < count; i++) {
            getListView().setItemChecked(i, false);
        }
        mListAdapter.notifyDataSetChanged();
    }

    private int mStatusBarColor = 0;
    void setStatusBarColor(int statusBarColor) {
        final ObjectAnimator statusBarAnimation = ObjectAnimator.ofInt(getWindow(),
                "statusBarColor", mStatusBarColor, statusBarColor);
        statusBarAnimation.setEvaluator(new ArgbEvaluator());
        statusBarAnimation.start();
    }

    private class ModeCallback implements ListView.MultiChoiceModeListener {
        private HashSet<Long> mSelectedThreadIds;
        private HashSet<String> mSelectedThreadNumbers;
        private ActionMode mMode;

        // this function is called from within onOptionsItemSelected() when standard menu
        // button is pressed and we are in the ContextActionMode.
        // This way all ContextActionMenu handling is done in one place,
        // in the onActionItemClicked() instead of having to repeat it in the
        // onOptionsItemSelected()
        public void onOptionsItemSelected(MenuItem item) {
            onActionItemClicked(mMode, item);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            ConversationListAdapter adapter = (ConversationListAdapter)getListView().getAdapter();
            adapter.setIsInActionMode(true);
            MenuInflater inflater = getMenuInflater();
            mSelectedThreadIds = new HashSet<Long>();
            mSelectedThreadNumbers = new HashSet<String>();
            mMode = mode;
            inflater.inflate(R.menu.conversation_multi_select_menu, menu);
            mActionContextMenuEnb = true;
            // reload Menu so that it matches the ContextActionMenu
            invalidateOptionsMenu();
            if(mFilterSpinner.getVisibility() == View.VISIBLE){
                mFilterSpinner.setEnabled(false);
            }

            int actionBarColor = getResources().getColor(R.color.mms_next_theme_bulkedit_actionbar);
            int statusBarColor = getResources().getColor(R.color.mms_next_theme_bulkedit_statusbar);

            // Start with theme color
            mStatusBarColor = actionBarColor;
            setStatusBarColor(statusBarColor);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.block:
                    if (mSelectedThreadIds.size() > 0) {
                        String[] numbers = new String[mSelectedThreadNumbers.size()];
                        mSelectedThreadNumbers.toArray(numbers);
                        confirmBlockUsersFromThreads(numbers);
                    }
                    mode.finish();
                    break;
                case R.id.delete:
                    if (mSelectedThreadIds.size() > 0) {
                        confirmDeleteThreads(mSelectedThreadIds, mQueryHandler);
                    }
                    mode.finish();
                    break;
                case R.id.selection_toggle:
                    if (allItemsSelected()) {
                        unCheckAll();
                    } else {
                        checkAll();
                        mode.invalidate();
                    }
                    break;
                case R.id.markAsUnread:
                    if (mSelectedThreadIds.size() > 0) {
                        confirmMarkAsUnreadDialog(
                                new MarkAsUnreadThreadListener(
                                        mSelectedThreadIds, mQueryHandler, ConversationList.this),
                                        mSelectedThreadIds, ConversationList.this);
                    }
                    mode.finish();
                    break;
                case R.id.markAsRead:
                    if (mSelectedThreadIds.size() > 0) {
                        confirmMarkAsReadDialog(
                                new MarkAsReadThreadListener(
                                        mSelectedThreadIds, mQueryHandler, ConversationList.this),
                                mSelectedThreadIds, ConversationList.this);
                    }
                    mode.finish();
                    break;
                default:
                    break;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            ConversationListAdapter adapter = (ConversationListAdapter)getListView().getAdapter();
            adapter.uncheckAll();
            adapter.setIsInActionMode(false);
            mSelectedThreadIds = null;
            mSelectedThreadNumbers = null;
            setStatusBarColor(getResources().getColor(R.color.mms_next_theme_color_dark));
            if(mFilterSpinner.getVisibility() == View.VISIBLE){
                mFilterSpinner.setEnabled(true);
            }

            mActionContextMenuEnb = false;
            // leaving the ContextActionMode, reload non-ContextActionMenu
            invalidateOptionsMenu();
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                int position, long id, boolean checked) {
            final int checkedCount = mListHeadersListView.getCheckedItemCount();

            mode.setTitle(getString(R.string.selected_count, checkedCount));
            mode.getMenu().findItem(R.id.selection_toggle).setTitle(getString(
                    allItemsSelected() ? R.string.deselected_all : R.string.selected_all));

            Cursor cursor  = (Cursor)mListHeadersListView.getItemAtPosition(position);
            Conversation conv = Conversation.from(ConversationList.this, cursor);
            conv.setIsChecked(checked);
            long threadId = conv.getThreadId();

            if (checked) {
                mSelectedThreadIds.add(threadId);
                for (Contact contact : conv.getRecipients()) {
                    mSelectedThreadNumbers.add(contact.getNumber());
                }
            } else {
                mSelectedThreadIds.remove(threadId);
                for (Contact contact : conv.getRecipients()) {
                    mSelectedThreadNumbers.remove(contact.getNumber());
                }
            }
        }

        private boolean allItemsSelected() {
            return mListHeadersListView.getCount() == mListHeadersListView.getCheckedItemCount();
        }
    }

    private void log(String format, Object... args) {
        String s = String.format(format, args);
        Log.d(TAG, "[" + Thread.currentThread().getId() + "] " + s);
    }

}
