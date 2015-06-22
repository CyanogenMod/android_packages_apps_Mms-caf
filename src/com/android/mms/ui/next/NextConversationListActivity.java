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
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.ActionMenuView;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.data.Conversation.ConversationQueryHandler;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.SmsRejectedReceiver;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.ConversationList;
import com.android.mms.ui.ConversationListAdapter;
import com.android.mms.ui.ConversationListAdapter.OnContentChangedListener;
import com.android.mms.ui.MSimSpinnerAdapter;
import com.android.mms.ui.MailBoxMessageList;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.android.mms.ui.SearchActivity;
import com.android.mms.ui.SearchActivityExtend;
import com.android.mms.util.DraftCache;
import com.google.android.mms.pdu.PduHeaders;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import java.util.Collection;
import java.util.HashSet;

/**
 * NextConversationListActivity
 * <pre>
 *     This is the main activity for viewing a list of conversations
 * </pre>
 *
 * @see {@link Activity}
 * @see {@link com.android.mms.util.DraftCache.OnDraftChangedListener}
 * @see {@link OnContentChangedListener}
 * @see {@link OnKeyListener}
 * @see {@link OnClickListener}
 * @see {@link OnItemClickListener}
 * @see {@link OnQueryTextListener}
 */
public class NextConversationListActivity extends Activity implements
        DraftCache.OnDraftChangedListener, OnContentChangedListener, OnKeyListener,
        OnClickListener, OnItemClickListener, OnQueryTextListener {

    // Constants
    private static final String TAG = NextConversationListActivity.class.getSimpleName();

    /* package */
    static void logd(String format, Object... args) {
        if (Log.isLoggable(LogTag.TAG, Log.DEBUG)) {
            String s = String.format(format, args);
            Log.d(LogTag.TAG, "[" + Thread.currentThread().getId() + "] " + s);
        }
    }

    /* package */
    static void loge(String format, Object... args) {
        String s = String.format(format, args);
        Log.e(LogTag.TAG, "[" + Thread.currentThread().getId() + "] " + s);
    }

    // IDs of the context menu items for the list of conversations.
    public static final int MENU_DELETE = 0;
    public static final int MENU_VIEW = 1;
    public static final int MENU_VIEW_CONTACT = 2;
    public static final int MENU_ADD_TO_CONTACTS = 3;

    // Members
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static long mLastDeletedThread = -1;
    private ThreadListQueryHandler mQueryHandler;
    private NextConversationListAdapter mListAdapter;
    private ProgressDialog mProgressDialog;
    private Integer mFilterSubId = null;
    private Toast mComposeDisabledToast;
    private MenuItem mSearchItem;
    private int mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
    private int mSavedFirstItemOffset;

    // Toolbar Views
    private Spinner mFilterSpinner;
    private TextView mUnreadConvCount;
    private SearchView mSearchView;

    // Views
    private StickyListHeadersListView mListView;
    private LinearLayout mEmptyView;
    private TextView mEmptyTextView;
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
        setupFilterSpinner();

        View actionButton = findViewById(R.id.floating_action_button);
        actionButton.setOnClickListener(this);

        mQueryHandler = new ThreadListQueryHandler(this);

    }

    @Override
    public void onStart() {
        super.onStart();

        MessagingNotification.cancelNotification(getApplicationContext(),
                SmsRejectedReceiver.SMS_REJECTED_NOTIFICATION_ID);

        DraftCache.getInstance().addOnDraftChangedListener(this);

//        startAsyncQuery();

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
    public void onNewIntent(Intent intent) {
        // Handle intents that occur after the activity has already been created.
        startAsyncQuery();
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
        mListView.setOnKeyListener(this);
        mListView.setOnItemClickListener(this);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setMultiChoiceModeListener(new ModeCallback());
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mEmptyView = (LinearLayout) findViewById(R.id.ll_empty);
        mEmptyTextView = (TextView) findViewById(R.id.tv_empty);
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

    /* package */ void startAsyncQuery() {
        try {
            mEmptyTextView.setText(R.string.loading_conversations);

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
            // [TODO][MSB]: for (token : currentRunningTokens) { cancel(token); }
        }
    }

    private void createNewMessage() {
        startActivity(ComposeMessageActivity.createIntent(this, 0));
    }

    private void openThread(long threadId) {
        startActivity(ComposeMessageActivity.createIntent(this, threadId));
    }

    /**
     * Build and show the proper mark as unread thread dialog. The UI is slightly different
     * depending on whether we're deleting single/multiple threads or all threads.
     *
     * @param listener gets called when the delete button is pressed
     * @param threadIds the thread IDs to be deleted (pass null for all threads)
     * @param context used to load the various UI elements
     */
    private static void confirmMarkAsUnreadDialog(final MarkAsUnreadThreadListener listener,
            Collection<Long> threadIds,
            Context context) {
        View contents = View.inflate(context, R.layout.mark_unread_thread_dialog_view, null);
        TextView msg = (TextView) contents.findViewById(R.id.message);
        if (threadIds == null) {
            msg.setText(R.string.confirm_mark_unread_all_conversations);
        } else {
            // Show the number of threads getting marked as unread in the confirmation dialog.
            int cnt = threadIds.size();
            msg.setText(context.getResources().getQuantityString(
                    R.plurals.confirm_mark_unread_conversation, cnt, cnt));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_mark_unread_dialog_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(true)
                .setPositiveButton(R.string.menu_as_unread, listener)
                .setNegativeButton(R.string.no, null)
                .setView(contents)
                .show();
    }

    /**
     * Build and show the proper mark as read thread dialog. The UI is slightly different depending
     * on whether we're reading single/multiple threads or all threads.
     *
     * @param listener gets called when the markAsRead button is pressed
     * @param threadIds the thread IDs to be read (pass null for all threads)
     * @param context used to load the various UI elements
     */
    private static void confirmMarkAsReadDialog(final MarkAsReadThreadListener listener,
            Collection<Long> threadIds,
            Context context) {
        View contents = View.inflate(context, R.layout.mark_read_thread_dialog_view, null);
        TextView msg = (TextView) contents.findViewById(R.id.message);
        if (threadIds == null) {
            msg.setText(R.string.confirm_mark_read_all_conversations);
        } else {
            // Show the number of threads getting marked as unread in the confirmation dialog.
            int cnt = threadIds.size();
            msg.setText(context.getResources().getQuantityString(
                    R.plurals.confirm_mark_read_conversation, cnt, cnt));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_mark_read_dialog_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(true)
                .setPositiveButton(R.string.menu_as_read, listener)
                .setNegativeButton(R.string.no, null)
                .setView(contents)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //In folder mode, it will jump to MailBoxMessageList,finish current
        //activity, no need create option menu.
        if (MessageUtils.isMailboxMode()) {
            return true;
        }

        // Short circuit the menu
//        menu = mActionMenuView.getMenu();

        getMenuInflater().inflate(R.menu.conversation_list_menu, menu);

        if (!getResources().getBoolean(R.bool.config_classify_search)) {
            mSearchItem = menu.findItem(R.id.search);
            mSearchItem.setActionView(new SearchView(this));
            mSearchView = (SearchView) mSearchItem.getActionView();
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setQueryHint(getString(R.string.search_hint));
            mSearchView.setIconifiedByDefault(true);
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

            if (searchManager != null) {
                SearchableInfo info = searchManager.getSearchableInfo(this.getComponentName());
                mSearchView.setSearchableInfo(info);
            }
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

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //In folder mode, it will jump to MailBoxMessageList,finish current
        //activity, no need prepare option menu.
        if (MessageUtils.isMailboxMode()) {
            return true;
        }

        // Short circuit the menu
//        menu = mActionMenuView.getMenu();

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
        switch (item.getItemId()) {
            case R.id.search:
                if (getResources().getBoolean(R.bool.config_classify_search)) {
                    Intent searchintent = new Intent(this, SearchActivityExtend.class);
                    startActivityIfNeeded(searchintent, -1);
                    break;
                }
                return true;
            case R.id.action_delete_all:
                // The invalid threadId of -1 means all threads here.
                ThreadUtil.confirmDeleteThread(-1L, mQueryHandler);
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

    /* package */ StickyListHeadersListView getListView() {
        return mListView;
    }

    /* package */ NextConversationListAdapter getAdapter() {
        return mListAdapter;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo
            menuInfo) {
        if (mListAdapter == null) {
            return;
        }
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
    public void onDraftChanged(final long threadId, final boolean hasDraft) {
        mQueryHandler.post(new Runnable() {
            @Override
            public void run() {
                logd("onDraftChanged: threadId=" + threadId + ", hasDraft=" + hasDraft);
                mListAdapter.notifyDataSetChanged();
            }
        });
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.floating_action_button:
                if (mIsSmsEnabled) {
                    createNewMessage();
                } else {
                    // Display a toast letting the user know they can not compose.
                    if (mComposeDisabledToast == null) {
                        mComposeDisabledToast = Toast.makeText(this,
                                R.string.compose_disabled_toast, Toast.LENGTH_SHORT);
                    }
                    mComposeDisabledToast.show();
                }
                break;
            default:
                break;
        }

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Note: don't read the thread id data from the ConversationListItem view passed in.
        // It's unreliable to read the cached data stored in the view because the ListItem
        // can be recycled, and the same view could be assigned to a different position
        // if you click the list item fast enough. Instead, get the cursor at the position
        // clicked and load the data from the cursor.
        // (ConversationListAdapter extends CursorAdapter, so getItemAtPosition() should
        // return the cursor object, which is moved to the position passed in)
        Cursor cursor = (Cursor) mListView.getWrappedList().getItemAtPosition(position);
        Conversation conv = Conversation.from(this, cursor);
        long tid = conv.getThreadId();

        if (LogTag.VERBOSE) {
            Log.d(TAG, "onListItemClick: pos=" + position + ", view=" + view + ", tid=" + tid);
        }

        openThread(tid);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        Intent intent = new Intent();
        intent.setClass(this, SearchActivity.class);
        intent.putExtra(SearchManager.QUERY, query);
        startActivity(intent);
        mSearchItem.collapseActionView();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    public static class DeleteThreadListener implements DialogInterface.OnClickListener {
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
                            int token = ThreadListQueryHandler.DELETE_CONVERSATION_TOKEN;
                            if (mInfo.getDeleteType() != com.android.mms.ui.ConversationList
                                    .MessageDeleteTypes.ALL) {
                                token = ComposeMessageActivity.DELETE_MESSAGE_TOKEN;
                            }

                            if (mCallBack != null) {
                                mCallBack.run();
                            }
                            if (mContext instanceof ConversationList) {
//                                ((ConversationList) mContext).unbindListeners(mInfo.getThreadIds());
                            }
                            if (mInfo.getThreadIds() == null) {
                                Conversation.startDeleteAll(mHandler, token, mDeleteLockedMessages);
                                DraftCache.getInstance().refresh();
                            } else {
                                int size = mInfo.getThreadIds().size();
                                if (size > 0 && mCallBack != null) {
                                    // Save the last thread id.
                                    // And cancel deleting dialog after this thread been deleted.
                                    mLastDeletedThread = (mInfo.getThreadIds()
                                            .toArray(new Long[size]))[size - 1];
                                }
                                Conversation.startDelete(mHandler, token, mDeleteLockedMessages,
                                        mInfo.getThreadIds(), mInfo.getDeleteType());
                            }
                        }
                    });
            dialog.dismiss();
        }

    }

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

    private class ModeCallback implements ListView.MultiChoiceModeListener {
        private HashSet<Long> mSelectedThreadIds;

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = getMenuInflater();
            mSelectedThreadIds = new HashSet<Long>();
            inflater.inflate(R.menu.conversation_multi_select_menu, menu);
            if (mFilterSpinner.getVisibility() == View.VISIBLE) {
                mFilterSpinner.setEnabled(false);
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete:
                    if (mSelectedThreadIds.size() > 0) {
                        ThreadUtil.confirmDeleteThreads(mSelectedThreadIds, mQueryHandler);
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
                                        mSelectedThreadIds, mQueryHandler,
                                        NextConversationListActivity.this),
                                mSelectedThreadIds, NextConversationListActivity.this);
                    }
                    mode.finish();
                    break;
                case R.id.markAsRead:
                    if (mSelectedThreadIds.size() > 0) {
                        confirmMarkAsReadDialog(
                                new MarkAsReadThreadListener(
                                        mSelectedThreadIds, mQueryHandler,
                                        NextConversationListActivity.this),
                                mSelectedThreadIds, NextConversationListActivity.this);
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
            ConversationListAdapter adapter = (ConversationListAdapter) getListView().getAdapter();
            adapter.uncheckAll();
            mSelectedThreadIds = null;
            if (mFilterSpinner.getVisibility() == View.VISIBLE) {
                mFilterSpinner.setEnabled(true);
            }
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                int position, long id, boolean checked) {
            final int checkedCount = mListView.getCheckedItemCount();

            mode.setTitle(getString(R.string.selected_count, checkedCount));
            mode.getMenu().findItem(R.id.selection_toggle).setTitle(getString(
                    allItemsSelected() ? R.string.deselected_all : R.string.selected_all));

            Cursor cursor = (Cursor) mListView.getItemAtPosition(position);
            Conversation conv = Conversation.from(NextConversationListActivity.this, cursor);
            conv.setIsChecked(checked);
            long threadId = conv.getThreadId();

            if (checked) {
                mSelectedThreadIds.add(threadId);
            } else {
                mSelectedThreadIds.remove(threadId);
            }
        }

        private boolean allItemsSelected() {
            return mListView.getCount() == mListView.getCheckedItemCount();
        }
    }

    public static class MarkAsUnreadThreadListener implements DialogInterface.OnClickListener {
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
                            int token = ThreadListQueryHandler.MARK_CONVERSATION_UNREAD_TOKEN;
                            if (mThreadIds == null) {
                                Conversation.startMarkAsUnreadAll(mContext, mHandler, token);
                                DraftCache.getInstance().refresh();
                            } else {
                                Conversation
                                        .startMarkAsUnread(mContext, mHandler, token, mThreadIds);
                            }
                        }
                    });
            dialog.dismiss();
        }
    }

    public static class MarkAsReadThreadListener implements DialogInterface.OnClickListener {
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
                            int token = ThreadListQueryHandler.MARK_CONVERSATION_READ_TOKEN;
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

}
